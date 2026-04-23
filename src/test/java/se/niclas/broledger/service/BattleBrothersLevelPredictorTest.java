package se.niclas.broledger.service;

import org.junit.jupiter.api.Test;
import se.niclas.broledger.model.Stat;

import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.*;

class BattleBrothersLevelPredictorTest {

    private static int[] allPriority3() {
        int[] p = new int[Stat.values().length];
        Arrays.fill(p, 3);
        return p;
    }

    private static int[] allZeroStars() {
        return new int[Stat.values().length];
    }

    // Three fixed stats covering 10 levels each — all fixed, greedy == naive.
    @Test
    void allFixedStats_greedyEqualsNaive() throws Exception {
        int N = Stat.values().length;
        int[] rolls = new int[N];
        rolls[Stat.HEALTH.ordinal()]      = 10;
        rolls[Stat.MELEE_SKILL.ordinal()] = 10;
        rolls[Stat.INITIATIVE.ordinal()]  = 10;

        double[][] result = ExpectedStatsCalculator.expectedGains(
                10, allZeroStars(), rolls, allPriority3());

        double[] greedy = result[0];
        double[] naive  = result[1];
        assertArrayEquals(greedy, naive, 1e-9,
                "All-fixed allocation: greedy must equal naive");
    }

    // Naive values must match expected mean × count formula for 0-star stats.
    @Test
    void naiveMatchesMeanTimesCount_zeroStars() throws Exception {
        int N = Stat.values().length;
        int[] rolls = new int[N];
        rolls[Stat.HEALTH.ordinal()]      = 10; // mean 3.0 → 30
        rolls[Stat.MELEE_SKILL.ordinal()] = 10; // mean 2.0 → 20
        rolls[Stat.INITIATIVE.ordinal()]  = 10; // mean 4.0 → 40

        double[][] result = ExpectedStatsCalculator.expectedGains(
                10, allZeroStars(), rolls, allPriority3());
        double[] naive = result[1];

        assertEquals(30.0, naive[Stat.HEALTH.ordinal()],      1e-9);
        assertEquals(20.0, naive[Stat.MELEE_SKILL.ordinal()], 1e-9);
        assertEquals(40.0, naive[Stat.INITIATIVE.ordinal()],  1e-9);
        // Un-allocated stats contribute 0
        assertEquals(0.0,  naive[Stat.RESOLVE.ordinal()],     1e-9);
    }

    // Naive for a stat with 3 stars and 10 rolls.
    // HEALTH 3★: range (4,5), mean 4.5 → 45.0
    @Test
    void naiveMatchesMeanTimesCount_threeStars() throws Exception {
        int N = Stat.values().length;
        int[] stars = new int[N];
        stars[Stat.HEALTH.ordinal()]      = 3;
        stars[Stat.MELEE_SKILL.ordinal()] = 0;
        stars[Stat.INITIATIVE.ordinal()]  = 0;

        int[] rolls = new int[N];
        rolls[Stat.HEALTH.ordinal()]      = 10;
        rolls[Stat.MELEE_SKILL.ordinal()] = 10;
        rolls[Stat.INITIATIVE.ordinal()]  = 10;

        double[][] result = ExpectedStatsCalculator.expectedGains(
                10, stars, rolls, allPriority3());
        double[] naive = result[1];

        assertEquals(45.0, naive[Stat.HEALTH.ordinal()], 1e-9,
                "HEALTH 3★: 10 × 4.5 = 45.0");
    }

    // Greedy >= naive for any flexible allocation.
    @Test
    void greedyAtLeastNaive_flexStats() throws Exception {
        int N = Stat.values().length;
        // 2 fixed (HEALTH, MELEE_SKILL), 1 flex (INITIATIVE with 5 rolls) + RESOLVE 5 rolls
        int[] rolls = new int[N];
        rolls[Stat.HEALTH.ordinal()]      = 10;
        rolls[Stat.MELEE_SKILL.ordinal()] = 10;
        rolls[Stat.INITIATIVE.ordinal()]  = 5;
        rolls[Stat.RESOLVE.ordinal()]     = 5;

        double[][] result = ExpectedStatsCalculator.expectedGains(
                10, allZeroStars(), rolls, allPriority3());

        double[] greedy = result[0];
        double[] naive  = result[1];
        for (Stat s : Stat.values()) {
            if (rolls[s.ordinal()] > 0) {
                assertTrue(greedy[s.ordinal()] >= naive[s.ordinal()] - 1e-9,
                        "greedy[" + s + "] should be >= naive[" + s + "]");
            }
        }
    }

    // Greedy strictly > naive for a genuinely contested flex stat.
    @Test
    void greedyExceedsNaive_whenFlexContested() throws Exception {
        int N = Stat.values().length;
        int[] rolls = new int[N];
        rolls[Stat.HEALTH.ordinal()]      = 10;
        rolls[Stat.MELEE_SKILL.ordinal()] = 10;
        rolls[Stat.INITIATIVE.ordinal()]  = 5;
        rolls[Stat.RESOLVE.ordinal()]     = 5;

        double[][] result = ExpectedStatsCalculator.expectedGains(
                10, allZeroStars(), rolls, allPriority3());

        double greedyInit = result[0][Stat.INITIATIVE.ordinal()];
        double naiveInit  = result[1][Stat.INITIATIVE.ordinal()];
        double greedyRes  = result[0][Stat.RESOLVE.ordinal()];
        double naiveRes   = result[1][Stat.RESOLVE.ordinal()];
        // The selection benefit must boost at least one of the two flex stats above naive.
        assertTrue(greedyInit + greedyRes > naiveInit + naiveRes - 1e-9,
                "Total greedy gain should be >= total naive gain for contested flex stats");
    }

    // Zero-allocation stats must not count toward the K > 6 guard.
    // Before the fix: 1 fixed + 1 flex + 6 zero-alloc → K=7 (old: N-fixedCount) → wrongly threw.
    // After the fix:  K=1 (only the genuinely-flexible stat) → runs greedy without error.
    @Test
    void zeroAllocStats_doNotInflateK() throws Exception {
        int N = Stat.values().length;
        int[] rolls = new int[N];
        // levelsRemaining=3, budget=9: 3 fixed stats, 1 flex, 4 unallocated
        rolls[Stat.HEALTH.ordinal()]        = 3; // fixed
        rolls[Stat.MELEE_SKILL.ordinal()]   = 3; // fixed
        rolls[Stat.MELEE_DEFENSE.ordinal()] = 3; // fixed
        rolls[Stat.INITIATIVE.ordinal()]    = 0; // unallocated — must not count toward K
        // all others default 0
        // sum = 9 = 3×3 ✓, no flex stat → flexSlots=0, greedy==naive (trivially)
        assertDoesNotThrow(() ->
                ExpectedStatsCalculator.expectedGains(3, allZeroStars(), rolls, allPriority3()));

        // Now put 2 into initiative (flex) and reduce one fixed to compensate
        rolls[Stat.HEALTH.ordinal()]        = 3; // fixed
        rolls[Stat.MELEE_SKILL.ordinal()]   = 3; // fixed
        rolls[Stat.MELEE_DEFENSE.ordinal()] = 1; // flex (1 < 3)
        rolls[Stat.INITIATIVE.ordinal()]    = 2; // flex (2 < 3)
        // sum = 9 ✓, fixedCount=2, genuinelyFlexible=2, old K=6, new K=2 — both ≤ 6, fine
        double[][] result = ExpectedStatsCalculator.expectedGains(
                3, allZeroStars(), rolls, allPriority3());
        // Greedy gain for flex stats should be >= naive
        assertTrue(result[0][Stat.MELEE_DEFENSE.ordinal()] >= result[1][Stat.MELEE_DEFENSE.ordinal()] - 1e-9);
        assertTrue(result[0][Stat.INITIATIVE.ordinal()]    >= result[1][Stat.INITIATIVE.ordinal()]    - 1e-9);
    }

    // levelsRemaining=0 → both arrays are zero.
    @Test
    void levelsRemaining0_returnsZeroGains() throws Exception {
        int[] rolls = new int[Stat.values().length]; // all zero
        double[][] result = ExpectedStatsCalculator.expectedGains(
                0, allZeroStars(), rolls, allPriority3());
        for (double v : result[0]) assertEquals(0.0, v, 1e-9);
    }

    // Validation: sum of rolls != 3 × levelsRemaining.
    @Test
    void validation_wrongRollSum_throws() {
        int N = Stat.values().length;
        int[] rolls = new int[N];
        rolls[Stat.HEALTH.ordinal()] = 5; // sum=5 != 3×2=6
        assertThrows(IllegalArgumentException.class, () ->
                ExpectedStatsCalculator.expectedGains(
                        2, allZeroStars(), rolls, allPriority3()));
    }

    // Validation: a single roll exceeds levelsRemaining.
    @Test
    void validation_rollExceedsLevels_throws() {
        int N = Stat.values().length;
        int[] rolls = new int[N];
        rolls[Stat.HEALTH.ordinal()]      = 3; // exceeds levelsRemaining=2
        rolls[Stat.MELEE_SKILL.ordinal()] = 2;
        rolls[Stat.INITIATIVE.ordinal()]  = 1;
        assertThrows(IllegalArgumentException.class, () ->
                ExpectedStatsCalculator.expectedGains(
                        2, allZeroStars(), rolls, allPriority3()));
    }

    // TooManyFlexibleStatsException when K > 6.
    @Test
    void tooManyFlexStats_throws() {
        int N = Stat.values().length;
        int[] rolls = new int[N];
        // Spread across 8 stats so none equals levelsRemaining — all flex.
        // Sum must equal 3×4=12, each rolled 1-2 times, none = levelsRemaining=4
        // (8 stats each 1 roll = 8 != 12; try: 4 stats with 2 rolls + 4 with 1 roll = 12)
        rolls[Stat.HEALTH.ordinal()]        = 2;
        rolls[Stat.RESOLVE.ordinal()]       = 2;
        rolls[Stat.FATIGUE.ordinal()]       = 2;
        rolls[Stat.INITIATIVE.ordinal()]    = 2;
        rolls[Stat.MELEE_SKILL.ordinal()]   = 1;
        rolls[Stat.RANGED_SKILL.ordinal()]  = 1;
        rolls[Stat.MELEE_DEFENSE.ordinal()] = 1;
        rolls[Stat.RANGED_DEFENSE.ordinal()]= 1;
        // All < levelsRemaining=4 → all 8 are flex → K=8 > 6
        assertThrows(ExpectedStatsCalculator.TooManyFlexibleStatsException.class, () ->
                ExpectedStatsCalculator.expectedGains(
                        4, allZeroStars(), rolls, allPriority3()));
    }
}
