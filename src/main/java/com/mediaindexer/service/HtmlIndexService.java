package com.mediaindexer.service;

import com.mediaindexer.config.MediaIndexerConfig;
import com.mediaindexer.model.MediaFile;
import com.mediaindexer.model.MiniThumbnail;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public class HtmlIndexService {
    private static final Logger logger = LoggerFactory.getLogger(HtmlIndexService.class);
    
    private final DatabaseService databaseService;
    private final MediaIndexerConfig config;
    
    public HtmlIndexService(DatabaseService databaseService, MediaIndexerConfig config) {
        this.databaseService = databaseService;
        this.config = config;
    }
    
    public void generateHtmlIndex() throws SQLException, IOException {
        logger.info("Starting HTML index generation");
        
        Files.createDirectories(Paths.get(config.getHtmlOutputDir()));
        
        List<MiniThumbnail> miniThumbnails = databaseService.getAllMiniThumbnails();
        Map<Long, MediaFile> mediaFileMap = createMediaFileMap();
        
        logger.info("Found {} mini thumbnails to include in HTML index", miniThumbnails.size());
        
        generatePaginatedHtmlPages(miniThumbnails, mediaFileMap);
        
        logger.info("HTML index generation completed");
    }
    
    private Map<Long, MediaFile> createMediaFileMap() throws SQLException {
        List<MediaFile> allFiles = databaseService.getAllMediaFiles();
        Map<Long, MediaFile> fileMap = new HashMap<>();
        
        for (MediaFile file : allFiles) {
            fileMap.put(file.getId(), file);
        }
        
        return fileMap;
    }
    
    private void generatePaginatedHtmlPages(List<MiniThumbnail> miniThumbnails, 
                                          Map<Long, MediaFile> mediaFileMap) throws IOException {
        long maxPageSize = config.getHtml().getMaxPageSizeBytes();
        int pageNumber = 1;
        long currentPageSize = 0;
        StringBuilder currentPageContent = new StringBuilder();
        int totalPages = estimateTotalPages(miniThumbnails, maxPageSize);
        
        for (int i = 0; i < miniThumbnails.size(); i++) {
            MiniThumbnail miniThumbnail = miniThumbnails.get(i);
            MediaFile mediaFile = mediaFileMap.get(miniThumbnail.getMediaFileId());
            
            if (mediaFile == null) {
                logger.warn("Media file not found for mini thumbnail ID: {}", miniThumbnail.getId());
                continue;
            }
            
            String thumbnailHtml = createThumbnailHtml(miniThumbnail, mediaFile);
            long thumbnailSize = thumbnailHtml.getBytes(StandardCharsets.UTF_8).length;
            
            if (currentPageSize + thumbnailSize > maxPageSize && currentPageContent.length() > 0) {
                writeHtmlPage(currentPageContent.toString(), pageNumber, totalPages);
                pageNumber++;
                currentPageContent = new StringBuilder();
                currentPageSize = 0;
            }
            
            currentPageContent.append(thumbnailHtml);
            currentPageSize += thumbnailSize;
        }
        
        if (currentPageContent.length() > 0) {
            writeHtmlPage(currentPageContent.toString(), pageNumber, totalPages);
        }
        
        logger.info("Generated {} HTML pages", pageNumber);
    }
    
    private int estimateTotalPages(List<MiniThumbnail> miniThumbnails, long maxPageSize) {
        if (miniThumbnails.isEmpty()) return 1;

        // FIXME need to fix the null base64Data case here, this is due no thumbnail and no graceful handling
        long averageSize = miniThumbnails.stream()
            .mapToLong(mt -> Optional.ofNullable(mt.getBase64Data()).orElse("").length())
            .sum() / miniThumbnails.size();
        
        long estimatedItemsPerPage = maxPageSize / (averageSize + 500); // 500 bytes for HTML overhead
        return (int) Math.ceil((double) miniThumbnails.size() / estimatedItemsPerPage);
    }
    
    private String createThumbnailHtml(MiniThumbnail miniThumbnail, MediaFile mediaFile) {
        String fileName = Paths.get(mediaFile.getFilePath()).getFileName().toString();
        String encodedPath = encodeFileUri(mediaFile.getFilePath());
        // FIXME need to fix the null format and base64Data, this is due no thumbnail and no graceful handling
        String base64Image = "data:image/" + Optional.ofNullable(miniThumbnail.getFormat()).orElse("").toLowerCase() +
                           ";base64," + Optional.ofNullable(miniThumbnail.getBase64Data()).orElse("");
        
        return String.format(
            "<div class=\"thumbnail-container\">" +
            "<a href=\"%s\" title=\"%s\" tabindex=\"0\">" +
            "<img src=\"%s\" alt=\"%s\" width=\"%d\" height=\"%d\" loading=\"lazy\" />" +
            "</a>" +
            "</div>\n",
            encodedPath,
            escapeHtml(fileName),
            base64Image,
            escapeHtml(fileName),
            miniThumbnail.getWidth(),
            miniThumbnail.getHeight()
        );
    }
    
    private void writeHtmlPage(String content, int pageNumber, int totalPages) throws IOException {
        String fileName = pageNumber == 1 ? 
            config.getHtml().getIndexFileName() : 
            String.format("index-page-%d.html", pageNumber);
        
        Path filePath = Paths.get(config.getHtmlOutputDir(), fileName);
        
        String htmlContent = createHtmlDocument(content, pageNumber, totalPages);
        Files.writeString(filePath, htmlContent, StandardCharsets.UTF_8);
        
        logger.debug("Generated HTML page: {}", filePath);
    }
    
    private String createHtmlDocument(String content, int pageNumber, int totalPages) {
        String navigation = createNavigation(pageNumber, totalPages);
        
        return String.format("""
            <!DOCTYPE html>
            <html lang="en">
            <head>
                <meta charset="UTF-8">
                <meta name="viewport" content="width=device-width, initial-scale=1.0">
                <title>Media Index - Page %d of %d</title>
                <style>
                    body {
                        font-family: Arial, sans-serif;
                        margin: 0;
                        padding: 20px;
                        background-color: #f5f5f5;
                    }
                    
                    .header {
                        text-align: center;
                        margin-bottom: 20px;
                    }
                    
                    .navigation {
                        text-align: center;
                        margin: 20px 0;
                        padding: 15px;
                        background-color: white;
                        border-radius: 5px;
                        box-shadow: 0 2px 5px rgba(0,0,0,0.1);
                    }
                    
                    .navigation a {
                        text-decoration: none;
                        color: #007bff;
                        margin: 0 10px;
                        padding: 8px 16px;
                        border: 1px solid #007bff;
                        border-radius: 3px;
                        display: inline-block;
                    }
                    
                    .navigation a:hover {
                        background-color: #007bff;
                        color: white;
                    }
                    
                    .navigation a.current {
                        background-color: #007bff;
                        color: white;
                    }
                    
                    .gallery {
                        display: grid;
                        grid-template-columns: repeat(auto-fill, minmax(120px, 1fr));
                        gap: 10px;
                        max-width: 1200px;
                        margin: 0 auto;
                    }
                    
                    .thumbnail-container {
                        background-color: white;
                        border-radius: 5px;
                        padding: 8px;
                        box-shadow: 0 2px 5px rgba(0,0,0,0.1);
                        transition: transform 0.2s ease;
                    }
                    
                    .thumbnail-container:hover {
                        transform: scale(1.05);
                    }
                    
                    .thumbnail-container img {
                        width: 100%%;
                        height: auto;
                        border-radius: 3px;
                    }
                    
                    .thumbnail-container a {
                        display: block;
                        text-decoration: none;
                    }
                    
                    .thumbnail-container a:focus {
                        outline: 2px solid #007bff;
                        outline-offset: 2px;
                    }
                    
                    @media (max-width: 768px) {
                        .gallery {
                            grid-template-columns: repeat(auto-fill, minmax(100px, 1fr));
                            gap: 8px;
                        }
                        
                        .navigation a {
                            margin: 5px;
                            padding: 6px 12px;
                            font-size: 14px;
                        }
                    }
                </style>
            </head>
            <body>
                <div class="header">
                    <h1>Media Index</h1>
                    <p>Page %d of %d</p>
                </div>
                
                %s
                
                <div class="gallery">
                    %s
                </div>
                
                %s
                
                <div style="text-align: center; margin-top: 40px; color: #666; font-size: 14px;">
                    <p>Generated by Media Indexer</p>
                </div>
            </body>
            </html>
            """, pageNumber, totalPages, pageNumber, totalPages, navigation, content, navigation);
    }
    
    private String createNavigation(int currentPage, int totalPages) {
        if (totalPages <= 1) {
            return "";
        }
        
        StringBuilder nav = new StringBuilder("<div class=\"navigation\">");
        
        if (currentPage > 1) {
            nav.append("<a href=\"").append(getPageFileName(currentPage - 1))
               .append("\">&laquo; Previous</a>");
        }
        
        int startPage = Math.max(1, currentPage - 5);
        int endPage = Math.min(totalPages, currentPage + 5);
        
        if (startPage > 1) {
            nav.append("<a href=\"").append(getPageFileName(1)).append("\">1</a>");
            if (startPage > 2) {
                nav.append("<span>...</span>");
            }
        }
        
        for (int i = startPage; i <= endPage; i++) {
            if (i == currentPage) {
                nav.append("<a href=\"").append(getPageFileName(i))
                   .append("\" class=\"current\">").append(i).append("</a>");
            } else {
                nav.append("<a href=\"").append(getPageFileName(i))
                   .append("\">").append(i).append("</a>");
            }
        }
        
        if (endPage < totalPages) {
            if (endPage < totalPages - 1) {
                nav.append("<span>...</span>");
            }
            nav.append("<a href=\"").append(getPageFileName(totalPages))
               .append("\">").append(totalPages).append("</a>");
        }
        
        if (currentPage < totalPages) {
            nav.append("<a href=\"").append(getPageFileName(currentPage + 1))
               .append("\">Next &raquo;</a>");
        }
        
        nav.append("</div>");
        return nav.toString();
    }
    
    private String getPageFileName(int pageNumber) {
        return pageNumber == 1 ? 
            config.getHtml().getIndexFileName() : 
            String.format("index-page-%d.html", pageNumber);
    }
    
    private String encodeFileUri(String filePath) {
        try {
            Path path = Paths.get(filePath);
            StringBuilder encodedPath = new StringBuilder("file://");
            
            for (int i = 0; i < path.getNameCount(); i++) {
                if (i > 0) encodedPath.append("/");
                encodedPath.append(URLEncoder.encode(path.getName(i).toString(), StandardCharsets.UTF_8));
            }
            
            return encodedPath.toString();
        } catch (Exception e) {
            logger.warn("Failed to encode file URI: {}", filePath, e);
            return "file://" + filePath;
        }
    }
    
    private String escapeHtml(String text) {
        if (text == null) return "";
        
        return text.replace("&", "&amp;")
                  .replace("<", "&lt;")
                  .replace(">", "&gt;")
                  .replace("\"", "&quot;")
                  .replace("'", "&#x27;");
    }
}