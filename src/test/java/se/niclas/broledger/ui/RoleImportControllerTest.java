package se.niclas.broledger.ui;

import org.junit.jupiter.api.Test;
import se.niclas.broledger.model.RolePack;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class RoleImportControllerTest {

    @Test
    void packTitleText_prefersLabel() {
        RolePack pack = new RolePack("My Pack", List.of());
        assertEquals("My Pack", RoleImportController.packTitleText(pack, "AB3D9F2K"));
    }

    @Test
    void packTitleText_fallsBackToCodeWhenUntitled() {
        assertEquals("Untitled pack (AB3D9F2K)",
                RoleImportController.packTitleText(new RolePack(null, List.of()), "AB3D9F2K"));
        assertEquals("Untitled pack (AB3D9F2K)",
                RoleImportController.packTitleText(new RolePack("", List.of()), "AB3D9F2K"));
    }

    @Test
    void importCheckboxLabel_flagsUpdateInPlaceRoles() {
        assertEquals("Tank", RoleImportController.importCheckboxLabel("Tank", false));
        assertEquals("Tank  (updates existing)", RoleImportController.importCheckboxLabel("Tank", true));
    }
}
