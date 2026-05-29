package se.niclas.broledger.service;

import org.junit.jupiter.api.Test;
import se.niclas.broledger.model.Brother;
import se.niclas.broledger.model.BrotherAnnotation;
import se.niclas.broledger.model.Stat;
import se.niclas.broledger.service.AnnotationService.Reconciliation;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class AnnotationServiceHelpersTest {

    // ---- A1: statDeltas ----------------------------------------------------

    @Test
    void statDeltas_returnsPositiveDeltasOnly() {
        Brother ob = makeStats(50, 40, 0, 0, 0, 0, 0, 0);
        Brother nb = makeStats(53, 38, 0, 0, 0, 0, 0, 0); // health +3, resolve -2
        Map<Stat, Integer> d = AnnotationService.statDeltas(ob, nb);
        assertEquals(3, d.get(Stat.HEALTH));
        assertNull(d.get(Stat.RESOLVE)); // negative delta excluded
    }

    @Test
    void statDeltas_emptyWhenNoPositiveDeltas() {
        Brother ob = makeStats(60, 50, 0, 0, 0, 0, 0, 0);
        Brother nb = makeStats(60, 50, 0, 0, 0, 0, 0, 0);
        assertTrue(AnnotationService.statDeltas(ob, nb).isEmpty());
    }

    @Test
    void statDeltas_allStatsDetected() {
        int[] old = {50, 40, 80, 60, 40, 20, 15, 70};
        int[] nw  = {51, 41, 81, 61, 41, 21, 16, 71};
        Brother ob = makeFromArray(old);
        Brother nb = makeFromArray(nw);
        Map<Stat, Integer> d = AnnotationService.statDeltas(ob, nb);
        for (Stat s : Stat.values()) {
            assertEquals(1, d.get(s), "expected delta=1 for " + s);
        }
    }

    // ---- A2: raisesLowerBound ----------------------------------------------

    @Test
    void raisesLowerBound_meleeSkill0Stars_delta2_is1() {
        // MELEE_SKILL (statIndex=3), 0★: max=3 → ceil(2/3)=1
        assertEquals(1, AnnotationService.raisesLowerBound(2, Stat.MELEE_SKILL.statIndex(), 0));
    }

    @Test
    void raisesLowerBound_health0Stars_delta5_is2() {
        // HEALTH (statIndex=0), 0★: max=4 → ceil(5/4)=2
        assertEquals(2, AnnotationService.raisesLowerBound(5, Stat.HEALTH.statIndex(), 0));
    }

    @Test
    void raisesLowerBound_exactMax_is1() {
        // MELEE_SKILL, 0★: max=3 → ceil(3/3)=1
        assertEquals(1, AnnotationService.raisesLowerBound(3, Stat.MELEE_SKILL.statIndex(), 0));
    }

    @Test
    void raisesLowerBound_maxPlusOne_is2() {
        // MELEE_SKILL, 0★: max=3 → ceil(4/3)=2
        assertEquals(2, AnnotationService.raisesLowerBound(4, Stat.MELEE_SKILL.statIndex(), 0));
    }

    // ---- A3: addedPerks ----------------------------------------------------

    @Test
    void addedPerks_returnsNewPerksOnly() {
        Brother ob = makeWithPerks("AAAA", "BBBB");
        Brother nb = makeWithPerks("AAAA", "BBBB", "CCCC");
        assertEquals(List.of("CCCC"), AnnotationService.addedPerks(ob, nb));
    }

    @Test
    void addedPerks_emptyWhenNoneAdded() {
        Brother ob = makeWithPerks("AAAA");
        Brother nb = makeWithPerks("AAAA");
        assertTrue(AnnotationService.addedPerks(ob, nb).isEmpty());
    }

    @Test
    void addedPerks_allNewWhenObHasNone() {
        Brother ob = makeWithPerks();
        Brother nb = makeWithPerks("XXXX", "YYYY");
        assertEquals(2, AnnotationService.addedPerks(ob, nb).size());
    }

    // ---- A4: isPost11Reconcile --------------------------------------------

    @Test
    void isPost11Reconcile_trueWhenBothPast11() {
        assertTrue(AnnotationService.isPost11Reconcile(makeWithLevel(12), makeWithLevel(13)));
    }

    @Test
    void isPost11Reconcile_trueWhenObExactly11() {
        assertTrue(AnnotationService.isPost11Reconcile(makeWithLevel(11), makeWithLevel(12)));
    }

    @Test
    void isPost11Reconcile_falseWhenObBelow11() {
        // mixed 10→12 crossing: treated as pre-11 by design
        assertFalse(AnnotationService.isPost11Reconcile(makeWithLevel(10), makeWithLevel(12)));
    }

    @Test
    void isPost11Reconcile_falseWhenNbExactly11() {
        // ob=11, nb=11: nb must be > 11
        assertFalse(AnnotationService.isPost11Reconcile(makeWithLevel(11), makeWithLevel(11)));
    }

    // ---- A5: findMatch -----------------------------------------------------

    @Test
    void findMatch_returnsMatchingBrother() {
        Brother a = makeWithFp("FP_A");
        Brother b = makeWithFp("FP_B");
        assertSame(a, AnnotationService.findMatch(List.of(a, b), "FP_A"));
    }

    @Test
    void findMatch_returnsNullWhenNotFound() {
        assertNull(AnnotationService.findMatch(List.of(makeWithFp("FP_A")), "FP_MISSING"));
    }

    @Test
    void findMatch_returnsNullForEmptyList() {
        assertNull(AnnotationService.findMatch(List.of(), "FP_ANY"));
    }

    // ---- A6: levelsAssigned -----------------------------------------------

    @Test
    void levelsAssigned_oneLevelDecreased() {
        assertEquals(1, AnnotationService.levelsAssigned(makeWithPoints(2), makeWithPoints(1), false));
    }

    @Test
    void levelsAssigned_normalPlusGifted() {
        assertEquals(2, AnnotationService.levelsAssigned(makeWithPoints(1), makeWithPoints(0), true));
    }

    @Test
    void levelsAssigned_giftedOnlyNoLevelDecrease() {
        assertEquals(1, AnnotationService.levelsAssigned(makeWithPoints(0), makeWithPoints(0), true));
    }

    @Test
    void levelsAssigned_neverNegativeWhenPointsIncrease() {
        // Defensive: levelPoints went up (shouldn't happen, but Math.max guards it)
        assertEquals(0, AnnotationService.levelsAssigned(makeWithPoints(1), makeWithPoints(2), false));
    }

    // ---- B1: reconcilePost11 ----------------------------------------------

    @Test
    void reconcilePost11_emptyDeltasReturnsFalse() {
        Reconciliation r = AnnotationService.reconcilePost11(new BrotherAnnotation("FP"), Map.of());
        assertFalse(r.changed());
        assertNull(r.updatedPost11Increases());
    }

    @Test
    void reconcilePost11_recordsDeltas() {
        BrotherAnnotation a = new BrotherAnnotation("FP");
        Map<Stat, Integer> deltas = new EnumMap<>(Stat.class);
        deltas.put(Stat.HEALTH, 1);
        deltas.put(Stat.MELEE_SKILL, 1);
        Reconciliation r = AnnotationService.reconcilePost11(a, deltas);
        assertTrue(r.changed());
        assertEquals(1, r.updatedPost11Increases()[Stat.HEALTH.ordinal()]);
        assertEquals(1, r.updatedPost11Increases()[Stat.MELEE_SKILL.ordinal()]);
    }

    @Test
    void reconcilePost11_accumulatesOnExistingPost11() {
        BrotherAnnotation a = new BrotherAnnotation("FP");
        a.post11Increases = new int[Stat.values().length];
        a.post11Increases[Stat.HEALTH.ordinal()] = 2;
        Reconciliation r = AnnotationService.reconcilePost11(a, Map.of(Stat.HEALTH, 1));
        assertEquals(3, r.updatedPost11Increases()[Stat.HEALTH.ordinal()]);
    }

    @Test
    void reconcilePost11_doesNotMutateAnnotationArray() {
        BrotherAnnotation a = new BrotherAnnotation("FP");
        a.post11Increases = new int[Stat.values().length];
        a.post11Increases[Stat.HEALTH.ordinal()] = 1;
        AnnotationService.reconcilePost11(a, Map.of(Stat.RESOLVE, 1));
        assertEquals(1, a.post11Increases[Stat.HEALTH.ordinal()]);  // original unchanged
        assertEquals(0, a.post11Increases[Stat.RESOLVE.ordinal()]); // original unchanged
    }

    // ---- B2: consumePlannedIncreases --------------------------------------

    @Test
    void consumePlanned_nullStatIncreasesReturnsFalse() {
        Reconciliation r = AnnotationService.consumePlannedIncreases(
                new BrotherAnnotation("FP"), makeFromArray(new int[8]), Map.of(Stat.HEALTH, 3));
        assertFalse(r.changed());
        assertNull(r.updatedStatIncreases());
    }

    @Test
    void consumePlanned_consumesLowerBoundProof() {
        // HEALTH 0★: delta=5, max=4 → ceil(5/4)=2, planned=3 → consume 2, leave 1
        BrotherAnnotation a = annotationWithPlan(Stat.HEALTH, 3);
        Reconciliation r = AnnotationService.consumePlannedIncreases(
                a, makeFromArray(new int[8]), Map.of(Stat.HEALTH, 5));
        assertTrue(r.changed());
        assertEquals(2, r.consumedIncreases().get(Stat.HEALTH));
        assertEquals(1, r.updatedStatIncreases()[Stat.HEALTH.ordinal()]);
        assertTrue(r.adjustedStats().contains(Stat.HEALTH));
    }

    @Test
    void consumePlanned_capsAtPlanned() {
        // HEALTH 0★: delta=12, ceil(12/4)=3, planned=2 → consume 2, leave 0
        BrotherAnnotation a = annotationWithPlan(Stat.HEALTH, 2);
        Reconciliation r = AnnotationService.consumePlannedIncreases(
                a, makeFromArray(new int[8]), Map.of(Stat.HEALTH, 12));
        assertEquals(2, r.consumedIncreases().get(Stat.HEALTH));
        assertEquals(0, r.updatedStatIncreases()[Stat.HEALTH.ordinal()]);
    }

    @Test
    void consumePlanned_skipsDeltaWithZeroPlanned() {
        BrotherAnnotation a = new BrotherAnnotation("FP");
        a.statIncreases = new int[Stat.values().length]; // all zeros
        Reconciliation r = AnnotationService.consumePlannedIncreases(
                a, makeFromArray(new int[8]), Map.of(Stat.HEALTH, 3));
        assertFalse(r.changed());
    }

    @Test
    void consumePlanned_doesNotMutateAnnotationArray() {
        BrotherAnnotation a = annotationWithPlan(Stat.HEALTH, 5);
        int[] snapshot = a.statIncreases.clone();
        AnnotationService.consumePlannedIncreases(a, makeFromArray(new int[8]), Map.of(Stat.HEALTH, 4));
        assertArrayEquals(snapshot, a.statIncreases);
    }

    @Test
    void consumePlanned_multipleStats() {
        // HEALTH 0★: delta=4 → ceil(4/4)=1, planned=2 → consume 1, leave 1
        // MELEE_SKILL 0★: delta=3 → ceil(3/3)=1, planned=1 → consume 1, leave 0
        BrotherAnnotation a = new BrotherAnnotation("FP");
        a.statIncreases = new int[Stat.values().length];
        a.statIncreases[Stat.HEALTH.ordinal()]      = 2;
        a.statIncreases[Stat.MELEE_SKILL.ordinal()] = 1;
        Map<Stat, Integer> deltas = new EnumMap<>(Stat.class);
        deltas.put(Stat.HEALTH, 4);
        deltas.put(Stat.MELEE_SKILL, 3);
        Reconciliation r = AnnotationService.consumePlannedIncreases(a, makeFromArray(new int[8]), deltas);
        assertTrue(r.changed());
        assertEquals(2, r.adjustedStats().size());
        assertEquals(1, r.updatedStatIncreases()[Stat.HEALTH.ordinal()]);
        assertEquals(0, r.updatedStatIncreases()[Stat.MELEE_SKILL.ordinal()]);
    }

    // ---- helpers -----------------------------------------------------------

    /** stats[] in save-file order: [0]=HP [1]=Res [2]=Fat [3]=MS [4]=RS [5]=MD [6]=RD [7]=Init */
    private static Brother makeStats(int hp, int res, int fat, int ms,
                                     int rs, int md, int rd, int init) {
        return makeFromArray(new int[]{hp, res, fat, ms, rs, md, rd, init});
    }

    private static Brother makeFromArray(int[] stats) {
        Brother b = new Brother();
        b.stats = stats;
        b.stars = new int[8];
        return b;
    }

    private static Brother makeWithPerks(String... perkIds) {
        Brother b = new Brother();
        b.stats = new int[8];
        b.stars = new int[8];
        for (String id : perkIds) b.perkIds.add(id);
        return b;
    }

    private static Brother makeWithLevel(int levelTotal) {
        Brother b = new Brother();
        b.stats = new int[8];
        b.stars = new int[8];
        b.levelTotal = levelTotal;
        return b;
    }

    private static Brother makeWithFp(String fp) {
        Brother b = new Brother();
        b.fingerprint = fp;
        b.stats = new int[8];
        b.stars = new int[8];
        return b;
    }

    private static Brother makeWithPoints(int levelPoints) {
        Brother b = new Brother();
        b.stats = new int[8];
        b.stars = new int[8];
        b.levelPoints = levelPoints;
        return b;
    }

    private static BrotherAnnotation annotationWithPlan(Stat s, int count) {
        BrotherAnnotation a = new BrotherAnnotation("FP");
        a.statIncreases = new int[Stat.values().length];
        a.statIncreases[s.ordinal()] = count;
        return a;
    }
}
