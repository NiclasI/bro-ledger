package se.niclas.broledger.service;

import se.niclas.broledger.model.ShareRecord;
import se.niclas.broledger.model.Stat;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
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
    private static final ObjectMapper MAPPER = JsonMapper.builder()
            .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
            .build();

    private static AppConfig instance;
    /** Overrides CONFIG_FILE during tests so save()/load() never touch ~/.bro-ledger/config.json. */
    static Path configFileForTest;


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
    /**
     * When true, the app captures a timestamped copy of the save file (plus the current
     * planned-increase annotations) into {@code ~/.bro-ledger/replay/<hash>/} before each
     * overwrite is reconciled. Disabled by default. Use ReplayAnalyzer to inspect the sequence.
     */
    public boolean replayCaptureEnabled = false;
    /**
     * Most-recent-first history of role packs published from this machine, capped at
     * {@link #RECENT_SHARES_MAX}. Each entry carries the server-issued owner token that
     * authorizes updating its code — entries that roll off the cap lose that ability
     * permanently (the fallback is always publishing a new code).
     */
    public List<ShareRecord> recentShares;

    /** How many published packs stay updateable from this machine. */
    public static final int RECENT_SHARES_MAX = 5;

    private AppConfig() {}

    public static AppConfig getInstance() {
        if (instance == null) instance = new AppConfig();
        return instance;
    }

    /** Load from the default config file; silently falls back to defaults if absent. */
    public void load() {
        loadFrom(configFileForTest != null ? configFileForTest : CONFIG_FILE);
    }

    /** Load from an explicit file path (used by tests). */
    public void loadFrom(Path file) {
        if (!Files.exists(file)) {
            log.fine("AppConfig: no config file at " + file + " — using defaults");
            return;
        }
        log.fine("AppConfig: loading from " + file);
        try {
            MAPPER.readerForUpdating(this).readValue(file.toFile());
            log.fine("AppConfig: loaded — lastSaveFilePath=" + lastSaveFilePath
                    + ", gameArtDirectory=" + gameArtDirectory);
        } catch (Exception e) {
            // File exists but is unreadable (corrupt, truncated, wrong format).
            // Preserve it under a .bad-<timestamp> name so the user can recover it,
            // and let the next save() write a fresh file. Do NOT silently overwrite
            // with defaults — that would permanently destroy the user's settings.
            log.severe("AppConfig: could not read config — starting with defaults. Original file preserved. Error: " + e.getMessage());
            try {
                Path backup = file.resolveSibling(file.getFileName() + ".bad-" + System.currentTimeMillis());
                Files.move(file, backup);
                log.warning("AppConfig: corrupt config backed up to " + backup);
            } catch (Exception ex) {
                log.warning("AppConfig: could not back up corrupt config — " + ex.getMessage());
            }
        }
    }

    /** Persist current values to the default config file. */
    public void save() {
        saveTo(configFileForTest != null ? configFileForTest : CONFIG_FILE);
    }

    /** Persist current values to an explicit file path (used by tests). */
    public void saveTo(Path file) {
        log.fine("AppConfig: saving to " + file
                + " — lastSaveFilePath=" + lastSaveFilePath
                + ", gameArtDirectory=" + gameArtDirectory);
        Path tmp = file.resolveSibling(file.getFileName() + ".tmp");
        try {
            Files.createDirectories(file.getParent());
            MAPPER.writerWithDefaultPrettyPrinter().writeValue(tmp.toFile(), this);
            try {
                Files.move(tmp, file, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException ex) {
                Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING);
            }
            log.fine("AppConfig: saved OK");
        } catch (Exception e) {
            log.warning("AppConfig: could not save config — " + e.getMessage());
            try { Files.deleteIfExists(tmp); } catch (Exception ignored) {}
        }
    }

    /**
     * Records a publish or update in {@link #recentShares}: an existing entry with the
     * same code is replaced and moved to the top (an update refreshes title/timestamp
     * without duplicating), and the list is trimmed to {@link #RECENT_SHARES_MAX}.
     * The caller is responsible for {@link #save()}.
     */
    public void recordShare(ShareRecord record) {
        if (recentShares == null) recentShares = new ArrayList<>();
        recentShares.removeIf(r -> r == null || (record.code != null && record.code.equals(r.code)));
        recentShares.add(0, record);
        while (recentShares.size() > RECENT_SHARES_MAX) {
            recentShares.remove(recentShares.size() - 1);
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
            String abbrev = statAbbrevFromColumnId(colId);
            if (abbrev != null) {
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

    /**
     * Extracts the stat abbreviation from an overview column-ID of the form {@code "stat-<abbrev>"}.
     * Returns {@code null} for non-stat column IDs.
     */
    static String statAbbrevFromColumnId(String colId) {
        if (colId != null && colId.startsWith("stat-")) return colId.substring(5);
        return null;
    }

    /** For tests only — resets the singleton and config-file override so the next getInstance() starts fresh. */
    static void resetForTest() {
        instance = null;
        configFileForTest = null;
    }
}
