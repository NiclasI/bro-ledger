package se.niclas.broledger.model;

/** Runtime stats parsed from the save for one inventory item. */
public class ItemStats {
    // Common
    public float durability;
    public float maxDurability;
    public int icon;
    public String iconSet;
    public String repair;      // raw 1-byte hex
    public int magicNumber;

    // Armor / helmet
    public float fatigue;
    public float baseFatigue;
    public float baseDurability;
    public String attachment;       // 4-byte hex, "00000000" = none
    public int attachmentIcon;
    public String house;            // 1-byte hex (noble armors/shields)

    // Named item
    public String name;

    // Weapon
    public int damageMin;
    public int damageMax;
    public float damageArmor;
    public int headChance;
    public int damageShield;
    public int hitBonus;
    public float penetration;
    public int fatigueUse;
    public int ammo;
    public int ammoMax;

    // Shield
    public int mDef;
    public int rDef;

    // Auxiliary
    public float amount;    // provisions
    public float expiry;    // provisions
    public int bought;      // provisions / commodity

    // Special
    public String description;  // davkul armor

    public boolean hasAttachment() {
        return attachment != null && !attachment.equals("00000000");
    }
}
