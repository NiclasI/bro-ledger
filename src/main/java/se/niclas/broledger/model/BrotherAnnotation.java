package se.niclas.broledger.model;

public class BrotherAnnotation {

    public String fingerprint;
    public String roleId;       // UUID of assigned Role, or null
    public Integer sortIndex;   // manual sort position; null = no manual order
    /** Per-stat planned level-up increases (lv 1–11), indexed by Stat.ordinal(). Null = all zero. */
    public int[] statIncreases;
    /** Per-stat post-lv11 increases already taken (+1 each), indexed by Stat.ordinal(). Null = all zero. */
    public int[] post11Increases;

    public BrotherAnnotation() {}

    public BrotherAnnotation(String fingerprint) {
        this.fingerprint = fingerprint;
    }
}
