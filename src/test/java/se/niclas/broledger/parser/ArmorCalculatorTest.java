package se.niclas.broledger.parser;

import org.junit.jupiter.api.Test;
import se.niclas.broledger.model.Brother;
import se.niclas.broledger.model.InventorySlot;
import se.niclas.broledger.model.ItemStats;

import static org.junit.jupiter.api.Assertions.*;

class ArmorCalculatorTest {

    // ---- helpers ----

    private static Brother emptyBrother() {
        return new Brother(); // equippedSlots already initialised to empty
    }

    private static void equip(Brother b, int slot, float durability, float baseFatigue) {
        InventorySlot s = b.equippedSlots[slot];
        s.empty = false;
        s.itemId = "DEADBEEF";
        s.stats = new ItemStats();
        s.stats.durability = durability;
        s.stats.baseFatigue = baseFatigue;
    }

    private static void equipWeapon(Brother b, int dmgMin, int dmgMax, int fatigueUse) {
        InventorySlot s = b.equippedSlots[0]; // weapon slot
        s.empty = false;
        s.itemId = "DEADBEEF";
        s.stats = new ItemStats();
        s.stats.damageMin = dmgMin;
        s.stats.damageMax = dmgMax;
        s.stats.fatigueUse = fatigueUse;
    }

    // ---- effectiveArmor ----

    @Test
    void effectiveArmorZeroWhenNothingEquipped() {
        assertEquals(0, ArmorCalculator.effectiveArmor(emptyBrother()));
    }

    @Test
    void effectiveArmorSumsBodyAndHelmet() {
        Brother b = emptyBrother();
        equip(b, 2, 150f, 0f); // body
        equip(b, 3, 60f,  0f); // helmet
        assertEquals(210, ArmorCalculator.effectiveArmor(b));
    }

    @Test
    void effectiveArmorIgnoresWeaponSlot() {
        Brother b = emptyBrother();
        equipWeapon(b, 10, 20, 5);
        equip(b, 2, 100f, 0f);
        assertEquals(100, ArmorCalculator.effectiveArmor(b));
    }

    // ---- totalFatiguePenalty ----

    @Test
    void fatiguePenaltyZeroWhenEmpty() {
        assertEquals(0, ArmorCalculator.totalFatiguePenalty(emptyBrother()));
    }

    @Test
    void fatiguePenaltySumsArmorAndWeapon() {
        Brother b = emptyBrother();
        equip(b, 2, 150f, 12f); // body: baseFatigue=12
        equip(b, 3, 60f,   6f); // helmet: baseFatigue=6
        equipWeapon(b, 10, 20, 8); // weapon: fatigueUse=8
        assertEquals(26, ArmorCalculator.totalFatiguePenalty(b));
    }

    // ---- weaponDamageRange ----

    @Test
    void damageRangeDashWhenNoWeapon() {
        assertEquals("—", ArmorCalculator.weaponDamageRange(emptyBrother()));
    }

    @Test
    void damageRangeDashWhenZeroDamage() {
        Brother b = emptyBrother();
        equipWeapon(b, 0, 0, 5);
        assertEquals("—", ArmorCalculator.weaponDamageRange(b));
    }

    @Test
    void damageRangeFormattedCorrectly() {
        Brother b = emptyBrother();
        equipWeapon(b, 30, 50, 8);
        assertEquals("30 – 50", ArmorCalculator.weaponDamageRange(b));
    }

    // ---- pure helpers (package-private) ----

    @Test
    void hasStats_nullSlot() {
        assertFalse(ArmorCalculator.hasStats(null));
    }

    @Test
    void hasStats_emptySlot() {
        InventorySlot s = InventorySlot.empty(0);
        assertFalse(ArmorCalculator.hasStats(s));
    }

    @Test
    void hasStats_slotWithoutStats() {
        InventorySlot s = InventorySlot.empty(0);
        s.empty = false;
        assertFalse(ArmorCalculator.hasStats(s));
    }

    @Test
    void hasStats_slotWithStats() {
        InventorySlot s = InventorySlot.empty(0);
        s.empty = false;
        s.stats = new ItemStats();
        assertTrue(ArmorCalculator.hasStats(s));
    }

    @Test
    void isUsableSlot_null() {
        assertFalse(ArmorCalculator.isUsableSlot(null));
    }

    @Test
    void isUsableSlot_emptySlot() {
        assertFalse(ArmorCalculator.isUsableSlot(InventorySlot.empty(0)));
    }

    @Test
    void isUsableSlot_nonEmpty() {
        InventorySlot s = InventorySlot.empty(0);
        s.empty = false;
        assertTrue(ArmorCalculator.isUsableSlot(s));
    }

    @Test
    void hasNoDamage_trueWhenBothZero() {
        ItemStats s = new ItemStats();
        s.damageMin = 0;
        s.damageMax = 0;
        assertTrue(ArmorCalculator.hasNoDamage(s));
    }

    @Test
    void hasNoDamage_falseWhenMinNonZero() {
        ItemStats s = new ItemStats();
        s.damageMin = 5;
        s.damageMax = 0;
        assertFalse(ArmorCalculator.hasNoDamage(s));
    }

    @Test
    void formatDamageRange_dashWhenBothZero() {
        assertEquals("—", ArmorCalculator.formatDamageRange(0, 0));
    }

    @Test
    void formatDamageRange_rangeString() {
        assertEquals("15 – 25", ArmorCalculator.formatDamageRange(15, 25));
    }
}
