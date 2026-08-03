package se.niclas.broledger.ui;

import se.niclas.broledger.model.Brother;
import se.niclas.broledger.model.InventorySlot;
import se.niclas.broledger.model.Role;
import se.niclas.broledger.model.Stat;
import se.niclas.broledger.service.ExpectedStatsCalculator;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
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

    /**
     * True when every entry fits within {@code cap}. A stat can reach the sum-matching
     * total (e.g. via a temporarily higher cap while a Gifted perk was planned) while a
     * single stat still exceeds the current cap — that stale state must not be treated
     * as a valid, fully-allocated budget.
     */
    static boolean allWithinCap(int[] arr, int cap) {
        if (arr == null) return true;
        for (int v : arr) if (v > cap) return false;
        return true;
    }

    // ---- gifted perk bonus --------------------------------------------------

    private static final String GIFTED_HEX_ID = "9899E380";

    /** True when the brother already owns the Gifted perk. */
    static boolean hasGiftedPerk(Brother b) {
        return b.perkIds.stream().anyMatch(id -> GIFTED_HEX_ID.equalsIgnoreCase(id));
    }

    /** True when Gifted is PLANNED in the perk plan and not yet owned. */
    static boolean isGiftedPending(Brother b, Map<String, String> planStatus) {
        if (b == null || hasGiftedPerk(b)) return false;
        if (planStatus == null) return false;
        return "PLANNED".equals(planStatus.get(GIFTED_HEX_ID));
    }

    /**
     * Remaining pre-lv11 level-ups, plus one extra when a not-yet-owned Gifted perk
     * is planned (Gifted grants a bonus level-up).
     */
    static int effectiveRemainingLevels(Brother b, int remainingLevels, Map<String, String> planStatus) {
        return remainingLevels + (isGiftedPending(b, planStatus) ? 1 : 0);
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

    /**
     * Per-tier custom perk display order, matching the in-game perk tree layout. Edit a tier's
     * list to reorder its perks; names not present in a tier's list (or tiers not listed here)
     * sort after recognized ones and ultimately fall back to alphabetical.
     */
    private static final Map<Integer, List<String>> PERK_TIER_ORDER = Map.of(
            1, List.of("Adrenaline", "Bags and Belts", "Colossus", "Crippling Strikes",
                    "Fast Adaptation", "Nine Lives", "Pathfinder", "Recover", "Student"),
            2, List.of("Bullseye", "Dodge", "Executioner", "Fortified Mind",
                    "Gifted", "Quick Hands", "Resilient", "Steel Brow"),
            3, List.of("Anticipation", "Backstabber", "Brawny", "Rally the Troops",
                    "Relentless", "Rotation", "Shield Expert", "Taunt"),
            4, List.of("Mace Mastery", "Flail Mastery", "Hammer Mastery", "Axe Mastery",
                    "Cleaver Mastery", "Sword Mastery", "Dagger Mastery", "Polearm Mastery",
                    "Spear Mastery", "Crossbow Mastery", "Bow Mastery", "Throwing Mastery"),
            5, List.of("Footwork", "Lone Wolf", "Overwhelm", "Reach Advantage", "Underdog"),
            6, List.of("Battle Forged", "Berserk", "Head Hunter", "Nimble"),
            7, List.of("Duelist", "Fearsome", "Indomitable", "Killing Frenzy"));

    /**
     * Rank of a perk name within its tier's list in {@link #PERK_TIER_ORDER} (case-insensitive).
     * Returns {@link Integer#MAX_VALUE} for an unlisted tier or an unrecognized name, so it sorts
     * after recognized ones (callers should chain a name comparator afterward as the final tiebreaker).
     */
    public static int perkOrderRank(int tier, String name) {
        if (name == null) return Integer.MAX_VALUE;
        List<String> order = PERK_TIER_ORDER.get(tier);
        if (order == null) return Integer.MAX_VALUE;
        for (int i = 0; i < order.size(); i++) {
            if (name.equalsIgnoreCase(order.get(i))) return i;
        }
        return Integer.MAX_VALUE;
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
     * True when a level-up's plan under-covered it: the total planned increases consumed
     * across all stats is below the expected 3 per assigned level. Returns false when no
     * plan was consumed (empty/null map) — nothing to flag.
     */
    static boolean isUnderConsumed(Map<Stat, Integer> consumedIncreases, int levelsAssigned) {
        if (consumedIncreases == null || consumedIncreases.isEmpty()) return false;
        int total = consumedIncreases.values().stream().mapToInt(Integer::intValue).sum();
        return total < 3 * levelsAssigned;
    }
}
