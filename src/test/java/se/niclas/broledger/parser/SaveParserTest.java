package se.niclas.broledger.parser;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import se.niclas.broledger.model.Brother;
import se.niclas.broledger.model.Stat;
import se.niclas.broledger.service.DictionaryService;
import se.niclas.broledger.tools.parser.HexDumpSaveParser;

import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration test against a real .sav file.
 *
 * Run with:
 *   mvn test -Dsav.file=/path/to/your/save.sav
 *
 * Tests are skipped when no sav.file system property is provided.
 */
//@EnabledIfSystemProperty(named = "sav.file", matches = ".+")
class SaveParserTest {

    private static List<Brother> brothers;

    @BeforeAll
    static void load() throws Exception {
        DictionaryService dict = DictionaryService.getInstance();
        dict.loadFromClasspath();

        String savPath = System.getProperty("sav.file");
        if(savPath == null) {
            savPath = "c:\\dev\\repo\\bb-keeper\\src\\test\\example\\TestSavefile.sav";
        }
        Path savFile = Path.of(savPath);
        if (System.getProperty("hex.dump") != null) {
            brothers = new HexDumpSaveParser(dict).parse(savFile);
        } else {
            brothers = new SaveParser(dict).parse(savFile);
        }
        for(Brother b : brothers) {
            System.out.println(b.name + "found");
        }
    }

    @Test
    void brotherCountIsPositive() {
        assertFalse(brothers.isEmpty(), "Expected at least one brother in the save");
    }

    @Test
    void firstBrotherHasName() {
        Brother first = brothers.getFirst();
        assertNotNull(first.name, "Brother name should not be null");
        assertFalse(first.name.isBlank(), "Brother name should not be blank");
    }

    @Test
    void firstBrotherHasValidStats() {
        Brother first = brothers.getFirst();
        // Health (index 0) should be a plausible non-zero value
        assertTrue(first.stats[Stat.HEALTH.statIndex()] > 0,
                "Health stat should be positive, got: " + first.stats[Stat.HEALTH.statIndex()]);
        // All 8 stats should be within sane range
        for (int i = 0; i < 8; i++) {
            assertTrue(first.stats[i] >= 0 && first.stats[i] <= 300,
                    "Stat[" + i + "] out of range: " + first.stats[i]);
        }
    }

    @Test
    void firstBrotherHasBackground() {
        Brother first = brothers.getFirst();
        assertNotNull(first.backgroundHexId, "Background should not be null");
        assertEquals(8, first.backgroundHexId.length(), "Background ID should be 8 hex chars");
    }

    @Test
    void firstBrotherStarsInRange() {
        Brother first = brothers.getFirst();
        for (int i = 0; i < 8; i++) {
            assertTrue(first.stars[i] >= 0 && first.stars[i] <= 3,
                    "Star[" + i + "] out of range: " + first.stars[i]);
        }
    }

    @Test
    void firstBrotherHasFingerprint() {
        Brother first = brothers.getFirst();
        assertNotNull(first.fingerprint);
        assertEquals(32, first.fingerprint.length(), "Fingerprint should be 32 hex chars");
    }

    @Test
    void perksAreKnownIds() {
        DictionaryService dict = DictionaryService.getInstance();
        for (Brother b : brothers) {
            for (String perkId : b.perkIds) {
                assertTrue(dict.has(perkId),
                        "Perk id not in dictionary: " + perkId + " (brother: " + b.name + ")");
            }
        }
    }

    @Test
    void debugBrother() {
        DictionaryService dict = DictionaryService.getInstance();
        for (Brother b : brothers) {
            System.out.println(b.name + "found");
        }
    }

    @Test
    void printRoster() {
        // Not an assertion — useful for manual inspection
        for (Brother b : brothers) {
            System.out.printf("%-25s %-20s  HP=%-4d MS=%-4d  perks=%s  traits=%s%n",
                    b.name,
                    b.title,
                    b.stats[Stat.HEALTH.statIndex()],
                    b.stats[Stat.MELEE_SKILL.statIndex()],
                    String.format("(%s)",String.join(",",b.perkIds)),
                    String.format("(%s)",String.join(",",b.traits.stream().map(te -> te.id).toList()))
            );
        }
    }
}
