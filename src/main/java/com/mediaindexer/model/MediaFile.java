package com.mediaindexer.model;

import java.time.LocalDateTime;

public class MediaFile {
    private Long id;
    private String filePath;
    private String extension;
    private long fileSize;
    private LocalDateTime lastModified;
    private LocalDateTime lastScanned;
    private String quickHash;
    private String contentHash;

    public MediaFile() {}

    public MediaFile(String filePath, String extension, long fileSize, LocalDateTime lastModified) {
        this.filePath = filePath;
        this.extension = extension;
        this.fileSize = fileSize;
        this.lastModified = lastModified;
        this.lastScanned = LocalDateTime.now();
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getFilePath() {
        return filePath;
    }

    public void setFilePath(String filePath) {
        this.filePath = filePath;
    }

    public String getExtension() {
        return extension;
    }

    public void setExtension(String extension) {
        this.extension = extension;
    }

    public long getFileSize() {
        return fileSize;
    }

    public void setFileSize(long fileSize) {
        this.fileSize = fileSize;
    }

    public LocalDateTime getLastModified() {
        return lastModified;
    }

    public void setLastModified(LocalDateTime lastModified) {
        this.lastModified = lastModified;
    }

    public LocalDateTime getLastScanned() {
        return lastScanned;
    }

    public void setLastScanned(LocalDateTime lastScanned) {
        this.lastScanned = lastScanned;
    }

    public String getQuickHash() {
        return quickHash;
    }

    public void setQuickHash(String quickHash) {
        this.quickHash = quickHash;
    }

    public String getContentHash() {
        return contentHash;
    }

    public void setContentHash(String contentHash) {
        this.contentHash = contentHash;
    }
}