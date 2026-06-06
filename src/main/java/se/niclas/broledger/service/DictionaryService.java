package se.niclas.broledger.service;

import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;
import se.niclas.broledger.model.DictionaryEntry;

import java.io.IOException;
import java.io.InputStream;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class DictionaryService {

    private static DictionaryService instance;

    private final Map<String, DictionaryEntry> entries = new HashMap<>(1200);
    private final ObjectMapper mapper = JsonMapper.builder()
            .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
            .build();

    private DictionaryService() {}

    public static DictionaryService getInstance() {
        if (instance == null) instance = new DictionaryService();
        return instance;
    }

    /**
     * Loads entries from an {@code InputStream} in the legacy
     * {@code [[hexId, {fields}], …]} format. Used as a testing seam.
     */
    public void load(InputStream json) throws IOException {
        // Format: [[hexId, {fields}], ...]
        List<List<JsonNode>> raw = mapper.readValue(json,
                new TypeReference<List<List<JsonNode>>>() {});

        for (List<JsonNode> pair : raw) {
            String id = pair.get(0).asText().toUpperCase();
            DictionaryEntry entry = mapper.treeToValue(pair.get(1), DictionaryEntry.class);
            entries.put(id, entry);
        }
    }

    public void loadFromClasspath() throws IOException {
        GameDataService gds = GameDataService.getInstance();
        gds.loadFromClasspath();
        loadFromEntries(gds.getEntries());
    }

    /**
     * Populates this service from the {@code entries} node of game-data.json.
     * Each entry's top-level fields map to {@link DictionaryEntry}; inline item
     * stats are carried in the optional {@code base} sub-object.
     */
    void loadFromEntries(JsonNode entriesNode) {
        if (entriesNode == null || entriesNode.isMissingNode()) return;
        Map<String, JsonNode> entryMap = mapper.convertValue(
                entriesNode, new TypeReference<Map<String, JsonNode>>() {});
        entryMap.forEach((key, node) -> {
            String hexId = key.toUpperCase();

            // Flatten top-level + base sub-object into one ObjectNode for DictionaryEntry
            ObjectNode flat = mapper.createObjectNode();
            Map<String, JsonNode> nodeFields = mapper.convertValue(
                    node, new TypeReference<Map<String, JsonNode>>() {});
            nodeFields.forEach((k, v) -> {
                if (!"base".equals(k) && !"weapon".equals(k)
                        && !"modifier".equals(k) && !"assets".equals(k)) {
                    flat.set(k, v);
                }
            });

            // Merge base sub-object fields into the flat node
            JsonNode base = node.get("base");
            if (base != null && !base.isMissingNode()) {
                Map<String, JsonNode> baseFields = mapper.convertValue(
                        base, new TypeReference<Map<String, JsonNode>>() {});
                baseFields.forEach(flat::set);
            }

            try {
                DictionaryEntry entry = mapper.treeToValue(flat, DictionaryEntry.class);
                entries.put(hexId, entry);
            } catch (Exception ex) {
                throw new RuntimeException("Failed to parse entry " + hexId, ex);
            }
        });
    }

    public boolean has(String hexId) {
        return entries.containsKey(hexId.toUpperCase());
    }

    public DictionaryEntry get(String hexId) {
        return entries.get(hexId.toUpperCase());
    }

    public String getType(String hexId) {
        DictionaryEntry e = get(hexId);
        return e != null ? e.type : null;
    }

    public String getName(String hexId) {
        DictionaryEntry e = get(hexId);
        return e != null ? e.name : hexId;
    }

    /** All entries sorted by name, filtered by type. */
    public List<Map.Entry<String, DictionaryEntry>> getAllByType(String type) {
        return entries.entrySet().stream()
                .filter(e -> type.equals(e.getValue().type))
                .sorted(Comparator.comparing(e -> e.getValue().name,
                        Comparator.nullsLast(Comparator.naturalOrder())))
                .toList();
    }

    public Map<String, DictionaryEntry> getAll() {
        return Collections.unmodifiableMap(entries);
    }
}
