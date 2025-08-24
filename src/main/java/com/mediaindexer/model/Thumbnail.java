package com.mediaindexer.model;

import java.time.LocalDateTime;

public class Thumbnail {
    private Long id;
    private Long mediaFileId;
    private String thumbnailPath;
    private int width;
    private int height;
    private int orientation;
    private String format;
    private LocalDateTime createdAt;
    private boolean failed;
    private String errorMessage;
    private String errorType;

    public Thumbnail() {}

    public Thumbnail(Long mediaFileId, String thumbnailPath, int width, int height, 
                    int orientation, String format) {
        this.mediaFileId = mediaFileId;
        this.thumbnailPath = thumbnailPath;
        this.width = width;
        this.height = height;
        this.orientation = orientation;
        this.format = format;
        this.createdAt = LocalDateTime.now();
        this.failed = false;
        this.errorMessage = null;
        this.errorType = null;
    }

    public Thumbnail(Long mediaFileId, String thumbnailPath, int width, int height, 
                    int orientation, String format, boolean failed, String errorMessage) {
        this.mediaFileId = mediaFileId;
        this.thumbnailPath = thumbnailPath;
        this.width = width;
        this.height = height;
        this.orientation = orientation;
        this.format = format;
        this.createdAt = LocalDateTime.now();
        this.failed = failed;
        this.errorMessage = errorMessage;
        this.errorType = null;
    }

    public Thumbnail(Long mediaFileId, String thumbnailPath, int width, int height, 
                    int orientation, String format, boolean failed, String errorMessage, String errorType) {
        this.mediaFileId = mediaFileId;
        this.thumbnailPath = thumbnailPath;
        this.width = width;
        this.height = height;
        this.orientation = orientation;
        this.format = format;
        this.createdAt = LocalDateTime.now();
        this.failed = failed;
        this.errorMessage = errorMessage;
        this.errorType = errorType;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getMediaFileId() {
        return mediaFileId;
    }

    public void setMediaFileId(Long mediaFileId) {
        this.mediaFileId = mediaFileId;
    }

    public String getThumbnailPath() {
        return thumbnailPath;
    }

    public void setThumbnailPath(String thumbnailPath) {
        this.thumbnailPath = thumbnailPath;
    }

    public int getWidth() {
        return width;
    }

    public void setWidth(int width) {
        this.width = width;
    }

    public int getHeight() {
        return height;
    }

    public void setHeight(int height) {
        this.height = height;
    }

    public int getOrientation() {
        return orientation;
    }

    public void setOrientation(int orientation) {
        this.orientation = orientation;
    }

    public String getFormat() {
        return format;
    }

    public void setFormat(String format) {
        this.format = format;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public boolean isFailed() {
        return failed;
    }

    public void setFailed(boolean failed) {
        this.failed = failed;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public void setErrorMessage(String errorMessage) {
        this.errorMessage = errorMessage;
    }

    public String getErrorType() {
        return errorType;
    }

    public void setErrorType(String errorType) {
        this.errorType = errorType;
    }
}