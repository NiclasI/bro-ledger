package se.niclas.broledger.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import se.niclas.broledger.model.Role;
import se.niclas.broledger.model.RolePack;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class RoleServiceTest {

    @TempDir
    Path tempDir;

    private RoleService service;
    private Path rolesFile;

    @BeforeEach
    void setUp() {
        rolesFile = tempDir.resolve("roles.json");
        AppConfig.resetForTest();
        AppConfig.configFileForTest = tempDir.resolve("config.json");
        service = RoleService.createForTest(rolesFile);
    }

    @Test
    void getAllReturnsEmptyInitially() {
        assertTrue(service.getAll().isEmpty());
    }

    @Test
    void addCreatesRoleWithUuid() {
        Role r = service.add("Tank", true);
        assertNotNull(r.id);
        assertEquals("Tank", r.name);
        assertTrue(r.frontline);
    }

    @Test
    void addedRoleAppearsInGetAll() {
        service.add("Archer", false);
        assertEquals(1, service.getAll().size());
        assertEquals("Archer", service.getAll().get(0).name);
    }

    @Test
    void getAllReturnsInRoleOrderWhenSet() {
        Role w = service.add("Warrior", true);
        Role a = service.add("Archer",  false);
        Role m = service.add("Medic",   true);

        // Role order is built by add(): [warrior, archer, medic]
        List<Role> all = service.getAll();
        assertEquals("Warrior", all.get(0).name);
        assertEquals("Archer",  all.get(1).name);
        assertEquals("Medic",   all.get(2).name);
    }

    @Test
    void getAllReturnsInsertionOrderWhenNoRoleOrder() {
        AppConfig.getInstance().roleOrder = null;
        service.add("Warrior", true);
        service.add("Archer",  false);

        // No roleOrder → insertion order
        List<String> names = service.getAll().stream().map(r -> r.name).toList();
        assertEquals(List.of("Warrior", "Archer"), names);
    }

    @Test
    void getByIdReturnsCorrectRole() {
        Role r = service.add("Scout", true);
        Role found = service.getById(r.id);
        assertNotNull(found);
        assertEquals("Scout", found.name);
    }

    @Test
    void getByIdReturnsNullForUnknownId() {
        assertNull(service.getById("does-not-exist"));
    }

    @Test
    void updateChangesName() {
        Role r = service.add("OldName", true);
        r.name = "NewName";
        service.update(r);
        assertEquals("NewName", service.getById(r.id).name);
    }

    @Test
    void updatePersistsAcrossReload() {
        Role r = service.add("Healer", false);
        r.name = "Cleric";
        service.update(r);

        service = RoleService.createForTest(rolesFile);

        assertEquals("Cleric", service.getById(r.id).name);
    }

    @Test
    void deleteRemovesRole() {
        Role r = service.add("Temp", true);
        service.delete(r.id);
        assertNull(service.getById(r.id));
        assertTrue(service.getAll().isEmpty());
    }

    @Test
    void deletePersistsAcrossReload() {
        Role r = service.add("Gone", true);
        service.delete(r.id);

        service = RoleService.createForTest(rolesFile);

        assertTrue(service.getAll().isEmpty());
    }

    @Test
    void defaultPriorityIsThreeForAllStats() {
        Role r = service.add("Default", true);
        for (int p : r.priority) assertEquals(3, p);
    }

    @Test
    void rolesFileCreatedInConfigDir() {
        service.add("X", true);
        assertTrue(Files.exists(tempDir.resolve("roles.json")));
    }

    @Test
    void moveUpdatesOrder() {
        Role a = service.add("A", true);
        Role b = service.add("B", true);
        Role c = service.add("C", true);

        // Initial: [A, B, C]; move A to end
        service.move(a.id, 2);
        List<Role> all = service.getAll();
        assertEquals("B", all.get(0).name);
        assertEquals("C", all.get(1).name);
        assertEquals("A", all.get(2).name);
    }

    @Test
    void moveClampsToValidRange() {
        Role a = service.add("A", true);
        Role b = service.add("B", true);

        service.move(a.id, 100); // clamp to end
        assertEquals("B", service.getAll().get(0).name);
        assertEquals("A", service.getAll().get(1).name);

        service.move(a.id, -5); // clamp to start
        assertEquals("A", service.getAll().get(0).name);
    }

    @Test
    void deleteRemovesIdFromRoleOrder() {
        Role a = service.add("A", true);
        Role b = service.add("B", true);

        service.delete(a.id);

        List<String> order = AppConfig.getInstance().roleOrder;
        assertFalse(order.contains(a.id));
        assertTrue(order.contains(b.id));
    }

    @Test
    void moveWorksWhenRoleOrderIsEmpty() {
        // Simulate roles that exist in the store but roleOrder is empty (e.g. after delete).
        Role a = service.add("A", true);
        Role b = service.add("B", true);
        AppConfig.getInstance().roleOrder = new ArrayList<>(); // force empty

        // getAll() falls back to insertion order [A, B]; move should still work
        service.move(a.id, 1);
        List<Role> all = service.getAll();
        assertEquals(2, all.size());
        assertEquals("B", all.get(0).name);
        assertEquals("A", all.get(1).name);
    }

    @Test
    void addPopulatesRoleOrder() {
        Role a = service.add("A", true);
        Role b = service.add("B", true);

        List<String> order = AppConfig.getInstance().roleOrder;
        assertEquals(List.of(a.id, b.id), order);
    }

    // ---- importPack ---------------------------------------------------------

    private static Role packRole(String name, boolean frontline) {
        Role r = new Role();
        r.id = "sender-id-" + name;
        r.name = name;
        r.frontline = frontline;
        r.targetStats = new int[]{1, 2, 3, 4, 5, 6, 7, 8};
        r.priority = new int[]{1, 1, 2, 2, 3, 3, 1, 2};
        r.perkPlanTemplate = new LinkedHashMap<>(java.util.Map.of("00000001", "PLANNED"));
        return r;
    }

    @Test
    void importPackMintsFreshUuidsAndPreservesFields() {
        Role incoming = packRole("Tank", true);
        RolePack pack = new RolePack("Test Pack", List.of(incoming));

        List<Role> imported = service.importPack(pack, List.of(incoming), "AB3D9F2K");

        assertEquals(1, imported.size());
        Role r = imported.get(0);
        assertNotEquals(incoming.id, r.id);
        assertEquals("Tank", r.name);
        assertTrue(r.frontline);
        assertArrayEquals(incoming.targetStats, r.targetStats);
        assertArrayEquals(incoming.priority, r.priority);
        assertEquals(incoming.perkPlanTemplate, r.perkPlanTemplate);
    }

    @Test
    void importPackAppendsToRoleOrder() {
        Role incoming = packRole("Archer", false);
        RolePack pack = new RolePack(null, List.of(incoming));

        List<Role> imported = service.importPack(pack, List.of(incoming), "AB3D9F2K");

        assertEquals(List.of(imported.get(0).id), AppConfig.getInstance().roleOrder);
        assertEquals(1, service.getAll().size());
    }

    @Test
    void importPackOnlyImportsSelectedSubset() {
        Role tank = packRole("Tank", true);
        Role archer = packRole("Archer", false);
        RolePack pack = new RolePack(null, List.of(tank, archer));

        List<Role> imported = service.importPack(pack, List.of(tank), "AB3D9F2K");

        assertEquals(1, imported.size());
        assertEquals("Tank", imported.get(0).name);
        assertEquals(1, service.getAll().size());
    }

    @Test
    void importPackToleratesDuplicateNames() {
        Role existing = service.add("Tank", true);
        Role incoming = packRole("Tank", true);
        RolePack pack = new RolePack(null, List.of(incoming));

        List<Role> imported = service.importPack(pack, List.of(incoming), "AB3D9F2K");

        assertEquals(2, service.getAll().size());
        assertNotEquals(existing.id, imported.get(0).id);
    }

    @Test
    void importPackWithEmptySelectionImportsNothing() {
        Role incoming = packRole("Tank", true);
        RolePack pack = new RolePack(null, List.of(incoming));

        List<Role> imported = service.importPack(pack, List.of(), "AB3D9F2K");

        assertTrue(imported.isEmpty());
        assertTrue(service.getAll().isEmpty());
    }

    // ---- provenance / update-in-place ----------------------------------------

    @Test
    void importPackRecordsProvenance() {
        Role incoming = packRole("Tank", true);
        RolePack pack = new RolePack(null, List.of(incoming));

        Role imported = service.importPack(pack, List.of(incoming), "AB3D9F2K").get(0);

        assertEquals("AB3D9F2K", imported.sourcePackCode);
        assertEquals(incoming.id, imported.sourceRoleId);
    }

    @Test
    void reimportFromSameCodeUpdatesInPlace() {
        Role v1 = packRole("Tank", true);
        Role first = service.importPack(new RolePack(null, List.of(v1)), List.of(v1), "AB3D9F2K").get(0);

        Role v2 = packRole("Tank v2", false);
        v2.id = v1.id; // sender's role id is stable across pack updates
        v2.targetStats = new int[]{9, 9, 9, 9, 9, 9, 9, 9};
        Role second = service.importPack(new RolePack(null, List.of(v2)), List.of(v2), "AB3D9F2K").get(0);

        // Same local role, updated content, no duplicate, order untouched.
        assertEquals(first.id, second.id);
        assertEquals("Tank v2", service.getById(first.id).name);
        assertFalse(service.getById(first.id).frontline);
        assertArrayEquals(v2.targetStats, service.getById(first.id).targetStats);
        assertEquals(1, service.getAll().size());
        assertEquals(List.of(first.id), AppConfig.getInstance().roleOrder);
        // Provenance survives the in-place update.
        assertEquals("AB3D9F2K", second.sourcePackCode);
        assertEquals(v1.id, second.sourceRoleId);
    }

    @Test
    void reimportFromDifferentCodeCreatesDuplicate() {
        Role incoming = packRole("Tank", true);
        service.importPack(new RolePack(null, List.of(incoming)), List.of(incoming), "AB3D9F2K");
        service.importPack(new RolePack(null, List.of(incoming)), List.of(incoming), "ZZ3D9F2K");

        assertEquals(2, service.getAll().size());
    }

    @Test
    void importWithNullCodeNeverMatchesAndRecordsNoPackCode() {
        Role incoming = packRole("Tank", true);
        Role a = service.importPack(new RolePack(null, List.of(incoming)), List.of(incoming), null).get(0);
        Role b = service.importPack(new RolePack(null, List.of(incoming)), List.of(incoming), null).get(0);

        assertNotEquals(a.id, b.id);
        assertEquals(2, service.getAll().size());
        assertNull(a.sourcePackCode);
    }

    @Test
    void findBySourceMatchesOnlyExactPair() {
        Role incoming = packRole("Tank", true);
        Role imported = service.importPack(
                new RolePack(null, List.of(incoming)), List.of(incoming), "AB3D9F2K").get(0);

        assertEquals(imported, service.findBySource("AB3D9F2K", incoming.id));
        assertNull(service.findBySource("ZZ3D9F2K", incoming.id));
        assertNull(service.findBySource("AB3D9F2K", "other-sender-id"));
        assertNull(service.findBySource(null, incoming.id));
        assertNull(service.findBySource("AB3D9F2K", null));
    }

    @Test
    void provenancePersistsAcrossReload() {
        Role incoming = packRole("Tank", true);
        Role imported = service.importPack(
                new RolePack(null, List.of(incoming)), List.of(incoming), "AB3D9F2K").get(0);

        service = RoleService.createForTest(rolesFile);

        Role reloaded = service.getById(imported.id);
        assertEquals("AB3D9F2K", reloaded.sourcePackCode);
        assertEquals(incoming.id, reloaded.sourceRoleId);
    }
}
