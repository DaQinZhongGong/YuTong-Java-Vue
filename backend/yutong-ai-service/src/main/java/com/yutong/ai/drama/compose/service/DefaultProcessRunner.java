package com.yutong.ai.drama.compose.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * {@link ProcessRunner} 默认实现 — 基于 {@link ProcessBuilder}。
 *
 * <p>生产约束:
 * <ul>
 *   <li>命令以 List 传参, 不走 shell, 无注入面</li>
 *   <li>stdout/stderr 分开消费 (单线程顺序读, 先 stdout 后 stderr, 避免管道阻塞;
 *       ffmpeg 日志量中等, -loglevel error 下远小于管道缓冲)</li>
 *   <li>超时后 destroyForcibly, 避免僵尸 ffmpeg 占 CPU</li>
 *   <li>输出截断保留尾部 8KB (排障只看尾部)</li>
 * </ul>
 */
@Component
public class DefaultProcessRunner implements ProcessRunner {

    private static final Logger log = LoggerFactory.getLogger(DefaultProcessRunner.class);

    /** 保留输出尾部字节数 */
    static final int TAIL_LIMIT = 8 * 1024;

    @Override
    public ProcessResult run(List<String> command, long timeoutSec) {
        Process process;
        try {
            process = new ProcessBuilder(command).redirectErrorStream(false).start();
        } catch (Exception e) {
            // 二进制缺失 / 无执行权限等: 调用方按失败关闭处理
            log.error("process start failed cmd={}", command.isEmpty() ? "?" : command.get(0), e);
            throw new ProcessStartException("进程启动失败: " + e.getMessage(), e);
        }
        boolean finished;
        try {
            finished = process.waitFor(Math.max(1, timeoutSec), TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            process.destroyForcibly();
            return new ProcessResult(-1, "", "interrupted", true);
        }
        if (!finished) {
            process.destroyForcibly();
            String err = drain(process);
            return new ProcessResult(-1, "", truncate("TIMEOUT after " + timeoutSec + "s\n" + err), true);
        }
        int exit = process.exitValue();
        String out = readAll(process, true);
        String err = readAll(process, false);
        return new ProcessResult(exit, truncate(out), truncate(err), false);
    }

    private String drain(Process process) {
        try {
            return readAll(process, false);
        } catch (Exception e) {
            return "";
        }
    }

    private String readAll(Process process, boolean stdout) {
        StringBuilder sb = new StringBuilder();
        try (BufferedReader br = new BufferedReader(new InputStreamReader(
                stdout ? process.getInputStream() : process.getErrorStream(), StandardCharsets.UTF_8))) {
            char[] buf = new char[4096];
            int n;
            while ((n = br.read(buf)) != -1) {
                sb.append(buf, 0, n);
                if (sb.length() > TAIL_LIMIT * 2) {
                    sb.delete(0, sb.length() - TAIL_LIMIT * 2);
                }
            }
        } catch (Exception e) {
            log.debug("read process stream failed", e);
        }
        return sb.toString();
    }

    private String truncate(String s) {
        if (s == null) return "";
        if (s.length() <= TAIL_LIMIT) return s;
        return s.substring(s.length() - TAIL_LIMIT);
    }

    /**
     * 进程无法启动 (二进制缺失/无权限)。调用方将其转为失败关闭, 不伪装成功。
     */
    public static class ProcessStartException extends RuntimeException {
        public ProcessStartException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
