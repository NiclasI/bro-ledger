package se.niclas.broledger.service;

import tools.jackson.databind.JsonNode;
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

    private static final String VERSION_FILE = "game-art-version.json";

    public record ExtractionResult(int total, int extracted, int missing, int fallback) {}

    @FunctionalInterface
    public interface ProgressListener {
        void onProgress(int done, int total, String currentFile);
    }

    private static final Set<String> PATTERN_PREFIXES = new LinkedHashSet<>(List.of(
        "gfx/ui/items/accessory/",
        "gfx/ui/items/ammo/",
        "gfx/ui/items/armor/",
        "gfx/ui/items/armor_upgrades/",
        "gfx/ui/backgrounds/",
        "gfx/ui/items/consumables/",
        "gfx/ui/items/helmets/",
        "gfx/ui/injury/",
        "gfx/ui/items/loot/",
        "gfx/ui/items/misc/",
        "gfx/ui/perks/",
        "gfx/ui/items/shields/",
        "gfx/skills/",
        "gfx/ui/items/supplies/",
        "gfx/ui/items/tools/",
        "gfx/ui/items/trade/",
        "gfx/ui/traits/",
        "gfx/ui/items/weapons/"
    ));

    public ExtractionResult extractMapped(Path datFile, Path outDir, ProgressListener listener)
            throws IOException {
        Map<String, String> fileMap = loadAssetMap();
        try (ZipFile zf = new ZipFile(datFile.toFile(), StandardCharsets.UTF_8)) {
            Map<String, String> mapped = doExtractMapped(zf, outDir, fileMap, listener);
            int missing = fileMap.size() - mapped.size();
            writeVersionFile(outDir);
            return new ExtractionResult(fileMap.size(), mapped.size(), missing, 0);
        }
    }

    public ExtractionResult extractWithFallback(Path datFile, Path outDir, ProgressListener listener)
            throws IOException {
        Map<String, String> fileMap = loadAssetMap();
        try (ZipFile zf = new ZipFile(datFile.toFile(), StandardCharsets.UTF_8)) {
            List<? extends java.util.zip.ZipEntry> entries = Collections.list(zf.entries());
            int combinedTotal = fileMap.size() + entries.size();

            int phase1Offset = fileMap.size();
            ProgressListener phase1Listener = listener == null ? null :
                    (done, ignored, file) -> listener.onProgress(done, combinedTotal, file);
            ProgressListener phase2Listener = listener == null ? null :
                    (done, ignored, file) -> listener.onProgress(phase1Offset + done, combinedTotal, file);

            Map<String, String> mapped = doExtractMapped(zf, outDir, fileMap, phase1Listener);
            List<String> fallback = doExtractFallback(zf, outDir, new HashSet<>(mapped.keySet()), entries, phase2Listener);
            int missing = fileMap.size() - mapped.size();
            writeVersionFile(outDir);
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
            var zipEntry = zf.getEntry(src);
            String label;
            if (zipEntry == null) {
                log.warning("Archive missing: " + src);
                label = "[missing] " + src;
            } else {
                Path target = outDir.resolve(src);
                Files.createDirectories(target.getParent());
                try (InputStream is = zf.getInputStream(zipEntry)) {
                    Files.copy(is, target, StandardCopyOption.REPLACE_EXISTING);
                }
                mapped.put(src, src);
                label = src;
            }
            done++;
            if (listener != null) listener.onProgress(done, total, label);
        }
        return mapped;
    }

    private List<String> doExtractFallback(ZipFile zf, Path outDir,
                                           Set<String> alreadyMapped,
                                           List<? extends java.util.zip.ZipEntry> entries,
                                           ProgressListener listener) throws IOException {
        List<String> fallback = new ArrayList<>();
        int done = 0;
        for (var entry : entries) {
            done++;
            String name = entry.getName();
            if (alreadyMapped.contains(name)) continue;
            if (!name.toLowerCase(Locale.ROOT).endsWith(".png")) continue;
            boolean relevant = false;
            for (String prefix : PATTERN_PREFIXES) {
                if (name.contains(prefix)) { relevant = true; break; }
            }
            if (!relevant) continue;
            Path target = outDir.resolve(name);
            Files.createDirectories(target.getParent());
            try (InputStream is = zf.getInputStream(entry)) {
                Files.copy(is, target, StandardCopyOption.REPLACE_EXISTING);
            }
            fallback.add(name);
            if (listener != null) listener.onProgress(done, entries.size(), name);
        }
        return fallback;
    }

    private Map<String, String> loadAssetMap() throws IOException {
        GameDataService gds = GameDataService.getInstance();
        gds.loadFromClasspath();

        // All paths to extract are the union of values across all imageSlots sub-maps.
        Set<String> paths = new LinkedHashSet<>();
        JsonNode imageSlotsNode = gds.getImageSlots();
        if (!imageSlotsNode.isMissingNode() && imageSlotsNode.isObject()) {
            for (JsonNode subMap : imageSlotsNode) {
                if (!subMap.isObject()) continue;
                for (JsonNode value : subMap) {
                    if (value.isTextual()) paths.add(value.asText());
                }
            }
        }

        Map<String, String> result = new LinkedHashMap<>();
        paths.forEach(src -> result.put(src, src));
        return result;
    }

    /**
     * Returns true if the game-art directory's version file is missing or older
     * than the version declared in game-data.json.
     */
    public static boolean isGameArtOutdated(Path gameArtDir) {
        if (gameArtDir == null || !Files.isDirectory(gameArtDir)) return false;
        try {
            GameDataService gds = GameDataService.getInstance();
            gds.loadFromClasspath();
            int expected = gds.getGameArtVersion();
            int actual = readExtractedVersion(gameArtDir);
            return actual < expected;
        } catch (Exception e) {
            log.warning("Could not check game-art version: " + e.getMessage());
            return false;
        }
    }

    private static int readExtractedVersion(Path gameArtDir) {
        Path versionFile = gameArtDir.resolve(VERSION_FILE);
        if (!Files.exists(versionFile)) return -1;
        try {
            JsonNode node = new ObjectMapper().readTree(versionFile.toFile());
            return node.path("version").asInt(-1);
        } catch (Exception e) {
            return -1;
        }
    }

    private void writeVersionFile(Path outDir) {
        try {
            GameDataService gds = GameDataService.getInstance();
            gds.loadFromClasspath();
            int version = gds.getGameArtVersion();
            Path versionFile = outDir.resolve(VERSION_FILE);
            Files.createDirectories(outDir);
            new ObjectMapper().writerWithDefaultPrettyPrinter()
                    .writeValue(versionFile.toFile(), Map.of("version", version));
        } catch (Exception e) {
            log.warning("Could not write version file: " + e.getMessage());
        }
    }
}
