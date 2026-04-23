package se.niclas.broledger.service;

import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;

/**
 * Resolves a displayable item to a game-art relative path using the
 * image-map.json produced by se.niclas.broledger.tools.assets.ImageMapGenerator.
 *
 * Resolution priority (first match wins):
 *  1. slotHexIcon[slot:hexId:icon]   — named-item icon override
 *  2. hexDirect[hexId]               — perks / traits / backgrounds / direct hex mappings
 *  3. slotIcon[slot:icon:iconSet]     — slot+icon with iconSet qualifier
 *  4. slotIcon[slot:icon]            — generic slot+icon
 *  5. null (caller should use placeholder)
 */
public class ImageMapService {

    private static ImageMapService instance;

    private Map<String, String> hexDirect      = new HashMap<>();
    private Map<String, String> slotIcon       = new HashMap<>();
    private Map<String, String> slotHexIcon    = new HashMap<>();
    private Map<String, String> slotHexHouse   = new HashMap<>();
    private Map<String, String> slotAttachment = new HashMap<>();

    private ImageMapService() {}

    public static ImageMapService getInstance() {
        if (instance == null) instance = new ImageMapService();
        return instance;
    }

    public void loadFromClasspath() throws IOException {
        try (InputStream is = ImageMapService.class
                .getResourceAsStream("/se/niclas/broledger/data/image-map.json")) {
            if (is == null) throw new IOException("image-map.json not found in classpath");
            load(is);
        }
    }

    public void load(InputStream json) throws IOException {
        ObjectMapper mapper = new ObjectMapper();
        JsonNode root = mapper.readTree(json);
        hexDirect      = toMap(mapper, root.get("hexDirect"));
        slotIcon       = toMap(mapper, root.get("slotIcon"));
        slotHexIcon    = toMap(mapper, root.get("slotHexIcon"));
        slotHexHouse   = toMap(mapper, root.get("slotHexHouse"));
        slotAttachment = toMap(mapper, root.get("slotAttachment"));
    }

    /**
     * Resolve an equipped item to a game-art path.
     *
     * @param slot     slot name ("body", "helmet", "weapon", …)
     * @param hexId    8-char uppercase hex item ID (may be null)
     * @param icon     icon index from save data / dictionary
     * @param iconSet  iconSet from dictionary entry (may be null)
     * @return relative path from game-art root, or null if not mapped
     */
    public String resolve(String slot, String hexId, int icon, String iconSet) {
        if (slot != null && hexId != null) {
            String byHexIcon = slotHexIcon.get(slot + ":" + hexId + ":" + icon);
            if (byHexIcon != null) return byHexIcon;
        }
        if (hexId != null) {
            String direct = hexDirect.get(hexId);
            if (direct != null) return direct;
        }
        if (slot != null) {
            if (iconSet != null) {
                String withSet = slotIcon.get(slot + ":" + icon + ":" + iconSet);
                if (withSet != null) return withSet;
            }
            return slotIcon.get(slot + ":" + icon);
        }
        return null;
    }

    /** Resolve perks, traits, and backgrounds (hex-direct only). */
    public String resolveHex(String hexId) {
        if (hexId == null) return null;
        return hexDirect.get(hexId.toUpperCase());
    }

    /** Resolve an armor attachment overlay. */
    public String resolveAttachment(String slot, String attachmentHexId) {
        if (slot == null || attachmentHexId == null) return null;
        return slotAttachment.get(slot + ":" + attachmentHexId.toUpperCase());
    }

    /** Resolve faction armor by slot + item hex ID + house index. */
    public String resolveHexHouse(String slot, String hexId, int house) {
        if (slot == null || hexId == null) return null;
        return slotHexHouse.get(slot + ":" + hexId.toUpperCase() + ":" + house);
    }

    private static Map<String, String> toMap(ObjectMapper mapper, JsonNode node) {
        if (node == null || node.isNull()) return new HashMap<>();
        return mapper.convertValue(node, new TypeReference<Map<String, String>>() {});
    }
}
