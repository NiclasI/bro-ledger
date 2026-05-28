package se.niclas.broledger.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import se.niclas.broledger.model.Brother;
import se.niclas.broledger.model.BrotherAnnotation;
import se.niclas.broledger.model.Stat;
import se.niclas.broledger.service.AnnotationService.LevelUpEvent;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class AnnotationServiceTest {

    @TempDir
    Path tempDir;

    private AnnotationService service;
    private Path savePath;

    @BeforeEach
    void setUp() throws IOException {
        savePath = tempDir.resolve("test.sav");
        Files.writeString(savePath, "dummy");
        // Fresh instance via reflection to avoid singleton bleed between tests
        try {
            var field = AnnotationService.class.getDeclaredField("instance");
            field.setAccessible(true);
            field.set(null, null);
        } catch (Exception e) {
            fail("Could not reset singleton: " + e.getMessage());
        }
        service = AnnotationService.getInstance();
        service.loadFor(savePath);
    }

    @Test
    void getReturnsFreshAnnotationForUnknownFingerprint() {
        BrotherAnnotation a = service.get("ABCD1234");
        assertNotNull(a);
        assertNull(a.roleId);
    }

    @Test
    void setRolePersistsAcrossReload() {
        service.setRole("FP001", "uuid-tank");

        service.loadFor(savePath);
        assertEquals("uuid-tank", service.get("FP001").roleId);
    }

    @Test
    void nullRoleIdPersistsAsNull() {
        service.setRole("FP001", "uuid-tank");
        service.setRole("FP001", null);

        service.loadFor(savePath);
        assertNull(service.get("FP001").roleId);
    }

    @Test
    void multipleBrothersPersistedIndependently() {
        service.setRole("A", "uuid-archer");
        service.setRole("B", "uuid-warrior");

        service.loadFor(savePath);
        assertEquals("uuid-archer",  service.get("A").roleId);
        assertEquals("uuid-warrior", service.get("B").roleId);
    }

    @Test
    void keeperJsonCreatedAdjacentToSave() {
        service.setRole("FP003", "uuid-scout");
        Path expected = savePath.resolveSibling(savePath.getFileName() + ".broledger.json");
        assertTrue(Files.exists(expected), "broledger.json not created adjacent to save");
    }

    @Test
    void overwritingRoleUpdatesFile() {
        service.setRole("FP004", "uuid-first");
        service.setRole("FP004", "uuid-second");

        service.loadFor(savePath);
        assertEquals("uuid-second", service.get("FP004").roleId);
    }

    @Test
    void statIncreasesPersistAcrossReload() {
        int[] inc = new int[Stat.values().length];
        inc[Stat.HEALTH.ordinal()]      = 3;
        inc[Stat.MELEE_SKILL.ordinal()] = 2;
        service.setStatIncreases("FP005", inc);

        service.loadFor(savePath);
        int[] loaded = service.get("FP005").statIncreases;
        assertNotNull(loaded);
        assertEquals(3, loaded[Stat.HEALTH.ordinal()]);
        assertEquals(2, loaded[Stat.MELEE_SKILL.ordinal()]);
        assertEquals(0, loaded[Stat.INITIATIVE.ordinal()]);
    }

    @Test
    void statIncreases_nullForBrotherWithNoIncreases() {
        service.setRole("FP006", "uuid-x");
        service.loadFor(savePath);
        assertNull(service.get("FP006").statIncreases,
                "statIncreases must be null when never set");
    }

    @Test
    void statIncreases_oldFileWithoutFieldLoadsWithoutError() throws IOException {
        // Write a broledger.json that has no statIncreases field (legacy format)
        Path keeperJson = savePath.resolveSibling(savePath.getFileName() + ".broledger.json");
        Files.writeString(keeperJson, "{\"version\":1,\"annotations\":{\"FP007\":{\"roleId\":\"uuid-y\"}}}");

        service.loadFor(savePath);
        BrotherAnnotation a = service.get("FP007");
        assertEquals("uuid-y", a.roleId, "roleId from legacy file must load");
        assertNull(a.statIncreases, "statIncreases must default to null in legacy file");
    }

    // ---- reconcileOnReload tests -------------------------------------------

    private static Brother makeBrother(String fp, String name, int levelPoints,
                                       int[] stats, int[] stars) {
        Brother b = new Brother();
        b.fingerprint = fp;
        b.name        = name;
        b.levelPoints = levelPoints;
        b.stats       = stats;
        b.stars       = stars;
        return b;
    }

    @Test
    void reconcile_consumesPlannedIncreaseOnLevelUp() {
        int[] statsOld = new int[8];
        int[] statsNew = new int[8];
        statsOld[Stat.MELEE_SKILL.statIndex()] = 50;
        statsNew[Stat.MELEE_SKILL.statIndex()] = 52; // delta=2, within 0★ range (1,3)

        Brother ob = makeBrother("R1", "Gunda", 1, statsOld, new int[8]);
        Brother nb = makeBrother("R1", "Gunda", 0, statsNew, new int[8]);

        int[] planned = new int[Stat.values().length];
        planned[Stat.MELEE_SKILL.ordinal()] = 1;
        service.setStatIncreases("R1", planned);

        List<LevelUpEvent> events = service.reconcileOnReload(List.of(ob), List.of(nb));

        assertEquals(1, events.size());
        LevelUpEvent e = events.get(0);
        assertEquals("Gunda", e.name());
        assertTrue(e.adjusted());
        assertTrue(e.adjustedStats().contains(Stat.MELEE_SKILL));
        assertEquals(2, e.statDeltas().get(Stat.MELEE_SKILL));
        assertEquals(0, service.get("R1").statIncreases[Stat.MELEE_SKILL.ordinal()]);
    }

    @Test
    void reconcile_noAdjustmentWhenNoPlan() {
        int[] statsOld = new int[8];
        int[] statsNew = new int[8];
        statsOld[Stat.HEALTH.statIndex()] = 60;
        statsNew[Stat.HEALTH.statIndex()] = 63; // delta=3, no statIncreases set

        Brother ob = makeBrother("R2", "Torben", 1, statsOld, new int[8]);
        Brother nb = makeBrother("R2", "Torben", 0, statsNew, new int[8]);

        List<LevelUpEvent> events = service.reconcileOnReload(List.of(ob), List.of(nb));

        assertEquals(1, events.size());
        LevelUpEvent e = events.get(0);
        assertFalse(e.adjusted());
        assertTrue(e.adjustedStats().isEmpty());
        assertFalse(e.statDeltas().isEmpty());
    }

    @Test
    void reconcile_statDeltaWithinRangeForStars() {
        // Each combination: stat, starCount, delta — delta must satisfy rangeForStars
        record Case(Stat stat, int stars, int delta) {}
        List<Case> cases = List.of(
                new Case(Stat.MELEE_SKILL, 0, 2), // 0★ range (1,3)
                new Case(Stat.MELEE_SKILL, 2, 3), // 2★ range (3,3)
                new Case(Stat.HEALTH,      3, 4), // 3★ range (4,5)
                new Case(Stat.INITIATIVE,  1, 4)  // 1★ range (4,5); stars[starIndex=3]
        );

        for (Case c : cases) {
            // Reset singleton so each sub-case starts clean
            try {
                var field = AnnotationService.class.getDeclaredField("instance");
                field.setAccessible(true);
                field.set(null, null);
            } catch (Exception ex) {
                fail("Reset failed: " + ex.getMessage());
            }
            service = AnnotationService.getInstance();
            service.loadFor(savePath);

            int[] statsOld = new int[8];
            int[] statsNew = new int[8];
            int[] starsArr = new int[8];
            statsOld[c.stat().statIndex()] = 50;
            statsNew[c.stat().statIndex()] = 50 + c.delta();
            starsArr[c.stat().starIndex()]  = c.stars();

            Brother ob = makeBrother("RANGE_" + c.stat(), c.stat().name(), 1, statsOld, starsArr);
            Brother nb = makeBrother("RANGE_" + c.stat(), c.stat().name(), 0, statsNew, starsArr);

            List<LevelUpEvent> events = service.reconcileOnReload(List.of(ob), List.of(nb));

            assertEquals(1, events.size(), "expected event for " + c.stat());
            int actualDelta = events.get(0).statDeltas().getOrDefault(c.stat(), -1);
            StatPotentialCalculator.Range r =
                    StatPotentialCalculator.rangeForStars(c.stat().statIndex(), c.stars());
            assertTrue(actualDelta >= r.min() && actualDelta <= r.max(),
                    "delta " + actualDelta + " out of range [" + r.min() + ".." + r.max()
                    + "] for " + c.stat() + " " + c.stars() + "★");
        }
    }

    @Test
    void reconcile_countsMatchLevelUps() {
        // Two levels assigned: algorithm decrements each stat at most once per reconcile call
        int[] statsOld = new int[8];
        int[] statsNew = new int[8];
        statsOld[Stat.HEALTH.statIndex()]      = 60;
        statsNew[Stat.HEALTH.statIndex()]      = 65; // +5 across 2 levels, detected as single delta
        statsOld[Stat.MELEE_SKILL.statIndex()] = 40;
        statsNew[Stat.MELEE_SKILL.statIndex()] = 42; // +2

        Brother ob = makeBrother("R3", "Wulf", 2, statsOld, new int[8]);
        Brother nb = makeBrother("R3", "Wulf", 0, statsNew, new int[8]);

        int[] planned = new int[Stat.values().length];
        planned[Stat.HEALTH.ordinal()]      = 2;
        planned[Stat.MELEE_SKILL.ordinal()] = 1;
        service.setStatIncreases("R3", planned);

        List<LevelUpEvent> events = service.reconcileOnReload(List.of(ob), List.of(nb));

        assertEquals(1, events.size());
        assertTrue(events.get(0).adjusted());
        // Each stat decremented at most once regardless of level count
        assertEquals(1, service.get("R3").statIncreases[Stat.HEALTH.ordinal()]);
        assertEquals(0, service.get("R3").statIncreases[Stat.MELEE_SKILL.ordinal()]);
        assertEquals(2, events.get(0).adjustedStats().size());
    }

    @Test
    void reconcile_noEventForNonLeveledBrother() {
        int[] stats = new int[8];
        stats[Stat.HEALTH.statIndex()] = 60;

        Brother ob = makeBrother("R4", "Himsa", 1, stats, new int[8]);
        Brother nb = makeBrother("R4", "Himsa", 1, stats.clone(), new int[8]); // levelPoints unchanged

        List<LevelUpEvent> events = service.reconcileOnReload(List.of(ob), List.of(nb));

        assertTrue(events.isEmpty());
    }

    @Test
    void reconcile_skipsUnknownFingerprint() {
        int[] statsOld = new int[8];
        int[] statsNew = new int[8];
        statsNew[Stat.HEALTH.statIndex()] = 3;

        Brother ob = makeBrother("KNOWN", "Otto", 1, statsOld, new int[8]);
        Brother nb = makeBrother("NEW",   "Rolf", 0, statsNew, new int[8]); // no match in old list

        List<LevelUpEvent> events = service.reconcileOnReload(List.of(ob), List.of(nb));

        assertTrue(events.isEmpty());
    }
}
