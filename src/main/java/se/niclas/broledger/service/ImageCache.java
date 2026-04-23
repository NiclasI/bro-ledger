package se.niclas.broledger.service;

import javafx.scene.image.Image;

import java.io.ByteArrayInputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * LRU image cache (max 300 entries).
 * Returns a gray placeholder for any path that cannot be loaded.
 */
public class ImageCache {

    private static final int MAX_SIZE = 300;

    private static ImageCache instance;

    private final Map<String, Image> cache = Collections.synchronizedMap(
            new LinkedHashMap<>(MAX_SIZE + 1, 0.75f, true) {
                @Override
                protected boolean removeEldestEntry(Map.Entry<String, Image> eldest) {
                    return size() > MAX_SIZE;
                }
            });

    private final Image placeholder = buildPlaceholder();

    private ImageCache() {}

    public static ImageCache getInstance() {
        if (instance == null) instance = new ImageCache();
        return instance;
    }

    /**
     * Load an image from {@code gameArtRoot/relativePath}.
     * Returns the placeholder if the file does not exist or fails to load.
     */
    public Image get(Path gameArtRoot, String relativePath) {
        if (relativePath == null) return placeholder;
        String key = gameArtRoot.toString() + "/" + relativePath;
        return cache.computeIfAbsent(key, k -> loadOrPlaceholder(gameArtRoot, relativePath));
    }

    public Image getPlaceholder() {
        return placeholder;
    }

    public void invalidate() {
        cache.clear();
    }

    private Image loadOrPlaceholder(Path gameArtRoot, String relativePath) {
        Path file = gameArtRoot.resolve(relativePath.replace("/", java.io.File.separator));
        if (!Files.exists(file)) return placeholder;
        try {
            return new Image(file.toUri().toString(), true);
        } catch (Exception e) {
            return placeholder;
        }
    }

    // 1×1 gray PNG, base64-encoded inline so no resource file is needed.
    private static Image buildPlaceholder() {
        byte[] grayPng = {
            (byte)0x89,0x50,0x4E,0x47,0x0D,0x0A,0x1A,0x0A, // PNG signature
            0x00,0x00,0x00,0x0D,0x49,0x48,0x44,0x52,        // IHDR chunk (13 bytes)
            0x00,0x00,0x00,0x01,0x00,0x00,0x00,0x01,        // width=1, height=1
            0x08,0x00,0x00,0x00,0x00,(byte)0x3A,0x7E,(byte)0x9B,0x55, // bit depth=8, color=grayscale, crc
            0x00,0x00,0x00,0x0A,0x49,0x44,0x41,0x54,        // IDAT chunk (10 bytes)
            0x78,(byte)0x9C,0x62,0x60,0x00,0x00,0x00,0x02,0x00,0x01, // zlib-compressed gray pixel
            (byte)0xE2,0x21,(byte)0xBC,0x33,                // IDAT crc
            0x00,0x00,0x00,0x00,0x49,0x45,0x4E,0x44,        // IEND chunk
            (byte)0xAE,0x42,0x60,(byte)0x82                 // IEND crc
        };
        return new Image(new ByteArrayInputStream(grayPng));
    }
}
