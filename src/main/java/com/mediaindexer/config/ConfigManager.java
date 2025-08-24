package com.mediaindexer.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.Constructor;
import org.yaml.snakeyaml.inspector.TagInspector;

import java.io.FileInputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

public class ConfigManager {
    private static final Logger logger = LoggerFactory.getLogger(ConfigManager.class);
    private static final String DEFAULT_CONFIG_FILE = "media-indexer-config.yaml";
    
    public static MediaIndexerConfig loadConfig(String configPath) {
        Path path = Paths.get(configPath != null ? configPath : DEFAULT_CONFIG_FILE);
        
        if (!Files.exists(path)) {
            logger.warn("Config file not found at {}, using default configuration", path);
            return new MediaIndexerConfig();
        }
        
        try (FileInputStream inputStream = new FileInputStream(path.toFile())) {
            LoaderOptions loadingConfig = new LoaderOptions();
            TagInspector taginspector =
                    tag -> tag.getClassName().equals(MediaIndexerConfig.class.getName());
            loadingConfig.setTagInspector(taginspector);

            Yaml yaml = new Yaml(new Constructor(MediaIndexerConfig.class, loadingConfig));
            MediaIndexerConfig config = yaml.load(inputStream);
            logger.info("Loaded configuration from {}", path);
            return config != null ? config : new MediaIndexerConfig();
        } catch (Exception e) {
            logger.error("Failed to load configuration from {}, using defaults", path, e);
            return new MediaIndexerConfig();
        }
    }
    
    public static void saveConfig(MediaIndexerConfig config, String configPath) throws IOException {
        Path path = Paths.get(configPath != null ? configPath : DEFAULT_CONFIG_FILE);
        
        Yaml yaml = new Yaml();
        try (FileWriter writer = new FileWriter(path.toFile())) {
            yaml.dump(config, writer);
            logger.info("Configuration saved to {}", path);
        }
    }
    
    public static void createDefaultConfig(String configPath) throws IOException {
        MediaIndexerConfig defaultConfig = new MediaIndexerConfig();
        defaultConfig.getScanRoots().add(System.getProperty("user.home") + "/Pictures");
        
        saveConfig(defaultConfig, configPath);
        logger.info("Default configuration created");
    }
    
    public static void addScanRoot(String configPath, String rootPath) throws IOException {
        MediaIndexerConfig config = loadConfig(configPath);
        
        if (!config.getScanRoots().contains(rootPath)) {
            config.getScanRoots().add(rootPath);
            saveConfig(config, configPath);
            logger.info("Added scan root: {}", rootPath);
        } else {
            logger.info("Scan root already exists: {}", rootPath);
        }
    }
}