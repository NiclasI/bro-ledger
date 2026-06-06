package se.niclas.broledger.service;

import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.MissingNode;

import java.io.IOException;
import java.io.InputStream;

/**
 * Central loader for the unified game-data.json.
 *
 * <p>All other data services call {@link #loadFromClasspath()} (idempotent) and
 * then pull their slice via the accessor methods. The root node is cached after
 * the first load; subsequent calls are no-ops.
 *
 * <p>Sections:
 * <ul>
 *   <li>{@link #getEntries()} — keyed by upper-cased hex ID; each entry carries
 *       common fields ({@code name, type, slot, …}) plus typed sub-objects
 *       ({@code weapon}, {@code modifier}, {@code base}).</li>
 *   <li>{@link #getImageSlots()} — all image path mappings: {@code hexDirect}
 *       (hex ID → archive src path) plus four composite-key slot maps
 *       ({@code slotIcon}, {@code slotHexIcon}, {@code slotHexHouse},
 *       {@code slotAttachment}).</li>
 * </ul>
 */
public class GameDataService {

    private static GameDataService instance;

    private final ObjectMapper mapper = JsonMapper.builder()
            .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
            .build();

    /** Cached root; null until first successful load. */
    private JsonNode root;

    private GameDataService() {}

    public static GameDataService getInstance() {
        if (instance == null) instance = new GameDataService();
        return instance;
    }

    /**
     * Parses game-data.json from the classpath. Idempotent — subsequent calls
     * are no-ops if the file was already loaded successfully.
     */
    public void loadFromClasspath() throws IOException {
        if (root != null) return;
        try (InputStream is = GameDataService.class
                .getResourceAsStream("/se/niclas/broledger/data/game-data.json")) {
            if (is == null) throw new IOException("game-data.json not found in classpath");
            root = mapper.readTree(is);
        }
    }

    /** All item/perk/trait/background entries keyed by upper-cased hex ID. */
    public JsonNode getEntries() {
        return safeGet("entries");
    }

    /**
     * Composite-key image maps for slot-based equipment art.
     * Contains: {@code slotIcon}, {@code slotHexIcon}, {@code slotHexHouse},
     * {@code slotAttachment}.
     */
    public JsonNode getImageSlots() {
        return safeGet("imageSlots");
    }

    /**
     * Monotonically increasing version of the asset extraction map.
     * Written to {@code game-art-version.json} after extraction; compared on
     * startup to detect stale installs. Returns 0 if the field is absent.
     */
    public int getGameArtVersion() {
        JsonNode node = safeGet("gameArtVersion");
        return node.isMissingNode() ? 0 : node.asInt(0);
    }

    private JsonNode safeGet(String field) {
        if (root == null) return MissingNode.getInstance();
        JsonNode node = root.get(field);
        return node != null ? node : MissingNode.getInstance();
    }
}
