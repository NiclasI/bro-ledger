package se.niclas.broledger.service;

import se.niclas.broledger.model.Brother;
import se.niclas.broledger.model.Stat;
import se.niclas.broledger.model.StatModifier;
import se.niclas.broledger.model.TraitEntry;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class StatModifierService {


    public record Breakdown(int base, int flat, int sumPct, int finalValue,
                            List<Contribution> contributions) {}

    public record Contribution(String hexId, String name,
                               Integer percentage, Integer points) {}

    private static final ObjectMapper MAPPER = JsonMapper.builder()
            .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
            .build();

    private static StatModifierService instance;

    private final Map<String, StatModifier> byHexId = new HashMap<>();

    private StatModifierService() {}

    public static StatModifierService getInstance() {
        if (instance == null) instance = new StatModifierService();
        return instance;
    }

    public void loadFromClasspath() throws IOException {
        try (InputStream is = StatModifierService.class
                .getResourceAsStream("/se/niclas/broledger/data/traits_perks.json")) {
            if (is == null) throw new IOException("traits_perks.json not found in classpath");
            loadFromStream(is);
        }
    }

    void loadFromStream(InputStream is) throws IOException {
        Wrapper w = MAPPER.readValue(is, Wrapper.class);
        byHexId.clear();
        if (w.modifiers != null) {
            for (StatModifier m : w.modifiers) {
                if (m.hexId != null) byHexId.put(m.hexId.toUpperCase(), m);
            }
        }
    }

    /** Returns the modifier for the given hex ID, or null if none. */
    public StatModifier byHexId(String hexId) {
        return hexId != null ? byHexId.get(hexId.toUpperCase()) : null;
    }

    /** Returns the perk tier (1–7) for the given hex ID, or null for traits / unknowns. */
    public Integer getTier(String hexId) {
        StatModifier m = byHexId(hexId);
        return m != null ? m.tier : null;
    }

    /**
     * Computes the modified stat value for a brother using the brother's own base stat.
     *
     * Order: flat additions first, then percentage applied to (base + flat).
     * Multiple percentages are additive. Rounding: (int) cast truncates toward 0.
     */
    public Breakdown compute(Brother b, Stat stat) {
        return computeWithBase(b, stat, b.stats[stat.statIndex()]);
    }

    /**
     * Computes a stat breakdown using an explicit base value instead of b.stats[statIdx].
     * Used to apply the trait/perk pipeline to a projected base (e.g. potential at level 11).
     */
    public Breakdown computeWithBase(Brother b, Stat stat, int baseOverride) {
        if (stat == null || b == null)
            return new Breakdown(baseOverride, 0, 0, baseOverride, List.of());
        String key = stat.jsonKey();

        List<Contribution> contributions = new ArrayList<>();
        int sumFlat = 0;
        int sumPct  = 0;

        for (String hexId : b.perkIds) {
            accumulate(hexId, key, contributions);
        }
        for (TraitEntry t : b.traits) {
            accumulate(t.id, key, contributions);
        }

        for (Contribution c : contributions) {
            if (c.points()     != null) sumFlat += c.points();
            if (c.percentage() != null) sumPct  += c.percentage();
        }

        int finalValue = applyModifiers(baseOverride, sumFlat, sumPct);
        return new Breakdown(baseOverride, sumFlat, sumPct, finalValue, contributions);
    }

    /**
     * Core stat modifier formula: flat bonuses applied first, then additive percentages, truncated to int.
     * {@code (base + flatBonus) × (1 + sumPercent / 100)}
     */
    static int applyModifiers(int base, int flatBonus, int sumPercent) {
        return (int) ((base + flatBonus) * (1 + sumPercent / 100.0));
    }

    private void accumulate(String hexId, String statKey, List<Contribution> out) {
        StatModifier mod = byHexId(hexId);
        if (mod == null || mod.effects == null) return;
        for (StatModifier.Effect e : mod.effects) {
            if (!statKey.equals(e.stat)) continue;
            String name = DictionaryService.getInstance().getName(hexId);
            out.add(new Contribution(hexId, name, e.percentage, e.points));
        }
    }

    // ---- Jackson wrapper ---------------------------------------------------

    public static class Wrapper {
        public List<StatModifier> modifiers = new ArrayList<>();
    }
}
