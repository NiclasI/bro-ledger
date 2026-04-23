package se.niclas.broledger.parser;

enum ItemType {
    GENERIC_WEAPON("genericWeapon"),
    NAMED_WEAPON  ("namedWeapon"),
    GENERIC_ARMOR ("genericArmor"),
    NAMED_ARMOR   ("namedArmor"),
    GENERIC_HELMET("genericHelmet"),
    NAMED_HELMET  ("namedHelmet"),
    GENERIC_SHIELD("genericShield"),
    NAMED_SHIELD  ("namedShield"),
    AUXILIARY     ("auxiliary"),
    ATTACHMENT    ("attachment"),
    UNKNOWN       ("");

    final String key;

    ItemType(String key) { this.key = key; }

    static ItemType fromString(String s) {
        if (s != null) {
            for (ItemType t : values()) {
                if (t.key.equals(s)) return t;
            }
        }
        return UNKNOWN;
    }
}
