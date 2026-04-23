package se.niclas.broledger.model;

/**
 * The eight Brother stats, each carrying its two index conventions:
 * <ul>
 *   <li>{@link #statIndex()} — position in {@code Brother.stats[]} (read/display order)</li>
 *   <li>{@link #starIndex()} — position in {@code Brother.stars[]} (save-file / talent order,
 *       where Initiative is at 3, not 7)</li>
 * </ul>
 * Declaration order matches the UI display order (HP → Res → Fat → Init → MS → RS → MD → RD),
 * so {@code Stat.values()} can be iterated directly for display.
 */
public enum Stat {
    HEALTH        (0, 0, "health",        "HP",   "Health"),
    RESOLVE       (1, 1, "resolve",       "Res",  "Resolve"),
    FATIGUE       (2, 2, "fatigue",       "Fat",  "Fatigue"),
    INITIATIVE    (7, 3, "initiative",    "Init", "Initiative"),
    MELEE_SKILL   (3, 4, "melee_skill",   "MS",   "Melee Skill"),
    RANGED_SKILL  (4, 5, "ranged_skill",  "RS",   "Ranged Skill"),
    MELEE_DEFENSE (5, 6, "melee_defense", "MD",   "Melee Def"),
    RANGED_DEFENSE(6, 7, "ranged_defense","RD",   "Ranged Def");

    private final int    statIndex;
    private final int    starIndex;
    private final String jsonKey;
    private final String abbrev;
    private final String displayName;

    Stat(int statIndex, int starIndex, String jsonKey, String abbrev, String displayName) {
        this.statIndex   = statIndex;
        this.starIndex   = starIndex;
        this.jsonKey     = jsonKey;
        this.abbrev      = abbrev;
        this.displayName = displayName;
    }

    /** Index into {@code Brother.stats[]} (read/display order). */
    public int statIndex() { return statIndex; }

    /** Index into {@code Brother.stars[]} (save-file/talent order). */
    public int starIndex() { return starIndex; }

    /** Snake-case key used in {@code traits_perks.json} stat effects. */
    public String jsonKey() { return jsonKey; }

    /** Short display abbreviation used as column header and CSS id suffix. */
    public String abbrev() { return abbrev; }

    /** Human-readable label used in the role-manager stat editor. */
    public String displayName() { return displayName; }
}
