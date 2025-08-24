package com.mediaindexer.service;

import com.drew.imaging.ImageMetadataReader;
import com.drew.metadata.Metadata;
import com.drew.metadata.exif.ExifIFD0Directory;
import com.mediaindexer.config.MediaIndexerConfig;
import com.mediaindexer.model.MediaFile;
import com.mediaindexer.model.MiniThumbnail;
import com.mediaindexer.model.Thumbnail;
import com.mediaindexer.model.ThumbnailErrorType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.geom.AffineTransform;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.sql.SQLException;
import java.util.Base64;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

public class ThumbnailService {
    private static final Logger logger = LoggerFactory.getLogger(ThumbnailService.class);
    
    private final DatabaseService databaseService;
    private final MediaIndexerConfig config;
    private final AtomicLong processedCount = new AtomicLong(0);
    
    public ThumbnailService(DatabaseService databaseService, MediaIndexerConfig config) {
        this.databaseService = databaseService;
        this.config = config;
    }
    
    public void generateThumbnails() throws SQLException, IOException {
        logger.info("Starting thumbnail generation");
        
        Files.createDirectories(Paths.get(config.getThumbnailOutputDir()));
        
        List<MediaFile> mediaFiles = databaseService.getAllMediaFiles();
        logger.info("Found {} media files to process", mediaFiles.size());
        
        processedCount.set(0);
        
        for (MediaFile mediaFile : mediaFiles) {
            try {
                generateThumbnailForFile(mediaFile);
                processedCount.incrementAndGet();
                
                if (processedCount.get() % 100 == 0) {
                    logger.info("Generated thumbnails for {} files...", processedCount.get());
                }
                
            } catch (Exception e) {
                logger.error("Failed to generate thumbnail for file: {}", mediaFile.getFilePath(), e);
            }
        }
        
        logger.info("Thumbnail generation completed. Processed {} files", processedCount.get());
    }
    
    public void generateMiniThumbnails() throws SQLException {
        logger.info("Starting mini thumbnail generation");
        
        List<MediaFile> mediaFiles = databaseService.getAllMediaFiles();
        logger.info("Found {} media files to process", mediaFiles.size());
        
        processedCount.set(0);
        
        for (MediaFile mediaFile : mediaFiles) {
            try {
                generateMiniThumbnailForFile(mediaFile);
                processedCount.incrementAndGet();
                
                if (processedCount.get() % 100 == 0) {
                    logger.info("Generated mini thumbnails for {} files...", processedCount.get());
                }
                
            } catch (Exception e) {
                logger.error("Failed to generate mini thumbnail for file: {}", mediaFile.getFilePath(), e);
            }
        }
        
        logger.info("Mini thumbnail generation completed. Processed {} files", processedCount.get());
    }
    
    private void generateThumbnailForFile(MediaFile mediaFile) throws IOException, SQLException {
        Path inputPath = Paths.get(mediaFile.getFilePath());
        
        if (!Files.exists(inputPath)) {
            logger.warn("File no longer exists: {}", mediaFile.getFilePath());
            return;
        }
        
        String thumbnailFileName = mediaFile.getId() + ".jpg";
        Path thumbnailPath = Paths.get(config.getThumbnailOutputDir(), thumbnailFileName);
        
        if (Files.exists(thumbnailPath)) {
            logger.debug("Thumbnail already exists: {}", thumbnailPath);
            return;
        }
        
        BufferedImage originalImage;
        try {
            originalImage = ImageIO.read(inputPath.toFile());
            if (originalImage == null) {
                String errorMsg = String.format("Image decoding failed: unsupported format or corrupted file - %s", inputPath);
                logger.warn("Could not decode image file (unsupported format or corrupted): {} [Format: {}]", 
                    inputPath, mediaFile.getExtension());
                saveThumbnailFailure(mediaFile.getId(), errorMsg, ThumbnailErrorType.DECODING_ERROR);
                return;
            }
        } catch (IOException e) {
            String errorMsg = String.format("Image reading failed (I/O error): %s - %s", inputPath, e.getMessage());
            logger.warn("Could not read image file (I/O error): {} - {} [Cause: {}]", 
                inputPath, e.getMessage(), e.getClass().getSimpleName());
            saveThumbnailFailure(mediaFile.getId(), errorMsg, ThumbnailErrorType.IO_ERROR);
            return;
        }
        
        int orientation = getExifOrientation(inputPath.toFile());
        BufferedImage rotatedImage = applyExifOrientation(originalImage, orientation);
        
        BufferedImage thumbnail = createThumbnail(rotatedImage, config.getThumbnail().getMaxDimension());
        
        writeImageToFile(thumbnail, thumbnailPath.toFile(), "JPEG", config.getThumbnail().getQuality());
        
        Thumbnail thumbnailRecord = new Thumbnail(
            mediaFile.getId(),
            thumbnailPath.toString(),
            thumbnail.getWidth(),
            thumbnail.getHeight(),
            orientation,
            "JPEG"
        );
        
        databaseService.saveThumbnail(thumbnailRecord);
    }
    
    private void generateMiniThumbnailForFile(MediaFile mediaFile) throws IOException, SQLException {
        Path inputPath = Paths.get(mediaFile.getFilePath());
        
        if (!Files.exists(inputPath)) {
            logger.warn("File no longer exists: {}", mediaFile.getFilePath());
            return;
        }
        
        BufferedImage originalImage;
        try {
            originalImage = ImageIO.read(inputPath.toFile());
            if (originalImage == null) {
                String errorMsg = String.format("Image decoding failed: unsupported format or corrupted file - %s", inputPath);
                logger.warn("Could not decode image file (unsupported format or corrupted): {} [Format: {}]", 
                    inputPath, mediaFile.getExtension());
                saveMiniThumbnailFailure(mediaFile.getId(), errorMsg, ThumbnailErrorType.DECODING_ERROR);
                return;
            }
        } catch (IOException e) {
            String errorMsg = String.format("Image reading failed (I/O error): %s - %s", inputPath, e.getMessage());
            logger.warn("Could not read image file (I/O error): {} - {} [Cause: {}]", 
                inputPath, e.getMessage(), e.getClass().getSimpleName());
            saveMiniThumbnailFailure(mediaFile.getId(), errorMsg, ThumbnailErrorType.IO_ERROR);
            return;
        }
        
        int orientation = getExifOrientation(inputPath.toFile());
        BufferedImage rotatedImage = applyExifOrientation(originalImage, orientation);
        
        BufferedImage miniThumbnail = createMiniThumbnail(rotatedImage, config.getMiniThumbnail().getMaxHeight());
        
        String base64Data = encodeImageToBase64(miniThumbnail, "JPEG", config.getMiniThumbnail().getQuality());
        
        MiniThumbnail miniThumbnailRecord = new MiniThumbnail(
            mediaFile.getId(),
            base64Data,
            miniThumbnail.getWidth(),
            miniThumbnail.getHeight(),
            orientation,
            "JPEG"
        );
        
        databaseService.saveMiniThumbnail(miniThumbnailRecord);
    }
    
    private BufferedImage createThumbnail(BufferedImage original, int maxDimension) {
        int originalWidth = original.getWidth();
        int originalHeight = original.getHeight();
        
        double scale = Math.min((double) maxDimension / originalWidth, (double) maxDimension / originalHeight);
        
        int newWidth = (int) (originalWidth * scale);
        int newHeight = (int) (originalHeight * scale);
        
        BufferedImage thumbnail = new BufferedImage(newWidth, newHeight, BufferedImage.TYPE_INT_RGB);
        Graphics2D g2d = thumbnail.createGraphics();
        
        g2d.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        g2d.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        
        g2d.drawImage(original, 0, 0, newWidth, newHeight, null);
        g2d.dispose();
        
        return thumbnail;
    }
    
    private BufferedImage createMiniThumbnail(BufferedImage original, int maxHeight) {
        int originalWidth = original.getWidth();
        int originalHeight = original.getHeight();
        
        double scale = (double) maxHeight / originalHeight;
        int newWidth = (int) (originalWidth * scale);

        BufferedImage miniThumbnail = new BufferedImage(newWidth, maxHeight, BufferedImage.TYPE_INT_RGB);
        Graphics2D g2d = miniThumbnail.createGraphics();
        
        g2d.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        g2d.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        
        g2d.drawImage(original, 0, 0, newWidth, maxHeight, null);
        g2d.dispose();
        
        return miniThumbnail;
    }
    
    private int getExifOrientation(File file) {
        try {
            Metadata metadata = ImageMetadataReader.readMetadata(file);
            ExifIFD0Directory exifDirectory = metadata.getFirstDirectoryOfType(ExifIFD0Directory.class);
            
            if (exifDirectory != null && exifDirectory.hasTagName(ExifIFD0Directory.TAG_ORIENTATION)) {
                return exifDirectory.getInt(ExifIFD0Directory.TAG_ORIENTATION);
            }
        } catch (Exception e) {
            logger.debug("Could not read EXIF orientation for file: {}", file.getPath());
        }
        
        return 1; // Default orientation
    }
    
    private BufferedImage applyExifOrientation(BufferedImage image, int orientation) {
        if (!config.getThumbnail().isRespectExifOrientation() || orientation == 1) {
            return image;
        }
        
        int width = image.getWidth();
        int height = image.getHeight();
        BufferedImage rotatedImage;
        AffineTransform transform = new AffineTransform();
        
        switch (orientation) {
            case 2: // Flip horizontally
                rotatedImage = new BufferedImage(width, height, image.getType());
                transform.scale(-1.0, 1.0);
                transform.translate(-width, 0);
                break;
                
            case 3: // Rotate 180 degrees
                rotatedImage = new BufferedImage(width, height, image.getType());
                transform.rotate(Math.PI, width / 2.0, height / 2.0);
                break;
                
            case 4: // Flip vertically
                rotatedImage = new BufferedImage(width, height, image.getType());
                transform.scale(1.0, -1.0);
                transform.translate(0, -height);
                break;
                
            case 5: // Rotate 90 degrees CW and flip horizontally
                rotatedImage = new BufferedImage(height, width, image.getType());
                transform.rotate(Math.PI / 2);
                transform.scale(-1.0, 1.0);
                break;
                
            case 6: // Rotate 90 degrees CW
                rotatedImage = new BufferedImage(height, width, image.getType());
                transform.translate(height, 0);
                transform.rotate(Math.PI / 2);
                break;
                
            case 7: // Rotate 90 degrees CCW and flip horizontally
                rotatedImage = new BufferedImage(height, width, image.getType());
                transform.scale(-1.0, 1.0);
                transform.translate(-height, 0);
                transform.translate(0, width);
                transform.rotate(3 * Math.PI / 2);
                break;
                
            case 8: // Rotate 90 degrees CCW
                rotatedImage = new BufferedImage(height, width, image.getType());
                transform.translate(0, width);
                transform.rotate(3 * Math.PI / 2);
                break;
                
            default:
                return image;
        }
        
        Graphics2D g2d = rotatedImage.createGraphics();
        g2d.setTransform(transform);
        g2d.drawImage(image, 0, 0, null);
        g2d.dispose();
        
        return rotatedImage;
    }
    
    private void writeImageToFile(BufferedImage image, File file, String format, float quality) throws IOException {
        javax.imageio.ImageWriter writer = ImageIO.getImageWritersByFormatName(format).next();
        javax.imageio.ImageWriteParam param = writer.getDefaultWriteParam();
        
        if (param.canWriteCompressed()) {
            param.setCompressionMode(javax.imageio.ImageWriteParam.MODE_EXPLICIT);
            param.setCompressionQuality(quality);
        }
        
        try (var output = ImageIO.createImageOutputStream(file)) {
            writer.setOutput(output);
            writer.write(null, new javax.imageio.IIOImage(image, null, null), param);
        } finally {
            writer.dispose();
        }
    }
    
    private String encodeImageToBase64(BufferedImage image, String format, float quality) throws IOException {
        try (ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
            javax.imageio.ImageWriter writer = ImageIO.getImageWritersByFormatName(format).next();
            javax.imageio.ImageWriteParam param = writer.getDefaultWriteParam();
            
            if (param.canWriteCompressed()) {
                param.setCompressionMode(javax.imageio.ImageWriteParam.MODE_EXPLICIT);
                param.setCompressionQuality(quality);
            }
            
            try (var output = ImageIO.createImageOutputStream(baos)) {
                writer.setOutput(output);
                writer.write(null, new javax.imageio.IIOImage(image, null, null), param);
            } finally {
                writer.dispose();
            }
            
            return Base64.getEncoder().encodeToString(baos.toByteArray());
        }
    }
    
    public long getProcessedCount() {
        return processedCount.get();
    }
    
    private void saveThumbnailFailure(Long mediaFileId, String errorMessage, ThumbnailErrorType errorType) throws SQLException {
        Thumbnail thumbnailRecord = new Thumbnail(
            mediaFileId,
            null,
            0,
            0,
            1,
            null,
            true,
            errorMessage,
            errorType.getCode()
        );
        
        databaseService.saveThumbnail(thumbnailRecord);
    }
    
    private void saveMiniThumbnailFailure(Long mediaFileId, String errorMessage, ThumbnailErrorType errorType) throws SQLException {
        MiniThumbnail miniThumbnailRecord = new MiniThumbnail(
            mediaFileId,
            null,
            0,
            0,
            1,
            null,
            true,
            errorMessage,
            errorType.getCode()
        );
        
        databaseService.saveMiniThumbnail(miniThumbnailRecord);
    }
}