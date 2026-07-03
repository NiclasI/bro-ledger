package se.niclas.broledger.service;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import se.niclas.broledger.model.Role;
import se.niclas.broledger.model.RolePack;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.NoSuchElementException;

import static org.junit.jupiter.api.Assertions.*;

class RoleShareServiceTest {

    private HttpServer server;

    @AfterEach
    void tearDown() {
        if (server != null) server.stop(0);
        RoleShareService.resetInstance();
    }

    @Test
    void publishReturnsCodeAndOwnerTokenFromServerResponse() throws Exception {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/packs", exchange -> {
            byte[] resp = "{\"code\":\"AB3D9F2K\",\"ownerToken\":\"cafe0123\"}"
                    .getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, resp.length);
            exchange.getResponseBody().write(resp);
            exchange.close();
        });
        server.start();

        RoleShareService service = RoleShareService.createForTest(baseUrl());
        Role role = new Role();
        role.id = "x"; role.name = "Tank";
        RoleShareService.PublishResult result = service.publish(new RolePack("Pack", List.of(role)));

        assertEquals("AB3D9F2K", result.code());
        assertEquals("cafe0123", result.ownerToken());
    }

    @Test
    void publishToleratesMissingOwnerToken() throws Exception {
        // An older server that predates updates returns only the code.
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/packs", exchange -> {
            byte[] resp = "{\"code\":\"AB3D9F2K\"}".getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, resp.length);
            exchange.getResponseBody().write(resp);
            exchange.close();
        });
        server.start();

        RoleShareService service = RoleShareService.createForTest(baseUrl());
        RoleShareService.PublishResult result = service.publish(new RolePack("Pack", List.of()));

        assertEquals("AB3D9F2K", result.code());
        assertNull(result.ownerToken());
    }

    @Test
    void updateSendsPutWithOwnerTokenHeader() throws Exception {
        var method = new java.util.concurrent.atomic.AtomicReference<String>();
        var token  = new java.util.concurrent.atomic.AtomicReference<String>();
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/packs/AB3D9F2K", exchange -> {
            method.set(exchange.getRequestMethod());
            token.set(exchange.getRequestHeaders().getFirst("X-Owner-Token"));
            byte[] resp = "{\"code\":\"AB3D9F2K\"}".getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, resp.length);
            exchange.getResponseBody().write(resp);
            exchange.close();
        });
        server.start();

        RoleShareService service = RoleShareService.createForTest(baseUrl());
        service.update("ab3d9f2k", "cafe0123", new RolePack("Pack", List.of()));

        assertEquals("PUT", method.get());
        assertEquals("cafe0123", token.get());
    }

    @Test
    void updateMapsForbiddenToFriendlyError() throws Exception {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/packs/AB3D9F2K", exchange -> {
            exchange.sendResponseHeaders(403, -1);
            exchange.close();
        });
        server.start();

        RoleShareService service = RoleShareService.createForTest(baseUrl());
        IOException ex = assertThrows(IOException.class,
                () -> service.update("AB3D9F2K", "wrong", new RolePack(null, List.of())));
        assertTrue(ex.getMessage().contains("can't be updated"));
    }

    @Test
    void updateMapsNotFoundToNoSuchElement() throws Exception {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/packs/AB3D9F2K", exchange -> {
            exchange.sendResponseHeaders(404, -1);
            exchange.close();
        });
        server.start();

        RoleShareService service = RoleShareService.createForTest(baseUrl());
        NoSuchElementException ex = assertThrows(NoSuchElementException.class,
                () -> service.update("AB3D9F2K", "cafe0123", new RolePack(null, List.of())));
        assertTrue(ex.getMessage().contains("expired"));
    }

    @Test
    void updateRejectsMissingTokenOrBadCodeWithoutNetworkCall() {
        // Deliberately unroutable base URL — a network attempt would fail differently.
        RoleShareService service = RoleShareService.createForTest("http://127.0.0.1:1");
        assertThrows(IllegalArgumentException.class,
                () -> service.update("AB3D9F2K", null, new RolePack(null, List.of())));
        assertThrows(IllegalArgumentException.class,
                () -> service.update("AB3D9F2K", "", new RolePack(null, List.of())));
        assertThrows(IllegalArgumentException.class,
                () -> service.update("bad code", "cafe0123", new RolePack(null, List.of())));
    }

    @Test
    void fetchReturnsDeserializedPack() throws Exception {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/packs/AB3D9F2K", exchange -> {
            byte[] resp = "{\"schemaVersion\":1,\"label\":\"Pack\",\"roles\":[{\"id\":\"x\",\"name\":\"Tank\",\"frontline\":true,\"targetStats\":[0,0,0,0,0,0,0,0],\"priority\":[3,3,3,3,3,3,3,3]}]}"
                    .getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, resp.length);
            exchange.getResponseBody().write(resp);
            exchange.close();
        });
        server.start();

        RoleShareService service = RoleShareService.createForTest(baseUrl());
        RolePack pack = service.fetch("AB3D9F2K");

        assertEquals("Pack", pack.label);
        assertEquals(1, pack.roles.size());
        assertEquals("Tank", pack.roles.get(0).name);
    }

    @Test
    void fetchThrowsOnNotFound() throws Exception {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/packs/ZZZZZZZZ", exchange -> {
            exchange.sendResponseHeaders(404, -1);
            exchange.close();
        });
        server.start();

        RoleShareService service = RoleShareService.createForTest(baseUrl());
        assertThrows(NoSuchElementException.class, () -> service.fetch("ZZZZZZZZ"));
    }

    @Test
    void fetchNormalizesCaseAndWhitespace() throws Exception {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/packs/AB3D9F2K", exchange -> {
            byte[] resp = "{\"schemaVersion\":1,\"roles\":[]}".getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, resp.length);
            exchange.getResponseBody().write(resp);
            exchange.close();
        });
        server.start();

        RoleShareService service = RoleShareService.createForTest(baseUrl());
        RolePack pack = service.fetch("  ab3d9f2k  ");
        assertNotNull(pack);
    }

    @Test
    void fetchRejectsMalformedCodeWithoutNetworkCall() {
        // Deliberately unroutable base URL — a network attempt would fail differently.
        RoleShareService service = RoleShareService.createForTest("http://127.0.0.1:1");
        assertThrows(IllegalArgumentException.class, () -> service.fetch("bad code/../x"));
        assertThrows(IllegalArgumentException.class, () -> service.fetch(""));
        assertThrows(IllegalArgumentException.class, () -> service.fetch(null));
    }

    @Test
    void normalizeCodeAcceptsValidRejectsInvalid() {
        assertEquals("AB3D9F2K", RoleShareService.normalizeCode(" ab3d9f2k "));
        assertEquals("ZZZZZZZZ", RoleShareService.normalizeCode("zzzzzzzz"));
        assertNull(RoleShareService.normalizeCode(null));
        assertNull(RoleShareService.normalizeCode(""));
        assertNull(RoleShareService.normalizeCode("AB3D9F2"));      // too short
        assertNull(RoleShareService.normalizeCode("AB3D9F2KX"));    // too long
        assertNull(RoleShareService.normalizeCode("AB3D9F0K"));     // 0 not in alphabet
        assertNull(RoleShareService.normalizeCode("AB3D9FIK"));     // I not in alphabet
        assertNull(RoleShareService.normalizeCode("AB3D 9F2K"));    // inner whitespace
    }

    @Test
    void fetchSanitizesHostilePackContents() throws Exception {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/packs/AB3D9F2K", exchange -> {
            byte[] resp = "{\"schemaVersion\":1,\"roles\":[null,{\"id\":\"x\",\"name\":null}]}"
                    .getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, resp.length);
            exchange.getResponseBody().write(resp);
            exchange.close();
        });
        server.createContext("/packs/CD3D9F2K", exchange -> {
            byte[] resp = "{\"schemaVersion\":1,\"roles\":null}".getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, resp.length);
            exchange.getResponseBody().write(resp);
            exchange.close();
        });
        server.start();

        RoleShareService service = RoleShareService.createForTest(baseUrl());

        RolePack withNullEntry = service.fetch("AB3D9F2K");
        assertEquals(1, withNullEntry.roles.size());          // null element dropped
        assertEquals("", withNullEntry.roles.get(0).name);    // missing name defaulted

        RolePack withNullList = service.fetch("CD3D9F2K");
        assertNotNull(withNullList.roles);
        assertTrue(withNullList.roles.isEmpty());
    }

    // ---- sanitize (direct) --------------------------------------------------

    @Test
    void sanitizeNormalizesStatArrays() throws Exception {
        Role wrongLengths = new Role();
        wrongLengths.name = "A";
        wrongLengths.targetStats = new int[]{1, 2};          // wrong length
        wrongLengths.priority = new int[]{1};                // wrong length

        Role badValues = new Role();
        badValues.name = "B";
        badValues.targetStats = new int[]{-5, 0, 1, 2, 3, 4, 5, 1000};   // negative + oversized thresholds
        badValues.priority = new int[]{0, 9, 2, 3, 1, -1, 3, 3};      // out-of-range priorities

        RolePack pack = sanitizedPackOf(wrongLengths, badValues);

        Role a = pack.roles.get(0);
        assertArrayEquals(new int[8], a.targetStats);
        assertArrayEquals(new int[]{3, 3, 3, 3, 3, 3, 3, 3}, a.priority);

        Role b = pack.roles.get(1);
        assertArrayEquals(new int[]{0, 0, 1, 2, 3, 4, 5, 999}, b.targetStats);
        assertArrayEquals(new int[]{1, 3, 2, 3, 1, 1, 3, 3}, b.priority);
    }

    @Test
    void sanitizeTruncatesOverLongName() throws Exception {
        Role r = new Role();
        r.name = "X".repeat(200);
        RolePack pack = sanitizedPackOf(r);
        assertEquals(60, pack.roles.get(0).name.length());
    }

    @Test
    void sanitizeFiltersPerkPlanToValidEntriesOnly() throws Exception {
        Role r = new Role();
        r.name = "A";
        java.util.Map<String, String> plan = new java.util.LinkedHashMap<>();
        plan.put("AAAA1111", "PLANNED");
        plan.put("BBBB2222", "OPTIONAL");
        plan.put("CCCC3333", "NOT_A_VALID_STATUS");
        plan.put(null, "PLANNED");
        r.perkPlanTemplate = plan;

        RolePack pack = sanitizedPackOf(r);
        java.util.Map<String, String> cleaned = pack.roles.get(0).perkPlanTemplate;
        assertEquals(java.util.Map.of("AAAA1111", "PLANNED", "BBBB2222", "OPTIONAL"), cleaned);
    }

    @Test
    void sanitizeDropsPerkPlanEntirelyWhenNothingValidSurvives() throws Exception {
        Role r = new Role();
        r.name = "A";
        r.perkPlanTemplate = new java.util.LinkedHashMap<>(java.util.Map.of("AAAA1111", "BOGUS"));

        RolePack pack = sanitizedPackOf(r);
        assertNull(pack.roles.get(0).perkPlanTemplate);
    }

    @Test
    void sanitizeCapsPerkPlanEntryCount() throws Exception {
        Role r = new Role();
        r.name = "A";
        java.util.Map<String, String> plan = new java.util.LinkedHashMap<>();
        for (int i = 0; i < 150; i++) {
            plan.put("PERK" + i, i % 2 == 0 ? "PLANNED" : "OPTIONAL");
        }
        r.perkPlanTemplate = plan;

        RolePack pack = sanitizedPackOf(r);
        assertEquals(100, pack.roles.get(0).perkPlanTemplate.size());
    }

    @Test
    void sanitizeRejectsNewerSchemaVersion() {
        RolePack pack = new RolePack("Future", List.of());
        pack.schemaVersion = RolePack.SCHEMA_VERSION + 1;
        IOException ex = assertThrows(IOException.class, () -> RoleShareService.sanitize(pack));
        assertTrue(ex.getMessage().contains("newer version"));
    }

    @Test
    void sanitizeTruncatesOversizedPackAndFlagsIt() throws Exception {
        List<Role> many = new java.util.ArrayList<>();
        for (int i = 0; i < 150; i++) {
            Role r = new Role();
            r.name = "Role " + i;
            many.add(r);
        }
        RolePack pack = RoleShareService.sanitize(new RolePack(null, many));

        assertEquals(100, pack.roles.size());
        assertTrue(pack.truncated());

        RolePack small = sanitizedPackOf(new Role());
        assertFalse(small.truncated());
    }

    private static RolePack sanitizedPackOf(Role... roles) throws IOException {
        return RoleShareService.sanitize(new RolePack(null, new java.util.ArrayList<>(List.of(roles))));
    }

    private String baseUrl() {
        return "http://127.0.0.1:" + server.getAddress().getPort();
    }
}
