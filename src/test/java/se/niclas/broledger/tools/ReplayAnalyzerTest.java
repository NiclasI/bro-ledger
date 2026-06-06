package se.niclas.broledger.tools;

import org.junit.jupiter.api.Test;
import se.niclas.broledger.model.Brother;
import se.niclas.broledger.model.Stat;
import se.niclas.broledger.service.AnnotationService.LevelUpEvent;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for the static/package-private helpers in {@link ReplayAnalyzer}.
 *
 * These tests cover the pure calculation and formatting kernels without
 * needing real .sav files or a DictionaryService.
 */
class ReplayAnalyzerTest {

    // ---- displayTs ---------------------------------------------------------

    @Test
    void displayTs_parsesKnownStem() {
        String ts = ReplayAnalyzer.displayTs("20260613T120000_000Z");
        assertTrue(ts.contains("2026"), "year must appear");
        assertTrue(ts.contains("06"),   "month must appear");
        assertTrue(ts.contains("13"),   "day must appear");
        assertTrue(ts.contains("12"),   "hour must appear");
        assertFalse(ts.equals("20260613T120000_000Z"),
                "should have been reformatted, not returned as-is");
    }

    @Test
    void displayTs_returnsInputOnBadFormat() {
        assertEquals("not-a-timestamp", ReplayAnalyzer.displayTs("not-a-timestamp"));
    }

    @Test
    void displayTs_handlesNullGracefully() {
        assertDoesNotThrow(() -> ReplayAnalyzer.displayTs(null));
    }

    // ---- stemOf ------------------------------------------------------------

    @Test
    void stemOf_stripsExtension() {
        Path p = Path.of("20260613T120000_000Z.sav");
        assertEquals("20260613T120000_000Z", ReplayAnalyzer.stemOf(p));
    }

    @Test
    void stemOf_noExtension() {
        Path p = Path.of("somefile");
        assertEquals("somefile", ReplayAnalyzer.stemOf(p));
    }

    // ---- isValid -----------------------------------------------------------

    @Test
    void isValid_rejectsBrotherWithNullName() {
        Brother b = new Brother();
        b.name     = null;
        b.levelTotal = 5;
        b.backgroundHexId = "AABBCCDD";
        assertFalse(ReplayAnalyzer.isValid(b));
    }

    @Test
    void isValid_rejectsBrotherWithLevel0() {
        Brother b = new Brother();
        b.name       = "Gorm";
        b.levelTotal = 0;
        b.backgroundHexId = "AABBCCDD";
        assertFalse(ReplayAnalyzer.isValid(b));
    }

    @Test
    void isValid_rejectsBrotherWithBlankName() {
        Brother b = new Brother();
        b.name        = "   ";
        b.levelTotal  = 5;
        b.backgroundHexId = "AABBCCDD";
        assertFalse(ReplayAnalyzer.isValid(b));
    }

    @Test
    void isValid_rejectsBrotherWithTooLongName() {
        Brother b = new Brother();
        b.name        = "X".repeat(61);
        b.levelTotal  = 5;
        b.backgroundHexId = "AABBCCDD";
        assertFalse(ReplayAnalyzer.isValid(b));
    }

    @Test
    void isValid_rejectsBrotherWithNullBackgroundHexId() {
        Brother b = new Brother();
        b.name            = "Gorm";
        b.levelTotal      = 5;
        b.backgroundHexId = null;
        assertFalse(ReplayAnalyzer.isValid(b));
    }

    // ---- detectAnomalies ---------------------------------------------------

    @Test
    void detectAnomalies_flagsNegativeStatDelta() {
        Brother ob = brother("A", "Sigrid", new int[8]);
        Brother nb = brother("A", "Sigrid", new int[8]);
        ob.stats[Stat.MELEE_SKILL.statIndex()] = 50;
        nb.stats[Stat.MELEE_SKILL.statIndex()] = 45; // decreased

        List<String> anomalies = new ArrayList<>();
        ReplayAnalyzer.detectAnomalies(List.of(ob), List.of(nb), anomalies);

        assertFalse(anomalies.isEmpty(), "negative stat delta must be flagged");
        assertTrue(anomalies.get(0).contains("MS") || anomalies.get(0).contains("decreased"));
    }

    @Test
    void detectAnomalies_noAnomalyForPositiveDelta() {
        Brother ob = brother("B", "Gorm", new int[8]);
        Brother nb = brother("B", "Gorm", new int[8]);
        ob.stats[Stat.HEALTH.statIndex()] = 60;
        nb.stats[Stat.HEALTH.statIndex()] = 63;

        List<String> anomalies = new ArrayList<>();
        ReplayAnalyzer.detectAnomalies(List.of(ob), List.of(nb), anomalies);

        assertTrue(anomalies.isEmpty());
    }

    @Test
    void detectAnomalies_noAnomalyForZeroDelta() {
        int[] stats = new int[8];
        Brother ob = brother("C", "Brunhilde", stats.clone());
        Brother nb = brother("C", "Brunhilde", stats.clone());

        List<String> anomalies = new ArrayList<>();
        ReplayAnalyzer.detectAnomalies(List.of(ob), List.of(nb), anomalies);

        assertTrue(anomalies.isEmpty());
    }

    // ---- reportRosterChanges -----------------------------------------------

    @Test
    void reportRosterChanges_flagsBrotherCountMismatch() {
        Brother ob = brother("A", "Gorm", new int[8]);
        Brother nb1 = brother("A", "Gorm", new int[8]);
        Brother nb2 = brother("B", "Wulf", new int[8]);

        List<String> anomalies = new ArrayList<>();
        ByteArrayOutputStream buf = new ByteArrayOutputStream();
        ReplayAnalyzer.reportRosterChanges(
                List.of(ob), List.of(nb1, nb2), new PrintStream(buf), anomalies);

        assertFalse(anomalies.isEmpty(), "brother-count change must be flagged");
    }

    @Test
    void reportRosterChanges_noFlagWhenCountSame() {
        Brother ob = brother("A", "Gorm", new int[8]);
        Brother nb = brother("A", "Gorm", new int[8]);

        List<String> anomalies = new ArrayList<>();
        ByteArrayOutputStream buf = new ByteArrayOutputStream();
        ReplayAnalyzer.reportRosterChanges(
                List.of(ob), List.of(nb), new PrintStream(buf), anomalies);

        assertTrue(anomalies.isEmpty());
    }

    @Test
    void reportRosterChanges_printsAddedBrother() {
        Brother ob  = brother("A", "Gorm", new int[8]);
        Brother nb1 = brother("A", "Gorm", new int[8]);
        Brother nb2 = brother("B", "Urist", new int[8]);

        ByteArrayOutputStream buf = new ByteArrayOutputStream();
        ReplayAnalyzer.reportRosterChanges(
                List.of(ob), List.of(nb1, nb2), new PrintStream(buf), new ArrayList<>());

        assertTrue(buf.toString().contains("Urist"), "added brother name must appear in output");
        assertTrue(buf.toString().contains("+"),     "'+ new' marker expected");
    }

    @Test
    void reportRosterChanges_printsRemovedBrother() {
        Brother ob  = brother("A", "Gorm", new int[8]);
        Brother ob2 = brother("B", "Tobi", new int[8]);
        Brother nb  = brother("A", "Gorm", new int[8]); // Tobi removed

        ByteArrayOutputStream buf = new ByteArrayOutputStream();
        ReplayAnalyzer.reportRosterChanges(
                List.of(ob, ob2), List.of(nb), new PrintStream(buf), new ArrayList<>());

        assertTrue(buf.toString().contains("Tobi"), "removed brother name must appear");
        assertTrue(buf.toString().contains("-"), "'-' marker expected");
    }

    // ---- crossCheck --------------------------------------------------------

    @Test
    void crossCheck_noIssuesWhenMapsMatch() {
        Brother b = brother("A", "Gorm", new int[8]);

        Map<String, int[]> computed = new HashMap<>();
        Map<String, int[]> actual   = new HashMap<>();
        int[] arr = {1, 0, 0, 0, 0, 0, 0, 0};
        computed.put("A", arr.clone());
        actual  .put("A", arr.clone());

        List<String> issues = ReplayAnalyzer.crossCheck(
                List.of(b), computed, new HashMap<>(), actual, new HashMap<>());

        assertTrue(issues.isEmpty());
    }

    @Test
    void crossCheck_flagsMismatch() {
        Brother b = brother("A", "Gorm", new int[8]);

        Map<String, int[]> computed = new HashMap<>();
        Map<String, int[]> actual   = new HashMap<>();
        computed.put("A", new int[]{2, 0, 0, 0, 0, 0, 0, 0});
        actual  .put("A", new int[]{1, 0, 0, 0, 0, 0, 0, 0});

        List<String> issues = ReplayAnalyzer.crossCheck(
                List.of(b), computed, new HashMap<>(), actual, new HashMap<>());

        assertFalse(issues.isEmpty(), "mismatch must be reported");
        assertTrue(issues.get(0).contains("Gorm"), "brother name must appear in issue");
        assertTrue(issues.get(0).contains("planned"), "field label must appear");
    }

    @Test
    void crossCheck_treatsNullAndAllZeroAsEquivalent() {
        Brother b = brother("A", "Gorm", new int[8]);

        Map<String, int[]> computed = new HashMap<>(); // no entry for "A"
        Map<String, int[]> actual   = new HashMap<>();
        actual.put("A", new int[8]);  // all-zero

        List<String> issues = ReplayAnalyzer.crossCheck(
                List.of(b), computed, new HashMap<>(), actual, new HashMap<>());

        assertTrue(issues.isEmpty(),
                "null and all-zero must be considered equivalent (no planned increases)");
    }

    // ---- helpers -----------------------------------------------------------

    private static Brother brother(String fp, String name, int[] stats) {
        Brother b = new Brother();
        b.fingerprint = fp;
        b.name        = name;
        b.stats       = stats.clone();
        b.stars       = new int[8];
        return b;
    }

    private static LevelUpEvent fakeEvent(String name, boolean adjusted, int levels) {
        return new LevelUpEvent(
                name, adjusted, levels,
                Map.of(), List.of(), Map.of(), List.of(), false);
    }
}
