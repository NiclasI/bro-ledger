package se.niclas.broledger.service;

import se.niclas.broledger.model.Brother;
import se.niclas.broledger.model.Stat;

/**
 * Computes the expected (mean) attribute value a brother will have at level 11,
 * based on current level, base stat, and talent stars.
 *
 * Roll ranges sourced from the Battle Brothers wiki (Talents.html in project root).
 * Rule: 1★ adds +1 to min, 2★ adds +2 to min, 3★ adds +2 to min and +1 to max.
 */
public final class StatPotentialCalculator {

    public record Range(int min, int max) {}

    /**
     * @param basePotential    expected base stat at level 11 (before trait/perk mods)
     * @param finalPotential   basePotential after trait/perk modifiers
     * @param minBasePotential worst-case base stat at level 11
     * @param maxBasePotential best-case base stat at level 11
     * @param remainingLevels  level-ups remaining before level 11 (0 means already ≥ 11)
     */
    public record Potential(int basePotential, int finalPotential,
                            int minBasePotential, int maxBasePotential,
                            int remainingLevels) {}

    // Base (0★) per-level roll min/max, indexed by Stat.statIndex().
    private static final Range[] BASE_RANGES = new Range[8];
    static {
        BASE_RANGES[Stat.HEALTH.statIndex()]        = new Range(2, 4);
        BASE_RANGES[Stat.RESOLVE.statIndex()]       = new Range(2, 4);
        BASE_RANGES[Stat.FATIGUE.statIndex()]       = new Range(2, 4);
        BASE_RANGES[Stat.MELEE_SKILL.statIndex()]   = new Range(1, 3);
        BASE_RANGES[Stat.RANGED_SKILL.statIndex()]  = new Range(2, 4);
        BASE_RANGES[Stat.MELEE_DEFENSE.statIndex()] = new Range(1, 3);
        BASE_RANGES[Stat.RANGED_DEFENSE.statIndex()]= new Range(2, 4);
        BASE_RANGES[Stat.INITIATIVE.statIndex()]    = new Range(3, 5);
    }

    private StatPotentialCalculator() {}

    /** 0★ per-level roll range for the given stat index. Package-private; use {@link Stat} in callers. */
    static Range baseRange(int statIdx) {
        if (statIdx < 0 || statIdx >= BASE_RANGES.length) return null;
        return BASE_RANGES[statIdx];
    }

    /** Per-level roll range adjusted for the given star count (0–3). Package-private; use {@link Stat} in callers. */
    static Range rangeForStars(int statIdx, int stars) {
        Range base = baseRange(statIdx);
        if (base == null) return null;
        int s = Math.max(0, Math.min(stars, 3));
        int min = base.min() + Math.min(s, 2);
        int max = base.max() + (s >= 3 ? 1 : 0);
        return new Range(min, max);
    }

    /**
     * Expected base potential (no trait/perk mods) rounded to nearest integer.
     * Returns currentBase unchanged when currentLevel >= 11.
     */
    public static int expectedBasePotential(int currentBase, Stat stat,
                                            int stars, int currentLevel) {
        int remaining = Math.max(0, 11 - currentLevel);
        if (remaining == 0) return currentBase;
        Range r = rangeForStars(stat.statIndex(), stars);
        if (r == null) return currentBase;
        double avgRoll = (r.min() + r.max()) / 2.0;
        return (int) Math.round(currentBase + remaining * avgRoll);
    }

    /**
     * Full potential calculation including trait/perk modifiers.
     *
     * @param b    the brother
     * @param stat the stat (carries both statIndex and starIndex)
     */
    public static Potential compute(Brother b, Stat stat) {
        int statIdx   = stat.statIndex();
        int remaining = ExpectedStatsCalculator.remainingLevels(b);
        if (remaining == 0) {
            StatModifierService.Breakdown bd =
                    StatModifierService.getInstance().compute(b, stat);
            return new Potential(b.stats[statIdx], bd.finalValue(),
                                 b.stats[statIdx], b.stats[statIdx], 0);
        }

        int stars = b.stars[stat.starIndex()];
        Range r = rangeForStars(statIdx, stars);
        if (r == null) r = new Range(0, 0);

        int base = b.stats[statIdx];
        int minBase = base + remaining * r.min();
        int maxBase = base + remaining * r.max();
        int expectedBase = (int) Math.round(base + remaining * (r.min() + r.max()) / 2.0);

        StatModifierService.Breakdown bd =
                StatModifierService.getInstance().computeWithBase(b, stat, expectedBase);

        return new Potential(expectedBase, bd.finalValue(), minBase, maxBase, remaining);
    }
}
