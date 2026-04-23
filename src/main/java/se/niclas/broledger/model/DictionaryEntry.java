package se.niclas.broledger.model;

public class DictionaryEntry {
    public String name;
    public String type;
    public String subType;
    public String slot;
    public Integer icon;
    public String iconSet;

    // Weapon stats (used for generic weapons; named weapons carry these in save data)
    public Integer durability;
    public Integer fatigue;
    public Integer fatigueUse;
    public Integer damageMin;
    public Integer damageMax;
    public Double damageArmor;
    public Integer headChance;
    public Integer damageShield;
    public Integer hitBonus;
    public Double penetration;
    public Integer ammoMax;

    // Shield stats
    public Integer mDef;
    public Integer rDef;
}
