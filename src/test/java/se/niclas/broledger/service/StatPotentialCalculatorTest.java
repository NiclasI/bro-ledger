package se.niclas.broledger.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import se.niclas.broledger.model.Brother;
import se.niclas.broledger.model.Stat;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.*;

class StatPotentialCalculatorTest {

    // ---- rangeForStars: wiki table coverage --------------------------------

    @ParameterizedTest(name = "stat={0} stars={1} -> {2}-{3}")
    @CsvSource({
        // HP (STAT_HEALTH=0)
        "0,0, 2,4",  "0,1, 3,4",  "0,2, 4,4",  "0,3, 4,5",
        // Resolve (STAT_RESOLVE=1)
        "1,0, 2,4",  "1,1, 3,4",  "1,2, 4,4",  "1,3, 4,5",
        // Fatigue (STAT_FATIGUE=2)
        "2,0, 2,4",  "2,1, 3,4",  "2,2, 4,4",  "2,3, 4,5",
        // Melee Skill (STAT_MELEE_SKILL=3)
        "3,0, 1,3",  "3,1, 2,3",  "3,2, 3,3",  "3,3, 3,4",
        // Ranged Skill (STAT_RANGED_SKILL=4)
        "4,0, 2,4",  "4,1, 3,4",  "4,2, 4,4",  "4,3, 4,5",
        // Melee Defense (STAT_MELEE_DEFENSE=5)
        "5,0, 1,3",  "5,1, 2,3",  "5,2, 3,3",  "5,3, 3,4",
        // Ranged Defense (STAT_RANGED_DEFENSE=6)
        "6,0, 2,4",  "6,1, 3,4",  "6,2, 4,4",  "6,3, 4,5",
        // Initiative (STAT_INITIATIVE=7)
        "7,0, 3,5",  "7,1, 4,5",  "7,2, 5,5",  "7,3, 5,6",
    })
    void rangeForStars_matchesWikiTable(int statIdx, int stars, int expMin, int expMax) {
        StatPotentialCalculator.Range r = StatPotentialCalculator.rangeForStars(statIdx, stars);
        assertNotNull(r);
        assertEquals(expMin, r.min(), "min mismatch for stat=" + statIdx + " stars=" + stars);
        assertEquals(expMax, r.max(), "max mismatch for stat=" + statIdx + " stars=" + stars);
    }

    // ---- expectedBasePotential --------------------------------------------

    @Test
    void expectedBasePotential_hp_0star_level1() {
        // HP, 0★, at level 1: 10 rolls × avg 3.0 = +30
        int result = StatPotentialCalculator.expectedBasePotential(50, Stat.HEALTH, 0, 1);
        assertEquals(80, result);
    }

    @Test
    void expectedBasePotential_hp_3star_level1() {
        // HP, 3★, at level 1: range 4-5, avg 4.5 → 10 × 4.5 = 45, round(50+45)=95
        int result = StatPotentialCalculator.expectedBasePotential(50, Stat.HEALTH, 3, 1);
        assertEquals(95, result);
    }

    @Test
    void expectedBasePotential_msSkill_1star_level6() {
        // Melee Skill, 1★, level 6: 5 rolls × avg 2.5 = +12.5 → round(20+12.5)=33
        int result = StatPotentialCalculator.expectedBasePotential(20, Stat.MELEE_SKILL, 1, 6);
        assertEquals(33, result);
    }

    @Test
    void expectedBasePotential_clampsAtLevel11() {
        int result = StatPotentialCalculator.expectedBasePotential(70, Stat.MELEE_SKILL, 3, 11);
        assertEquals(70, result);
    }

    @Test
    void expectedBasePotential_clampsAboveLevel11() {
        int result = StatPotentialCalculator.expectedBasePotential(70, Stat.MELEE_SKILL, 3, 15);
        assertEquals(70, result);
    }

    // ---- Initiative uses STAT_INITIATIVE=7 (statOrder) but
    //      STAR_INITIATIVE=3 (talentOrder) — compute() must accept both ------

    @Test
    void compute_initiativeIndexing_usesCorrectStarOrder() {
        Brother b = new Brother();
        b.levelTotal = 1;
        b.stats[Stat.INITIATIVE.statIndex()] = 80;
        b.stars[Stat.INITIATIVE.starIndex()] = 2; // 2★ Initiative

        StatPotentialCalculator.Potential p =
                StatPotentialCalculator.compute(b, Stat.INITIATIVE);

        // 2★ Initiative range: 5-5 (avg 5.0); 10 rolls → +50
        assertEquals(130, p.basePotential(), "expected base 80+50=130");
        assertEquals(10, p.remainingLevels());
    }

    // ---- compute() applies trait/perk modifiers --------------------------

    @Test
    void compute_appliesModifiers() throws Exception {
        StatModifierService svc = StatModifierService.getInstance();
        String json = """
                {"modifiers":[{"hexId":"AABBCCDD","effects":[{"stat":"health","percentage":25}]}]}
                """;
        svc.loadFromStream(new ByteArrayInputStream(json.getBytes(StandardCharsets.UTF_8)));

        Brother b = new Brother();
        b.levelTotal = 1;
        b.stats[Stat.HEALTH.statIndex()] = 50;
        b.stars[Stat.HEALTH.starIndex()] = 3; // 3★: range 4-5, avg 4.5 → expectedBase = 95
        b.perkIds.add("AABBCCDD");

        StatPotentialCalculator.Potential p =
                StatPotentialCalculator.compute(b, Stat.HEALTH);

        // finalPotential = (int)(95 * 1.25) = 118
        assertEquals(95, p.basePotential());
        assertEquals(118, p.finalPotential());
        assertEquals(10, p.remainingLevels());
    }

    // ---- remainingLevels = 0 at level 11 ----------------------------------

    @Test
    void compute_atLevel11_remainingZero() {
        Brother b = new Brother();
        b.levelTotal = 11;
        b.stats[Stat.MELEE_SKILL.statIndex()] = 65;

        StatPotentialCalculator.Potential p =
                StatPotentialCalculator.compute(b, Stat.MELEE_SKILL);

        assertEquals(0, p.remainingLevels());
        assertEquals(65, p.basePotential());
        assertEquals(65, p.minBasePotential());
        assertEquals(65, p.maxBasePotential());
    }

    // ---- new pure helpers --------------------------------------------------

    @Test
    void applyStars_zeroStars_identity() {
        StatPotentialCalculator.Range base = new StatPotentialCalculator.Range(2, 4);
        StatPotentialCalculator.Range r = StatPotentialCalculator.applyStars(base, 0);
        assertEquals(2, r.min());
        assertEquals(4, r.max());
    }

    @Test
    void applyStars_oneStar_raisesMin() {
        StatPotentialCalculator.Range base = new StatPotentialCalculator.Range(2, 4);
        StatPotentialCalculator.Range r = StatPotentialCalculator.applyStars(base, 1);
        assertEquals(3, r.min());
        assertEquals(4, r.max());
    }

    @Test
    void applyStars_threeStar_raisesMinAndMax() {
        StatPotentialCalculator.Range base = new StatPotentialCalculator.Range(2, 4);
        StatPotentialCalculator.Range r = StatPotentialCalculator.applyStars(base, 3);
        assertEquals(4, r.min()); // +2
        assertEquals(5, r.max()); // +1
    }

    @Test
    void applyStars_clampsBelowZero() {
        StatPotentialCalculator.Range base = new StatPotentialCalculator.Range(2, 4);
        StatPotentialCalculator.Range r = StatPotentialCalculator.applyStars(base, -5);
        assertEquals(2, r.min());
        assertEquals(4, r.max());
    }

    @Test
    void applyStars_clampsAboveThree() {
        StatPotentialCalculator.Range base = new StatPotentialCalculator.Range(2, 4);
        // clamped to 3
        StatPotentialCalculator.Range r3 = StatPotentialCalculator.applyStars(base, 3);
        StatPotentialCalculator.Range r9 = StatPotentialCalculator.applyStars(base, 9);
        assertEquals(r3.min(), r9.min());
        assertEquals(r3.max(), r9.max());
    }

    @Test
    void projectBase_addsRemainingTimesRoll() {
        assertEquals(60, StatPotentialCalculator.projectBase(50, 5, 2)); // 50+5*2=60
        assertEquals(50, StatPotentialCalculator.projectBase(50, 0, 3)); // no levels left
    }

    @Test
    void projectExpectedBase_roundsCorrectly() {
        StatPotentialCalculator.Range r = new StatPotentialCalculator.Range(2, 4); // mean=3.0
        // 50 + 5 * 3.0 = 65.0 → round → 65
        assertEquals(65, StatPotentialCalculator.projectExpectedBase(50, 5, r));
    }

    @Test
    void projectExpectedBase_matchesRangeForStars0Stars() {
        // HEALTH 0★: range(2,4), mean=3. base=40, remaining=4 → round(40+12) = 52
        StatPotentialCalculator.Range r = StatPotentialCalculator.rangeForStars(Stat.HEALTH.statIndex(), 0);
        assertEquals(52, StatPotentialCalculator.projectExpectedBase(40, 4, r));
    }
}
