package se.niclas.broledger.service;

import se.niclas.broledger.model.Role;
import se.niclas.broledger.model.RolePack;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.regex.Pattern;

/**
 * Publishes and fetches role packs against the serverless role-share store.
 * The store is opaque JSON keyed by a short share code — see server/ (Cloudflare Worker).
 */
public class RoleShareService {

    /** Public base URL of the deployed role-share Worker. Swappable for tests via createForTest. */
    private static final String DEFAULT_BASE_URL = "https://bro-ledger-roles.niclas-cf8.workers.dev";

    /** Mirrors the Worker's code format: 8 chars from a 32-char alphabet (no 0/O/1/I). */
    private static final Pattern CODE_PATTERN = Pattern.compile("[23456789ABCDEFGHJKLMNPQRSTUVWXYZ]{8}");

    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(20);

    /** Sanity cap on roles accepted from a fetched pack — the store's contents are untrusted. */
    private static final int MAX_PACK_ROLES = 100;

    /** Sanity cap on a role's name length in a fetched pack. */
    private static final int MAX_ROLE_NAME_LENGTH = 60;

    /** Sanity cap on the number of perk-plan entries accepted from a fetched pack. */
    private static final int MAX_PERK_PLAN_ENTRIES = 100;

    /** Sanity cap on a target-stat threshold accepted from a fetched pack. */
    private static final int MAX_TARGET_STAT = 999;

    private static final ObjectMapper MAPPER = JsonMapper.builder()
            .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
            .build();

    private static RoleShareService instance;

    private final String     baseUrl;
    private final HttpClient client;

    private RoleShareService(String baseUrl) {
        this.baseUrl = baseUrl;
        this.client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build();
    }

    public static RoleShareService getInstance() {
        if (instance == null) {
            String override = System.getProperty("broledger.roleShareBaseUrl");
            instance = new RoleShareService(override != null && !override.isBlank() ? override : DEFAULT_BASE_URL);
        }
        return instance;
    }

    /** For tests only — creates a fresh instance pointed at a local stub URL. */
    static RoleShareService createForTest(String baseUrl) {
        instance = new RoleShareService(baseUrl);
        return instance;
    }

    /** For tests only — resets the singleton so the next getInstance() call starts fresh. */
    static void resetInstance() {
        instance = null;
    }

    /**
     * Normalizes raw user input to canonical share-code form (trimmed, upper-cased),
     * or returns {@code null} when the input cannot be a valid code.
     */
    public static String normalizeCode(String raw) {
        if (raw == null) return null;
        String code = raw.trim().toUpperCase(Locale.ROOT);
        return CODE_PATTERN.matcher(code).matches() ? code : null;
    }

    /**
     * Result of a successful publish: the share code, plus the server-issued owner token
     * that authorizes {@link #update} on that code — or a null token when the server
     * predates updates (the pack is then immutable).
     */
    public record PublishResult(String code, String ownerToken) {}

    /**
     * Publishes a role pack and returns its share code and owner token.
     * Blocking — callers must invoke this off the JavaFX application thread.
     */
    public PublishResult publish(RolePack pack) throws IOException, InterruptedException {
        String body = MAPPER.writeValueAsString(pack);
        HttpRequest request = HttpRequest.newBuilder(URI.create(baseUrl + "/packs"))
                .header("Content-Type", "application/json")
                .timeout(REQUEST_TIMEOUT)
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build();
        HttpResponse<String> response = send(request);
        if (response.statusCode() != 200) {
            throw new IOException("Publish failed: HTTP " + response.statusCode());
        }
        JsonNode node = MAPPER.readTree(response.body());
        JsonNode codeNode = node.get("code");
        if (codeNode == null) throw new IOException("Publish failed: no code in response");
        JsonNode tokenNode = node.get("ownerToken");
        return new PublishResult(codeNode.asText(), tokenNode == null ? null : tokenNode.asText());
    }

    /**
     * Overwrites the pack stored under {@code code} with new contents, authorized by the
     * owner token issued when the code was first published. Everyone holding the code
     * sees the new contents on their next fetch.
     * Blocking — callers must invoke this off the JavaFX application thread.
     */
    public void update(String rawCode, String ownerToken, RolePack pack)
            throws IOException, InterruptedException {
        String code = normalizeCode(rawCode);
        if (code == null) {
            throw new IllegalArgumentException("Not a valid share code: " + rawCode);
        }
        if (ownerToken == null || ownerToken.isEmpty()) {
            throw new IllegalArgumentException("No owner token for code " + code);
        }
        String body = MAPPER.writeValueAsString(pack);
        HttpRequest request = HttpRequest.newBuilder(URI.create(baseUrl + "/packs/" + code))
                .header("Content-Type", "application/json")
                .header("X-Owner-Token", ownerToken)
                .timeout(REQUEST_TIMEOUT)
                .PUT(HttpRequest.BodyPublishers.ofString(body))
                .build();
        HttpResponse<String> response = send(request);
        if (response.statusCode() == 404) {
            throw new NoSuchElementException(
                    "Code " + code + " has expired — publish as a new code instead.");
        }
        if (response.statusCode() == 403) {
            throw new IOException("Code " + code + " can't be updated from this computer.");
        }
        if (response.statusCode() != 200) {
            throw new IOException("Update failed: HTTP " + response.statusCode());
        }
    }

    /**
     * Fetches a role pack by its share code. The code is normalized first; an input that
     * cannot be a valid code fails fast with {@link IllegalArgumentException}, without a
     * network round-trip (and without ever reaching URI construction).
     * Blocking — callers must invoke this off the JavaFX application thread.
     */
    public RolePack fetch(String rawCode) throws IOException, InterruptedException {
        String code = normalizeCode(rawCode);
        if (code == null) {
            throw new IllegalArgumentException("Not a valid share code: " + rawCode);
        }
        HttpRequest request = HttpRequest.newBuilder(URI.create(baseUrl + "/packs/" + code))
                .timeout(REQUEST_TIMEOUT)
                .GET()
                .build();
        HttpResponse<String> response = send(request);
        if (response.statusCode() == 404) {
            throw new NoSuchElementException("No role pack found for code: " + code);
        }
        if (response.statusCode() != 200) {
            throw new IOException("Fetch failed: HTTP " + response.statusCode());
        }
        return sanitize(MAPPER.readValue(response.body(), RolePack.class));
    }

    /**
     * Hardens a fetched pack against malformed or hostile content — the store accepts any
     * well-formed JSON from anyone, so the deserialized shape cannot be trusted: {@code roles}
     * may be null or contain nulls, names may be missing, and the per-stat arrays may have
     * the wrong length (UI code indexes them 0–7) or out-of-range values. Packs declaring a
     * schema version newer than this build understands are rejected outright; oversized packs
     * are truncated and flagged via {@link RolePack#truncated()}.
     */
    static RolePack sanitize(RolePack pack) throws IOException {
        if (pack == null) throw new IOException("Fetch failed: empty pack");
        if (pack.schemaVersion > RolePack.SCHEMA_VERSION) {
            throw new IOException("This pack was made with a newer version of the app"
                    + " (pack schema " + pack.schemaVersion + ", supported " + RolePack.SCHEMA_VERSION + ")");
        }
        if (pack.roles == null) pack.roles = new ArrayList<>();
        pack.roles.removeIf(Objects::isNull);
        if (pack.roles.size() > MAX_PACK_ROLES) {
            pack.roles = new ArrayList<>(pack.roles.subList(0, MAX_PACK_ROLES));
            pack.markTruncated();
        }
        for (Role r : pack.roles) {
            if (r.name == null) r.name = "";
            if (r.name.length() > MAX_ROLE_NAME_LENGTH) r.name = r.name.substring(0, MAX_ROLE_NAME_LENGTH);
            r.targetStats = normalizedTargets(r.targetStats);
            r.priority = normalizedPriorities(r.priority);
            r.perkPlanTemplate = normalizedPerkPlan(r.perkPlanTemplate);
        }
        return pack;
    }

    /**
     * Length-8 guarantee for target stats; thresholds are clamped to
     * {@code [0, MAX_TARGET_STAT]} (negative = not set, oversized values capped).
     */
    private static int[] normalizedTargets(int[] a) {
        if (a == null || a.length != 8) return new int[8];
        for (int i = 0; i < a.length; i++) {
            if (a[i] < 0) a[i] = 0;
            else if (a[i] > MAX_TARGET_STAT) a[i] = MAX_TARGET_STAT;
        }
        return a;
    }

    /**
     * Keeps only well-formed perk-plan entries — non-null keys with a value of
     * {@code "OPTIONAL"} or {@code "PLANNED"} — capped at {@link #MAX_PERK_PLAN_ENTRIES}.
     * Returns null when the input is null/empty or nothing survives filtering.
     */
    private static Map<String, String> normalizedPerkPlan(Map<String, String> in) {
        if (in == null || in.isEmpty()) return null;
        Map<String, String> out = new LinkedHashMap<>();
        for (Map.Entry<String, String> e : in.entrySet()) {
            if (e.getKey() == null) continue;
            if (!"OPTIONAL".equals(e.getValue()) && !"PLANNED".equals(e.getValue())) continue;
            out.put(e.getKey(), e.getValue());
            if (out.size() >= MAX_PERK_PLAN_ENTRIES) break;
        }
        return out.isEmpty() ? null : out;
    }

    /** Length-8 guarantee for priorities; values clamped to the valid 1–3 range. */
    private static int[] normalizedPriorities(int[] a) {
        if (a == null || a.length != 8) {
            int[] d = new int[8];
            Arrays.fill(d, 3);
            return d;
        }
        for (int i = 0; i < a.length; i++) {
            a[i] = Math.max(1, Math.min(3, a[i]));
        }
        return a;
    }

    private HttpResponse<String> send(HttpRequest request) throws IOException, InterruptedException {
        return client.send(request, HttpResponse.BodyHandlers.ofString());
    }
}
