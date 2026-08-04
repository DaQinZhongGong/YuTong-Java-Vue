package com.yutong.system.file.service;

import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * 将内存中的 byte[] 适配为 {@link MultipartFile}，便于复用 {@link FileService#upload}
 * 上传导出生成的 CSV / 导入源文件 / 导入错误报告等内存内容到 MinIO。
 *
 * <p>设计来源: 52-后端服务分工与接口实现详设 — 导入导出异步任务需在异步线程中上传内存内容，
 * 此时原始请求的 MultipartFile 流已关闭，需要先用 {@code file.getBytes()} 读取字节再包装。
 *
 * <p>包级可见: 仅 {@link FileService#uploadBytes} 内部使用，不对外暴露适配细节。
 */
class ByteArrayMultipartFile implements MultipartFile {

    private final String name;
    private final String originalFilename;
    private final String contentType;
    private final byte[] content;

    ByteArrayMultipartFile(String name, String originalFilename, String contentType, byte[] content) {
        this.name = name;
        this.originalFilename = originalFilename;
        this.contentType = contentType;
        this.content = content != null ? content : new byte[0];
    }

    @Override
    public String getName() {
        return this.name;
    }

    @Override
    public String getOriginalFilename() {
        return this.originalFilename;
    }

    @Override
    public String getContentType() {
        return this.contentType;
    }

    @Override
    public boolean isEmpty() {
        return this.content.length == 0;
    }

    @Override
    public long getSize() {
        return this.content.length;
    }

    @Override
    public byte[] getBytes() {
        return this.content;
    }

    @Override
    public InputStream getInputStream() {
        return new ByteArrayInputStream(this.content);
    }

    @Override
    public void transferTo(File dest) throws IOException {
        Files.write(dest.toPath(), this.content);
    }

    @Override
    public void transferTo(Path dest) throws IOException, IllegalStateException {
        Files.write(dest, this.content);
    }
}
