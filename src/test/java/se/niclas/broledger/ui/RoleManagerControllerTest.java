package se.niclas.broledger.ui;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class RoleManagerControllerTest {

    @Test
    void deleteConfirmationText_unknownUsage() {
        String text = RoleManagerController.deleteConfirmationText(null);
        assertTrue(text.contains("Any brothers assigned to it"));
        assertTrue(text.contains("cannot be undone"));
    }

    @Test
    void deleteConfirmationText_zeroAssigned() {
        String text = RoleManagerController.deleteConfirmationText(0L);
        assertTrue(text.contains("No brothers in the current save"));
    }

    @Test
    void deleteConfirmationText_someAssigned() {
        String text = RoleManagerController.deleteConfirmationText(4L);
        assertTrue(text.contains("assigned to 4 brother(s) in the current save"));
        assertTrue(text.contains("cleared"));
    }
}
