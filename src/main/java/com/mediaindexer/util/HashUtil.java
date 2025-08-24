package com.mediaindexer.util;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

public class HashUtil {
    private static final Logger logger = LoggerFactory.getLogger(HashUtil.class);
    private static final int BUFFER_SIZE = 8192;
    
    public static String computeQuickHash(String filePath, long fileSize, LocalDateTime lastModified) {
        String fileName = Paths.get(filePath).getFileName().toString();
        String input = fileName + "|" + fileSize + "|" + lastModified.format(DateTimeFormatter.ISO_LOCAL_DATE_TIME);
        
        try {
            MessageDigest md = MessageDigest.getInstance("MD5");
            byte[] hashBytes = md.digest(input.getBytes());
            return bytesToHex(hashBytes);
        } catch (NoSuchAlgorithmException e) {
            logger.error("MD5 algorithm not available", e);
            return String.valueOf(input.hashCode());
        }
    }
    
    public static String computeContentHash(Path filePath, String algorithm) throws IOException {
        try {
            MessageDigest digest = MessageDigest.getInstance(algorithm);
            
            try (InputStream inputStream = Files.newInputStream(filePath)) {
                byte[] buffer = new byte[BUFFER_SIZE];
                int bytesRead;
                
                while ((bytesRead = inputStream.read(buffer)) != -1) {
                    digest.update(buffer, 0, bytesRead);
                }
            }
            
            return bytesToHex(digest.digest());
            
        } catch (NoSuchAlgorithmException e) {
            logger.error("Hash algorithm {} not available", algorithm, e);
            throw new IOException("Hash algorithm not available: " + algorithm, e);
        }
    }
    
    public static String computeFNV1Hash(Path filePath) throws IOException {
        final long FNV_OFFSET_BASIS = 0xcbf29ce484222325L;
        final long FNV_PRIME = 0x100000001b3L;
        
        long hash = FNV_OFFSET_BASIS;
        
        try (InputStream inputStream = Files.newInputStream(filePath)) {
            byte[] buffer = new byte[BUFFER_SIZE];
            int bytesRead;
            
            while ((bytesRead = inputStream.read(buffer)) != -1) {
                for (int i = 0; i < bytesRead; i++) {
                    hash ^= (buffer[i] & 0xff);
                    hash *= FNV_PRIME;
                }
            }
        }
        
        return Long.toHexString(hash);
    }
    
    private static String bytesToHex(byte[] bytes) {
        StringBuilder result = new StringBuilder();
        for (byte b : bytes) {
            result.append(String.format("%02x", b));
        }
        return result.toString();
    }
}