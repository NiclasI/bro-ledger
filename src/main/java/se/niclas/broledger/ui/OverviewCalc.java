package se.niclas.broledger.ui;

import se.niclas.broledger.model.InventorySlot;
import se.niclas.broledger.model.Role;
import se.niclas.broledger.service.ExpectedStatsCalculator;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.function.Function;
import java.util.function.ToIntFunction;
import java.util.function.ToLongFunction;

/**
 * Pure, side-effect-free helpers for the overview and card UI.
 * All methods are static and directly unit-testable.
 */
public final class OverviewCalc {

    private OverviewCalc() {}

    // ---- budget model ------------------------------------------------------

    /** Snapshot of a brother's increase budget at a given point in time. */
    record LevelBudget(boolean post11, int cap, int totalBudget, int used, int free) {}

    /**
     * Derives the level-increase budget for a brother.
     *
     * @param levelTotal      {@code Brother.levelTotal}
     * @param remainingLevels pre-lv11 remaining level-ups (from {@link ExpectedStatsCalculator#remainingLevels})
     * @param preIncreases    {@code BrotherAnnotation.statIncreases}; may be null
     * @param post11Increases {@code BrotherAnnotation.post11Increases}; may be null
     */
    static LevelBudget levelBudget(int levelTotal, int remainingLevels,
                                   int[] preIncreases, int[] post11Increases) {
        boolean post11 = levelTotal > 11;
        int cap         = post11 ? Math.max(0, levelTotal - 11) : remainingLevels;
        int totalBudget = 3 * cap;
        int used        = sumOrZero(post11 ? post11Increases : preIncreases);
        return new LevelBudget(post11, cap, totalBudget, used, totalBudget - used);
    }

    /** Null-safe sum of an int array; returns 0 when arr is null. */
    static int sumOrZero(int[] arr) {
        return arr == null ? 0 : Arrays.stream(arr).sum();
    }

    // ---- budget state & formatting ----------------------------------------

    enum BudgetState { UNDER, EXACT, OVER }

    /** Classifies a used/total pair as over-budget, exact, or under-budget. */
    static BudgetState budgetState(int used, int total) {
        if (used > total) return BudgetState.OVER;
        if (used < total) return BudgetState.UNDER;
        return BudgetState.EXACT;
    }

    /**
     * Formats the "used/total" budget label text.
     * Post-11 uses a "-X/-Y" prefix; pre-11 uses "X/Y".
     */
    static String formatBudgetLabel(boolean post11, int used, int total) {
        if (post11) return "-" + used + "/-" + total;
        return used + "/" + total;
    }

    // ---- target / priority -------------------------------------------------

    enum TargetState { MET, REACHABLE, UNMET }

    /** Classifies a stat's distance from its target given current and projected-potential values. */
    static TargetState targetState(int current, int potential, int target) {
        if (current >= target)   return TargetState.MET;
        if (potential >= target) return TargetState.REACHABLE;
        return TargetState.UNMET;
    }

    /**
     * Safe accessor for a priority/targetStats array element.
     *
     * @param priority  the array (may be null)
     * @param row       index to read
     * @param fallback  value returned when array is null or row is out of bounds
     */
    static int priorityAt(int[] priority, int row, int fallback) {
        if (priority == null || row < 0 || row >= priority.length) return fallback;
        return priority[row];
    }

    // ---- increase editor ---------------------------------------------------

    /**
     * Returns true when the given delta (±1) can be applied given the current count,
     * per-stat cap, and remaining free budget.
     */
    static boolean canApplyIncrease(int delta, int current, int cap, int freeBudget) {
        if (delta > 0) return current < cap && freeBudget > 0;
        if (delta < 0) return current > 0;
        return false;
    }

    // ---- expected value display --------------------------------------------

    /**
     * Formats an {@link ExpectedStatsCalculator.Expected} result as a display string.
     * Returns "—" only when there are remaining levels but none allocated to this stat.
     */
    static String expectedDisplay(ExpectedStatsCalculator.Expected exp) {
        if (exp.remainingLevels() > 0 && exp.count() == 0) return "—";
        return String.valueOf(exp.finalExpected());
    }

    // ---- perk sorting ------------------------------------------------------

    /**
     * Builds a comparator over perk hex-IDs for the given sort mode.
     *
     * @param mode     the sort mode to apply
     * @param tierOf   maps a perk hex-ID to its tier (use {@link #tierOrMax} for the default)
     * @param countOf  maps a perk hex-ID to its commonality count (higher = more common)
     * @param nameOf   maps a perk hex-ID to its display name
     */
    static Comparator<String> perkComparator(PerkSortMode mode,
                                              ToIntFunction<String> tierOf,
                                              ToLongFunction<String> countOf,
                                              Function<String, String> nameOf) {
        Comparator<String> byTier  = Comparator.comparingInt(tierOf);
        Comparator<String> byCommon = Comparator.comparingLong((String id) -> -countOf.applyAsLong(id));
        Comparator<String> byName  = Comparator.comparing(nameOf, String.CASE_INSENSITIVE_ORDER);
        return switch (mode) {
            case TIER           -> byTier.thenComparing(byName);
            case COMMONALITY    -> byCommon.thenComparing(byName);
            case TIER_THEN_COMMON -> byTier.thenComparing(byCommon).thenComparing(byName);
            case OFF            -> Comparator.naturalOrder();
        };
    }

    /** Returns {@code tier} when non-null, otherwise {@link Integer#MAX_VALUE} (untiered-last). */
    public static int tierOrMax(Integer tier) {
        return tier != null ? tier : Integer.MAX_VALUE;
    }

    /** Formats a perk name with its tier prefix ("[T1] name") when a tier is available. */
    public static String decoratePerkName(Integer tier, String name) {
        return tier != null ? "[T" + tier + "] " + name : name;
    }

    // ---- sort / classification helpers ------------------------------------

    /**
     * Sort key for grouping brothers by role line.
     * Returns 0 for frontline (or no-role) brothers, 1 for non-frontline.
     */
    static int frontlineKey(Role role) {
        return (role != null && !role.frontline) ? 1 : 0;
    }

    /**
     * Determines the CSS tier style class for a weapon slot dot.
     * Returns {@code null} when no tier class applies.
     */
    static String tierStyleClass(String itemType, Integer tier) {
        if ("namedWeapon".equals(itemType)) return "tier-named";
        if (tier != null && tier >= 1 && tier <= 3) return "tier-" + tier;
        return null;
    }

    /**
     * Gathers up to 4 pouch slots from a brother's equipped slots and extra pouches,
     * padding with empty slots when needed.
     */
    static List<InventorySlot> gatherPouches(InventorySlot[] equippedSlots,
                                              List<InventorySlot> extraPouches) {
        List<InventorySlot> list = new ArrayList<>();
        if (equippedSlots != null && equippedSlots.length > 6) list.add(equippedSlots[6]);
        if (extraPouches != null) list.addAll(extraPouches);
        while (list.size() < 4) list.add(InventorySlot.empty(6));
        return list.subList(0, 4);
    }

    // ---- level-up modal ----------------------------------------------------

    /** Derives the status text for a level-up event card. */
    static String levelUpStatusText(boolean post11, boolean adjusted) {
        if (post11) return adjusted ? "Post-lv11 increases recorded ✓" : "Post-lv11 level-up detected";
        return adjusted ? "Planned increases adjusted ✓" : "No planned increases to adjust";
    }

    /**
     * Returns true when a planned-increase consumption was only partial
     * (consumed fewer planned increases than the number of levels assigned).
     */
    static boolean isPartiallyConsumed(Integer consumed, int levelsAssigned) {
        return consumed != null && consumed < levelsAssigned;
    }
}
