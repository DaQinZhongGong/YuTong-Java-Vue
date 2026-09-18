package com.yutong.ai.drama.compose.service;

import java.util.List;

/**
 * 进程执行器抽象 — 将 {@link ProcessBuilder} 挡在接口后面, 便于单测 stub。
 * 设计来源: ADR 0004 P2-D 批次 5-C (Windows 开发机无 ffmpeg, 单测不得依赖真实二进制)。
 */
public interface ProcessRunner {

    /**
     * 同步执行命令, 等待结束或超时。
     *
     * @param command    完整命令 (首元素为二进制路径)
     * @param timeoutSec 超时秒数, 超时后杀进程并标记 timedOut
     * @return 执行结果 (stdout/stderr 已截断为尾部, 避免大输出撑内存)
     */
    ProcessResult run(List<String> command, long timeoutSec);

    /**
     * 进程执行结果。
     *
     * @param exitCode   退出码 (超时 kill 时为 -1)
     * @param stdoutTail 标准输出尾部 (截断)
     * @param stderrTail 标准错误尾部 (截断)
     * @param timedOut   是否超时
     */
    record ProcessResult(int exitCode, String stdoutTail, String stderrTail, boolean timedOut) {
    }
}
