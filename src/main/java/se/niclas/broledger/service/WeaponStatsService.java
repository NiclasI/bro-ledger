package se.niclas.broledger.service;

import tools.jackson.core.StreamReadFeature;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;
import se.niclas.broledger.model.WeaponStats;

import java.io.IOException;
import java.io.InputStream;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

public class WeaponStatsService {

    private static WeaponStatsService instance;

    private final Map<String, WeaponStats> entries = new HashMap<>(200);
    private final ObjectMapper mapper = JsonMapper.builder()
            .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
            .enable(StreamReadFeature.INCLUDE_SOURCE_IN_LOCATION)
            .build();

    private WeaponStatsService() {}

    public static WeaponStatsService getInstance() {
        if (instance == null) instance = new WeaponStatsService();
        return instance;
    }

    public void load(InputStream json) throws IOException {
        Map<String, WeaponStats> raw = mapper.readValue(json,
                new TypeReference<Map<String, WeaponStats>>() {});
        raw.forEach((k, v) -> entries.put(k.toUpperCase(), v));
    }

    public void loadFromClasspath() throws IOException {
        try (InputStream is = WeaponStatsService.class
                .getResourceAsStream("/se/niclas/broledger/data/weapon-stats.json")) {
            if (is == null) throw new IOException("weapon-stats.json not found in classpath");
            load(is);
        }
    }

    public WeaponStats get(String hexId) {
        return hexId != null ? entries.get(hexId.toUpperCase()) : null;
    }

    public Integer getTier(String hexId) {
        WeaponStats s = get(hexId);
        return s != null ? s.tier : null;
    }

    public String getWeaponClass(String hexId) {
        WeaponStats s = get(hexId);
        return s != null ? s.weaponClass : null;
    }

    public Map<String, WeaponStats> getAll() {
        return Collections.unmodifiableMap(entries);
    }
}
