package se.niclas.broledger.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import se.niclas.broledger.model.Stat;

import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class AppConfigTest {

    @TempDir
    Path tmp;

    private AppConfig fresh() {
        // Bypass the singleton so each test gets a clean instance.
        try {
            var ctor = AppConfig.class.getDeclaredConstructor();
            ctor.setAccessible(true);
            return (AppConfig) ctor.newInstance();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Test
    void defaultValues() {
        AppConfig cfg = fresh();
        assertNull(cfg.lastSaveFilePath);
        assertNull(cfg.gameArtDirectory);
        assertEquals(1800, cfg.windowWidth);
        assertEquals(1320, cfg.windowHeight);
    }

    @Test
    void roundTrip() {
        Path file = tmp.resolve("config.json");

        AppConfig written = fresh();
        written.lastSaveFilePath = "/saves/my.sav";
        written.gameArtDirectory = "/art";
        written.windowWidth      = 1600;
        written.windowHeight     = 1000;
        written.saveTo(file);

        AppConfig read = fresh();
        read.loadFrom(file);

        assertEquals("/saves/my.sav", read.lastSaveFilePath);
        assertEquals("/art",          read.gameArtDirectory);
        assertEquals(1600,            read.windowWidth);
        assertEquals(1000,            read.windowHeight);
    }

    @Test
    void loadMissingFileKeepsDefaults() {
        AppConfig cfg = fresh();
        cfg.loadFrom(tmp.resolve("nonexistent.json"));
        assertEquals(1800, cfg.windowWidth);
        assertNull(cfg.lastSaveFilePath);
    }

    @Test
    void partialJsonPreservesDefaults() throws Exception {
        // File contains only windowWidth — other fields should stay at defaults.
        Path file = tmp.resolve("partial.json");
        java.nio.file.Files.writeString(file, "{\"windowWidth\":800}");

        AppConfig cfg = fresh();
        cfg.loadFrom(file);

        assertEquals(800,  cfg.windowWidth);
        assertEquals(1320, cfg.windowHeight);  // default preserved
        assertNull(cfg.lastSaveFilePath);
    }

    @Test
    void hasGameArtDirectory_falseWhenNull() {
        AppConfig cfg = fresh();
        assertFalse(cfg.hasGameArtDirectory());
    }

    @Test
    void hasGameArtDirectory_falseWhenPathDoesNotExist() {
        AppConfig cfg = fresh();
        cfg.gameArtDirectory = "/does/not/exist/ever";
        assertFalse(cfg.hasGameArtDirectory());
    }

    @Test
    void hasGameArtDirectory_trueForExistingDirectory(@TempDir Path dir) {
        AppConfig cfg = fresh();
        cfg.gameArtDirectory = dir.toString();
        assertTrue(cfg.hasGameArtDirectory());
        assertEquals(dir, cfg.gameArtRoot());
    }

    @Test
    void orderedStats_nullConfigReturnsCanonicalOrder() {
        AppConfig cfg = fresh();
        Stat[] result = cfg.orderedStats();
        assertArrayEquals(Stat.values(), result);
    }

    @Test
    void orderedStats_emptyConfigReturnsCanonicalOrder() {
        AppConfig cfg = fresh();
        cfg.overviewColumnOrder = List.of();
        assertArrayEquals(Stat.values(), cfg.orderedStats());
    }

    @Test
    void orderedStats_fullReorderRespected() {
        AppConfig cfg = fresh();
        // Reverse the default order
        cfg.overviewColumnOrder = List.of(
            "stat-RD", "stat-MD", "stat-RS", "stat-MS",
            "stat-Init", "stat-Fat", "stat-Res", "stat-HP"
        );
        Stat[] result = cfg.orderedStats();
        assertEquals(Stat.RANGED_DEFENSE, result[0]);
        assertEquals(Stat.MELEE_DEFENSE,  result[1]);
        assertEquals(Stat.RANGED_SKILL,   result[2]);
        assertEquals(Stat.MELEE_SKILL,    result[3]);
        assertEquals(Stat.INITIATIVE,     result[4]);
        assertEquals(Stat.FATIGUE,        result[5]);
        assertEquals(Stat.RESOLVE,        result[6]);
        assertEquals(Stat.HEALTH,         result[7]);
        assertEquals(8, result.length);
    }

    @Test
    void orderedStats_partialConfigAppendsMissingStats() {
        AppConfig cfg = fresh();
        cfg.overviewColumnOrder = List.of("stat-MS", "stat-HP", "other-col");
        Stat[] result = cfg.orderedStats();
        assertEquals(8, result.length);
        assertEquals(Stat.MELEE_SKILL, result[0]);
        assertEquals(Stat.HEALTH,      result[1]);
        // Remaining 6 stats appended in canonical order (no duplicates)
        long distinct = java.util.Arrays.stream(result).distinct().count();
        assertEquals(8, distinct);
    }

    @Test
    void orderedStats_unknownEntriesIgnored() {
        AppConfig cfg = fresh();
        cfg.overviewColumnOrder = List.of("stat-UNKNOWN", "stat-HP", "stat-Res");
        Stat[] result = cfg.orderedStats();
        assertEquals(8, result.length);
        assertEquals(Stat.HEALTH,  result[0]);
        assertEquals(Stat.RESOLVE, result[1]);
    }

    // ---- statAbbrevFromColumnId -------------------------------------------

    @Test
    void statAbbrevFromColumnId_extractsAbbrev() {
        assertEquals("HP",  AppConfig.statAbbrevFromColumnId("stat-HP"));
        assertEquals("MS",  AppConfig.statAbbrevFromColumnId("stat-MS"));
        assertEquals("RD",  AppConfig.statAbbrevFromColumnId("stat-RD"));
    }

    @Test
    void statAbbrevFromColumnId_nullForNonStatColumn() {
        assertNull(AppConfig.statAbbrevFromColumnId("level"));
        assertNull(AppConfig.statAbbrevFromColumnId("name"));
        assertNull(AppConfig.statAbbrevFromColumnId(null));
    }

    @Test
    void statAbbrevFromColumnId_nullForEmptyAbbrev() {
        // "stat-" with nothing after it returns empty string (not null)
        assertEquals("", AppConfig.statAbbrevFromColumnId("stat-"));
    }
}
