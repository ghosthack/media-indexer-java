package com.mediaindexer.model;

public enum ThumbnailErrorType {
    DECODING_ERROR("DECODING", "Image decoding failed"),
    IO_ERROR("IO", "File access/I/O error"),
    UNSUPPORTED_FORMAT("FORMAT", "Unsupported image format"),
    FILE_NOT_FOUND("NOT_FOUND", "File not found"),
    CORRUPTED_FILE("CORRUPTED", "Corrupted or invalid file");
    
    private final String code;
    private final String description;
    
    ThumbnailErrorType(String code, String description) {
        this.code = code;
        this.description = description;
    }
    
    public String getCode() {
        return code;
    }
    
    public String getDescription() {
        return description;
    }
    
    public static ThumbnailErrorType fromCode(String code) {
        for (ThumbnailErrorType type : values()) {
            if (type.code.equals(code)) {
                return type;
            }
        }
        return null;
    }
}