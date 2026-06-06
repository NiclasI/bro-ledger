package se.niclas.broledger.model;

public enum PerkPlanStatus {
    NOT, OPTIONAL, PLANNED;

    public PerkPlanStatus next() {
        return switch (this) {
            case NOT      -> PLANNED;
            case PLANNED  -> OPTIONAL;
            case OPTIONAL -> NOT;
        };
    }

    public static PerkPlanStatus fromString(String s) {
        if ("OPTIONAL".equalsIgnoreCase(s)) return OPTIONAL;
        if ("PLANNED".equalsIgnoreCase(s))  return PLANNED;
        return NOT;
    }
}
