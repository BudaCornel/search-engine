package com.buda.searchengine.indexer.processor;

import com.buda.searchengine.crawler.ContentExtractor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

public class ImageFileProcessor implements FileProcessor {

    private static final Logger logger = LoggerFactory.getLogger(ImageFileProcessor.class);

    private static final Set<String> IMAGE_EXTENSIONS = Set.of(
            "png", "jpg", "jpeg", "bmp", "gif", "wbmp"
    );

    private static final int MAX_SAMPLES_PER_DIM = 100;
    private static final int ALPHA_THRESHOLD     = 128;

    private final ContentExtractor contentExtractor;

    public ImageFileProcessor(ContentExtractor contentExtractor) {
        this.contentExtractor = contentExtractor;
    }

    @Override
    public boolean supports(Path file, String mimeType) {
        if (mimeType != null && mimeType.startsWith("image/")) return true;
        String name = file.getFileName().toString();
        int dot = name.lastIndexOf('.');
        if (dot < 0) return false;
        return IMAGE_EXTENSIONS.contains(name.substring(dot + 1).toLowerCase());
    }

    @Override
    public Optional<ProcessedContent> process(Path file) {
        BufferedImage img;
        try {
            img = ImageIO.read(file.toFile());
        } catch (IOException e) {
            logger.warn("Failed to read image: {}", file, e);
            return Optional.empty();
        }
        if (img == null) {
            logger.debug("ImageIO returned null (unsupported format?): {}", file);
            return Optional.empty();
        }

        String color = extractDominantColor(img);
        String preview = String.format("[Image %dx%d, dominant color: %s]",
                img.getWidth(), img.getHeight(), color);
        String hash = contentExtractor.computeHash(file);
        return Optional.of(ProcessedContent.ofImage(preview, color, hash));
    }

    private String extractDominantColor(BufferedImage img) {
        int w = img.getWidth();
        int h = img.getHeight();
        int strideX = Math.max(1, w / MAX_SAMPLES_PER_DIM);
        int strideY = Math.max(1, h / MAX_SAMPLES_PER_DIM);

        Map<String, Integer> counts = new HashMap<>();
        for (int y = 0; y < h; y += strideY) {
            for (int x = 0; x < w; x += strideX) {
                int rgb = img.getRGB(x, y);
                int alpha = (rgb >> 24) & 0xff;
                if (alpha < ALPHA_THRESHOLD) continue;
                counts.merge(classify(rgb), 1, Integer::sum);
            }
        }

        return counts.entrySet().stream()
                .max(Map.Entry.comparingByValue())
                .map(Map.Entry::getKey)
                .orElse("unknown");
    }

    private String classify(int rgb) {
        int r = (rgb >> 16) & 0xff;
        int g = (rgb >>  8) & 0xff;
        int b =  rgb        & 0xff;
        float[] hsb = Color.RGBtoHSB(r, g, b, null);
        float hue = hsb[0] * 360f;
        float sat = hsb[1];
        float val = hsb[2];

        if (val < 0.15f) return "black";
        if (sat < 0.15f) return val > 0.85f ? "white" : "gray";

        if (hue < 15  || hue >= 345) return "red";
        if (hue <  40)               return "orange";
        if (hue <  65)               return "yellow";
        if (hue < 165)               return "green";
        if (hue < 200)               return "cyan";
        if (hue < 255)               return "blue";
        if (hue < 290)               return "purple";
        return "pink";
    }
}
