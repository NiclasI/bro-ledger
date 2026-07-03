package se.niclas.broledger.model;

import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

import java.util.LinkedHashMap;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class RolePackTest {

    private static final ObjectMapper MAPPER = JsonMapper.builder().build();

    @Test
    void roundTripsThroughJackson() {
        Role role = new Role();
        role.id = "abc-123";
        role.name = "Tank";
        role.frontline = true;
        role.targetStats = new int[]{1, 2, 3, 4, 5, 6, 7, 8};
        role.priority = new int[]{1, 1, 2, 2, 3, 3, 1, 2};
        role.perkPlanTemplate = new LinkedHashMap<>(java.util.Map.of("00000001", "PLANNED"));

        RolePack pack = new RolePack("My Pack", List.of(role));

        String json = MAPPER.writeValueAsString(pack);
        RolePack parsed = MAPPER.readValue(json, RolePack.class);

        assertEquals(1, parsed.schemaVersion);
        assertEquals("My Pack", parsed.label);
        assertEquals(1, parsed.roles.size());
        Role parsedRole = parsed.roles.get(0);
        assertEquals("Tank", parsedRole.name);
        assertTrue(parsedRole.frontline);
        assertArrayEquals(role.targetStats, parsedRole.targetStats);
        assertArrayEquals(role.priority, parsedRole.priority);
        assertEquals(role.perkPlanTemplate, parsedRole.perkPlanTemplate);
    }

    @Test
    void nullLabelIsAllowed() {
        RolePack pack = new RolePack(null, List.of());
        String json = MAPPER.writeValueAsString(pack);
        RolePack parsed = MAPPER.readValue(json, RolePack.class);
        assertNull(parsed.label);
        assertTrue(parsed.roles.isEmpty());
    }

    @Test
    void truncatedFlagIsNotSerialized() {
        RolePack pack = new RolePack("Pack", List.of());
        pack.markTruncated();
        String json = MAPPER.writeValueAsString(pack);
        assertFalse(json.contains("truncated"));
        RolePack parsed = MAPPER.readValue(json, RolePack.class);
        assertFalse(parsed.truncated());
    }

    // ---- forSharing ---------------------------------------------------------

    @Test
    void forSharingCopiesContentAndKeepsId() {
        Role role = new Role();
        role.id = "local-uuid";
        role.name = "Tank";
        role.frontline = false;
        role.targetStats = new int[]{1, 2, 3, 4, 5, 6, 7, 8};
        role.priority = new int[]{1, 1, 2, 2, 3, 3, 1, 2};
        role.perkPlanTemplate = new LinkedHashMap<>(java.util.Map.of("00000001", "PLANNED"));

        RolePack pack = RolePack.forSharing("My Pack", List.of(role));

        assertEquals("My Pack", pack.label);
        Role copy = pack.roles.get(0);
        assertEquals("local-uuid", copy.id);
        assertEquals("Tank", copy.name);
        assertFalse(copy.frontline);
        assertArrayEquals(role.targetStats, copy.targetStats);
        assertArrayEquals(role.priority, copy.priority);
        assertEquals(role.perkPlanTemplate, copy.perkPlanTemplate);
        // Deep copies — mutating the pack never touches the catalogue role.
        assertNotSame(role.targetStats, copy.targetStats);
        assertNotSame(role.perkPlanTemplate, copy.perkPlanTemplate);
    }

    @Test
    void forSharingStripsProvenance() {
        Role role = new Role();
        role.id = "local-uuid";
        role.name = "Imported Tank";
        role.sourcePackCode = "AB3D9F2K"; // must never leak into a published pack
        role.sourceRoleId = "sender-uuid";

        RolePack pack = RolePack.forSharing(null, List.of(role));

        Role copy = pack.roles.get(0);
        assertNull(copy.sourcePackCode);
        assertNull(copy.sourceRoleId);
        String json = MAPPER.writeValueAsString(pack);
        assertFalse(json.contains("AB3D9F2K"));
    }

    @Test
    void forSharingSkipsNullRolesAndTolerateNullList() {
        Role role = new Role();
        role.id = "x";
        java.util.List<Role> withNull = new java.util.ArrayList<>();
        withNull.add(null);
        withNull.add(role);

        assertEquals(1, RolePack.forSharing(null, withNull).roles.size());
        assertTrue(RolePack.forSharing(null, null).roles.isEmpty());
    }
}
