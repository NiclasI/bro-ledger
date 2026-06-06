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
        // 3E7523FA = Fast Adaptation perk
        assertEquals("gfx/ui/perks/perk_33.png", svc.resolveHex("3E7523FA"));
    }

    @Test
    void resolveHex_knownTrait() {
        // 6FF46EFE = Athletic trait
        assertEquals("gfx/ui/traits/trait_icon_21.png", svc.resolveHex("6FF46EFE"));
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
        // slot=body, icon=1 → inventory_body_armor_01.png (archive src path)
        assertEquals("gfx/ui/items/armor/inventory_body_armor_01.png",
                svc.resolve("body", null, 1, null));
    }

    @Test
    void resolve_unknownSlotReturnsNull() {
        assertNull(svc.resolve("body", null, 999, null));
    }

    // ---- Resolution priority -----------------------------------------------

    @Test
    void resolve_slotHexIconBeatsSlotIcon() {
        // slot=body, icon=1, hex=BF3413DE (named Southern Mail) overrides generic body:1
        String generic = svc.resolve("body", null,       1, null);
        String named   = svc.resolve("body", "BF3413DE", 1, null);
        assertNotNull(named);
        assertNotEquals(generic, named);
    }

    @Test
    void resolve_hexDirectBeatsSlotIcon() {
        // A hex with a direct mapping returns the archive src path (gfx/ui/perks/…)
        String direct = svc.resolveHex("3E7523FA");
        assertNotNull(direct);
        assertTrue(direct.startsWith("gfx/ui/perks/"));
    }

    // ---- resolveAttachment -------------------------------------------------

    @Test
    void resolveAttachment_knownEntry() {
        // slot=body, attachment=76B57B86 (Bone Platings)
        assertEquals("gfx/ui/items/armor_upgrades/inventory_upgrade_06.png",
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
        // slot=body, hex=2847F403, house=1 → faction armor variant
        assertEquals("gfx/ui/items/armor/inventory_faction_armor_01.png",
                svc.resolveHexHouse("body", "2847F403", 1));
    }

    @Test
    void resolveHexHouse_unknownReturnsNull() {
        assertNull(svc.resolveHexHouse("body", "2847F403", 999));
    }

    // ---- Two hex IDs sharing one image ------------------------------------

    @Test
    void resolveHex_commaGroupedBothMapped() {
        // D6461010 and B6FBBE7B share the same trait icon
        assertEquals(svc.resolveHex("D6461010"), svc.resolveHex("B6FBBE7B"));
        assertNotNull(svc.resolveHex("D6461010"));
    }
}
