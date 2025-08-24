package com.mediaindexer.service;

import com.mediaindexer.model.MediaFile;
import com.mediaindexer.model.MiniThumbnail;
import com.mediaindexer.model.Thumbnail;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.*;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class DatabaseService {
    private static final Logger logger = LoggerFactory.getLogger(DatabaseService.class);
    private static final DateTimeFormatter DATETIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    
    private Connection connection;
    
    public DatabaseService(String dbPath) throws SQLException {
        this.connection = DriverManager.getConnection("jdbc:sqlite:" + dbPath);
        initializeSchema();
        migrateSchema();
    }
    
    private void initializeSchema() throws SQLException {
        try (Statement stmt = connection.createStatement()) {
            stmt.execute("PRAGMA foreign_keys = ON");
            
            stmt.execute("""
                CREATE TABLE IF NOT EXISTS media_files (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    file_path TEXT NOT NULL UNIQUE,
                    extension TEXT NOT NULL,
                    file_size INTEGER NOT NULL,
                    last_modified TEXT NOT NULL,
                    last_scanned TEXT NOT NULL,
                    quick_hash TEXT,
                    content_hash TEXT
                )
            """);
            
            stmt.execute("CREATE INDEX IF NOT EXISTS idx_quick_hash ON media_files(quick_hash)");
            stmt.execute("CREATE INDEX IF NOT EXISTS idx_content_hash ON media_files(content_hash)");
            stmt.execute("CREATE INDEX IF NOT EXISTS idx_file_path ON media_files(file_path)");
            
            stmt.execute("""
                CREATE TABLE IF NOT EXISTS thumbnails (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    media_file_id INTEGER NOT NULL,
                    thumbnail_path TEXT,
                    width INTEGER NOT NULL,
                    height INTEGER NOT NULL,
                    orientation INTEGER DEFAULT 1,
                    format TEXT,
                    created_at TEXT NOT NULL,
                    failed BOOLEAN DEFAULT 0,
                    error_message TEXT,
                    error_type TEXT,
                    FOREIGN KEY (media_file_id) REFERENCES media_files(id) ON DELETE CASCADE
                )
            """);
            
            stmt.execute("CREATE INDEX IF NOT EXISTS idx_thumbnails_media_file_id ON thumbnails(media_file_id)");
            
            stmt.execute("""
                CREATE TABLE IF NOT EXISTS mini_thumbnails (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    media_file_id INTEGER NOT NULL,
                    base64_data TEXT,
                    width INTEGER NOT NULL,
                    height INTEGER NOT NULL,
                    orientation INTEGER DEFAULT 1,
                    format TEXT,
                    created_at TEXT NOT NULL,
                    failed BOOLEAN DEFAULT 0,
                    error_message TEXT,
                    error_type TEXT,
                    FOREIGN KEY (media_file_id) REFERENCES media_files(id) ON DELETE CASCADE
                )
            """);
            
            stmt.execute("CREATE INDEX IF NOT EXISTS idx_mini_thumbnails_media_file_id ON mini_thumbnails(media_file_id)");
        }
    }
    
    private void addColumnIfNotExists(String tableName, String columnName, String columnDefinition) throws SQLException {
        try (Statement stmt = connection.createStatement()) {
            try {
                stmt.execute(String.format("ALTER TABLE %s ADD COLUMN %s %s", tableName, columnName, columnDefinition));
                logger.info("Added column {} to table {}", columnName, tableName);
            } catch (SQLException e) {
                // Column already exists, ignore
                if (!e.getMessage().contains("duplicate column name")) {
                    throw e;
                }
            }
        }
    }
    
    private void migrateSchema() throws SQLException {
        // Add new columns if they don't exist (for existing databases)
        addColumnIfNotExists("thumbnails", "failed", "BOOLEAN DEFAULT 0");
        addColumnIfNotExists("thumbnails", "error_message", "TEXT");
        addColumnIfNotExists("thumbnails", "error_type", "TEXT");
        addColumnIfNotExists("mini_thumbnails", "failed", "BOOLEAN DEFAULT 0");
        addColumnIfNotExists("mini_thumbnails", "error_message", "TEXT");
        addColumnIfNotExists("mini_thumbnails", "error_type", "TEXT");
    }
    
    public MediaFile saveMediaFile(MediaFile mediaFile) throws SQLException {
        String sql = """
            INSERT OR REPLACE INTO media_files (file_path, extension, file_size, last_modified, last_scanned, quick_hash, content_hash)
            VALUES (?, ?, ?, ?, ?, ?, ?)
            RETURNING id
        """;
        
        try (PreparedStatement stmt = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            stmt.setString(1, mediaFile.getFilePath());
            stmt.setString(2, mediaFile.getExtension());
            stmt.setLong(3, mediaFile.getFileSize());
            stmt.setString(4, mediaFile.getLastModified().format(DATETIME_FORMATTER));
            stmt.setString(5, mediaFile.getLastScanned().format(DATETIME_FORMATTER));
            stmt.setString(6, mediaFile.getQuickHash());
            stmt.setString(7, mediaFile.getContentHash());
            
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    long id = rs.getLong("id");
                    mediaFile.setId(id);
                }
            }
        }
        return mediaFile;
    }
    
    public Optional<MediaFile> findMediaFileByPath(String filePath) throws SQLException {
        String sql = "SELECT * FROM media_files WHERE file_path = ?";
        
        try (PreparedStatement stmt = connection.prepareStatement(sql)) {
            stmt.setString(1, filePath);
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return Optional.of(mapMediaFileFromResultSet(rs));
                }
            }
        }
        return Optional.empty();
    }
    
    public List<MediaFile> findMediaFilesByQuickHash(String quickHash) throws SQLException {
        String sql = "SELECT * FROM media_files WHERE quick_hash = ?";
        List<MediaFile> files = new ArrayList<>();
        
        try (PreparedStatement stmt = connection.prepareStatement(sql)) {
            stmt.setString(1, quickHash);
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    files.add(mapMediaFileFromResultSet(rs));
                }
            }
        }
        return files;
    }
    
    public List<MediaFile> getAllMediaFiles() throws SQLException {
        String sql = "SELECT * FROM media_files ORDER BY file_path";
        List<MediaFile> files = new ArrayList<>();
        
        try (PreparedStatement stmt = connection.prepareStatement(sql);
             ResultSet rs = stmt.executeQuery()) {
            while (rs.next()) {
                files.add(mapMediaFileFromResultSet(rs));
            }
        }
        return files;
    }
    
    public Thumbnail saveThumbnail(Thumbnail thumbnail) throws SQLException {
        String sql = """
            INSERT OR REPLACE INTO thumbnails (media_file_id, thumbnail_path, width, height, orientation, format, created_at, failed, error_message, error_type)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
        """;
        
        try (PreparedStatement stmt = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            stmt.setLong(1, thumbnail.getMediaFileId());
            stmt.setString(2, thumbnail.getThumbnailPath());
            stmt.setInt(3, thumbnail.getWidth());
            stmt.setInt(4, thumbnail.getHeight());
            stmt.setInt(5, thumbnail.getOrientation());
            stmt.setString(6, thumbnail.getFormat());
            stmt.setString(7, thumbnail.getCreatedAt().format(DATETIME_FORMATTER));
            stmt.setBoolean(8, thumbnail.isFailed());
            stmt.setString(9, thumbnail.getErrorMessage());
            stmt.setString(10, thumbnail.getErrorType());
            
            int affectedRows = stmt.executeUpdate();
            if (affectedRows > 0) {
                try (ResultSet generatedKeys = stmt.getGeneratedKeys()) {
                    if (generatedKeys.next()) {
                        thumbnail.setId(generatedKeys.getLong(1));
                    }
                }
            }
        }
        return thumbnail;
    }
    
    public MiniThumbnail saveMiniThumbnail(MiniThumbnail miniThumbnail) throws SQLException {
        String sql = """
            INSERT OR REPLACE INTO mini_thumbnails (media_file_id, base64_data, width, height, orientation, format, created_at, failed, error_message, error_type)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            RETURNING id
        """;
        
        try (PreparedStatement stmt = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            stmt.setLong(1, miniThumbnail.getMediaFileId());
            stmt.setString(2, miniThumbnail.getBase64Data());
            stmt.setInt(3, miniThumbnail.getWidth());
            stmt.setInt(4, miniThumbnail.getHeight());
            stmt.setInt(5, miniThumbnail.getOrientation());
            stmt.setString(6, miniThumbnail.getFormat());
            stmt.setString(7, miniThumbnail.getCreatedAt().format(DATETIME_FORMATTER));
            stmt.setBoolean(8, miniThumbnail.isFailed());
            stmt.setString(9, miniThumbnail.getErrorMessage());
            stmt.setString(10, miniThumbnail.getErrorType());

            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    long id = rs.getLong("id");
                    miniThumbnail.setId(id);
                }
            }
        }
        return miniThumbnail;
    }
    
    public List<MiniThumbnail> getAllMiniThumbnails() throws SQLException {
        String sql = """
            SELECT mt.*, mf.file_path 
            FROM mini_thumbnails mt
            JOIN media_files mf ON mt.media_file_id = mf.id
            ORDER BY mf.file_path
        """;
        List<MiniThumbnail> miniThumbnails = new ArrayList<>();
        
        try (PreparedStatement stmt = connection.prepareStatement(sql);
             ResultSet rs = stmt.executeQuery()) {
            while (rs.next()) {
                miniThumbnails.add(mapMiniThumbnailFromResultSet(rs));
            }
        }
        return miniThumbnails;
    }
    
    private MediaFile mapMediaFileFromResultSet(ResultSet rs) throws SQLException {
        MediaFile mediaFile = new MediaFile();
        mediaFile.setId(rs.getLong("id"));
        mediaFile.setFilePath(rs.getString("file_path"));
        mediaFile.setExtension(rs.getString("extension"));
        mediaFile.setFileSize(rs.getLong("file_size"));
        mediaFile.setLastModified(LocalDateTime.parse(rs.getString("last_modified"), DATETIME_FORMATTER));
        mediaFile.setLastScanned(LocalDateTime.parse(rs.getString("last_scanned"), DATETIME_FORMATTER));
        mediaFile.setQuickHash(rs.getString("quick_hash"));
        mediaFile.setContentHash(rs.getString("content_hash"));
        return mediaFile;
    }
    
    private MiniThumbnail mapMiniThumbnailFromResultSet(ResultSet rs) throws SQLException {
        MiniThumbnail miniThumbnail = new MiniThumbnail();
        miniThumbnail.setId(rs.getLong("id"));
        miniThumbnail.setMediaFileId(rs.getLong("media_file_id"));
        miniThumbnail.setBase64Data(rs.getString("base64_data"));
        miniThumbnail.setWidth(rs.getInt("width"));
        miniThumbnail.setHeight(rs.getInt("height"));
        miniThumbnail.setOrientation(rs.getInt("orientation"));
        miniThumbnail.setFormat(rs.getString("format"));
        miniThumbnail.setCreatedAt(LocalDateTime.parse(rs.getString("created_at"), DATETIME_FORMATTER));
        miniThumbnail.setFailed(rs.getBoolean("failed"));
        miniThumbnail.setErrorMessage(rs.getString("error_message"));
        miniThumbnail.setErrorType(rs.getString("error_type"));
        return miniThumbnail;
    }
    
    private Thumbnail mapThumbnailFromResultSet(ResultSet rs) throws SQLException {
        Thumbnail thumbnail = new Thumbnail();
        thumbnail.setId(rs.getLong("id"));
        thumbnail.setMediaFileId(rs.getLong("media_file_id"));
        thumbnail.setThumbnailPath(rs.getString("thumbnail_path"));
        thumbnail.setWidth(rs.getInt("width"));
        thumbnail.setHeight(rs.getInt("height"));
        thumbnail.setOrientation(rs.getInt("orientation"));
        thumbnail.setFormat(rs.getString("format"));
        thumbnail.setCreatedAt(LocalDateTime.parse(rs.getString("created_at"), DATETIME_FORMATTER));
        thumbnail.setFailed(rs.getBoolean("failed"));
        thumbnail.setErrorMessage(rs.getString("error_message"));
        thumbnail.setErrorType(rs.getString("error_type"));
        return thumbnail;
    }
    
    public List<Thumbnail> getFailedThumbnails() throws SQLException {
        String sql = """
            SELECT t.*, mf.file_path 
            FROM thumbnails t
            JOIN media_files mf ON t.media_file_id = mf.id
            WHERE t.failed = 1
            ORDER BY mf.file_path
        """;
        List<Thumbnail> failedThumbnails = new ArrayList<>();
        
        try (PreparedStatement stmt = connection.prepareStatement(sql);
             ResultSet rs = stmt.executeQuery()) {
            while (rs.next()) {
                Thumbnail thumbnail = mapThumbnailFromResultSet(rs);
                failedThumbnails.add(thumbnail);
            }
        }
        return failedThumbnails;
    }
    
    public List<MiniThumbnail> getFailedMiniThumbnails() throws SQLException {
        String sql = """
            SELECT mt.*, mf.file_path 
            FROM mini_thumbnails mt
            JOIN media_files mf ON mt.media_file_id = mf.id
            WHERE mt.failed = 1
            ORDER BY mf.file_path
        """;
        List<MiniThumbnail> failedMiniThumbnails = new ArrayList<>();
        
        try (PreparedStatement stmt = connection.prepareStatement(sql);
             ResultSet rs = stmt.executeQuery()) {
            while (rs.next()) {
                failedMiniThumbnails.add(mapMiniThumbnailFromResultSet(rs));
            }
        }
        return failedMiniThumbnails;
    }
    
    public MediaFile getMediaFileById(Long id) throws SQLException {
        String sql = "SELECT * FROM media_files WHERE id = ?";
        
        try (PreparedStatement stmt = connection.prepareStatement(sql)) {
            stmt.setLong(1, id);
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return mapMediaFileFromResultSet(rs);
                }
            }
        }
        return null;
    }
    
    public void close() throws SQLException {
        if (connection != null && !connection.isClosed()) {
            connection.close();
        }
    }
    
    public void optimizeDatabase() throws SQLException {
        logger.info("Optimizing database...");
        try (Statement stmt = connection.createStatement()) {
            stmt.execute("VACUUM");
            stmt.execute("ANALYZE");
        }
        logger.info("Database optimization completed");
    }
    
    public long getMediaFileCount() throws SQLException {
        String sql = "SELECT COUNT(*) FROM media_files";
        try (PreparedStatement stmt = connection.prepareStatement(sql);
             ResultSet rs = stmt.executeQuery()) {
            if (rs.next()) {
                return rs.getLong(1);
            }
        }
        return 0;
    }
    
    public long getThumbnailCount() throws SQLException {
        String sql = "SELECT COUNT(*) FROM thumbnails";
        try (PreparedStatement stmt = connection.prepareStatement(sql);
             ResultSet rs = stmt.executeQuery()) {
            if (rs.next()) {
                return rs.getLong(1);
            }
        }
        return 0;
    }
    
    public long getMiniThumbnailCount() throws SQLException {
        String sql = "SELECT COUNT(*) FROM mini_thumbnails";
        try (PreparedStatement stmt = connection.prepareStatement(sql);
             ResultSet rs = stmt.executeQuery()) {
            if (rs.next()) {
                return rs.getLong(1);
            }
        }
        return 0;
    }
}