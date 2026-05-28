package se.niclas.broledger.service;

import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.*;
import java.util.logging.Logger;
import java.util.zip.ZipFile;

public class AssetExtractor {

    private static final Logger log = Logger.getLogger(AssetExtractor.class.getName());

    public record ExtractionResult(int total, int extracted, int missing, int fallback) {}

    @FunctionalInterface
    public interface ProgressListener {
        void onProgress(int done, int total, String currentFile);
    }

    private static final Map<String, String> PATTERN_MAP = new LinkedHashMap<>();
    static {
        PATTERN_MAP.put("gfx/ui/items/accessory/",      "accessory");
        PATTERN_MAP.put("gfx/ui/items/ammo/",           "ammo");
        PATTERN_MAP.put("gfx/ui/items/armor/",          "armor");
        PATTERN_MAP.put("gfx/ui/items/armor_upgrades/", "armor-upgrades");
        PATTERN_MAP.put("gfx/ui/backgrounds/",          "backgrounds");
        PATTERN_MAP.put("gfx/ui/items/consumables/",    "consumables");
        PATTERN_MAP.put("gfx/ui/items/helmets/",        "helmets");
        PATTERN_MAP.put("gfx/ui/injury/",               "injury");
        PATTERN_MAP.put("gfx/ui/items/loot/",           "loot");
        PATTERN_MAP.put("gfx/ui/items/misc/",           "misc");
        PATTERN_MAP.put("gfx/ui/perks/",                "perks");
        PATTERN_MAP.put("gfx/ui/items/shields/",        "shields");
        PATTERN_MAP.put("gfx/skills/",                  "skills");
        PATTERN_MAP.put("gfx/ui/items/supplies/",       "supplies");
        PATTERN_MAP.put("gfx/ui/items/tools/",          "tools");
        PATTERN_MAP.put("gfx/ui/items/trade/",          "trade");
        PATTERN_MAP.put("gfx/ui/traits/",               "traits");
        PATTERN_MAP.put("gfx/ui/items/weapons/",        "weapons");
    }

    public ExtractionResult extractMapped(Path datFile, Path outDir, ProgressListener listener)
            throws IOException {
        Map<String, String> fileMap = loadAssetMap();
        try (ZipFile zf = new ZipFile(datFile.toFile(), StandardCharsets.UTF_8)) {
            Map<String, String> mapped = doExtractMapped(zf, outDir, fileMap, listener);
            int missing = fileMap.size() - mapped.size();
            return new ExtractionResult(fileMap.size(), mapped.size(), missing, 0);
        }
    }

    public ExtractionResult extractWithFallback(Path datFile, Path outDir, ProgressListener listener)
            throws IOException {
        Map<String, String> fileMap = loadAssetMap();
        try (ZipFile zf = new ZipFile(datFile.toFile(), StandardCharsets.UTF_8)) {
            Map<String, String> mapped = doExtractMapped(zf, outDir, fileMap, listener);
            List<String> fallback = doExtractFallback(zf, outDir, new HashSet<>(mapped.keySet()), listener);
            int missing = fileMap.size() - mapped.size();
            return new ExtractionResult(fileMap.size(), mapped.size(), missing, fallback.size());
        }
    }

    private Map<String, String> doExtractMapped(ZipFile zf, Path outDir,
                                                Map<String, String> fileMap,
                                                ProgressListener listener) throws IOException {
        Map<String, String> mapped = new LinkedHashMap<>();
        int total = fileMap.size();
        int done = 0;
        for (Map.Entry<String, String> entry : fileMap.entrySet()) {
            String src = entry.getKey();
            String dst = entry.getValue();
            var zipEntry = zf.getEntry(src);
            if (zipEntry == null) {
                log.warning("Archive missing: " + src);
            } else {
                Path target = outDir.resolve(dst);
                Files.createDirectories(target.getParent());
                try (InputStream is = zf.getInputStream(zipEntry)) {
                    Files.copy(is, target, StandardCopyOption.REPLACE_EXISTING);
                }
                mapped.put(src, dst);
            }
            done++;
            if (listener != null) listener.onProgress(done, total, dst);
        }
        return mapped;
    }

    private List<String> doExtractFallback(ZipFile zf, Path outDir,
                                           Set<String> alreadyMapped,
                                           ProgressListener listener) throws IOException {
        List<String> fallback = new ArrayList<>();
        List<? extends java.util.zip.ZipEntry> entries = Collections.list(zf.entries());
        int done = 0;
        for (var entry : entries) {
            String name = entry.getName();
            if (alreadyMapped.contains(name)) { done++; continue; }
            if (!name.toLowerCase(Locale.ROOT).endsWith(".png")) { done++; continue; }
            for (Map.Entry<String, String> pattern : PATTERN_MAP.entrySet()) {
                if (name.contains(pattern.getKey())) {
                    String sub = pattern.getValue();
                    String basename = Path.of(name).getFileName().toString();
                    Path target = outDir.resolve(sub).resolve(basename);
                    Files.createDirectories(target.getParent());
                    try (InputStream is = zf.getInputStream(entry)) {
                        Files.copy(is, target, StandardCopyOption.REPLACE_EXISTING);
                    }
                    fallback.add(name);
                    if (listener != null) listener.onProgress(done, entries.size(), sub + "/" + basename);
                    break;
                }
            }
            done++;
        }
        return fallback;
    }

    private Map<String, String> loadAssetMap() throws IOException {
        try (InputStream is = AssetExtractor.class
                .getResourceAsStream("/se/niclas/broledger/data/asset-map.json")) {
            if (is == null) throw new IOException("asset-map.json not found in classpath");
            return new ObjectMapper().readValue(is, new TypeReference<Map<String, String>>() {});
        }
    }
}
