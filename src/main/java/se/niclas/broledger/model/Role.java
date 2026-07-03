package se.niclas.broledger.model;

public class Role {

    public String  id;
    public String  name     = "";
    public boolean frontline = true;

    // Per-stat threshold, 0 = not set. StatOrder indexing (same as Brother.stats).
    public int[] targetStats = new int[8];

    // Per-stat priority: 1=P1, 2=P2, 3=P3 (default). StatOrder indexing.
    public int[] priority = new int[]{3, 3, 3, 3, 3, 3, 3, 3};

    // Perk plan template: hex ID → "OPTIONAL" | "PLANNED". Null/absent = NOT. Null map = no template.
    public java.util.Map<String, String> perkPlanTemplate;

    // Provenance of an imported role: the share code it was fetched with and the sender's
    // role id inside that pack. Enables update-in-place when the same pack is re-imported.
    // Null for locally created roles. Never published — RolePack.forSharing strips it, so
    // codes of previously imported packs can't leak into new packs.
    public String sourcePackCode;
    public String sourceRoleId;

    public Role() {}

    /**
     * Copies shareable content (everything except {@link #id} and provenance) from
     * {@code src} into {@code dest}, deep-copying arrays and the perk-plan template.
     */
    public static void copyContentInto(Role src, Role dest) {
        dest.name        = src.name;
        dest.frontline   = src.frontline;
        dest.targetStats = src.targetStats != null ? src.targetStats.clone() : new int[8];
        dest.priority    = src.priority != null ? src.priority.clone()
                                                : new int[]{3, 3, 3, 3, 3, 3, 3, 3};
        dest.perkPlanTemplate = src.perkPlanTemplate != null
                ? new java.util.LinkedHashMap<>(src.perkPlanTemplate) : null;
    }

    @Override
    public String toString() {
        return name != null ? name : "";
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Role r)) return false;
        return id != null && id.equals(r.id);
    }

    @Override
    public int hashCode() {
        return id != null ? id.hashCode() : 0;
    }
}
