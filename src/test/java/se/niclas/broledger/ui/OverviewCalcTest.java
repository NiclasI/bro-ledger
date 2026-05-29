package se.niclas.broledger.ui;

import org.junit.jupiter.api.Test;
import se.niclas.broledger.model.InventorySlot;
import se.niclas.broledger.model.Role;
import se.niclas.broledger.service.ExpectedStatsCalculator;

import java.util.Comparator;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class OverviewCalcTest {

    // ---- sumOrZero ---------------------------------------------------------

    @Test
    void sumOrZero_nullArray() {
        assertEquals(0, OverviewCalc.sumOrZero(null));
    }

    @Test
    void sumOrZero_emptyArray() {
        assertEquals(0, OverviewCalc.sumOrZero(new int[0]));
    }

    @Test
    void sumOrZero_summed() {
        assertEquals(6, OverviewCalc.sumOrZero(new int[]{1, 2, 3}));
    }

    // ---- levelBudget -------------------------------------------------------

    @Test
    void levelBudget_pre11() {
        OverviewCalc.LevelBudget lb = OverviewCalc.levelBudget(5, 6, new int[]{3, 3, 0, 0, 0, 0, 0, 0}, null);
        assertFalse(lb.post11());
        assertEquals(6, lb.cap());
        assertEquals(18, lb.totalBudget());
        assertEquals(6,  lb.used());
        assertEquals(12, lb.free());
    }

    @Test
    void levelBudget_post11() {
        int[] post11 = new int[8];
        post11[0] = 2;
        OverviewCalc.LevelBudget lb = OverviewCalc.levelBudget(14, 0, null, post11);
        assertTrue(lb.post11());
        assertEquals(3, lb.cap());        // 14-11=3
        assertEquals(9, lb.totalBudget());
        assertEquals(2, lb.used());
        assertEquals(7, lb.free());
    }

    @Test
    void levelBudget_nullArrays_usedZero() {
        OverviewCalc.LevelBudget lb = OverviewCalc.levelBudget(5, 6, null, null);
        assertEquals(0, lb.used());
        assertEquals(18, lb.free());
    }

    // ---- budgetState -------------------------------------------------------

    @Test
    void budgetState_under() {
        assertEquals(OverviewCalc.BudgetState.UNDER, OverviewCalc.budgetState(5, 10));
    }

    @Test
    void budgetState_exact() {
        assertEquals(OverviewCalc.BudgetState.EXACT, OverviewCalc.budgetState(10, 10));
    }

    @Test
    void budgetState_over() {
        assertEquals(OverviewCalc.BudgetState.OVER, OverviewCalc.budgetState(11, 10));
    }

    // ---- formatBudgetLabel -------------------------------------------------

    @Test
    void formatBudgetLabel_pre11() {
        assertEquals("5/10", OverviewCalc.formatBudgetLabel(false, 5, 10));
    }

    @Test
    void formatBudgetLabel_post11() {
        assertEquals("-5/-10", OverviewCalc.formatBudgetLabel(true, 5, 10));
    }

    // ---- targetState -------------------------------------------------------

    @Test
    void targetState_met() {
        assertEquals(OverviewCalc.TargetState.MET, OverviewCalc.targetState(60, 65, 55));
    }

    @Test
    void targetState_reachable() {
        assertEquals(OverviewCalc.TargetState.REACHABLE, OverviewCalc.targetState(50, 65, 60));
    }

    @Test
    void targetState_unmet() {
        assertEquals(OverviewCalc.TargetState.UNMET, OverviewCalc.targetState(50, 58, 60));
    }

    // ---- priorityAt --------------------------------------------------------

    @Test
    void priorityAt_normalAccess() {
        assertEquals(2, OverviewCalc.priorityAt(new int[]{1, 2, 3}, 1, 0));
    }

    @Test
    void priorityAt_nullArray_fallback() {
        assertEquals(3, OverviewCalc.priorityAt(null, 0, 3));
    }

    @Test
    void priorityAt_outOfBounds_fallback() {
        assertEquals(3, OverviewCalc.priorityAt(new int[]{1, 2}, 5, 3));
    }

    @Test
    void priorityAt_negativeIndex_fallback() {
        assertEquals(3, OverviewCalc.priorityAt(new int[]{1, 2}, -1, 3));
    }

    // ---- canApplyIncrease --------------------------------------------------

    @Test
    void canApplyIncrease_plusAllowed() {
        assertTrue(OverviewCalc.canApplyIncrease(+1, 2, 5, 3));
    }

    @Test
    void canApplyIncrease_plusBlockedAtCap() {
        assertFalse(OverviewCalc.canApplyIncrease(+1, 5, 5, 3));
    }

    @Test
    void canApplyIncrease_plusBlockedByZeroBudget() {
        assertFalse(OverviewCalc.canApplyIncrease(+1, 2, 5, 0));
    }

    @Test
    void canApplyIncrease_plusBlockedByZeroCap() {
        assertFalse(OverviewCalc.canApplyIncrease(+1, 0, 0, 5));
    }

    @Test
    void canApplyIncrease_minusAllowed() {
        assertTrue(OverviewCalc.canApplyIncrease(-1, 1, 5, 0));
    }

    @Test
    void canApplyIncrease_minusBlockedAtZero() {
        assertFalse(OverviewCalc.canApplyIncrease(-1, 0, 5, 3));
    }

    @Test
    void canApplyIncrease_zeroDeltaAlwaysFalse() {
        assertFalse(OverviewCalc.canApplyIncrease(0, 2, 5, 3));
    }

    // ---- expectedDisplay ---------------------------------------------------

    @Test
    void expectedDisplay_dashWhenRemainingAndCountZero() {
        // remaining > 0, count == 0 → "—"
        ExpectedStatsCalculator.Expected exp = new ExpectedStatsCalculator.Expected(50, 55, 0, 5);
        assertEquals("—", OverviewCalc.expectedDisplay(exp));
    }

    @Test
    void expectedDisplay_valueWhenCountNonZero() {
        ExpectedStatsCalculator.Expected exp = new ExpectedStatsCalculator.Expected(50, 55, 2, 5);
        assertEquals("55", OverviewCalc.expectedDisplay(exp));
    }

    @Test
    void expectedDisplay_valueWhenRemainingZero() {
        ExpectedStatsCalculator.Expected exp = new ExpectedStatsCalculator.Expected(50, 55, 0, 0);
        assertEquals("55", OverviewCalc.expectedDisplay(exp));
    }

    // ---- tierOrMax / decoratePerkName --------------------------------------

    @Test
    void tierOrMax_nonNull() {
        assertEquals(2, OverviewCalc.tierOrMax(2));
    }

    @Test
    void tierOrMax_null() {
        assertEquals(Integer.MAX_VALUE, OverviewCalc.tierOrMax(null));
    }

    @Test
    void decoratePerkName_withTier() {
        assertEquals("[T2] Iron Lungs", OverviewCalc.decoratePerkName(2, "Iron Lungs"));
    }

    @Test
    void decoratePerkName_noTier() {
        assertEquals("Iron Lungs", OverviewCalc.decoratePerkName(null, "Iron Lungs"));
    }

    // ---- frontlineKey ------------------------------------------------------

    @Test
    void frontlineKey_null_isFrontline() {
        assertEquals(0, OverviewCalc.frontlineKey(null));
    }

    @Test
    void frontlineKey_frontlineRole() {
        Role r = new Role();
        r.frontline = true;
        assertEquals(0, OverviewCalc.frontlineKey(r));
    }

    @Test
    void frontlineKey_nonFrontlineRole() {
        Role r = new Role();
        r.frontline = false;
        assertEquals(1, OverviewCalc.frontlineKey(r));
    }

    // ---- tierStyleClass ----------------------------------------------------

    @Test
    void tierStyleClass_namedWeapon() {
        assertEquals("tier-named", OverviewCalc.tierStyleClass("namedWeapon", null));
        assertEquals("tier-named", OverviewCalc.tierStyleClass("namedWeapon", 2));
    }

    @Test
    void tierStyleClass_tier1to3() {
        assertEquals("tier-1", OverviewCalc.tierStyleClass("weapon", 1));
        assertEquals("tier-2", OverviewCalc.tierStyleClass("weapon", 2));
        assertEquals("tier-3", OverviewCalc.tierStyleClass("weapon", 3));
    }

    @Test
    void tierStyleClass_nullTier() {
        assertNull(OverviewCalc.tierStyleClass("weapon", null));
    }

    @Test
    void tierStyleClass_outOfRangeTier() {
        assertNull(OverviewCalc.tierStyleClass("weapon", 0));
        assertNull(OverviewCalc.tierStyleClass("weapon", 4));
    }

    // ---- gatherPouches -----------------------------------------------------

    @Test
    void gatherPouches_alwaysReturns4() {
        List<InventorySlot> pouches = OverviewCalc.gatherPouches(null, null);
        assertEquals(4, pouches.size());
    }

    @Test
    void gatherPouches_includesSlot6FromEquipped() {
        InventorySlot[] equipped = new InventorySlot[7];
        for (int i = 0; i < 7; i++) equipped[i] = InventorySlot.empty(i);
        equipped[6] = InventorySlot.empty(6);
        equipped[6].itemId = "AAAA0001"; // distinguish from empty
        List<InventorySlot> pouches = OverviewCalc.gatherPouches(equipped, null);
        assertEquals(4, pouches.size());
        assertEquals("AAAA0001", pouches.get(0).itemId);
    }

    @Test
    void gatherPouches_includesExtraPouches() {
        InventorySlot extra = InventorySlot.empty(6);
        extra.itemId = "EXTRA001";
        List<InventorySlot> pouches = OverviewCalc.gatherPouches(null, List.of(extra));
        assertEquals(4, pouches.size());
        assertEquals("EXTRA001", pouches.get(0).itemId);
    }

    // ---- levelUpStatusText / isPartiallyConsumed ---------------------------

    @Test
    void levelUpStatusText_post11Adjusted() {
        assertEquals("Post-lv11 increases recorded ✓", OverviewCalc.levelUpStatusText(true, true));
    }

    @Test
    void levelUpStatusText_post11NotAdjusted() {
        assertEquals("Post-lv11 level-up detected", OverviewCalc.levelUpStatusText(true, false));
    }

    @Test
    void levelUpStatusText_pre11Adjusted() {
        assertEquals("Planned increases adjusted ✓", OverviewCalc.levelUpStatusText(false, true));
    }

    @Test
    void levelUpStatusText_pre11NotAdjusted() {
        assertEquals("No planned increases to adjust", OverviewCalc.levelUpStatusText(false, false));
    }

    @Test
    void isPartiallyConsumed_trueWhenLessThanLevels() {
        assertTrue(OverviewCalc.isPartiallyConsumed(1, 2));
    }

    @Test
    void isPartiallyConsumed_falseWhenEqual() {
        assertFalse(OverviewCalc.isPartiallyConsumed(2, 2));
    }

    @Test
    void isPartiallyConsumed_falseWhenNull() {
        assertFalse(OverviewCalc.isPartiallyConsumed(null, 2));
    }

    // ---- perkComparator ----------------------------------------------------

    @Test
    void perkComparator_TIER_sortsByTier() {
        List<String> ids = List.of("C", "A", "B");
        Comparator<String> comp = OverviewCalc.perkComparator(
                PerkSortMode.TIER,
                id -> switch (id) { case "A" -> 1; case "B" -> 2; default -> 3; },
                id -> 0L,
                id -> id);
        List<String> sorted = ids.stream().sorted(comp).toList();
        assertEquals("A", sorted.get(0));
        assertEquals("B", sorted.get(1));
        assertEquals("C", sorted.get(2));
    }

    @Test
    void perkComparator_TIER_nameBreaksTie() {
        // both tier 1; should sort alphabetically by name
        Comparator<String> comp = OverviewCalc.perkComparator(
                PerkSortMode.TIER,
                id -> 1,
                id -> 0L,
                id -> switch (id) { case "A" -> "Zebra"; default -> "Apple"; });
        List<String> sorted = List.of("A", "B").stream().sorted(comp).toList();
        assertEquals("B", sorted.get(0)); // "Apple" < "Zebra"
    }

    @Test
    void perkComparator_COMMONALITY_sortsByCountDesc() {
        Comparator<String> comp = OverviewCalc.perkComparator(
                PerkSortMode.COMMONALITY,
                id -> 1,
                id -> switch (id) { case "A" -> 10L; case "B" -> 5L; default -> 1L; },
                id -> id);
        List<String> sorted = List.of("C", "B", "A").stream().sorted(comp).toList();
        assertEquals("A", sorted.get(0)); // highest count first
    }

    @Test
    void perkComparator_OFF_naturalOrder() {
        // OFF mode returns Comparator.naturalOrder() → sorts alphabetically
        List<String> ids = List.of("C", "A", "B");
        Comparator<String> comp = OverviewCalc.perkComparator(
                PerkSortMode.OFF, id -> 1, id -> 0L, id -> id);
        List<String> sorted = ids.stream().sorted(comp).toList();
        assertEquals(List.of("A", "B", "C"), sorted);
    }
}
