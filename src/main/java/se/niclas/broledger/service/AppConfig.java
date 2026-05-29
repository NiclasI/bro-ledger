package se.niclas.broledger.service;

import se.niclas.broledger.model.Stat;
import tools.jackson.databind.ObjectMapper;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.logging.Logger;

/**
 * Application configuration persisted to ~/.bro-ledger/config.json.
 */
public class AppConfig {

    private static final Path CONFIG_DIR  = Path.of(System.getProperty("user.home"), ".bro-ledger");
    private static final Path CONFIG_FILE = CONFIG_DIR.resolve("config.json");

    private static final Logger log = Logger.getLogger(AppConfig.class.getName());
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private static AppConfig instance;

    // Public fields — read/write freely, then call save().
    public String       lastSaveFilePath;
    public String       gameArtDirectory;
    public int          windowWidth         = 1800;
    public int          windowHeight        = 1320;
    public Integer      windowX;
    public Integer      windowY;
    public List<String> overviewColumnOrder;
    /** Ordered list of role UUIDs defining user-chosen role display order. */
    public List<String> roleOrder;
    /** Expected-stats calculation mode: "NAIVE" (default) or "GREEDY". */
    public String expectedStatsMode = "NAIVE";
    /** Level-up modal behaviour: "MODAL" (default), "AUTO_CLOSE" (15 s), or "OFF". */
    public String levelUpModalMode = "MODAL";

    private AppConfig() {}

    public static AppConfig getInstance() {
        if (instance == null) instance = new AppConfig();
        return instance;
    }

    /** Load from the default config file; silently falls back to defaults if absent. */
    public void load() {
        loadFrom(CONFIG_FILE);
    }

    /** Load from an explicit file path (used by tests). */
    public void loadFrom(Path file) {
        if (!Files.exists(file)) return;
        try {
            AppConfig loaded = MAPPER.readValue(file.toFile(), AppConfig.class);
            this.lastSaveFilePath    = loaded.lastSaveFilePath;
            this.gameArtDirectory    = loaded.gameArtDirectory;
            if (loaded.windowWidth  > 0) this.windowWidth  = loaded.windowWidth;
            if (loaded.windowHeight > 0) this.windowHeight = loaded.windowHeight;
            this.windowX             = loaded.windowX;
            this.windowY             = loaded.windowY;
            this.overviewColumnOrder = loaded.overviewColumnOrder;
            this.roleOrder           = loaded.roleOrder;
            if (loaded.expectedStatsMode  != null) this.expectedStatsMode  = loaded.expectedStatsMode;
            if (loaded.levelUpModalMode   != null) this.levelUpModalMode   = loaded.levelUpModalMode;
        } catch (Exception e) {
            log.warning("AppConfig: could not read config — " + e.getMessage());
        }
    }

    /** Persist current values to the default config file. */
    public void save() {
        saveTo(CONFIG_FILE);
    }

    /** Persist current values to an explicit file path (used by tests). */
    public void saveTo(Path file) {
        try {
            Files.createDirectories(file.getParent());
            MAPPER.writerWithDefaultPrettyPrinter().writeValue(file.toFile(), this);
        } catch (Exception e) {
            log.warning("AppConfig: could not save config — " + e.getMessage());
        }
    }

    /** True if a game-art directory is configured and exists on disk. */
    public boolean hasGameArtDirectory() {
        return gameArtDirectory != null && Files.isDirectory(Path.of(gameArtDirectory));
    }

    /** Resolved game-art root path, or null if not configured. */
    public Path gameArtRoot() {
        return hasGameArtDirectory() ? Path.of(gameArtDirectory) : null;
    }

    /**
     * Stats in the user's chosen column order (driven by {@link #overviewColumnOrder}).
     * Always returns all 8 stats; any not present in the saved order are appended at the end.
     * This is the single source of truth for stat display order across all views.
     */
    public Stat[] orderedStats() {
        if (overviewColumnOrder == null || overviewColumnOrder.isEmpty()) {
            return Stat.values();
        }
        List<Stat> ordered = new ArrayList<>();
        Set<Stat>  seen    = new HashSet<>();
        for (String colId : overviewColumnOrder) {
            if (colId.startsWith("stat-")) {
                String abbrev = colId.substring(5);
                for (Stat s : Stat.values()) {
                    if (s.abbrev().equals(abbrev)) {
                        ordered.add(s);
                        seen.add(s);
                        break;
                    }
                }
            }
        }
        for (Stat s : Stat.values()) {
            if (!seen.contains(s)) ordered.add(s);
        }
        return ordered.toArray(new Stat[0]);
    }

    /** For tests only — resets the singleton so the next getInstance() starts fresh. */
    static void resetForTest() {
        instance = null;
    }
}
