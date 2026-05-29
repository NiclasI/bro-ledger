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
        // HEALTH delta=5, 0★ maxPerRoll=4: ceil(5/4)=2 raises proved → consume 2, leaving 0
        assertEquals(0, service.get("R3").statIncreases[Stat.HEALTH.ordinal()]);
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

    @Test
    void reconcile_reportsAddedPerks() {
        int[] stats = new int[8];

        Brother ob = makeBrother("P1", "Gorm", 1, stats, new int[8]);
        ob.perkIds.add("AAAA0001");

        Brother nb = makeBrother("P1", "Gorm", 0, stats.clone(), new int[8]);
        nb.perkIds.add("AAAA0001"); // existing perk
        nb.perkIds.add("BBBB0002"); // newly acquired perk

        List<LevelUpEvent> events = service.reconcileOnReload(List.of(ob), List.of(nb));

        assertEquals(1, events.size());
        LevelUpEvent e = events.get(0);
        assertEquals(List.of("BBBB0002"), e.addedPerkIds());
    }

    @Test
    void reconcile_consumesLowerBoundRaisesForMultipleLevels() {
        // N=3 levels, Health delta=9 at 0★: ceil(9/4)=3 raises proved, planned=5 → consume 3, leave 2
        int[] statsOld = new int[8], statsNew = new int[8];
        statsOld[Stat.HEALTH.statIndex()] = 60;
        statsNew[Stat.HEALTH.statIndex()] = 69;

        Brother ob = makeBrother("ML1", "Brunhilde", 3, statsOld, new int[8]);
        Brother nb = makeBrother("ML1", "Brunhilde", 0, statsNew, new int[8]);

        int[] planned = new int[Stat.values().length];
        planned[Stat.HEALTH.ordinal()] = 5;
        service.setStatIncreases("ML1", planned);

        List<LevelUpEvent> events = service.reconcileOnReload(List.of(ob), List.of(nb));

        assertEquals(1, events.size());
        assertTrue(events.get(0).adjusted());
        assertEquals(3, events.get(0).levelsAssigned());
        assertEquals(3, events.get(0).consumedIncreases().get(Stat.HEALTH));
        assertEquals(2, service.get("ML1").statIncreases[Stat.HEALTH.ordinal()]);
    }

    @Test
    void reconcile_capsConsumedAtPlannedCount() {
        // Health delta=12 at 0★: ceil(12/4)=3, but only 2 planned → consume 2, leave 0
        int[] statsOld = new int[8], statsNew = new int[8];
        statsOld[Stat.HEALTH.statIndex()] = 60;
        statsNew[Stat.HEALTH.statIndex()] = 72;

        Brother ob = makeBrother("ML2", "Gorm", 5, statsOld, new int[8]);
        Brother nb = makeBrother("ML2", "Gorm", 0, statsNew, new int[8]);

        int[] planned = new int[Stat.values().length];
        planned[Stat.HEALTH.ordinal()] = 2;
        service.setStatIncreases("ML2", planned);

        List<LevelUpEvent> events = service.reconcileOnReload(List.of(ob), List.of(nb));

        assertEquals(1, events.size());
        assertEquals(2, events.get(0).consumedIncreases().get(Stat.HEALTH));
        assertEquals(0, service.get("ML2").statIncreases[Stat.HEALTH.ordinal()]);
    }

    @Test
    void reconcile_post11RecordsStatDeltasInPost11Increases() {
        int[] statsOld = new int[8];
        int[] statsNew = new int[8];
        statsOld[Stat.HEALTH.statIndex()]      = 80;
        statsNew[Stat.HEALTH.statIndex()]      = 81; // +1
        statsOld[Stat.MELEE_SKILL.statIndex()] = 70;
        statsNew[Stat.MELEE_SKILL.statIndex()] = 71; // +1
        statsOld[Stat.RESOLVE.statIndex()]     = 55;
        statsNew[Stat.RESOLVE.statIndex()]     = 56; // +1

        Brother ob = makeBrother("Q1", "Gunnar", 1, statsOld, new int[8]);
        ob.levelTotal = 12;
        Brother nb = makeBrother("Q1", "Gunnar", 0, statsNew, new int[8]);
        nb.levelTotal = 12;

        List<LevelUpEvent> events = service.reconcileOnReload(List.of(ob), List.of(nb));

        assertEquals(1, events.size());
        LevelUpEvent e = events.get(0);
        assertTrue(e.post11());
        assertTrue(e.adjusted());
        assertEquals(1, e.levelsAssigned());
        assertTrue(e.consumedIncreases().isEmpty());  // no planned increases consumed
        assertEquals(1, e.statDeltas().get(Stat.HEALTH));
        assertEquals(1, e.statDeltas().get(Stat.MELEE_SKILL));
        assertEquals(1, e.statDeltas().get(Stat.RESOLVE));

        int[] post11 = service.get("Q1").post11Increases;
        assertNotNull(post11);
        assertEquals(1, post11[Stat.HEALTH.ordinal()]);
        assertEquals(1, post11[Stat.MELEE_SKILL.ordinal()]);
        assertEquals(1, post11[Stat.RESOLVE.ordinal()]);
        assertNull(service.get("Q1").statIncreases);  // planned increases untouched
    }

    @Test
    void reconcile_mixedLevelCrossingUsesPreEleven() {
        // Brother goes from level 10 to 12, crossing the boundary
        int[] statsOld = new int[8];
        int[] statsNew = new int[8];
        statsOld[Stat.MELEE_SKILL.statIndex()] = 60;
        statsNew[Stat.MELEE_SKILL.statIndex()] = 62; // combined delta from multiple levels

        Brother ob = makeBrother("Q2", "Sigreid", 2, statsOld, new int[8]);
        ob.levelTotal = 10;
        Brother nb = makeBrother("Q2", "Sigreid", 0, statsNew, new int[8]);
        nb.levelTotal = 12;

        int[] planned = new int[Stat.values().length];
        planned[Stat.MELEE_SKILL.ordinal()] = 2;
        service.setStatIncreases("Q2", planned);

        List<LevelUpEvent> events = service.reconcileOnReload(List.of(ob), List.of(nb));

        assertEquals(1, events.size());
        LevelUpEvent e = events.get(0);
        assertFalse(e.post11());          // treated as pre-11 (let user handle fallout)
        assertTrue(e.adjusted());
        assertNull(service.get("Q2").post11Increases);  // post11 untouched
    }

    @Test
    void reconcile_emitsEventForPerkOnlyChange() {
        int[] stats = new int[8];

        Brother ob = makeBrother("P3", "Sigrid", 0, stats, new int[8]);
        ob.perkIds.add("DDDD0004");

        Brother nb = makeBrother("P3", "Sigrid", 0, stats.clone(), new int[8]);
        nb.perkIds.add("DDDD0004");
        nb.perkIds.add("EEEE0005"); // perk selected separately, no pending stat level-ups

        List<LevelUpEvent> events = service.reconcileOnReload(List.of(ob), List.of(nb));

        assertEquals(1, events.size());
        LevelUpEvent e = events.get(0);
        assertEquals(List.of("EEEE0005"), e.addedPerkIds());
        assertEquals(0, e.levelsAssigned());
        assertTrue(e.statDeltas().isEmpty());
        assertFalse(e.adjusted());
    }

    @Test
    void reconcile_giftedPerkReconcilesStatDeltasWithoutLevelPointsChange() {
        int[] statsOld = new int[8];
        int[] statsNew = new int[8];
        statsOld[Stat.MELEE_SKILL.statIndex()] = 50;
        statsNew[Stat.MELEE_SKILL.statIndex()] = 52; // +2 from Gifted bonus

        Brother ob = makeBrother("G1", "Oda", 0, statsOld, new int[8]);
        Brother nb = makeBrother("G1", "Oda", 0, statsNew, new int[8]);
        nb.perkIds.add("9899E380"); // Gifted

        int[] planned = new int[Stat.values().length];
        planned[Stat.MELEE_SKILL.ordinal()] = 2;
        service.setStatIncreases("G1", planned);

        List<LevelUpEvent> events = service.reconcileOnReload(List.of(ob), List.of(nb));

        assertEquals(1, events.size());
        LevelUpEvent e = events.get(0);
        assertTrue(e.adjusted());
        assertEquals(1, e.levelsAssigned()); // Gifted counts as 1 round
        assertTrue(e.adjustedStats().contains(Stat.MELEE_SKILL));
        assertEquals(List.of("9899E380"), e.addedPerkIds());
        // delta=2 at 0★ (max 3/roll): ceil(2/3)=1 raise proved → consumes 1, leaves 1
        assertEquals(1, service.get("G1").statIncreases[Stat.MELEE_SKILL.ordinal()]);
    }

    @Test
    void reconcile_giftedCombinedWithNormalLevelUp() {
        int[] statsOld = new int[8];
        int[] statsNew = new int[8];
        statsOld[Stat.HEALTH.statIndex()]      = 60;
        statsNew[Stat.HEALTH.statIndex()]      = 63; // +3 combined (e.g. +1 normal + +2 Gifted)
        statsOld[Stat.MELEE_SKILL.statIndex()] = 50;
        statsNew[Stat.MELEE_SKILL.statIndex()] = 51; // +1 normal

        Brother ob = makeBrother("G2", "Brunhilde", 1, statsOld, new int[8]);
        Brother nb = makeBrother("G2", "Brunhilde", 0, statsNew, new int[8]);
        nb.perkIds.add("9899E380"); // Gifted selected alongside the level-up

        int[] planned = new int[Stat.values().length];
        planned[Stat.HEALTH.ordinal()]      = 2;
        planned[Stat.MELEE_SKILL.ordinal()] = 1;
        service.setStatIncreases("G2", planned);

        List<LevelUpEvent> events = service.reconcileOnReload(List.of(ob), List.of(nb));

        assertEquals(1, events.size());
        LevelUpEvent e = events.get(0);
        assertTrue(e.adjusted());
        assertEquals(2, e.levelsAssigned()); // 1 normal level + 1 Gifted
        assertEquals(List.of("9899E380"), e.addedPerkIds());
    }

    @Test
    void reconcile_emptyAddedPerksWhenNoneAdded() {
        int[] stats = new int[8];

        Brother ob = makeBrother("P2", "Hildr", 1, stats, new int[8]);
        ob.perkIds.add("CCCC0003");

        Brother nb = makeBrother("P2", "Hildr", 0, stats.clone(), new int[8]);
        nb.perkIds.add("CCCC0003"); // same perk, none added

        List<LevelUpEvent> events = service.reconcileOnReload(List.of(ob), List.of(nb));

        assertEquals(1, events.size());
        assertTrue(events.get(0).addedPerkIds().isEmpty());
    }
}
