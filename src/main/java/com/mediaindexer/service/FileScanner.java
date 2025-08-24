package com.mediaindexer.service;

import com.mediaindexer.config.MediaIndexerConfig;
import com.mediaindexer.model.MediaFile;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.attribute.BasicFileAttributes;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Stream;

public class FileScanner {
    private static final Logger logger = LoggerFactory.getLogger(FileScanner.class);
    
    private static final Set<String> SUPPORTED_EXTENSIONS = Set.of(
        ".jpg", ".jpeg", ".png", ".bmp", ".gif", ".webp", ".heic", ".heif",
        ".tiff", ".tif", ".dng", ".raw", ".cr2", ".nef", ".arw",
        ".mp4", ".mov", ".avi", ".mkv", ".webm", ".m4v"
    );
    
    private final DatabaseService databaseService;
    private final MediaIndexerConfig config;
    private final Set<Path> visitedPaths = new HashSet<>();
    private final AtomicLong scannedCount = new AtomicLong(0);
    private final AtomicLong processedCount = new AtomicLong(0);
    
    public FileScanner(DatabaseService databaseService, MediaIndexerConfig config) {
        this.databaseService = databaseService;
        this.config = config;
    }
    
    public void scanAllRoots() {
        logger.info("Starting file scan of {} root directories", config.getScanRoots().size());
        
        for (String rootPath : config.getScanRoots()) {
            try {
                scanDirectory(rootPath);
            } catch (Exception e) {
                logger.error("Failed to scan root directory: {}", rootPath, e);
            }
        }
        
        logger.info("File scan completed. Scanned: {}, Processed: {}", 
                   scannedCount.get(), processedCount.get());
    }
    
    public void scanDirectory(String rootPath) throws IOException, SQLException {
        Path root = Paths.get(rootPath).toAbsolutePath();
        
        if (!Files.exists(root)) {
            logger.warn("Scan root does not exist: {}", root);
            return;
        }
        
        if (!Files.isDirectory(root)) {
            logger.warn("Scan root is not a directory: {}", root);
            return;
        }
        
        logger.info("Scanning directory: {}", root);
        visitedPaths.clear();
        
        try (Stream<Path> paths = Files.walk(root)) {
            paths.filter(this::isMediaFile)
                 .filter(this::checkForInfiniteLoop)
                 .forEach(this::processFile);
        }
        
        logger.info("Completed scanning directory: {}", root);
    }
    
    private boolean isMediaFile(Path path) {
        if (!Files.isRegularFile(path)) {
            return false;
        }
        
        String fileName = path.getFileName().toString().toLowerCase();
        return SUPPORTED_EXTENSIONS.stream().anyMatch(fileName::endsWith);
    }
    
    private boolean checkForInfiniteLoop(Path path) {
        try {
            Path realPath = path.toRealPath();
            if (visitedPaths.contains(realPath)) {
                logger.debug("Skipping already visited path (potential loop): {}", path);
                return false;
            }
            visitedPaths.add(realPath);
            return true;
        } catch (IOException e) {
            logger.debug("Could not resolve real path for {}, skipping", path);
            return false;
        }
    }
    
    private void processFile(Path path) {
        try {
            scannedCount.incrementAndGet();
            
            BasicFileAttributes attrs = Files.readAttributes(path, BasicFileAttributes.class);
            LocalDateTime lastModified = LocalDateTime.ofInstant(
                attrs.lastModifiedTime().toInstant(), ZoneId.systemDefault());
            
            String filePath = path.toString();
            String extension = getFileExtension(path.getFileName().toString());
            long fileSize = attrs.size();
            
            var existingFile = databaseService.findMediaFileByPath(filePath);
            
            if (existingFile.isPresent()) {
                MediaFile existing = existingFile.get();
                if (existing.getLastModified().equals(lastModified) && 
                    existing.getFileSize() == fileSize) {
                    existing.setLastScanned(LocalDateTime.now());
                    databaseService.saveMediaFile(existing);
                    return;
                }
            }
            
            MediaFile mediaFile = new MediaFile(filePath, extension, fileSize, lastModified);
            databaseService.saveMediaFile(mediaFile);
            processedCount.incrementAndGet();
            
            if (processedCount.get() % 1000 == 0) {
                logger.info("Processed {} files...", processedCount.get());
            }
            
        } catch (Exception e) {
            logger.error("Failed to process file: {}", path, e);
        }
    }
    
    private String getFileExtension(String fileName) {
        int lastDot = fileName.lastIndexOf('.');
        return lastDot > 0 ? fileName.substring(lastDot).toLowerCase() : "";
    }
    
    public long getScannedCount() {
        return scannedCount.get();
    }
    
    public long getProcessedCount() {
        return processedCount.get();
    }
}