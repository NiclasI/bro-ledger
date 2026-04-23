package se.niclas.broledger.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import se.niclas.broledger.model.Brother;
import se.niclas.broledger.model.Stat;
import se.niclas.broledger.model.TraitEntry;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.*;

class StatModifierServiceTest {

    private StatModifierService svc;
    private Brother brother;

    @BeforeEach
    void setUp() {
        svc = StatModifierService.getInstance();
        brother = new Brother();
        brother.stats = new int[]{60, 40, 80, 55, 30, 20, 15, 70};
    }

    private void load(String json) throws Exception {
        svc.loadFromStream(new ByteArrayInputStream(json.getBytes(StandardCharsets.UTF_8)));
    }

    @Test
    void noModifiers_returnsBase() throws Exception {
        load("{\"modifiers\":[]}");
        var bd = svc.compute(brother, Stat.HEALTH);
        assertEquals(60, bd.finalValue());
        assertTrue(bd.contributions().isEmpty());
    }

    @Test
    void flatOnly_addsPoints() throws Exception {
        brother.perkIds.add("AAAAAAAA");
        load("""
            {"modifiers":[{"hexId":"AAAAAAAA","effects":[{"stat":"health","points":10}]}]}
            """);
        var bd = svc.compute(brother, Stat.HEALTH);
        assertEquals(70, bd.finalValue());
        assertEquals(10, bd.flat());
        assertEquals(0,  bd.sumPct());
        assertEquals(1,  bd.contributions().size());
    }

    @Test
    void flatNegative_subtractsPoints() throws Exception {
        brother.perkIds.add("AAAAAAAA");
        load("""
            {"modifiers":[{"hexId":"AAAAAAAA","effects":[{"stat":"health","points":-10}]}]}
            """);
        var bd = svc.compute(brother, Stat.HEALTH);
        assertEquals(50, bd.finalValue());
    }

    @Test
    void percentageOnly_positive() throws Exception {
        brother.perkIds.add("AAAAAAAA");
        // base 60, +25% → (int)(60 * 1.25) = 75
        load("""
            {"modifiers":[{"hexId":"AAAAAAAA","effects":[{"stat":"health","percentage":25}]}]}
            """);
        var bd = svc.compute(brother, Stat.HEALTH);
        assertEquals(75, bd.finalValue());
    }

    @Test
    void percentageOnly_negative_truncatesTowardZero() throws Exception {
        brother.perkIds.add("AAAAAAAA");
        // base 10, -25% → (int)(10 * 0.75) = 7 (truncate, not floor)
        brother.stats[Stat.HEALTH.statIndex()] = 10;
        load("""
            {"modifiers":[{"hexId":"AAAAAAAA","effects":[{"stat":"health","percentage":-25}]}]}
            """);
        var bd = svc.compute(brother, Stat.HEALTH);
        assertEquals(7, bd.finalValue());
    }

    @Test
    void flatThenPercentage_flatFirst() throws Exception {
        brother.perkIds.add("AAAAAAAA");
        // base 60, +5 flat → 65, then +25% → (int)(65 * 1.25) = 81
        load("""
            {"modifiers":[{"hexId":"AAAAAAAA","effects":[{"stat":"health","points":5},{"stat":"health","percentage":25}]}]}
            """);
        var bd = svc.compute(brother, Stat.HEALTH);
        assertEquals(81, bd.finalValue());
        assertEquals(5,  bd.flat());
        assertEquals(25, bd.sumPct());
    }

    @Test
    void multipleModifiers_percentagesAdditive() throws Exception {
        brother.perkIds.add("AAAAAAAA");
        brother.perkIds.add("BBBBBBBB");
        // base 60, +25% + +10% = +35% → (int)(60 * 1.35) = 81
        load("""
            {"modifiers":[
              {"hexId":"AAAAAAAA","effects":[{"stat":"health","percentage":25}]},
              {"hexId":"BBBBBBBB","effects":[{"stat":"health","percentage":10}]}
            ]}
            """);
        var bd = svc.compute(brother, Stat.HEALTH);
        assertEquals(81, bd.finalValue());
        assertEquals(35, bd.sumPct());
        assertEquals(2,  bd.contributions().size());
    }

    @Test
    void traitContributes() throws Exception {
        brother.traits.add(new TraitEntry("CCCCCCCC", null));
        load("""
            {"modifiers":[{"hexId":"CCCCCCCC","effects":[{"stat":"resolve","points":5}]}]}
            """);
        var bd = svc.compute(brother, Stat.RESOLVE);
        assertEquals(45, bd.finalValue());
    }

    @Test
    void unrelatedStat_ignored() throws Exception {
        brother.perkIds.add("AAAAAAAA");
        // modifier only affects resolve, not health
        load("""
            {"modifiers":[{"hexId":"AAAAAAAA","effects":[{"stat":"resolve","points":5}]}]}
            """);
        var bd = svc.compute(brother, Stat.HEALTH);
        assertEquals(60, bd.finalValue());
        assertTrue(bd.contributions().isEmpty());
    }

    @Test
    void truncationBoundary_positive() throws Exception {
        brother.perkIds.add("AAAAAAAA");
        // base 7, +33% → 7 * 1.33 = 9.31 → (int) = 9
        brother.stats[Stat.HEALTH.statIndex()] = 7;
        load("""
            {"modifiers":[{"hexId":"AAAAAAAA","effects":[{"stat":"health","percentage":33}]}]}
            """);
        assertEquals(9, svc.compute(brother, Stat.HEALTH).finalValue());
    }

    @Test
    void hexIdCaseInsensitive() throws Exception {
        brother.perkIds.add("aaaaaaaa");
        load("""
            {"modifiers":[{"hexId":"AAAAAAAA","effects":[{"stat":"health","points":3}]}]}
            """);
        assertEquals(63, svc.compute(brother, Stat.HEALTH).finalValue());
    }

    @Test
    void loadFromClasspath_doesNotThrow() {
        assertDoesNotThrow(() -> svc.loadFromClasspath());
    }
}
