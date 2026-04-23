package se.niclas.broledger.service;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ImageMapServiceTest {

    private static ImageMapService svc;

    @BeforeAll
    static void load() throws Exception {
        svc = ImageMapService.getInstance();
        svc.loadFromClasspath();
    }

    // ---- resolveHex (perks / traits / backgrounds) -------------------------

    @Test
    void resolveHex_knownPerk() {
        // [data-hex="3E7523FA"] → perks/fast-adaptation.png
        assertEquals("perks/fast-adaptation.png", svc.resolveHex("3E7523FA"));
    }

    @Test
    void resolveHex_knownTrait() {
        // [data-hex="6FF46EFE"] → traits/athletic.png
        assertEquals("traits/athletic.png", svc.resolveHex("6FF46EFE"));
    }

    @Test
    void resolveHex_unknownReturnsNull() {
        assertNull(svc.resolveHex("00000000"));
    }

    @Test
    void resolveHex_nullInputReturnsNull() {
        assertNull(svc.resolveHex(null));
    }

    @Test
    void resolveHex_caseInsensitive() {
        assertEquals(svc.resolveHex("3E7523FA"), svc.resolveHex("3e7523fa"));
    }

    // ---- resolve (slot + icon, generic items) ------------------------------

    @Test
    void resolve_genericBodyArmor() {
        // [data-slot="body"][data-icon="1"] → armor/body-armor-01.png
        assertEquals("armor/body-armor-01.png", svc.resolve("body", null, 1, null));
    }

    @Test
    void resolve_unknownSlotReturnsNull() {
        assertNull(svc.resolve("body", null, 999, null));
    }

    // ---- Resolution priority -----------------------------------------------

    @Test
    void resolve_slotHexIconBeatsSlotIcon() {
        // [data-slot="body"][data-icon="1"][data-hex="BF3413DE"] overrides
        // [data-slot="body"][data-icon="1"]
        String generic = svc.resolve("body", null,       1, null);
        String named   = svc.resolve("body", "BF3413DE", 1, null);
        assertNotNull(named);
        assertNotEquals(generic, named);
    }

    @Test
    void resolve_hexDirectBeatsSlotIcon() {
        // A hex with a direct mapping should return the direct path even when
        // a slot+icon fallback also exists.
        String direct = svc.resolveHex("3E7523FA");
        assertNotNull(direct);
        assertTrue(direct.startsWith("perks/"));
    }

    // ---- resolveAttachment -------------------------------------------------

    @Test
    void resolveAttachment_knownEntry() {
        // [data-slot="body"][data-attachment="76B57B86"] → armor-upgrades/bone-plating.png
        assertEquals("armor-upgrades/bone-plating.png",
                svc.resolveAttachment("body", "76B57B86"));
    }

    @Test
    void resolveAttachment_unknownReturnsNull() {
        assertNull(svc.resolveAttachment("body", "00000000"));
    }

    @Test
    void resolveAttachment_nullInputReturnsNull() {
        assertNull(svc.resolveAttachment(null, "76B57B86"));
        assertNull(svc.resolveAttachment("body", null));
    }

    // ---- resolveHexHouse ---------------------------------------------------

    @Test
    void resolveHexHouse_knownEntry() {
        // [data-slot="body"][data-hex="2847F403"][data-house="1"] → armor/faction-armor-01.png
        assertEquals("armor/faction-armor-01.png",
                svc.resolveHexHouse("body", "2847F403", 1));
    }

    @Test
    void resolveHexHouse_unknownReturnsNull() {
        assertNull(svc.resolveHexHouse("body", "2847F403", 999));
    }

    // ---- Comma-grouped selectors (two hex IDs share one image) ------------

    @Test
    void resolveHex_commaGroupedBothMapped() {
        // [data-hex="D6461010"], [data-hex="B6FBBE7B"] { ... honor-oath.png }
        assertEquals(svc.resolveHex("D6461010"), svc.resolveHex("B6FBBE7B"));
        assertNotNull(svc.resolveHex("D6461010"));
    }
}
