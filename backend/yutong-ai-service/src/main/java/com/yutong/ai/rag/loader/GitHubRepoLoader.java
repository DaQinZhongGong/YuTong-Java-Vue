package com.yutong.ai.rag.loader;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.*;

/**
 * GitHub 仓库加载器 — 从 GitHub 仓库拉取文本文件用于 RAG 入库。
 * 落点: 业界同类实现 GitHubLoader + ADR 0005 P3。
 *
 * <p>能力:
 * <ul>
 *   <li>解析 GitHub 仓库 URL → owner/repo/branch</li>
 *   <li>调用 GitHub API 获取文件树 (git/trees?recursive=1)</li>
 *   <li>过滤文本文件 (md/txt/code 等), 按大小限制下载</li>
 *   <li>返回 Map&lt;filePath, content&gt; 供上层入库</li>
 * </ul>
 *
 * <p>限制:
 * <ul>
 *   <li>单文件最大 1MB (GitHub API blob 限制)</li>
 *   <li>最多 100 个文件 (防止 API 滥用)</li>
 *   <li>需要 GitHub Token 时走 Authorization 头 (可选, 公开仓库无需)</li>
 *   <li>失败关闭: API 调用失败/超时抛异常, 不返回部分结果</li>
 * </ul>
 */
@Service
public class GitHubRepoLoader {

    private static final Logger log = LoggerFactory.getLogger(GitHubRepoLoader.class);

    private static final String GITHUB_API = "https://api.github.com";
    private static final int MAX_FILES = 100;
    private static final long MAX_FILE_SIZE = 1024 * 1024; // 1MB
    private static final Set<String> TEXT_EXTENSIONS = Set.of(
            "md", "txt", "rst", "adoc",
            "java", "kt", "py", "js", "ts", "go", "rs", "c", "h", "cpp", "cs",
            "sql", "sh", "yaml", "yml", "json", "xml", "properties", "toml",
            "html", "css", "vue", "svelte"
    );

    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;

    public GitHubRepoLoader(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
        this.httpClient = HttpClient.newBuilder()
                .version(HttpClient.Version.HTTP_1_1)
                .connectTimeout(Duration.ofSeconds(10))
                .build();
    }

    /**
     * 解析结果。
     */
    public record RepoInfo(String owner, String repo, String branch) {}

    /**
     * 加载结果。
     */
    public record LoadResult(
            String owner,
            String repo,
            String branch,
            int fileCount,
            Map<String, String> files
    ) {}

    /**
     * 从 GitHub 仓库加载文本文件。
     *
     * @param repoUrl GitHub 仓库 URL (如 https://github.com/owner/repo)
     * @param branch  分支名 (可空, 默认 main)
     * @param token   GitHub Token (可空, 公开仓库无需)
     */
    public LoadResult load(String repoUrl, String branch, String token) {
        RepoInfo info = parseRepoUrl(repoUrl);
        String effectiveBranch = (branch == null || branch.isBlank()) ? "main" : branch;

        try {
            // 1. 获取文件树
            List<Map<String, Object>> textFiles = fetchFileTree(info, effectiveBranch, token);

            // 2. 下载文本文件内容
            Map<String, String> files = new LinkedHashMap<>();
            int count = 0;
            for (Map<String, Object> file : textFiles) {
                if (count >= MAX_FILES) {
                    log.warn("[GitHubLoader] 达到文件数上限 {}, 停止下载", MAX_FILES);
                    break;
                }
                String path = (String) file.get("path");
                long size = ((Number) file.get("size")).longValue();
                if (size > MAX_FILE_SIZE) {
                    log.debug("[GitHubLoader] 跳过过大文件: {} ({} bytes)", path, size);
                    continue;
                }
                String content = fetchFileContent(info, effectiveBranch, path, token);
                if (content != null && !content.isBlank()) {
                    files.put(path, content);
                    count++;
                }
            }

            return new LoadResult(info.owner(), info.repo(), effectiveBranch, files.size(), files);
        } catch (Exception e) {
            throw new RuntimeException("GitHub 仓库加载失败: " + repoUrl + " - " + e.getMessage(), e);
        }
    }

    /**
     * 解析 GitHub 仓库 URL。
     * 支持: https://github.com/owner/repo / git@github.com:owner/repo.git
     */
    RepoInfo parseRepoUrl(String url) {
        if (url == null || url.isBlank()) {
            throw new IllegalArgumentException("GitHub 仓库 URL 不能为空");
        }
        url = url.trim();
        // 去掉 .git 后缀
        if (url.endsWith(".git")) {
            url = url.substring(0, url.length() - 4);
        }
        // HTTPS 格式
        if (url.contains("github.com/")) {
            String[] parts = url.split("github\\.com/");
            if (parts.length < 2) throw new IllegalArgumentException("无法解析 GitHub URL: " + url);
            String[] pathParts = parts[1].split("/");
            if (pathParts.length < 2) throw new IllegalArgumentException("GitHub URL 缺少 owner/repo: " + url);
            return new RepoInfo(pathParts[0], pathParts[1], null);
        }
        // SSH 格式: git@github.com:owner/repo
        if (url.contains("github.com:")) {
            String[] parts = url.split("github\\.com:");
            if (parts.length < 2) throw new IllegalArgumentException("无法解析 GitHub SSH URL: " + url);
            String[] pathParts = parts[1].split("/");
            if (pathParts.length < 2) throw new IllegalArgumentException("GitHub SSH URL 缺少 owner/repo: " + url);
            return new RepoInfo(pathParts[0], pathParts[1], null);
        }
        throw new IllegalArgumentException("不支持的 GitHub URL 格式: " + url);
    }

    /**
     * 获取文件树 (仅文本文件)。
     */
    private List<Map<String, Object>> fetchFileTree(RepoInfo info, String branch, String token)
            throws IOException, InterruptedException {
        String url = GITHUB_API + "/repos/" + info.owner() + "/" + info.repo()
                + "/git/trees/" + branch + "?recursive=1";
        JsonNode root = callGitHubApi(url, token);

        List<Map<String, Object>> result = new ArrayList<>();
        JsonNode tree = root.path("tree");
        if (tree.isArray()) {
            for (JsonNode node : tree) {
                if (!"blob".equals(node.path("type").asText())) continue;
                String path = node.path("path").asText();
                long size = node.path("size").asLong(0);
                String ext = extractExtension(path);
                if (ext != null && TEXT_EXTENSIONS.contains(ext)) {
                    result.add(Map.of("path", path, "size", size));
                }
            }
        }
        return result;
    }

    /**
     * 下载单个文件内容。
     */
    private String fetchFileContent(RepoInfo info, String branch, String path, String token)
            throws IOException, InterruptedException {
        // 使用 raw.githubusercontent.com (更简单)
        String rawUrl = "https://raw.githubusercontent.com/" + info.owner() + "/" + info.repo()
                + "/" + branch + "/" + path;
        HttpRequest.Builder builder = HttpRequest.newBuilder()
                .uri(URI.create(rawUrl))
                .timeout(Duration.ofSeconds(15))
                .GET();
        if (token != null && !token.isBlank()) {
            builder.header("Authorization", "Bearer " + token);
        }
        HttpResponse<String> resp = httpClient.send(builder.build(), HttpResponse.BodyHandlers.ofString());
        if (resp.statusCode() == 200) {
            return resp.body();
        }
        log.debug("[GitHubLoader] 下载失败: {} status={}", path, resp.statusCode());
        return null;
    }

    /**
     * 调用 GitHub API。
     */
    private JsonNode callGitHubApi(String url, String token) throws IOException, InterruptedException {
        HttpRequest.Builder builder = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("Accept", "application/vnd.github+json")
                .timeout(Duration.ofSeconds(15))
                .GET();
        if (token != null && !token.isBlank()) {
            builder.header("Authorization", "Bearer " + token);
        }
        HttpResponse<String> resp = httpClient.send(builder.build(), HttpResponse.BodyHandlers.ofString());
        if (resp.statusCode() != 200) {
            throw new IOException("GitHub API 调用失败: HTTP " + resp.statusCode());
        }
        return objectMapper.readTree(resp.body());
    }

    private String extractExtension(String path) {
        int dot = path.lastIndexOf('.');
        if (dot < 0 || dot == path.length() - 1) return null;
        return path.substring(dot + 1).toLowerCase();
    }
}
