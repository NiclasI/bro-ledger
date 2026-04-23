package se.niclas.broledger.service;

import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;
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
        try (InputStream is = DictionaryService.class
                .getResourceAsStream("/se/niclas/broledger/data/dictionary.json")) {
            if (is == null) throw new IOException("dictionary.json not found in classpath");
            load(is);
        }
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
