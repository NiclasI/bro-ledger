package se.niclas.broledger.ui;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import se.niclas.broledger.model.Brother;
import se.niclas.broledger.service.DictionaryService;

import static org.junit.jupiter.api.Assertions.*;

class MainControllerTest {

    @BeforeAll
    static void loadDictionary() throws Exception {
        DictionaryService.getInstance().loadFromClasspath();
    }

    // ---- helpers -----------------------------------------------------------

    /** A minimally valid brother. */
    private static Brother valid() {
        Brother b = new Brother();
        b.name           = "Horik";
        b.levelTotal     = 3;
        b.backgroundHexId = "F1D4D64F"; // Companion (Spear) — type=background in dictionary
        return b;
    }

    // ---- name validation ---------------------------------------------------

    @Test
    void validBrotherPasses() {
        assertTrue(MainController.isValid(valid()));
    }

    @Test
    void nullNameFails() {
        Brother b = valid();
        b.name = null;
        assertFalse(MainController.isValid(b));
    }

    @Test
    void blankNameFails() {
        Brother b = valid();
        b.name = "   ";
        assertFalse(MainController.isValid(b));
    }

    @Test
    void nameTooLongFails() {
        Brother b = valid();
        b.name = "A".repeat(61);
        assertFalse(MainController.isValid(b));
    }

    @Test
    void nameExactly60CharsIsValid() {
        Brother b = valid();
        b.name = "A".repeat(60);
        assertTrue(MainController.isValid(b));
    }

    // ---- level validation --------------------------------------------------

    @Test
    void levelZeroFails() {
        Brother b = valid();
        b.levelTotal = 0;
        assertFalse(MainController.isValid(b));
    }

    @Test
    void negativeLevelFails() {
        Brother b = valid();
        b.levelTotal = -1;
        assertFalse(MainController.isValid(b));
    }

    @Test
    void levelOnePasses() {
        Brother b = valid();
        b.levelTotal = 1;
        assertTrue(MainController.isValid(b));
    }

    // ---- background validation ---------------------------------------------

    @Test
    void nullBackgroundFails() {
        Brother b = valid();
        b.backgroundHexId = null;
        assertFalse(MainController.isValid(b));
    }

    @Test
    void unknownBackgroundHexFails() {
        Brother b = valid();
        b.backgroundHexId = "00000000"; // not in dictionary
        assertFalse(MainController.isValid(b));
    }

    @Test
    void perkHexIdAsBackgroundFails() {
        Brother b = valid();
        b.backgroundHexId = "3E7523FA"; // fast-adaptation perk, type != "background"
        assertFalse(MainController.isValid(b));
    }

    @Test
    void traitHexIdAsBackgroundFails() {
        Brother b = valid();
        b.backgroundHexId = "6FF46EFE"; // athletic trait, type != "background"
        assertFalse(MainController.isValid(b));
    }

    @Test
    void anotherKnownBackgroundPasses() {
        Brother b = valid();
        b.backgroundHexId = "B47205E5"; // Killer on the run
        assertTrue(MainController.isValid(b));
    }
}
