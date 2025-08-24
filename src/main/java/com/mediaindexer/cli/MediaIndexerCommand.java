package com.mediaindexer.cli;

import com.mediaindexer.config.ConfigManager;
import com.mediaindexer.config.MediaIndexerConfig;
import com.mediaindexer.model.MediaFile;
import com.mediaindexer.model.MiniThumbnail;
import com.mediaindexer.model.Thumbnail;
import com.mediaindexer.model.ThumbnailErrorType;
import com.mediaindexer.service.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

import java.util.List;
import java.util.concurrent.Callable;

@Command(name = "media-indexer", 
         description = "Cross-platform media indexing solution with thumbnail generation and HTML output",
         mixinStandardHelpOptions = true,
         version = "1.0.0")
public class MediaIndexerCommand implements Callable<Integer> {
    private static final Logger logger = LoggerFactory.getLogger(MediaIndexerCommand.class);
    
    @Option(names = {"-c", "--config"}, 
            description = "Configuration file path (default: media-indexer-config.yaml)")
    private String configPath;
    
    @CommandLine.ArgGroup(exclusive = true, multiplicity = "1")
    private OperationMode operationMode;
    
    static class OperationMode {
        @Option(names = {"--bootstrap"}, 
                description = "Bootstrap an initial YAML configuration file")
        private boolean bootstrap;
        
        @Option(names = {"--add-root"}, 
                description = "Add a root path to be scanned")
        private String addRoot;
        
        @Option(names = {"--quick-scan", "--quick"}, 
                description = "Scan filesystem roots using basic fast hashes")
        private boolean quickScan;
        
        @Option(names = {"--content-hash", "--hash"}, 
                description = "Generate full content hashes for scanned files")
        private boolean contentHash;
        
        @Option(names = {"--full-scan", "--full"}, 
                description = "Scan filesystem and generate both quick and content hashes")
        private boolean fullScan;
        
        @Option(names = {"--thumbnails", "--tn"}, 
                description = "Generate both thumbnails and mini thumbnails")
        private boolean thumbnails;
        
        @Option(names = {"--html"}, 
                description = "Generate HTML index pages")
        private boolean html;
        
        @Option(names = {"--status"}, 
                description = "Show database statistics and status")
        private boolean status;
        
        @Option(names = {"--diagnostic"}, 
                description = "List all files that failed thumbnail generation with error details")
        private boolean diagnostic;
    }
    
    @Override
    public Integer call() {
        try {
            logger.info("Media Indexer starting...");
            
            if (operationMode.bootstrap) {
                return handleBootstrap();
            }
            
            if (operationMode.addRoot != null) {
                return handleAddRoot(operationMode.addRoot);
            }
            
            MediaIndexerConfig config = ConfigManager.loadConfig(configPath);
            
            if (config.getScanRoots().isEmpty()) {
                logger.error("No scan roots configured. Use --add-root to add directories to scan, or --bootstrap to create a default configuration.");
                return 1;
            }

            DatabaseService databaseService = null;
            try {
                databaseService = new DatabaseService(config.getDatabasePath());
                if (operationMode.quickScan) {
                    return handleQuickScan(config, databaseService);
                }
                
                if (operationMode.contentHash) {
                    return handleContentHash(config, databaseService);
                }
                
                if (operationMode.fullScan) {
                    return handleFullScan(config, databaseService);
                }
                
                if (operationMode.thumbnails) {
                    return handleThumbnails(config, databaseService);
                }
                
                if (operationMode.html) {
                    return handleHtml(config, databaseService);
                }
                
                if (operationMode.status) {
                    return handleStatus(config, databaseService);
                }
                
                if (operationMode.diagnostic) {
                    return handleDiagnostic(config, databaseService);
                }
            } finally {
                if (databaseService != null) {
                    databaseService.close();
                }
            }
            
            return 0;
            
        } catch (Exception e) {
            logger.error("Operation failed", e);
            return 1;
        }
    }
    
    private Integer handleBootstrap() throws Exception {
        ConfigManager.createDefaultConfig(configPath);
        System.out.println("Default configuration created at: " + 
                          (configPath != null ? configPath : "media-indexer-config.yaml"));
        System.out.println("Edit the configuration file to add your media directories, then run with --add-root to add scan paths.");
        return 0;
    }
    
    private Integer handleAddRoot(String rootPath) throws Exception {
        ConfigManager.addScanRoot(configPath, rootPath);
        System.out.println("Added scan root: " + rootPath);
        return 0;
    }
    
    private Integer handleQuickScan(MediaIndexerConfig config, DatabaseService databaseService) throws Exception {
        logger.info("Starting quick scan operation");
        
        FileScanner fileScanner = new FileScanner(databaseService, config);
        fileScanner.scanAllRoots();
        
        HashingService hashingService = new HashingService(databaseService, config);
        hashingService.generateQuickHashes();
        
        System.out.printf("Quick scan completed. Scanned %d files, processed %d files.%n", 
                         fileScanner.getScannedCount(), fileScanner.getProcessedCount());
        return 0;
    }
    
    private Integer handleContentHash(MediaIndexerConfig config, DatabaseService databaseService) throws Exception {
        logger.info("Starting content hash generation");
        
        HashingService hashingService = new HashingService(databaseService, config);
        hashingService.generateContentHashes();
        hashingService.findDuplicates();
        
        System.out.printf("Content hash generation completed. Processed %d files.%n", 
                         hashingService.getProcessedCount());
        return 0;
    }
    
    private Integer handleFullScan(MediaIndexerConfig config, DatabaseService databaseService) throws Exception {
        logger.info("Starting full scan operation");
        
        FileScanner fileScanner = new FileScanner(databaseService, config);
        fileScanner.scanAllRoots();
        
        HashingService hashingService = new HashingService(databaseService, config);
        hashingService.generateQuickHashes();
        hashingService.generateContentHashes();
        hashingService.findDuplicates();
        
        System.out.printf("Full scan completed. Scanned %d files, processed %d files.%n", 
                         fileScanner.getScannedCount(), fileScanner.getProcessedCount());
        return 0;
    }
    
    private Integer handleThumbnails(MediaIndexerConfig config, DatabaseService databaseService) throws Exception {
        logger.info("Starting thumbnail generation");
        
        ThumbnailService thumbnailService = new ThumbnailService(databaseService, config);
        thumbnailService.generateThumbnails();
        thumbnailService.generateMiniThumbnails();
        
        System.out.printf("Thumbnail generation completed. Processed %d files.%n", 
                         thumbnailService.getProcessedCount());
        return 0;
    }
    
    private Integer handleHtml(MediaIndexerConfig config, DatabaseService databaseService) throws Exception {
        logger.info("Starting HTML index generation");
        
        HtmlIndexService htmlIndexService = new HtmlIndexService(databaseService, config);
        htmlIndexService.generateHtmlIndex();
        
        System.out.println("HTML index generation completed. Check the output directory: " + 
                          config.getHtmlOutputDir());
        return 0;
    }
    
    private Integer handleStatus(MediaIndexerConfig config, DatabaseService databaseService) throws Exception {
        logger.info("Gathering database statistics");
        
        long mediaFiles = databaseService.getMediaFileCount();
        long thumbnails = databaseService.getThumbnailCount();
        long miniThumbnails = databaseService.getMiniThumbnailCount();
        long quickHashDuplicateGroups = databaseService.getQuickHashDuplicateCount();
        long quickHashDuplicateFiles = databaseService.getQuickHashDuplicateFileCount();
        long contentHashDuplicateGroups = databaseService.getContentHashDuplicateCount();
        long contentHashDuplicateFiles = databaseService.getContentHashDuplicateFileCount();
        
        System.out.println("\n=== Media Indexer Status ===");
        System.out.println("Configuration file: " + (configPath != null ? configPath : "media-indexer-config.yaml"));
        System.out.println("Database: " + config.getDatabasePath());
        System.out.println();
        
        System.out.println("Scan Roots:");
        for (String root : config.getScanRoots()) {
            System.out.println("  - " + root);
        }
        System.out.println();
        
        System.out.println("Database Statistics:");
        System.out.println("  Media Files: " + mediaFiles);
        System.out.println("  Thumbnails: " + thumbnails);
        System.out.println("  Mini Thumbnails: " + miniThumbnails);
        System.out.println();
        
        System.out.println("Duplicate Analysis:");
        if (quickHashDuplicateGroups > 0) {
            System.out.println("  Quick Hash Duplicates: " + quickHashDuplicateGroups + " groups (" + quickHashDuplicateFiles + " files)");
        } else {
            System.out.println("  Quick Hash Duplicates: None found");
        }
        
        if (contentHashDuplicateGroups > 0) {
            System.out.println("  Content Hash Duplicates: " + contentHashDuplicateGroups + " groups (" + contentHashDuplicateFiles + " files)");
        } else {
            System.out.println("  Content Hash Duplicates: None found (run --content-hash to analyze)");
        }
        System.out.println();
        
        System.out.println("Output Directories:");
        System.out.println("  Thumbnails: " + config.getThumbnailOutputDir());
        System.out.println("  HTML: " + config.getHtmlOutputDir());
        
        return 0;
    }
    
    private Integer handleDiagnostic(MediaIndexerConfig config, DatabaseService databaseService) throws Exception {
        logger.info("Gathering thumbnail failure diagnostics");
        
        List<Thumbnail> failedThumbnails = databaseService.getFailedThumbnails();
        List<MiniThumbnail> failedMiniThumbnails = databaseService.getFailedMiniThumbnails();
        
        System.out.println("\n=== Thumbnail Generation Diagnostic Report ===");
        System.out.println("Configuration file: " + (configPath != null ? configPath : "media-indexer-config.yaml"));
        System.out.println("Database: " + config.getDatabasePath());
        System.out.println();
        
        if (failedThumbnails.isEmpty() && failedMiniThumbnails.isEmpty()) {
            System.out.println("✓ No thumbnail generation failures found!");
            return 0;
        }
        
        if (!failedThumbnails.isEmpty()) {
            System.out.println("=== Failed Thumbnails ===");
            System.out.printf("Found %d failed thumbnail(s):%n%n", failedThumbnails.size());
            
            for (Thumbnail thumbnail : failedThumbnails) {
                MediaFile mediaFile = databaseService.getMediaFileById(thumbnail.getMediaFileId());
                if (mediaFile != null) {
                    ThumbnailErrorType errorType = ThumbnailErrorType.fromCode(thumbnail.getErrorType());
                    String errorTypeDesc = errorType != null ? errorType.getDescription() : "Unknown error type";
                    
                    System.out.println("File: " + mediaFile.getFilePath());
                    System.out.println("  Error Type: " + errorTypeDesc + " (" + thumbnail.getErrorType() + ")");
                    System.out.println("  Error Message: " + thumbnail.getErrorMessage());
                    System.out.println("  Failed At: " + thumbnail.getCreatedAt());
                    System.out.println("  File Extension: " + mediaFile.getExtension());
                    System.out.println("  File Size: " + formatFileSize(mediaFile.getFileSize()));
                    System.out.println();
                }
            }
        }
        
        if (!failedMiniThumbnails.isEmpty()) {
            System.out.println("=== Failed Mini Thumbnails ===");
            System.out.printf("Found %d failed mini thumbnail(s):%n%n", failedMiniThumbnails.size());
            
            for (MiniThumbnail miniThumbnail : failedMiniThumbnails) {
                MediaFile mediaFile = databaseService.getMediaFileById(miniThumbnail.getMediaFileId());
                if (mediaFile != null) {
                    ThumbnailErrorType errorType = ThumbnailErrorType.fromCode(miniThumbnail.getErrorType());
                    String errorTypeDesc = errorType != null ? errorType.getDescription() : "Unknown error type";
                    
                    System.out.println("File: " + mediaFile.getFilePath());
                    System.out.println("  Error Type: " + errorTypeDesc + " (" + miniThumbnail.getErrorType() + ")");
                    System.out.println("  Error Message: " + miniThumbnail.getErrorMessage());
                    System.out.println("  Failed At: " + miniThumbnail.getCreatedAt());
                    System.out.println("  File Extension: " + mediaFile.getExtension());
                    System.out.println("  File Size: " + formatFileSize(mediaFile.getFileSize()));
                    System.out.println();
                }
            }
        }
        
        // Summary by error type
        System.out.println("=== Error Summary ===");
        long ioErrors = failedThumbnails.stream().filter(t -> "IO".equals(t.getErrorType())).count() +
                       failedMiniThumbnails.stream().filter(t -> "IO".equals(t.getErrorType())).count();
        long decodingErrors = failedThumbnails.stream().filter(t -> "DECODING".equals(t.getErrorType())).count() +
                             failedMiniThumbnails.stream().filter(t -> "DECODING".equals(t.getErrorType())).count();
        
        System.out.println("I/O Errors (file access issues): " + ioErrors);
        System.out.println("Decoding Errors (format/corruption issues): " + decodingErrors);
        System.out.println("Total Failed: " + (failedThumbnails.size() + failedMiniThumbnails.size()));
        
        return 0;
    }
    
    private String formatFileSize(long bytes) {
        if (bytes < 1024) return bytes + " B";
        int exp = (int) (Math.log(bytes) / Math.log(1024));
        char pre = "KMGTPE".charAt(exp - 1);
        return String.format("%.1f %sB", bytes / Math.pow(1024, exp), pre);
    }
}