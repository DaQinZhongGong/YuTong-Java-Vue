package com.yutong.ai.rag.loader;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * GitHubRepoLoader URL 解析测试。
 * (网络调用不测, 仅测纯函数逻辑)
 */
class GitHubRepoLoaderTest {

    private GitHubRepoLoader loader;

    @BeforeEach
    void setUp() {
        loader = new GitHubRepoLoader(new ObjectMapper());
    }

    @Test
    void parseRepoUrl_https() {
        var info = loader.parseRepoUrl("https://github.com/octocat/Hello-World");
        assertEquals("octocat", info.owner());
        assertEquals("Hello-World", info.repo());
    }

    @Test
    void parseRepoUrl_httpsWithGit() {
        var info = loader.parseRepoUrl("https://github.com/octocat/Hello-World.git");
        assertEquals("octocat", info.owner());
        assertEquals("Hello-World", info.repo());
    }

    @Test
    void parseRepoUrl_ssh() {
        var info = loader.parseRepoUrl("git@github.com:octocat/Hello-World.git");
        assertEquals("octocat", info.owner());
        assertEquals("Hello-World", info.repo());
    }

    @Test
    void parseRepoUrl_trailingSlash() {
        var info = loader.parseRepoUrl("https://github.com/octocat/Hello-World/");
        assertEquals("octocat", info.owner());
        assertEquals("Hello-World", info.repo());
    }

    @Test
    void parseRepoUrl_null_throws() {
        assertThrows(IllegalArgumentException.class, () -> loader.parseRepoUrl(null));
        assertThrows(IllegalArgumentException.class, () -> loader.parseRepoUrl(""));
    }

    @Test
    void parseRepoUrl_invalid_throws() {
        assertThrows(IllegalArgumentException.class, () -> loader.parseRepoUrl("https://example.com/foo/bar"));
        assertThrows(IllegalArgumentException.class, () -> loader.parseRepoUrl("https://github.com/onlyowner"));
    }
}
