package se.niclas.broledger.service;

import se.niclas.broledger.model.Brother;
import se.niclas.broledger.model.Role;
import se.niclas.broledger.model.Stat;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Computes expected stat values based on user-assigned level-up increase allocations.
 *
 * <p>The "expected" stat for a given allocation count is defined as:
 * {@code ceil(currentBase + mean_per_level_increase(stars) × count)},
 * then trait/perk modifiers applied — mirroring how {@link StatPotentialCalculator} works.
 *
 * <p>Two modes are available (see {@link Mode}). The mode is persisted in
 * {@link AppConfig#expectedStatsMode} and switched via the Preferences modal.
 *
 * <p>Increase allocations are stored in {@code BrotherAnnotation.statIncreases[]},
 * indexed by {@link Stat#ordinal()}, matching {@code Role.priority[]} indexing.
 */
public final class ExpectedStatsCalculator {

    private static final Stat[] STATS = Stat.values();
    private static final int    N     = STATS.length;

    /**
     * Returns {@code [greedy, naive]} expected total stat gains to level 11, both
     * indexed by {@link Stat#ordinal()}.
     *
     * <ul>
     *   <li>{@code result[0]} — greedy: deviation-based flexible-slot algorithm.</li>
     *   <li>{@code result[1]} — naive: {@code rolls[i] * E[roll[i]]} with no selection benefit.</li>
     * </ul>
     *
     * @param levelsRemaining  level-ups remaining (0–11)
     * @param stars            talent stars per stat (0–3), indexed by {@link Stat#ordinal()}
     * @param rolls            increases allocated to each stat; each ≤ levelsRemaining,
     *                         sum must equal 3 * levelsRemaining
     * @param priority         user preference per stat (1 = highest, 3 = lowest)
     * @throws TooManyFlexibleStatsException if more than 6 stats are flexible
     */
    public static double[][] expectedGains(int levelsRemaining, int[] stars, int[] rolls,
                                            int[] priority)
            throws TooManyFlexibleStatsException {
        validate(levelsRemaining, stars, rolls, priority);

        double[] naive = new double[N];
        for (int i = 0; i < N; i++) {
            StatPotentialCalculator.Range r = StatPotentialCalculator.rangeForStars(STATS[i].statIndex(), stars[i]);
            naive[i] = rolls[i] * meanRoll(r);
        }

        if (levelsRemaining == 0) return new double[][]{new double[N], naive};

        int fixedCount = 0;
        for (int i = 0; i < N; i++)
            if (isFixed(rolls[i], levelsRemaining)) fixedCount++;
        // K counts only genuinely-flexible stats: allocated but not maxed (rolls[i] > 0 && < levelsRemaining).
        // Zero-allocation stats are excluded — they can never be selected and must not inflate K.
        int K = 0;
        for (int i = 0; i < N; i++)
            if (isFlexible(rolls[i], levelsRemaining)) K++;
        if (K > 6) throw new TooManyFlexibleStatsException(K);
        int flexSlots = flexibleSlots(fixedCount);

        double[] greedy = new double[N];
        for (int i = 0; i < N; i++)
            if (isFixed(rolls[i], levelsRemaining)) {
                StatPotentialCalculator.Range r = StatPotentialCalculator.rangeForStars(STATS[i].statIndex(), stars[i]);
                greedy[i] = rolls[i] * meanRoll(r);
            }

        if (flexSlots == 0) return new double[][]{greedy, naive};

        int[]    flexIdx      = new int[K];
        int[]    flexRolls    = new int[K];
        int[]    lo           = new int[K];
        int[]    hi           = new int[K];
        double[] eRoll        = new double[K];
        int[]    flexPriority = new int[K];
        int k = 0;
        for (int i = 0; i < N; i++) {
            if (isFlexible(rolls[i], levelsRemaining)) {
                StatPotentialCalculator.Range r = StatPotentialCalculator.rangeForStars(STATS[i].statIndex(), stars[i]);
                flexIdx[k]      = i;
                flexRolls[k]    = rolls[i];
                lo[k]           = r.min();
                hi[k]           = r.max();
                eRoll[k]        = meanRoll(r);
                flexPriority[k] = priority[i];
                k++;
            }
        }

        Map<String, double[]> memo = new HashMap<>();
        double[] flexGains = greedyExpected(flexRolls, lo, hi, eRoll, flexPriority, flexSlots, memo);
        for (int j = 0; j < K; j++)
            greedy[flexIdx[j]] = flexGains[j];

        return new double[][]{greedy, naive};
    }

    /** Structured entry produced by {@link #rollPriorityGuideEntries}. */
    public record PriorityEntry(Stat stat, boolean fixed, int rollValue, double deviation) {}

    /**
     * Structured form of the roll-priority guide.
     * Fixed stats (always pick) come first; flexible stats follow sorted by descending deviation.
     *
     * @param levelsRemaining  level-ups remaining (0–11)
     * @param stars            talent stars per stat (0–3), indexed by {@link Stat#ordinal()}
     * @param rolls            increases allocated to each stat
     * @param priority         user preference per stat (1 = highest, 3 = lowest)
     * @throws TooManyFlexibleStatsException if more than 6 stats are flexible
     */
    public static List<PriorityEntry> rollPriorityGuideEntries(int levelsRemaining, int[] stars,
                                                                int[] rolls, int[] priority) {
        validate(levelsRemaining, stars, rolls, priority);
        List<PriorityEntry> out = new ArrayList<>();
        if (levelsRemaining == 0) return out;
        List<int[]> flexEntries = buildSortedFlexEntries(levelsRemaining, stars, rolls, priority);
        for (int i = 0; i < N; i++)
            if (isFixed(rolls[i], levelsRemaining))
                out.add(new PriorityEntry(STATS[i], true, 0, 0.0));
        for (int[] e : flexEntries) {
            StatPotentialCalculator.Range r =
                    StatPotentialCalculator.rangeForStars(STATS[e[0]].statIndex(), stars[e[0]]);
            out.add(new PriorityEntry(STATS[e[0]], false, e[1], rollDeviation(e[1], r)));
        }
        return out;
    }

    /**
     * Returns a human-readable pick-priority guide for the flexible slot(s) at each level-up.
     * Fixed stats appear first; flexible stats are listed as one entry per (stat, roll) outcome.
     *
     * @param levelsRemaining  level-ups remaining (0–11)
     * @param stars            talent stars per stat (0–3), indexed by {@link Stat#ordinal()}
     * @param rolls            increases allocated to each stat
     * @param priority         user preference per stat (1 = highest, 3 = lowest)
     * @throws TooManyFlexibleStatsException if more than 6 stats are flexible
     */
    public static List<String> rollPriorityGuide(int levelsRemaining, int[] stars, int[] rolls,
                                                   int[] priority) {
        validate(levelsRemaining, stars, rolls, priority);
        List<String> guide = new ArrayList<>();
        if (levelsRemaining == 0) return guide;
        List<int[]> entries = buildSortedFlexEntries(levelsRemaining, stars, rolls, priority);
        for (int i = 0; i < N; i++)
            if (isFixed(rolls[i], levelsRemaining))
                guide.add(String.format("Always pick %-18s  -", STATS[i].displayName()));
        for (int[] e : entries) {
            StatPotentialCalculator.Range r =
                    StatPotentialCalculator.rangeForStars(STATS[e[0]].statIndex(), stars[e[0]]);
            guide.add(String.format("%-18s  %d   %+.1f",
                    STATS[e[0]].displayName(), e[1], rollDeviation(e[1], r)));
        }
        return guide;
    }

    private static List<int[]> buildSortedFlexEntries(int levelsRemaining, int[] stars,
                                                       int[] rolls, int[] priority) {
        // No K > 6 guard here — sorting by deviation is O(K × roll_range), not exponential.
        // The K ≤ 6 limit lives only in expectedGains where the full enumeration runs.
        List<int[]> entries = new ArrayList<>();
        for (int i = 0; i < N; i++) {
            if (isFixed(rolls[i], levelsRemaining) || rolls[i] == 0) continue;
            StatPotentialCalculator.Range r =
                    StatPotentialCalculator.rangeForStars(STATS[i].statIndex(), stars[i]);
            for (int rv = r.max(); rv >= r.min(); rv--)
                entries.add(new int[]{i, rv});
        }
        entries.sort((a, b) -> {
            StatPotentialCalculator.Range rA =
                    StatPotentialCalculator.rangeForStars(STATS[a[0]].statIndex(), stars[a[0]]);
            StatPotentialCalculator.Range rB =
                    StatPotentialCalculator.rangeForStars(STATS[b[0]].statIndex(), stars[b[0]]);
            double devA = rollDeviation(a[1], rA);
            double devB = rollDeviation(b[1], rB);
            if (devA != devB) return Double.compare(devB, devA);
            if (rolls[a[0]] != rolls[b[0]]) return Integer.compare(rolls[b[0]], rolls[a[0]]);
            if (priority[a[0]] != priority[b[0]]) return Integer.compare(priority[a[0]], priority[b[0]]);
            return STATS[a[0]].displayName().compareTo(STATS[b[0]].displayName());
        });
        return entries;
    }

    private static double[] greedyExpected(int[] remaining, int[] lo, int[] hi, double[] eRoll,
                                           int[] priority, int flexSlots,
                                           Map<String, double[]> memo) {
        String   key    = Arrays.toString(remaining);
        double[] cached = memo.get(key);
        if (cached != null) return cached;

        int K = remaining.length;
        double[] result = new double[K];

        int   ac     = 0;
        int[] active = new int[K];
        for (int j = 0; j < K; j++) if (remaining[j] > 0) active[ac++] = j;
        if (ac == 0) { memo.put(key, result); return result; }

        int  slotsToFill = Math.min(flexSlots, ac);
        long totalCombos = 1;
        int[] aLo = new int[ac], aHi = new int[ac];
        for (int ai = 0; ai < ac; ai++) {
            aLo[ai] = lo[active[ai]];
            aHi[ai] = hi[active[ai]];
            totalCombos *= (aHi[ai] - aLo[ai] + 1);
        }

        double[] accumulated = new double[K];
        int[]    rollBuf     = new int[ac];
        enumerate(0, ac, aLo, aHi, rollBuf,
                  active, remaining, lo, hi, eRoll, priority, flexSlots, slotsToFill,
                  accumulated, memo);

        for (int j = 0; j < K; j++) result[j] = accumulated[j] / totalCombos;
        memo.put(key, result);
        return result;
    }

    private static void enumerate(int depth, int ac, int[] aLo, int[] aHi, int[] rollBuf,
                                  int[] active, int[] remaining,
                                  int[] lo, int[] hi, double[] eRoll, int[] priority,
                                  int flexSlots, int slotsToFill,
                                  double[] accumulated, Map<String, double[]> memo) {
        if (depth == ac) {
            boolean[] picked = new boolean[ac];
            for (int slot = 0; slot < slotsToFill; slot++) {
                int best = -1;
                for (int ai = 0; ai < ac; ai++) {
                    if (picked[ai]) continue;
                    if (best == -1) { best = ai; continue; }
                    double dBest = rollBuf[best] - eRoll[active[best]];
                    double dAi   = rollBuf[ai]   - eRoll[active[ai]];
                    if (dAi > dBest) { best = ai; continue; }
                    if (dAi < dBest) continue;
                    int rBest = remaining[active[best]], rAi = remaining[active[ai]];
                    if (rAi > rBest) { best = ai; continue; }
                    if (rAi < rBest) continue;
                    if (priority[active[ai]] < priority[active[best]]) best = ai;
                }
                picked[best] = true;
            }

            int[] newRem = remaining.clone();
            for (int ai = 0; ai < ac; ai++)
                if (picked[ai]) newRem[active[ai]]--;

            double[] sub = greedyExpected(newRem, lo, hi, eRoll, priority, flexSlots, memo);

            for (int j = 0; j < remaining.length; j++) accumulated[j] += sub[j];
            for (int ai = 0; ai < ac; ai++)
                if (picked[ai]) accumulated[active[ai]] += rollBuf[ai];
            return;
        }

        for (int r = aLo[depth]; r <= aHi[depth]; r++) {
            rollBuf[depth] = r;
            enumerate(depth + 1, ac, aLo, aHi, rollBuf,
                      active, remaining, lo, hi, eRoll, priority, flexSlots, slotsToFill,
                      accumulated, memo);
        }
    }

    private static void validate(int levelsRemaining, int[] stars, int[] rolls, int[] priority) {
        if (levelsRemaining < 0 || levelsRemaining > 11)
            throw new IllegalArgumentException(
                "levelsRemaining must be 0–11, got " + levelsRemaining);
        if (stars.length != N)
            throw new IllegalArgumentException("stars[] must have length " + N);
        if (rolls.length != N)
            throw new IllegalArgumentException("rolls[] must have length " + N);
        if (priority.length != N)
            throw new IllegalArgumentException("priority[] must have length " + N);
        int total = 0;
        for (int i = 0; i < N; i++) {
            if (stars[i] < 0 || stars[i] > 3)
                throw new IllegalArgumentException(
                    STATS[i].displayName() + ": stars must be 0–3, got " + stars[i]);
            if (rolls[i] < 0)
                throw new IllegalArgumentException(
                    STATS[i].displayName() + ": rolls must be >= 0, got " + rolls[i]);
            if (rolls[i] > levelsRemaining)
                throw new IllegalArgumentException(
                    STATS[i].displayName() + ": rolls (" + rolls[i] +
                    ") exceeds levelsRemaining (" + levelsRemaining + ")");
            if (priority[i] < 1 || priority[i] > 3)
                throw new IllegalArgumentException(
                    STATS[i].displayName() + ": priority must be 1–3, got " + priority[i]);
            total += rolls[i];
        }
        if (total != 3 * levelsRemaining)
            throw new IllegalArgumentException(
                "Sum of rolls (" + total + ") must equal 3 × levelsRemaining = " +
                (3 * levelsRemaining));
    }

    public enum Mode {
        /** Simple per-stat formula: ceil(currentBase + mean × count). Always live-updating. */
        NAIVE,
        /**
         * Greedy optimal from {@link BattleBrothersLevelPredictor}: accounts for choosing
         * high-roll levels. Only valid when all increases are assigned (sum == 3×remaining);
         * falls back to NAIVE for partial allocations.
         */
        GREEDY;

        public static Mode parse(String s) {
            if ("GREEDY".equalsIgnoreCase(s)) return GREEDY;
            return NAIVE;
        }
    }

    /**
     * @param baseExpected    projected base stat at level 11 (before trait/perk mods)
     * @param finalExpected   baseExpected after trait/perk modifiers
     * @param count           increases allocated to this stat
     * @param remainingLevels level-ups remaining before 11 (0 = already ≥ 11)
     */
    public record Expected(int baseExpected, int finalExpected, int count, int remainingLevels) {}

    private ExpectedStatsCalculator() {}

    /**
     * Level-ups still to allocate before lv 11, including earned-but-unassigned ones
     * ({@code Brother.levelPoints}). Returns 0 for brothers at lv 11 or beyond.
     */
    public static int remainingLevels(Brother b) {
        return remainingLevels(b.levelTotal, b.levelPoints);
    }

    /** Pure overload — computes remaining pre-lv11 level-ups from raw fields. */
    public static int remainingLevels(int levelTotal, int levelPoints) {
        if (levelTotal >= 11) return 0;
        return Math.max(0, 11 - levelTotal) + Math.max(0, levelPoints);
    }

    /**
     * Computes the expected stat value for one stat given the current allocation.
     * Delegates to the 5-arg overload with {@code null} post11Increases.
     *
     * @param b         the brother
     * @param stat      which stat
     * @param increases pre-lv11 increase counts indexed by {@link Stat#ordinal()}; may be null (all zero)
     * @param mode      calculation mode
     */
    public static Expected compute(Brother b, Stat stat, int[] increases, Mode mode) {
        return compute(b, stat, increases, null, mode);
    }

    /**
     * Computes the expected stat value normalised to lv 11.
     *
     * <p>For brothers at level ≤ 11: projects forward using {@code increases} (random-roll phase).
     * For brothers beyond level 11: strips the post-lv11 deterministic gains recorded in
     * {@code post11Increases} to produce the lv-11-comparable baseline.
     *
     * @param b              the brother
     * @param stat           which stat
     * @param increases      pre-lv11 increase counts indexed by {@link Stat#ordinal()}; may be null
     * @param post11Increases post-lv11 +1 gains recorded per stat; may be null (all zero)
     * @param mode           calculation mode (only relevant when {@code remaining > 0})
     */
    public static Expected compute(Brother b, Stat stat, int[] increases, int[] post11Increases, Mode mode) {
        int remaining   = remainingLevels(b);
        int currentBase = b.stats[stat.statIndex()];

        if (remaining == 0) {
            int p11count = (post11Increases != null && stat.ordinal() < post11Increases.length)
                    ? post11Increases[stat.ordinal()] : 0;
            int lv11Base = currentBase - p11count;
            StatModifierService.Breakdown bd =
                    StatModifierService.getInstance().computeWithBase(b, stat, lv11Base);
            return new Expected(lv11Base, bd.finalValue(), p11count, 0);
        }

        int count = (increases != null && stat.ordinal() < increases.length)
                ? increases[stat.ordinal()] : 0;

        if (count == 0) {
            StatModifierService.Breakdown bd =
                    StatModifierService.getInstance().computeWithBase(b, stat, currentBase);
            return new Expected(currentBase, bd.finalValue(), 0, remaining);
        }

        int baseExpected;
        if (mode == Mode.GREEDY && isFullyAllocated(increases, remaining)) {
            baseExpected = greedyBase(b, stat, increases, currentBase, remaining);
        } else {
            baseExpected = naiveBase(b, stat, currentBase, count);
        }

        StatModifierService.Breakdown bd =
                StatModifierService.getInstance().computeWithBase(b, stat, baseExpected);
        return new Expected(baseExpected, bd.finalValue(), count, remaining);
    }

    /**
     * Auto-assigns increases for a brother based on the role's stat priorities.
     * P1 stats are filled first (split equally with alphabetic tiebreaker when > 3),
     * then the remaining budget cascades to P2 and P3.
     *
     * @return int[8] increase allocations indexed by {@link Stat#ordinal()}, summing to 3×remaining
     */
    public static int[] autoAssignByRole(Brother b, Role role) {
        int remaining = remainingLevels(b);
        if (remaining == 0 || role == null) return new int[N];
        return allocateByRoleTiers(role.priority, remaining);
    }

    /**
     * Auto-assigns post-lv11 increases for a brother based on the role's stat priorities.
     * Same algorithm as {@link #autoAssignByRole} but uses the post-lv11 budget:
     * {@code 3 × (levelTotal − 11)} total, per-stat cap = {@code levelTotal − 11}.
     *
     * @return int[8] indexed by {@link Stat#ordinal()}, summing to 3×(levelTotal−11)
     */
    public static int[] autoAssignPost11ByRole(Brother b, Role role) {
        int postLevels = Math.max(0, b.levelTotal - 11);
        if (postLevels == 0 || role == null) return new int[N];
        return allocateByRoleTiers(role.priority, postLevels);
    }

    /**
     * Core role-priority tier-allocation algorithm.
     * P1 stats are filled first (up to perStatCap each), then P2, then P3.
     * When the budget can't fully cover a tier, it is split equally with alphabetical tiebreaker.
     *
     * @param rolePriority  per-stat priority (1–3), indexed by {@link Stat#ordinal()}; may be null
     * @param perStatCap    maximum increases per stat (= remaining pre-11 levels or post-11 levels)
     * @return int[8] allocations indexed by {@link Stat#ordinal()}, summing to 3 × perStatCap
     */
    static int[] allocateByRoleTiers(int[] rolePriority, int perStatCap) {
        int[] result = new int[N];
        int budget = 3 * perStatCap;
        for (int tier = 1; tier <= 3 && budget > 0; tier++) {
            List<Stat> tierStats = statsAtTier(rolePriority, tier);
            if (tierStats.isEmpty()) continue;
            int count = tierStats.size();
            if ((long) count * perStatCap <= budget) {
                // Can max out all stats in this tier.
                for (Stat s : tierStats) result[s.ordinal()] = perStatCap;
                budget -= count * perStatCap;
            } else {
                // Split budget equally; alphabetical tiebreaker for the remainder.
                tierStats.sort(Comparator.comparing(Stat::displayName));
                int each  = budget / count;
                int extra = budget % count;
                for (int i = 0; i < count; i++) {
                    result[tierStats.get(i).ordinal()] = each + (i < extra ? 1 : 0);
                }
                budget = 0;
            }
        }
        return result;
    }

    /**
     * Returns all stats whose effective priority equals the given tier.
     * Stats absent from (or out-of-bounds in) {@code rolePriority} default to priority 3.
     */
    static List<Stat> statsAtTier(int[] rolePriority, int tier) {
        List<Stat> tierStats = new ArrayList<>();
        for (Stat s : STATS) {
            int ord  = s.ordinal();
            int prio = (rolePriority != null && ord < rolePriority.length) ? rolePriority[ord] : 3;
            if (prio == tier) tierStats.add(s);
        }
        return tierStats;
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    // ---- pure calc / predicate helpers (package-private, directly unit-testable) ----

    /** Expected value of one roll for the given range: (min + max) / 2.0. */
    static double meanRoll(StatPotentialCalculator.Range r) {
        return (r.min() + r.max()) / 2.0;
    }

    /** Deviation of an actual roll value from the stat's expected mean: rollValue − E[roll]. */
    static double rollDeviation(int rollValue, StatPotentialCalculator.Range r) {
        return rollValue - meanRoll(r);
    }

    /** A stat is "fixed" (always chosen at every level-up) when all its rolls are allocated. */
    static boolean isFixed(int rolls, int levelsRemaining) {
        return rolls == levelsRemaining;
    }

    /** A stat is "flexible" when some but not all levels are allocated to it. */
    static boolean isFlexible(int rolls, int levelsRemaining) {
        return rolls > 0 && rolls < levelsRemaining;
    }

    /** Number of flexible pick slots per level-up (3 picks minus the fixed-stat count). */
    static int flexibleSlots(int fixedCount) {
        return 3 - fixedCount;
    }

    private static int naiveBase(Brother b, Stat stat, int currentBase, int count) {
        StatPotentialCalculator.Range r =
                StatPotentialCalculator.rangeForStars(stat.statIndex(), b.stars[stat.starIndex()]);
        return (int) Math.ceil(currentBase + count * meanRoll(r));
    }

    private static int greedyBase(Brother b, Stat stat, int[] increases, int currentBase, int remaining) {
        int[] stars = new int[N];
        for (int i = 0; i < N; i++) stars[i] = b.stars[STATS[i].starIndex()];
        int[] priority = new int[N];
        java.util.Arrays.fill(priority, 3);
        try {
            double[][] gains = expectedGains(remaining, stars, increases, priority);
            return (int) Math.ceil(currentBase + gains[0][stat.ordinal()]);
        } catch (TooManyFlexibleStatsException e) {
            return naiveBase(b, stat, currentBase, increases[stat.ordinal()]);
        }
    }

    static boolean isFullyAllocated(int[] increases, int remaining) {
        if (increases == null || increases.length < N) return false;
        int sum = 0;
        for (int v : increases) sum += v;
        return sum == 3 * remaining;
    }

    public static class TooManyFlexibleStatsException extends Exception {
        TooManyFlexibleStatsException(int k) {
            super("Flexible stat count " + k + " exceeds limit of 6 — computation would be too expensive.");
        }
    }
}
