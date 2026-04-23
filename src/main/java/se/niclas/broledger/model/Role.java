package se.niclas.broledger.model;

public class Role {

    public String  id;
    public String  name     = "";
    public boolean frontline = true;

    // Per-stat threshold, 0 = not set. StatOrder indexing (same as Brother.stats).
    public int[] targetStats = new int[8];

    // Per-stat priority: 1=P1, 2=P2, 3=P3 (default). StatOrder indexing.
    public int[] priority = new int[]{3, 3, 3, 3, 3, 3, 3, 3};

    public Role() {}

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
