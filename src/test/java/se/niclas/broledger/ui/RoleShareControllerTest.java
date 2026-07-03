package se.niclas.broledger.ui;

import org.junit.jupiter.api.Test;
import se.niclas.broledger.model.ShareRecord;

import static org.junit.jupiter.api.Assertions.*;

class RoleShareControllerTest {

    // ---- normalizedTitle ----------------------------------------------------

    @Test
    void normalizedTitle_blankBecomesNull() {
        assertNull(RoleShareController.normalizedTitle(null));
        assertNull(RoleShareController.normalizedTitle(""));
        assertNull(RoleShareController.normalizedTitle("   "));
    }

    @Test
    void normalizedTitle_trimsAndKeepsShortTitles() {
        assertEquals("My Pack", RoleShareController.normalizedTitle("  My Pack  "));
    }

    @Test
    void normalizedTitle_capsLength() {
        String longTitle = "x".repeat(RoleShareController.MAX_TITLE_LENGTH + 20);
        String result = RoleShareController.normalizedTitle(longTitle);
        assertEquals(RoleShareController.MAX_TITLE_LENGTH, result.length());
    }

    // ---- recentCellText -----------------------------------------------------

    @Test
    void recentCellText_showsTitleCodeCountAndDate() {
        ShareRecord r = new ShareRecord("AB3D9F2K", "tok", "My Pack", "2026-07-05T12:00:00Z", 3);
        assertEquals("My Pack — AB3D9F2K · 3 role(s) · 2026-07-05",
                RoleShareController.recentCellText(r));
    }

    @Test
    void recentCellText_untitledAndMissingDate() {
        ShareRecord r = new ShareRecord("AB3D9F2K", "tok", null, null, 1);
        assertEquals("Untitled — AB3D9F2K · 1 role(s)", RoleShareController.recentCellText(r));
    }

    @Test
    void recentCellText_flagsTokenlessEntries() {
        ShareRecord r = new ShareRecord("AB3D9F2K", null, "Old", "2026-07-05T12:00:00Z", 2);
        assertTrue(RoleShareController.recentCellText(r).endsWith("(not updatable)"));
    }
}
