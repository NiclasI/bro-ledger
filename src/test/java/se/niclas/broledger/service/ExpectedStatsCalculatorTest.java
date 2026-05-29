package se.niclas.broledger.service;

import org.junit.jupiter.api.Test;
import se.niclas.broledger.model.Brother;
import se.niclas.broledger.model.Role;
import se.niclas.broledger.model.Stat;

import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ExpectedStatsCalculatorTest {

    private static Brother brother(int levelTotal) {
        return brother(levelTotal, 0);
    }

    private static Brother brother(int levelTotal, int levelPoints) {
        Brother b = new Brother();
        b.levelTotal  = levelTotal;
        b.levelPoints = levelPoints;
        b.fingerprint = "TEST";
        // Leave stats/stars at 0; no perks → modifiers are identity
        return b;
    }

    // ---- naive formula: ceil(base + mean × count) ---------------------------

    @Test
    void naive_basicCeil() {
        // HEALTH 0★: range (2,4) mean=3.0. level=6 → remaining=5.
        // count=3 → ceil(50 + 9) = 59
        Brother b = brother(6);
        b.stats[Stat.HEALTH.statIndex()] = 50;
        int[] inc = new int[8];
        inc[Stat.HEALTH.ordinal()] = 3;

        ExpectedStatsCalculator.Expected e =
                ExpectedStatsCalculator.compute(b, Stat.HEALTH, inc, ExpectedStatsCalculator.Mode.NAIVE);

        assertEquals(59, e.baseExpected());
        assertEquals(59, e.finalExpected()); // no modifiers
        assertEquals(3, e.count());
        assertEquals(5, e.remainingLevels());
    }

    @Test
    void naive_halfRollRoundsUp() {
        // MELEE_SKILL 0★: range (1,3) mean=2.0. count=1 → ceil(20 + 2.0) = 22 (no fractional)
        Brother b = brother(6);
        b.stats[Stat.MELEE_SKILL.statIndex()] = 20;
        int[] inc = new int[8];
        inc[Stat.MELEE_SKILL.ordinal()] = 1;

        ExpectedStatsCalculator.Expected e =
                ExpectedStatsCalculator.compute(b, Stat.MELEE_SKILL, inc, ExpectedStatsCalculator.Mode.NAIVE);

        assertEquals(22, e.baseExpected());
    }

    @Test
    void naive_starAdjustedRange_onestar() {
        // INITIATIVE 1★: range (4,5) mean=4.5. count=2 → ceil(80 + 9) = 89
        Brother b = brother(6);
        b.stats[Stat.INITIATIVE.statIndex()] = 80;
        b.stars[Stat.INITIATIVE.starIndex()] = 1;
        int[] inc = new int[8];
        inc[Stat.INITIATIVE.ordinal()] = 2;

        ExpectedStatsCalculator.Expected e =
                ExpectedStatsCalculator.compute(b, Stat.INITIATIVE, inc, ExpectedStatsCalculator.Mode.NAIVE);

        assertEquals(89, e.baseExpected());
    }

    @Test
    void naive_countZero_returnsCurrentBase() {
        Brother b = brother(6);
        b.stats[Stat.RESOLVE.statIndex()] = 40;
        int[] inc = new int[8]; // all zero

        ExpectedStatsCalculator.Expected e =
                ExpectedStatsCalculator.compute(b, Stat.RESOLVE, inc, ExpectedStatsCalculator.Mode.NAIVE);

        assertEquals(40, e.baseExpected());
        assertEquals(0, e.count());
        assertEquals(5, e.remainingLevels());
    }

    @Test
    void remaining0_returnsCurrentBase() {
        Brother b = brother(11); // at level cap
        b.stats[Stat.HEALTH.statIndex()] = 75;
        int[] inc = new int[8];
        inc[Stat.HEALTH.ordinal()] = 2;

        ExpectedStatsCalculator.Expected e =
                ExpectedStatsCalculator.compute(b, Stat.HEALTH, inc, ExpectedStatsCalculator.Mode.NAIVE);

        assertEquals(75, e.baseExpected());
        assertEquals(0, e.remainingLevels());
    }

    @Test
    void nullIncreasesEquivalentToAllZero() {
        Brother b = brother(6);
        b.stats[Stat.HEALTH.statIndex()] = 50;

        ExpectedStatsCalculator.Expected withNull =
                ExpectedStatsCalculator.compute(b, Stat.HEALTH, null, ExpectedStatsCalculator.Mode.NAIVE);
        int[] allZero = new int[8];
        ExpectedStatsCalculator.Expected withZero =
                ExpectedStatsCalculator.compute(b, Stat.HEALTH, allZero, ExpectedStatsCalculator.Mode.NAIVE);

        assertEquals(withZero.baseExpected(), withNull.baseExpected());
    }

    // ---- greedy fallback when allocation is incomplete ----------------------

    @Test
    void greedyFallsBackToNaive_whenPartial() {
        // remaining=5, budget=15, only 3 assigned → partial → naive
        Brother b = brother(6);
        b.stats[Stat.HEALTH.statIndex()] = 50;
        int[] inc = new int[8];
        inc[Stat.HEALTH.ordinal()] = 3; // sum=3 != 15

        ExpectedStatsCalculator.Expected naive =
                ExpectedStatsCalculator.compute(b, Stat.HEALTH, inc, ExpectedStatsCalculator.Mode.NAIVE);
        ExpectedStatsCalculator.Expected greedy =
                ExpectedStatsCalculator.compute(b, Stat.HEALTH, inc, ExpectedStatsCalculator.Mode.GREEDY);

        assertEquals(naive.baseExpected(), greedy.baseExpected(),
                "Greedy must fall back to naive when allocation is incomplete");
    }

    // ---- autoAssignByRole ---------------------------------------------------

    private static Role roleWith3P1() {
        Role r = new Role();
        r.priority = new int[]{3, 3, 3, 3, 3, 3, 3, 3};
        r.priority[Stat.HEALTH.ordinal()]  = 1;
        r.priority[Stat.RESOLVE.ordinal()] = 1;
        r.priority[Stat.FATIGUE.ordinal()] = 1;
        return r;
    }

    @Test
    void autoAssign_3P1Stats_allMaxed() {
        // remaining=2, 3 P1 stats — each maxed to 2. Total = 6 = 3×2.
        Brother b = brother(9);
        Role role = roleWith3P1();
        int[] result = ExpectedStatsCalculator.autoAssignByRole(b, role);

        assertEquals(2, result[Stat.HEALTH.ordinal()]);
        assertEquals(2, result[Stat.RESOLVE.ordinal()]);
        assertEquals(2, result[Stat.FATIGUE.ordinal()]);
        // others must be 0
        assertEquals(0, result[Stat.MELEE_SKILL.ordinal()]);
        int sum = Arrays.stream(result).sum();
        assertEquals(6, sum, "sum must equal 3 × remaining = 6");
    }

    @Test
    void autoAssign_4P1Stats_splitEquallyAlphabetically() {
        // remaining=3, budget=9, 4 P1 stats.
        // Alphabetical displayNames: "Fatigue", "Health", "Melee Def", "Resolve"
        // each=2, extra=1 → first alphabetically ("Fatigue") gets +1 → 3.
        Brother b = brother(8);
        Role role = new Role();
        role.priority = new int[]{3, 3, 3, 3, 3, 3, 3, 3};
        role.priority[Stat.HEALTH.ordinal()]         = 1; // "Health"
        role.priority[Stat.RESOLVE.ordinal()]        = 1; // "Resolve"
        role.priority[Stat.FATIGUE.ordinal()]        = 1; // "Fatigue"
        role.priority[Stat.MELEE_DEFENSE.ordinal()]  = 1; // "Melee Def"

        int[] result = ExpectedStatsCalculator.autoAssignByRole(b, role);

        assertEquals(3, result[Stat.FATIGUE.ordinal()],       "Fatigue gets +1 (first alphabetically)");
        assertEquals(2, result[Stat.HEALTH.ordinal()]);
        assertEquals(2, result[Stat.MELEE_DEFENSE.ordinal()]);
        assertEquals(2, result[Stat.RESOLVE.ordinal()]);
        int sum = Arrays.stream(result).sum();
        assertEquals(9, sum, "sum must equal 3 × remaining = 9");
    }

    @Test
    void autoAssign_P1ThenP2Cascade() {
        // remaining=3, budget=9. 1 P1 → maxed (3). Remaining budget=6. 2 P2 → maxed (3 each). Done.
        Brother b = brother(8);
        Role role = new Role();
        role.priority = new int[]{3, 3, 3, 3, 3, 3, 3, 3};
        role.priority[Stat.MELEE_SKILL.ordinal()]   = 1;
        role.priority[Stat.HEALTH.ordinal()]         = 2;
        role.priority[Stat.INITIATIVE.ordinal()]     = 2;

        int[] result = ExpectedStatsCalculator.autoAssignByRole(b, role);

        assertEquals(3, result[Stat.MELEE_SKILL.ordinal()],  "P1 maxed");
        assertEquals(3, result[Stat.HEALTH.ordinal()],        "P2 maxed");
        assertEquals(3, result[Stat.INITIATIVE.ordinal()],    "P2 maxed");
        int sum = Arrays.stream(result).sum();
        assertEquals(9, sum);
    }

    @Test
    void autoAssign_remaining0_allZero() {
        Brother b = brother(11);
        int[] result = ExpectedStatsCalculator.autoAssignByRole(b, roleWith3P1());
        int sum = Arrays.stream(result).sum();
        assertEquals(0, sum, "no increases possible at max level");
    }

    @Test
    void autoAssign_nullRole_allZero() {
        Brother b = brother(6);
        int[] result = ExpectedStatsCalculator.autoAssignByRole(b, null);
        int sum = Arrays.stream(result).sum();
        assertEquals(0, sum);
    }

    @Test
    void autoAssign_sumAlwaysEquals3xRemaining() {
        // General invariant for various setups
        Brother b = brother(5); // remaining=6, budget=18
        Role role = new Role();
        role.priority = new int[]{3, 3, 3, 3, 3, 3, 3, 3};
        role.priority[Stat.MELEE_SKILL.ordinal()]   = 1;
        role.priority[Stat.HEALTH.ordinal()]         = 2;

        int[] result = ExpectedStatsCalculator.autoAssignByRole(b, role);
        int sum = Arrays.stream(result).sum();
        assertEquals(18, sum, "sum must equal 3 × 6 = 18");
    }

    @Test
    void autoAssign_perStatCapRespected() {
        // Every assigned stat must be <= remaining
        Brother b = brother(8); // remaining=3
        Role role = roleWith3P1(); // all assigned to 3 P1 stats
        int[] result = ExpectedStatsCalculator.autoAssignByRole(b, role);

        for (int i = 0; i < result.length; i++) {
            assertTrue(result[i] <= 3,
                    "Stat " + Stat.values()[i] + " exceeds per-stat cap: " + result[i]);
        }
    }

    // ---- remainingLevels: pending level-ups folded in -----------------------

    @Test
    void remainingLevels_noPending_equalsGapTo11() {
        assertEquals(6, ExpectedStatsCalculator.remainingLevels(brother(5, 0)));
        assertEquals(1, ExpectedStatsCalculator.remainingLevels(brother(10, 0)));
    }

    @Test
    void remainingLevels_withPending_addsPendingToGap() {
        assertEquals(7, ExpectedStatsCalculator.remainingLevels(brother(5, 1)));
        assertEquals(2, ExpectedStatsCalculator.remainingLevels(brother(10, 1)));
    }

    @Test
    void remainingLevels_atOrBeyond11_alwaysZero() {
        assertEquals(0, ExpectedStatsCalculator.remainingLevels(brother(11, 0)));
        assertEquals(0, ExpectedStatsCalculator.remainingLevels(brother(11, 1)));
        assertEquals(0, ExpectedStatsCalculator.remainingLevels(brother(15, 2)));
    }

    @Test
    void compute_pendingLevelReflectedInRemainingLevels() {
        // level=6, levelPoints=1 → remaining=6 (5 future + 1 pending)
        Brother b = brother(6, 1);
        b.stats[Stat.HEALTH.statIndex()] = 50;
        int[] inc = new int[8];

        ExpectedStatsCalculator.Expected e =
                ExpectedStatsCalculator.compute(b, Stat.HEALTH, inc, ExpectedStatsCalculator.Mode.NAIVE);
        assertEquals(6, e.remainingLevels());
    }

    @Test
    void autoAssign_pendingLevel_enlargesBudget() {
        // level=9, levelPoints=1 → remaining=3 (1 future + 1 pending), budget=9
        Brother b = brother(9, 1);
        Role role = roleWith3P1();
        int[] result = ExpectedStatsCalculator.autoAssignByRole(b, role);
        int sum = Arrays.stream(result).sum();
        assertEquals(9, sum, "budget must be 3 × 3 = 9");
    }

    // ---- Mode.parse --------------------------------------------------------

    @Test
    void modeParse_naiveDefault() {
        assertEquals(ExpectedStatsCalculator.Mode.NAIVE, ExpectedStatsCalculator.Mode.parse("NAIVE"));
        assertEquals(ExpectedStatsCalculator.Mode.NAIVE, ExpectedStatsCalculator.Mode.parse("invalid"));
        assertEquals(ExpectedStatsCalculator.Mode.NAIVE, ExpectedStatsCalculator.Mode.parse(null));
    }

    @Test
    void modeParse_greedy() {
        assertEquals(ExpectedStatsCalculator.Mode.GREEDY, ExpectedStatsCalculator.Mode.parse("GREEDY"));
        assertEquals(ExpectedStatsCalculator.Mode.GREEDY, ExpectedStatsCalculator.Mode.parse("greedy"));
    }

    // ---- new pure helpers --------------------------------------------------

    @Test
    void remainingLevels_pureOverload_matchesBrotherForm() {
        assertEquals(ExpectedStatsCalculator.remainingLevels(brother(7, 2)),
                     ExpectedStatsCalculator.remainingLevels(7, 2));
        assertEquals(ExpectedStatsCalculator.remainingLevels(brother(11, 0)),
                     ExpectedStatsCalculator.remainingLevels(11, 0));
    }

    @Test
    void isFixed_trueWhenEqual() {
        assertTrue(ExpectedStatsCalculator.isFixed(3, 3));
        assertFalse(ExpectedStatsCalculator.isFixed(2, 3));
        assertFalse(ExpectedStatsCalculator.isFixed(0, 3));
    }

    @Test
    void isFlexible_trueWhenAllocatedButNotMax() {
        assertTrue(ExpectedStatsCalculator.isFlexible(1, 3));
        assertTrue(ExpectedStatsCalculator.isFlexible(2, 3));
        assertFalse(ExpectedStatsCalculator.isFlexible(0, 3)); // not allocated
        assertFalse(ExpectedStatsCalculator.isFlexible(3, 3)); // maxed (fixed)
    }

    @Test
    void flexibleSlots_threeMinusFixed() {
        assertEquals(3, ExpectedStatsCalculator.flexibleSlots(0));
        assertEquals(1, ExpectedStatsCalculator.flexibleSlots(2));
        assertEquals(0, ExpectedStatsCalculator.flexibleSlots(3));
    }

    @Test
    void meanRoll_midpoint() {
        StatPotentialCalculator.Range r = new StatPotentialCalculator.Range(2, 4);
        assertEquals(3.0, ExpectedStatsCalculator.meanRoll(r), 1e-9);
    }

    @Test
    void rollDeviation_positive() {
        StatPotentialCalculator.Range r = new StatPotentialCalculator.Range(1, 3);
        // mean = 2.0; roll=3 → deviation = 1.0
        assertEquals(1.0, ExpectedStatsCalculator.rollDeviation(3, r), 1e-9);
    }

    @Test
    void allocateByRoleTiers_matchesOriginalAutoAssignByRole() {
        Role role = roleWith3P1();
        Brother b = brother(5, 0); // remaining = 6
        int[] expected = ExpectedStatsCalculator.autoAssignByRole(b, role);
        int remaining = ExpectedStatsCalculator.remainingLevels(b);
        int[] actual  = ExpectedStatsCalculator.allocateByRoleTiers(role.priority, remaining);
        assertArrayEquals(expected, actual);
    }

    @Test
    void allocateByRoleTiers_matchesOriginalAutoAssignPost11ByRole() {
        Role role = roleWith3P1();
        Brother b = brother(14, 0); // levelTotal=14 → postLevels=3
        int[] expected = ExpectedStatsCalculator.autoAssignPost11ByRole(b, role);
        int postLevels = Math.max(0, b.levelTotal - 11);
        int[] actual   = ExpectedStatsCalculator.allocateByRoleTiers(role.priority, postLevels);
        assertArrayEquals(expected, actual);
    }

    @Test
    void statsAtTier_returnsCorrectStats() {
        int[] priority = new int[8]; // all P3 by default
        priority[Stat.HEALTH.ordinal()]    = 1;
        priority[Stat.RESOLVE.ordinal()]   = 1;
        priority[Stat.FATIGUE.ordinal()]   = 2;
        List<se.niclas.broledger.model.Stat> p1 = ExpectedStatsCalculator.statsAtTier(priority, 1);
        assertEquals(2, p1.size());
        assertTrue(p1.contains(Stat.HEALTH));
        assertTrue(p1.contains(Stat.RESOLVE));
    }

    @Test
    void statsAtTier_defaultsToP3WhenPriorityNull() {
        // null priority → all stats default to 3
        List<se.niclas.broledger.model.Stat> p3 = ExpectedStatsCalculator.statsAtTier(null, 3);
        assertEquals(8, p3.size());
        assertTrue(ExpectedStatsCalculator.statsAtTier(null, 1).isEmpty());
    }

    @Test
    void isFullyAllocated_trueWhenSumEquals3xRemaining() {
        int[] inc = new int[8];
        inc[Stat.HEALTH.ordinal()]      = 3;
        inc[Stat.MELEE_SKILL.ordinal()] = 3;
        // sum=6, remaining=2 → 3×2=6 ✓
        assertTrue(ExpectedStatsCalculator.isFullyAllocated(inc, 2));
        assertFalse(ExpectedStatsCalculator.isFullyAllocated(inc, 3));
    }
}
