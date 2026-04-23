package se.niclas.broledger.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import se.niclas.broledger.model.BrotherAnnotation;
import se.niclas.broledger.model.Stat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class AnnotationServiceTest {

    @TempDir
    Path tempDir;

    private AnnotationService service;
    private Path savePath;

    @BeforeEach
    void setUp() throws IOException {
        savePath = tempDir.resolve("test.sav");
        Files.writeString(savePath, "dummy");
        // Fresh instance via reflection to avoid singleton bleed between tests
        try {
            var field = AnnotationService.class.getDeclaredField("instance");
            field.setAccessible(true);
            field.set(null, null);
        } catch (Exception e) {
            fail("Could not reset singleton: " + e.getMessage());
        }
        service = AnnotationService.getInstance();
        service.loadFor(savePath);
    }

    @Test
    void getReturnsFreshAnnotationForUnknownFingerprint() {
        BrotherAnnotation a = service.get("ABCD1234");
        assertNotNull(a);
        assertNull(a.roleId);
    }

    @Test
    void setRolePersistsAcrossReload() {
        service.setRole("FP001", "uuid-tank");

        service.loadFor(savePath);
        assertEquals("uuid-tank", service.get("FP001").roleId);
    }

    @Test
    void nullRoleIdPersistsAsNull() {
        service.setRole("FP001", "uuid-tank");
        service.setRole("FP001", null);

        service.loadFor(savePath);
        assertNull(service.get("FP001").roleId);
    }

    @Test
    void multipleBrothersPersistedIndependently() {
        service.setRole("A", "uuid-archer");
        service.setRole("B", "uuid-warrior");

        service.loadFor(savePath);
        assertEquals("uuid-archer",  service.get("A").roleId);
        assertEquals("uuid-warrior", service.get("B").roleId);
    }

    @Test
    void keeperJsonCreatedAdjacentToSave() {
        service.setRole("FP003", "uuid-scout");
        Path expected = savePath.resolveSibling(savePath.getFileName() + ".keeper.json");
        assertTrue(Files.exists(expected), "keeper.json not created adjacent to save");
    }

    @Test
    void overwritingRoleUpdatesFile() {
        service.setRole("FP004", "uuid-first");
        service.setRole("FP004", "uuid-second");

        service.loadFor(savePath);
        assertEquals("uuid-second", service.get("FP004").roleId);
    }

    @Test
    void statIncreasesPersistAcrossReload() {
        int[] inc = new int[Stat.values().length];
        inc[Stat.HEALTH.ordinal()]      = 3;
        inc[Stat.MELEE_SKILL.ordinal()] = 2;
        service.setStatIncreases("FP005", inc);

        service.loadFor(savePath);
        int[] loaded = service.get("FP005").statIncreases;
        assertNotNull(loaded);
        assertEquals(3, loaded[Stat.HEALTH.ordinal()]);
        assertEquals(2, loaded[Stat.MELEE_SKILL.ordinal()]);
        assertEquals(0, loaded[Stat.INITIATIVE.ordinal()]);
    }

    @Test
    void statIncreases_nullForBrotherWithNoIncreases() {
        service.setRole("FP006", "uuid-x");
        service.loadFor(savePath);
        assertNull(service.get("FP006").statIncreases,
                "statIncreases must be null when never set");
    }

    @Test
    void statIncreases_oldFileWithoutFieldLoadsWithoutError() throws IOException {
        // Write a keeper.json that has no statIncreases field (legacy format)
        Path keeperJson = savePath.resolveSibling(savePath.getFileName() + ".keeper.json");
        Files.writeString(keeperJson, "{\"version\":1,\"annotations\":{\"FP007\":{\"roleId\":\"uuid-y\"}}}");

        service.loadFor(savePath);
        BrotherAnnotation a = service.get("FP007");
        assertEquals("uuid-y", a.roleId, "roleId from legacy file must load");
        assertNull(a.statIncreases, "statIncreases must default to null in legacy file");
    }

}
