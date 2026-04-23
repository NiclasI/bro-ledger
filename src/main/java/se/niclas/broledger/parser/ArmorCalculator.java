package se.niclas.broledger.parser;

import se.niclas.broledger.model.Brother;
import se.niclas.broledger.model.InventorySlot;
import se.niclas.broledger.model.ItemStats;

/** Derives footer stats from equipped items. */
public class ArmorCalculator {

    private static final int SLOT_WEAPON = 0;
    private static final int SLOT_SHIELD = 1;
    private static final int SLOT_BODY   = 2;
    private static final int SLOT_HELMET = 3;

    /** Combined durability of body + helmet. */
    public static int effectiveArmor(Brother b) {
        return (int) durability(b, SLOT_BODY) + (int) durability(b, SLOT_HELMET);
    }

    /** Sum of armor baseFatigue penalties + weapon fatigueUse. */
    public static int totalFatiguePenalty(Brother b) {
        return (int) baseFatigue(b, SLOT_BODY)
             + (int) baseFatigue(b, SLOT_HELMET)
             + weaponFatigue(b);
    }

    /** "min – max" damage range for the weapon slot, or "—" if empty. */
    public static String weaponDamageRange(Brother b) {
        InventorySlot slot = slot(b, SLOT_WEAPON);
        if (slot == null || slot.stats == null) return "—";
        ItemStats s = slot.stats;
        if (s.damageMin == 0 && s.damageMax == 0) return "—";
        return s.damageMin + " – " + s.damageMax;
    }

    // ---- helpers ----

    private static float durability(Brother b, int idx) {
        InventorySlot s = slot(b, idx);
        return (s != null && s.stats != null) ? s.stats.durability : 0f;
    }

    private static float baseFatigue(Brother b, int idx) {
        InventorySlot s = slot(b, idx);
        return (s != null && s.stats != null) ? s.stats.baseFatigue : 0f;
    }

    private static int weaponFatigue(Brother b) {
        InventorySlot s = slot(b, SLOT_WEAPON);
        return (s != null && s.stats != null) ? s.stats.fatigueUse : 0;
    }

    private static InventorySlot slot(Brother b, int idx) {
        if (b.equippedSlots == null || idx >= b.equippedSlots.length) return null;
        InventorySlot s = b.equippedSlots[idx];
        return (s == null || s.empty) ? null : s;
    }
}
