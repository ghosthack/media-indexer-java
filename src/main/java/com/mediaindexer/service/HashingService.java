package com.mediaindexer.service;

import com.mediaindexer.config.MediaIndexerConfig;
import com.mediaindexer.model.MediaFile;
import com.mediaindexer.util.HashUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.sql.SQLException;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

public class HashingService {
    private static final Logger logger = LoggerFactory.getLogger(HashingService.class);
    
    private final DatabaseService databaseService;
    private final MediaIndexerConfig config;
    private final AtomicLong processedCount = new AtomicLong(0);
    
    public HashingService(DatabaseService databaseService, MediaIndexerConfig config) {
        this.databaseService = databaseService;
        this.config = config;
    }
    
    public void generateQuickHashes() throws SQLException {
        logger.info("Starting quick hash generation");
        
        List<MediaFile> mediaFiles = databaseService.getAllMediaFiles();
        logger.info("Found {} media files to process", mediaFiles.size());
        
        processedCount.set(0);
        
        for (MediaFile mediaFile : mediaFiles) {
            try {
                if (mediaFile.getQuickHash() == null || mediaFile.getQuickHash().isEmpty()) {
                    String quickHash = HashUtil.computeQuickHash(
                        mediaFile.getFilePath(), 
                        mediaFile.getFileSize(), 
                        mediaFile.getLastModified()
                    );
                    
                    mediaFile.setQuickHash(quickHash);
                    databaseService.saveMediaFile(mediaFile);
                }
                
                processedCount.incrementAndGet();
                
                if (processedCount.get() % 1000 == 0) {
                    logger.info("Generated quick hashes for {} files...", processedCount.get());
                }
                
            } catch (Exception e) {
                logger.error("Failed to generate quick hash for file: {}", mediaFile.getFilePath(), e);
            }
        }
        
        logger.info("Quick hash generation completed. Processed {} files", processedCount.get());
    }
    
    public void generateContentHashes() throws SQLException {
        logger.info("Starting content hash generation");
        
        List<MediaFile> mediaFiles = databaseService.getAllMediaFiles();
        logger.info("Found {} media files to process", mediaFiles.size());
        
        processedCount.set(0);
        String algorithm = config.getHashing().getContentHashAlgorithm();
        
        for (MediaFile mediaFile : mediaFiles) {
            try {
                if (mediaFile.getContentHash() == null || mediaFile.getContentHash().isEmpty()) {
                    Path filePath = Paths.get(mediaFile.getFilePath());
                    
                    if (!Files.exists(filePath)) {
                        logger.warn("File no longer exists: {}", mediaFile.getFilePath());
                        continue;
                    }
                    
                    String contentHash;
                    if ("FNV-1".equals(algorithm)) {
                        contentHash = HashUtil.computeFNV1Hash(filePath);
                    } else {
                        contentHash = HashUtil.computeContentHash(filePath, algorithm);
                    }
                    
                    mediaFile.setContentHash(contentHash);
                    databaseService.saveMediaFile(mediaFile);
                }
                
                processedCount.incrementAndGet();
                
                if (processedCount.get() % 100 == 0) {
                    logger.info("Generated content hashes for {} files...", processedCount.get());
                }
                
            } catch (Exception e) {
                logger.error("Failed to generate content hash for file: {}", mediaFile.getFilePath(), e);
            }
        }
        
        logger.info("Content hash generation completed. Processed {} files", processedCount.get());
    }
    
    public void findDuplicates() throws SQLException {
        logger.info("Analyzing duplicates based on content hashes");
        
        List<MediaFile> allFiles = databaseService.getAllMediaFiles();
        long duplicateGroups = 0;
        long totalDuplicates = 0;
        
        for (MediaFile file : allFiles) {
            if (file.getContentHash() != null) {
                List<MediaFile> duplicates = databaseService.findMediaFilesByQuickHash(file.getQuickHash());
                if (duplicates.size() > 1) {
                    if (duplicateGroups == 0 || duplicateGroups % 10 == 0) {
                        logger.info("Found duplicate group {} with {} files (first: {})", 
                                   duplicateGroups + 1, duplicates.size(), file.getFilePath());
                    }
                    duplicateGroups++;
                    totalDuplicates += duplicates.size() - 1;
                }
            }
        }
        
        logger.info("Duplicate analysis completed. Found {} duplicate groups with {} total duplicate files", 
                   duplicateGroups, totalDuplicates);
    }
    
    public long getProcessedCount() {
        return processedCount.get();
    }
}