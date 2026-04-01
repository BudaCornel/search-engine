package com.buda.searchengine.config;

import org.yaml.snakeyaml.Yaml;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class AppConfig {
    private static final Logger logger = LoggerFactory.getLogger(AppConfig.class);

    private String rootDirectory;
    private List<String> ignorePatterns;
    private int maxResults;
    private String reportFormat;

    private AppConfig() {}

    public static AppConfig load() {
        AppConfig config = new AppConfig();
        Yaml yaml = new Yaml();

        try (InputStream input = AppConfig.class.getClassLoader()
                .getResourceAsStream("config.yml")) {

            if (input == null) {
                logger.warn("config.yml not found, using defaults");
                return config.withDefaults();
            }

            Map<String, Object> data = yaml.load(input);

            config.rootDirectory = (String) data.getOrDefault("rootDirectory", ".");
            config.maxResults = (int) data.getOrDefault("maxResults", 20);
            config.reportFormat = (String) data.getOrDefault("reportFormat", "text");

            Object patterns = data.get("ignorePatterns");
            if (patterns instanceof List<?>) {
                config.ignorePatterns = ((List<?>) patterns).stream()
                        .map(Object::toString)
                        .toList();
            } else {
                config.ignorePatterns = new ArrayList<>();
            }

            logger.info("Configuration loaded: root={}, ignore={} patterns",
                    config.rootDirectory, config.ignorePatterns.size());

        } catch (Exception e) {
            logger.error("Failed to load config, using defaults", e);
            return config.withDefaults();
        }

        return config;
    }

    private AppConfig withDefaults() {
        this.rootDirectory = ".";
        this.ignorePatterns = List.of(".git", ".idea", "node_modules", "target");
        this.maxResults = 20;
        this.reportFormat = "text";
        return this;
    }

    public String getRootDirectory() { return rootDirectory; }
    public List<String> getIgnorePatterns() { return ignorePatterns; }
    public int getMaxResults() { return maxResults; }
    public String getReportFormat() { return reportFormat; }
}