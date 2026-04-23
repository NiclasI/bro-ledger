package se.niclas.broledger.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import se.niclas.broledger.model.Role;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
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
}
