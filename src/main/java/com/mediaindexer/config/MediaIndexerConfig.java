package com.mediaindexer.config;

import java.util.ArrayList;
import java.util.List;

public class MediaIndexerConfig {
    private String databasePath = "media-index.db";
    private String thumbnailOutputDir = "output/thumbnails";
    private String htmlOutputDir = "output/html";
    private List<String> scanRoots = new ArrayList<>();
    
    private ThumbnailConfig thumbnail = new ThumbnailConfig();
    private MiniThumbnailConfig miniThumbnail = new MiniThumbnailConfig();
    private HtmlConfig html = new HtmlConfig();
    private HashingConfig hashing = new HashingConfig();
    private PerformanceConfig performance = new PerformanceConfig();
    
    public static class ThumbnailConfig {
        private int maxDimension = 512;
        private float quality = 0.85f;
        private String format = "JPEG";
        private boolean respectExifOrientation = true;
        
        public int getMaxDimension() { return maxDimension; }
        public void setMaxDimension(int maxDimension) { this.maxDimension = maxDimension; }
        
        public float getQuality() { return quality; }
        public void setQuality(float quality) { this.quality = quality; }
        
        public String getFormat() { return format; }
        public void setFormat(String format) { this.format = format; }
        
        public boolean isRespectExifOrientation() { return respectExifOrientation; }
        public void setRespectExifOrientation(boolean respectExifOrientation) { this.respectExifOrientation = respectExifOrientation; }
    }
    
    public static class MiniThumbnailConfig {
        private int maxHeight = 100;
        private float quality = 0.85f;
        private String format = "JPEG";
        private boolean respectExifOrientation = true;
        
        public int getMaxHeight() { return maxHeight; }
        public void setMaxHeight(int maxHeight) { this.maxHeight = maxHeight; }
        
        public float getQuality() { return quality; }
        public void setQuality(float quality) { this.quality = quality; }
        
        public String getFormat() { return format; }
        public void setFormat(String format) { this.format = format; }
        
        public boolean isRespectExifOrientation() { return respectExifOrientation; }
        public void setRespectExifOrientation(boolean respectExifOrientation) { this.respectExifOrientation = respectExifOrientation; }
    }
    
    public static class HtmlConfig {
        private long maxPageSizeBytes = 200 * 1024 * 1024; // 200MB
        private String indexFileName = "index.html";
        
        public long getMaxPageSizeBytes() { return maxPageSizeBytes; }
        public void setMaxPageSizeBytes(long maxPageSizeBytes) { this.maxPageSizeBytes = maxPageSizeBytes; }
        
        public String getIndexFileName() { return indexFileName; }
        public void setIndexFileName(String indexFileName) { this.indexFileName = indexFileName; }
    }
    
    public static class HashingConfig {
        private String contentHashAlgorithm = "SHA-256";
        
        public String getContentHashAlgorithm() { return contentHashAlgorithm; }
        public void setContentHashAlgorithm(String contentHashAlgorithm) { this.contentHashAlgorithm = contentHashAlgorithm; }
    }
    
    public static class PerformanceConfig {
        private int thumbnailThreads = Runtime.getRuntime().availableProcessors();
        private int bufferPoolSize = 10;
        private int maxMemoryMB = 1024;
        
        public int getThumbnailThreads() { return thumbnailThreads; }
        public void setThumbnailThreads(int thumbnailThreads) { this.thumbnailThreads = thumbnailThreads; }
        
        public int getBufferPoolSize() { return bufferPoolSize; }
        public void setBufferPoolSize(int bufferPoolSize) { this.bufferPoolSize = bufferPoolSize; }
        
        public int getMaxMemoryMB() { return maxMemoryMB; }
        public void setMaxMemoryMB(int maxMemoryMB) { this.maxMemoryMB = maxMemoryMB; }
    }
    
    public String getDatabasePath() { return databasePath; }
    public void setDatabasePath(String databasePath) { this.databasePath = databasePath; }
    
    public String getThumbnailOutputDir() { return thumbnailOutputDir; }
    public void setThumbnailOutputDir(String thumbnailOutputDir) { this.thumbnailOutputDir = thumbnailOutputDir; }
    
    public String getHtmlOutputDir() { return htmlOutputDir; }
    public void setHtmlOutputDir(String htmlOutputDir) { this.htmlOutputDir = htmlOutputDir; }
    
    public List<String> getScanRoots() { return scanRoots; }
    public void setScanRoots(List<String> scanRoots) { this.scanRoots = scanRoots; }
    
    public ThumbnailConfig getThumbnail() { return thumbnail; }
    public void setThumbnail(ThumbnailConfig thumbnail) { this.thumbnail = thumbnail; }
    
    public MiniThumbnailConfig getMiniThumbnail() { return miniThumbnail; }
    public void setMiniThumbnail(MiniThumbnailConfig miniThumbnail) { this.miniThumbnail = miniThumbnail; }
    
    public HtmlConfig getHtml() { return html; }
    public void setHtml(HtmlConfig html) { this.html = html; }
    
    public HashingConfig getHashing() { return hashing; }
    public void setHashing(HashingConfig hashing) { this.hashing = hashing; }
    
    public PerformanceConfig getPerformance() { return performance; }
    public void setPerformance(PerformanceConfig performance) { this.performance = performance; }
}