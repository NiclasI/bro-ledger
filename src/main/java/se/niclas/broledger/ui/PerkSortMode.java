package se.niclas.broledger.ui;

enum PerkSortMode {
    OFF("Perk sort: Off"),
    COMMONALITY("Perk sort: Commonality"),
    TIER("Perk sort: Tier"),
    TIER_THEN_COMMON("Perk sort: Tier+Common");

    final String label;

    PerkSortMode(String label) { this.label = label; }

    PerkSortMode next() {
        PerkSortMode[] vals = values();
        return vals[(ordinal() + 1) % vals.length];
    }
}
