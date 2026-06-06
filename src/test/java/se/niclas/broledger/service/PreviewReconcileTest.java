package se.niclas.broledger.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import se.niclas.broledger.model.Brother;
import se.niclas.broledger.model.Stat;
import se.niclas.broledger.service.AnnotationService.LevelUpEvent;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for {@link AnnotationService#previewReconcile} and
 * {@link AnnotationService#readAnnotationMaps}.
 *
 * Verifies that the non-persisting facade produces the same events as
 * {@link AnnotationService#reconcileOnReload} and carries planned-increase
 * consumption forward across multiple transitions.
 */
class PreviewReconcileTest {

    @TempDir
    Path tempDir;

    private AnnotationService service;
    private Path savePath;

    @BeforeEach
    void setUp() throws Exception {
        savePath = tempDir.resolve("test.sav");
        Files.writeString(savePath, "dummy");
        resetSingleton();
        service = AnnotationService.getInstance();
        service.loadFor(savePath);
    }

    private void resetSingleton() throws Exception {
        var field = AnnotationService.class.getDeclaredField("instance");
        field.setAccessible(true);
        field.set(null, null);
    }

    // ---- helpers -----------------------------------------------------------

    private static Brother brother(String fp, String name, int levelPoints,
                                   int[] stats, int[] stars) {
        Brother b = new Brother();
        b.fingerprint = fp;
        b.name        = name;
        b.levelPoints = levelPoints;
        b.stats       = stats;
        b.stars       = stars;
        return b;
    }

    private static int[] statsWithHP(int hp) {
        int[] s = new int[8];
        s[Stat.HEALTH.statIndex()] = hp;
        return s;
    }

    private static int[] statsWithMS(int ms) {
        int[] s = new int[8];
        s[Stat.MELEE_SKILL.statIndex()] = ms;
        return s;
    }

    // ---- previewReconcile matches reconcileOnReload ------------------------

    @Test
    void previewReconcile_producesNoEvents_whenNoChange() {
        int[] stats = statsWithHP(60);
        Brother ob = brother("A", "Gorm", 1, stats, new int[8]);
        Brother nb = brother("A", "Gorm", 1, stats.clone(), new int[8]);

        Map<String, int[]> planned = new HashMap<>();
        Map<String, int[]> post11  = new HashMap<>();
        List<LevelUpEvent> events  = service.previewReconcile(
                List.of(ob), List.of(nb), planned, post11);

        assertTrue(events.isEmpty());
    }

    @Test
    void previewReconcile_producesLevelUpEvent() {
        int[] statsOld = statsWithHP(60);
        int[] statsNew = statsWithHP(63);

        Brother ob = brother("B", "Torben", 1, statsOld, new int[8]);
        Brother nb = brother("B", "Torben", 0, statsNew, new int[8]);

        Map<String, int[]> planned = new HashMap<>();
        Map<String, int[]> post11  = new HashMap<>();

        List<LevelUpEvent> events = service.previewReconcile(
                List.of(ob), List.of(nb), planned, post11);

        assertEquals(1, events.size());
        LevelUpEvent e = events.get(0);
        assertEquals("Torben", e.name());
        assertEquals(1, e.levelsAssigned());
        assertEquals(3, e.statDeltas().getOrDefault(Stat.HEALTH, 0));
        assertFalse(e.adjusted()); // no planned increases supplied
    }

    @Test
    void previewReconcile_consumesPlannedIncreases() {
        int[] statsOld = statsWithHP(60);
        int[] statsNew = statsWithHP(63);

        Brother ob = brother("C", "Wulf", 1, statsOld, new int[8]);
        Brother nb = brother("C", "Wulf", 0, statsNew, new int[8]);

        Map<String, int[]> planned = new HashMap<>();
        int[] inc = new int[Stat.values().length];
        inc[Stat.HEALTH.ordinal()] = 2;
        planned.put("C", inc.clone());

        Map<String, int[]> post11 = new HashMap<>();

        List<LevelUpEvent> events = service.previewReconcile(
                List.of(ob), List.of(nb), planned, post11);

        assertEquals(1, events.size());
        assertTrue(events.get(0).adjusted());
        // HP delta=3 at 0★: ceil(3/4)=1 raise proved → consume 1
        assertEquals(1, events.get(0).consumedIncreases().getOrDefault(Stat.HEALTH, 0));
        // planned map updated: 2-1=1 remaining
        int[] updated = planned.get("C");
        assertNotNull(updated);
        assertEquals(1, updated[Stat.HEALTH.ordinal()]);
    }

    @Test
    void previewReconcile_matchesReconcileOnReload() {
        int[] statsOld = statsWithMS(50);
        int[] statsNew = statsWithMS(52);

        Brother ob = brother("D", "Sigrid", 1, statsOld, new int[8]);
        Brother nb = brother("D", "Sigrid", 0, statsNew, new int[8]);

        // Set planned increases in the live service
        int[] inc = new int[Stat.values().length];
        inc[Stat.MELEE_SKILL.ordinal()] = 2;
        service.setStatIncreases("D", inc.clone());

        // Run the live reconcile (mutates the store + disk)
        List<LevelUpEvent> liveEvents = service.reconcileOnReload(
                List.of(ob), List.of(nb));

        // Reset singleton and run previewReconcile with same inputs
        try { resetSingleton(); } catch (Exception e) { fail(e); }
        service = AnnotationService.getInstance();
        service.loadFor(savePath);

        Map<String, int[]> planned = new HashMap<>();
        planned.put("D", inc.clone());
        Map<String, int[]> post11 = new HashMap<>();

        List<LevelUpEvent> previewEvents = service.previewReconcile(
                List.of(ob), List.of(nb), planned, post11);

        assertEquals(liveEvents.size(), previewEvents.size());
        LevelUpEvent live    = liveEvents.get(0);
        LevelUpEvent preview = previewEvents.get(0);

        assertEquals(live.name(),            preview.name());
        assertEquals(live.adjusted(),        preview.adjusted());
        assertEquals(live.levelsAssigned(),  preview.levelsAssigned());
        assertEquals(live.statDeltas(),      preview.statDeltas());
        assertEquals(live.adjustedStats(),   preview.adjustedStats());
        assertEquals(live.consumedIncreases(), preview.consumedIncreases());
        assertEquals(live.post11(),          preview.post11());
    }

    @Test
    void previewReconcile_doesNotWriteToDisk() throws IOException {
        int[] statsOld = statsWithHP(60);
        int[] statsNew = statsWithHP(64);

        Brother ob = brother("E", "Otto", 1, statsOld, new int[8]);
        Brother nb = brother("E", "Otto", 0, statsNew, new int[8]);

        Path annotPath = savePath.resolveSibling(savePath.getFileName() + ".broledger.json");
        assertFalse(Files.exists(annotPath), "no annotation file should exist yet");

        Map<String, int[]> planned = new HashMap<>();
        service.previewReconcile(List.of(ob), List.of(nb), planned, new HashMap<>());

        // No disk write should have occurred
        assertFalse(Files.exists(annotPath),
                "previewReconcile must not write to disk");
    }

    // ---- carry-forward across transitions ---------------------------------

    @Test
    void previewReconcile_carryForwardAcrossTwoTransitions() {
        // Transition 1: HP +3 → consumes 1 planned HP raise
        // Transition 2: HP +2 → consumes 1 more planned HP raise
        // Initial planned: HP=3
        // After T1: HP=2, after T2: HP=1

        int[] s0 = statsWithHP(60);
        int[] s1 = statsWithHP(63);
        int[] s2 = statsWithHP(65);

        Brother b0 = brother("F", "Brunhilde", 1, s0, new int[8]);
        Brother b1 = brother("F", "Brunhilde", 0, s1, new int[8]);
        Brother b2 = brother("F", "Brunhilde", 0 /* levelPoints same as b1 */, s2, new int[8]);
        // b2 level points must be lower than b1's for a level-up event
        b2.levelPoints = -1; // won't trigger; let's do it properly:
        // Actually b1.levelPoints=0 and b2.levelPoints must be < 0 which is invalid.
        // Use a fresh brother with levelPoints going from 1→0 for T2
        Brother b1real = brother("F", "Brunhilde", 1, s1, new int[8]); // gave back 1 level-point somehow
        Brother b2real = brother("F", "Brunhilde", 0, s2, new int[8]); // spent it

        Map<String, int[]> planned = new HashMap<>();
        int[] inc = new int[Stat.values().length];
        inc[Stat.HEALTH.ordinal()] = 3;
        planned.put("F", inc.clone());
        Map<String, int[]> post11 = new HashMap<>();

        // T1: b0 → b1
        service.previewReconcile(List.of(b0), List.of(b1), planned, post11);
        int afterT1 = planned.getOrDefault("F", new int[Stat.values().length])[Stat.HEALTH.ordinal()];

        // T2: b1real → b2real (different brother instance with levelPoints: 1→0)
        service.previewReconcile(List.of(b1real), List.of(b2real), planned, post11);
        int afterT2 = planned.getOrDefault("F", new int[Stat.values().length])[Stat.HEALTH.ordinal()];

        // After T1: ceil(3/4)=1 consumed → 3-1=2
        assertEquals(2, afterT1, "after T1: 1 HP raise consumed out of 3 planned");
        // After T2: ceil(2/4)=1 consumed → 2-1=1
        assertEquals(1, afterT2, "after T2: 1 more HP raise consumed, leaving 1");
    }

    // ---- readAnnotationMaps ------------------------------------------------

    @Test
    void readAnnotationMaps_emptyForNullPath() {
        Map<String, int[]> p = new HashMap<>(), q = new HashMap<>();
        AnnotationService.readAnnotationMaps(null, p, q);
        assertTrue(p.isEmpty() && q.isEmpty());
    }

    @Test
    void readAnnotationMaps_emptyForAbsentFile() throws Exception {
        Path absent = tempDir.resolve("absent.json");
        Map<String, int[]> p = new HashMap<>(), q = new HashMap<>();
        AnnotationService.readAnnotationMaps(absent, p, q);
        assertTrue(p.isEmpty() && q.isEmpty());
    }

    @Test
    void readAnnotationMaps_loadsPlannedIncreases() throws Exception {
        // Write a realistic annotations file
        Path annotPath = tempDir.resolve("snap.broledger.json");
        // Ordinals: HEALTH=0, RESOLVE=1, FATIGUE=2, INITIATIVE=3, MELEE_SKILL=4, …
        // statIncreases[0]=3 (HEALTH), statIncreases[4]=2 (MELEE_SKILL)
        Files.writeString(annotPath,
                "{\"version\":1,\"annotations\":{" +
                "\"FP001\":{\"statIncreases\":[3,0,0,0,2,0,0,0]}," +
                "\"FP002\":{\"post11Increases\":[1,0,0,0,0,0,0,0]}" +
                "}}");

        Map<String, int[]> planned = new HashMap<>();
        Map<String, int[]> post11  = new HashMap<>();
        AnnotationService.readAnnotationMaps(annotPath, planned, post11);

        assertNotNull(planned.get("FP001"));
        assertEquals(3, planned.get("FP001")[Stat.HEALTH.ordinal()]);
        assertEquals(2, planned.get("FP001")[Stat.MELEE_SKILL.ordinal()]);

        assertNotNull(post11.get("FP002"));
        assertEquals(1, post11.get("FP002")[Stat.HEALTH.ordinal()]);

        // FP002 has no statIncreases → not in planned
        assertNull(planned.get("FP002"));
    }

    @Test
    void readAnnotationMaps_corruptFileIsIgnored() throws Exception {
        Path corrupt = tempDir.resolve("corrupt.json");
        Files.writeString(corrupt, "{ not json");

        Map<String, int[]> p = new HashMap<>(), q = new HashMap<>();
        assertDoesNotThrow(() -> AnnotationService.readAnnotationMaps(corrupt, p, q));
        assertTrue(p.isEmpty() && q.isEmpty());
    }

    @Test
    void readAnnotationMaps_doesNotShareArrayWithOriginal() throws Exception {
        Path annotPath = tempDir.resolve("snap2.broledger.json");
        Files.writeString(annotPath,
                "{\"version\":1,\"annotations\":{" +
                "\"FP003\":{\"statIncreases\":[1,0,0,0,0,0,0,0]}" +
                "}}");

        Map<String, int[]> planned = new HashMap<>();
        AnnotationService.readAnnotationMaps(annotPath, planned, new HashMap<>());

        int[] arr = planned.get("FP003");
        assertNotNull(arr);
        int original = arr[Stat.HEALTH.ordinal()];
        arr[Stat.HEALTH.ordinal()] = 99; // mutate the returned copy

        // Re-read and confirm the file wasn't affected (and the map returns a fresh copy)
        Map<String, int[]> planned2 = new HashMap<>();
        AnnotationService.readAnnotationMaps(annotPath, planned2, new HashMap<>());
        assertEquals(original, planned2.get("FP003")[Stat.HEALTH.ordinal()],
                "readAnnotationMaps must return independent copies of the arrays");
    }
}
