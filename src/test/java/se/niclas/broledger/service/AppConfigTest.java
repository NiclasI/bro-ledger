package se.niclas.broledger.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import se.niclas.broledger.model.ShareRecord;
import se.niclas.broledger.model.Stat;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;

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
        assertFalse(cfg.replayCaptureEnabled, "replayCaptureEnabled must default to false");
    }

    @Test
    void roundTrip() {
        Path file = tmp.resolve("config.json");

        AppConfig written = fresh();
        written.lastSaveFilePath    = "/saves/my.sav";
        written.gameArtDirectory    = "/art";
        written.windowWidth         = 1600;
        written.windowHeight        = 1000;
        written.replayCaptureEnabled = true;
        written.saveTo(file);

        AppConfig read = fresh();
        read.loadFrom(file);

        assertEquals("/saves/my.sav", read.lastSaveFilePath);
        assertEquals("/art",          read.gameArtDirectory);
        assertEquals(1600,            read.windowWidth);
        assertEquals(1000,            read.windowHeight);
        assertTrue(read.replayCaptureEnabled, "replayCaptureEnabled must round-trip");
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

    // ---- recentShares -------------------------------------------------------

    private static ShareRecord share(String code, String title) {
        return new ShareRecord(code, "token-" + code, title, "2026-07-05T12:00:00Z", 3);
    }

    @Test
    void recordShare_capsAtMaxDroppingOldest() {
        AppConfig cfg = fresh();
        for (int i = 1; i <= AppConfig.RECENT_SHARES_MAX + 1; i++) {
            cfg.recordShare(share("CODE000" + i, "Pack " + i));
        }
        assertEquals(AppConfig.RECENT_SHARES_MAX, cfg.recentShares.size());
        // Most recent first; the very first share rolled off.
        assertEquals("CODE0006", cfg.recentShares.get(0).code);
        assertTrue(cfg.recentShares.stream().noneMatch(r -> r.code.equals("CODE0001")));
    }

    @Test
    void recordShare_sameCodeMovesToTopWithoutDuplicate() {
        AppConfig cfg = fresh();
        cfg.recordShare(share("CODEAAAA", "First"));
        cfg.recordShare(share("CODEBBBB", "Second"));
        cfg.recordShare(share("CODEAAAA", "First v2"));

        assertEquals(2, cfg.recentShares.size());
        assertEquals("CODEAAAA", cfg.recentShares.get(0).code);
        assertEquals("First v2", cfg.recentShares.get(0).title);
        assertEquals("CODEBBBB", cfg.recentShares.get(1).code);
    }

    @Test
    void recentShares_roundTrip() {
        Path file = tmp.resolve("config.json");

        AppConfig written = fresh();
        written.recordShare(new ShareRecord("AB3D9F2K", "cafe0123", "My Pack",
                "2026-07-05T12:00:00Z", 4));
        written.saveTo(file);

        AppConfig read = fresh();
        read.loadFrom(file);

        assertNotNull(read.recentShares);
        assertEquals(1, read.recentShares.size());
        ShareRecord r = read.recentShares.get(0);
        assertEquals("AB3D9F2K", r.code);
        assertEquals("cafe0123", r.ownerToken);
        assertEquals("My Pack", r.title);
        assertEquals("2026-07-05T12:00:00Z", r.uploadedAt);
        assertEquals(4, r.roleCount);
        assertTrue(r.updatable());
    }

    @Test
    void recordShare_tokenlessEntryIsNotUpdatable() {
        AppConfig cfg = fresh();
        cfg.recordShare(new ShareRecord("AB3D9F2K", null, "Old", "2026-07-05T12:00:00Z", 1));
        assertFalse(cfg.recentShares.get(0).updatable());
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

    // ---- Persistence hardening tests ----------------------------------------

    @Test
    void unknownFieldsInJsonAreIgnored() throws Exception {
        // A config.json written by an older (or newer) build may contain fields
        // that the current class doesn't recognise. They must be silently skipped,
        // not cause an exception that wipes all settings.
        Path file = tmp.resolve("config.json");
        Files.writeString(file,
                "{\"lastSaveFilePath\":\"/my/save.sav\"," +
                "\"windowWidth\":1600," +
                "\"obsoleteField\":\"some-old-value\"," +
                "\"anotherRemovedField\":42}");

        AppConfig cfg = fresh();
        cfg.loadFrom(file);   // must not throw

        assertEquals("/my/save.sav", cfg.lastSaveFilePath);
        assertEquals(1600, cfg.windowWidth);
        // Unknown fields didn't corrupt anything — other defaults intact
        assertEquals(1320, cfg.windowHeight);
    }

    @Test
    void corruptFileIsPreservedAndDefaultsUsed() throws Exception {
        // When config.json is corrupt (truncated write, encoding error, etc.),
        // loadFrom() must NOT silently keep defaults and then let the next save()
        // overwrite the file — that would permanently destroy the user's settings.
        // Instead: back the corrupt file up as config.json.bad-<ts> and use defaults.
        Path file = tmp.resolve("config.json");
        Files.writeString(file, "{ not valid json at all");

        AppConfig cfg = fresh();
        cfg.loadFrom(file);   // must not throw

        // Singleton stays at defaults
        assertNull(cfg.lastSaveFilePath);
        assertEquals(1800, cfg.windowWidth);

        // Corrupt original was renamed away (no longer exists at original path)
        assertFalse(Files.exists(file), "Corrupt config.json should have been renamed away");

        // A .bad-* backup must exist in the same directory
        try (Stream<Path> entries = Files.list(tmp)) {
            boolean backupExists = entries
                    .map(p -> p.getFileName().toString())
                    .anyMatch(n -> n.startsWith("config.json.bad-"));
            assertTrue(backupExists, "A config.json.bad-<ts> backup must have been created");
        }

        // A subsequent save() writes a fresh, valid file at the original path
        cfg.saveTo(file);
        assertTrue(Files.exists(file), "saveTo() should create a fresh config.json");
        String written = Files.readString(file);
        assertTrue(written.contains("windowWidth"), "New config.json should be valid JSON");
    }

    @Test
    void atomicWriteLeavesNoTmpFile() throws Exception {
        Path file = tmp.resolve("config.json");

        AppConfig cfg = fresh();
        cfg.lastSaveFilePath = "/saves/test.sav";
        cfg.saveTo(file);

        // No leftover .tmp sibling
        assertFalse(Files.exists(file.resolveSibling("config.json.tmp")),
                "saveTo() must not leave a .tmp file behind");

        // The real file is valid and round-trips
        AppConfig read = fresh();
        read.loadFrom(file);
        assertEquals("/saves/test.sav", read.lastSaveFilePath);
    }
}
