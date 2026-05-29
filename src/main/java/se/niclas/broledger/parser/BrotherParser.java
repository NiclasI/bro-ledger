package se.niclas.broledger.parser;

import se.niclas.broledger.model.Brother;
import se.niclas.broledger.model.InventorySlot;
import se.niclas.broledger.model.TraitEntry;
import se.niclas.broledger.service.DictionaryService;
import se.niclas.broledger.util.HexUtils;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.logging.Logger;

/**
 * Parses a single battleBrother from a positioned HexReader.
 * Direct port of the battleBrother constructor in classes.js.
 */
public class BrotherParser {

    private static final Logger log = Logger.getLogger(BrotherParser.class.getName());

    // Wildman and Barbarian background IDs — have an extra tattoo byte
    private static final String WILDMAN   = "6DF381C6";
    private static final String BARBARIAN = "CB90AA90";

    private final DictionaryService dict;
    private final ItemParser itemParser;

    public BrotherParser(DictionaryService dict, java.util.List<String> warnings) {
        this.dict       = dict;
        this.itemParser = new ItemParser(dict, warnings);
    }

    public Brother parse(HexReader reader, String fullHexData) {
        Brother b = new Brother();
        parseVisualLayers(reader, b);
        b.header = parseHeader(reader, fullHexData);
        parseActionPointsAndStats(reader, b);
        if (!parseInventory(reader, b)) return b; // partial parse on unknown item
        int circleCount = parseCircles(reader, b);
        parseDescriptions(reader, b);
        parseTraits(reader, b, circleCount);
        parseNameAndLevelData(reader, b);
        parseMoraleModifiers(reader, b);
        parseStarsAndTalents(reader, b);
        b.fingerprint = computeFingerprint(b);
        return b;
    }

    private void parseVisualLayers(HexReader reader, Brother b) {
        int layerCount = reader.readUInt8();
        for (int i = 0; i < layerCount; i++) {
            String key   = reader.readString();
            String value = reader.readHexBytes(SaveFormat.VISUAL_LAYER_VALUE_BYTES);
            String sep   = reader.readHexBytes(SaveFormat.VISUAL_LAYER_SEP_BYTES);
            b.visualLayerMap.put(key, new Brother.VisualLayer(value, sep));
        }
    }

    private String parseHeader(HexReader reader, String fullHexData) {
        int start = reader.getCursor();
        int memberCount = reader.readInt16LE();
        for (int i = 0; i < memberCount; i++) {
            int nameLen = reader.readInt16LE();
            reader.skip(nameLen);
            String memberType = reader.readHexBytes(1);
            if ("02".equals(memberType)) {
                reader.skip(1);
            } else if ("03".equals(memberType)) {
                reader.skip(reader.readUInt16LE());
            } else {
                reader.skip(4);
            }
        }
        return fullHexData.substring(start, reader.getCursor()).toUpperCase();
    }

    private void parseActionPointsAndStats(HexReader reader, Brother b) {
        b.actionPoints = reader.readUInt8();
        for (int i = 0; i < 8; i++) {
            b.stats[i] = reader.readInt16LE();
        }
        b.greedAndGluttony = reader.readHexBytes(SaveFormat.GREED_GLUTTONY_BYTES);
        b.pouchCount       = reader.readUInt8();
    }

    /** Returns false if an unknown item ID is encountered (signals a partial parse). */
    private boolean parseInventory(HexReader reader, Brother b) {
        int itemCount = reader.readUInt8();
        for (int i = 0; i < itemCount; i++) {
            int slotIndex = reader.readUInt8();
            String itemId = reader.readHexBytes(4);

            InventorySlot slot = new InventorySlot(slotIndex);
            slot.itemId = itemId;

            if (!dict.has(itemId)) {
                log.warning("BrotherParser: unknown item id " + itemId
                        + " in slot " + slotIndex + " — skipping brother remainder");
                return false;
            }

            slot.itemType = dict.getType(itemId);
            itemParser.parse(reader, slot);

            if (slotIndex == 6) {
                b.extraPouches.add(slot);
            } else if (slotIndex < 7) {
                b.equippedSlots[slotIndex] = slot;
            }
        }
        return true;
    }

    /** Reads perks and background; returns the raw circleCount for the subsequent trait pass. */
    private int parseCircles(HexReader reader, Brother b) {
        int circleCount = reader.readUInt16LE();
        for (int i = 0; i < circleCount; i++) {
            String hexId = reader.readHexBytes(4);
            reader.skip(1); // isNew byte

            if (!dict.has(hexId)) {
                log.warning("BrotherParser: unknown circle id " + hexId);
                continue;
            }

            String type = dict.getType(hexId);
            if ("background".equals(type)) {
                b.backgroundHexId = hexId;
                break;
            }
            b.perkIds.add(hexId);
        }
        return circleCount;
    }

    private void parseDescriptions(HexReader reader, Brother b) {
        b.description         = reader.readString();
        b.descriptionTemplate = reader.readString();
        b.unknown             = reader.readHexBytes(2);
        b.salaryMultiplier    = reader.readFloatLE();

        // Wildman and Barbarian backgrounds carry an extra tattoo byte here
        if (hasTattooByte(b.backgroundHexId)) {
            b.tattoo = reader.readHexBytes(1);
        }
    }

    private void parseTraits(HexReader reader, Brother b, int circleCount) {
        // Remaining circle entries after perks + background
        for (int i = b.perkIds.size() + 1; i < circleCount; i++) {
            String traitId = reader.readHexBytes(4);

            if (!dict.has(traitId)) {
                log.warning("BrotherParser: unknown trait id " + traitId);
                b.traits.add(new TraitEntry(traitId, null));
                continue;
            }

            String traitType = dict.getType(traitId);

            if (!"injury".equals(traitType)) {
                reader.skip(1); // isNew byte (injuries have no isNew)
            }

            String eventData = switch (traitType) {
                case "injury"    -> reader.readHexBytes(SaveFormat.INJURY_EVENT_BYTES);
                case "training"  -> reader.readHexBytes(SaveFormat.TRAINING_EVENT_BYTES);
                case "knowledge" -> reader.readHexBytes(SaveFormat.KNOWLEDGE_EVENT_BYTES);
                case "learning"  -> reader.readHexBytes(SaveFormat.LEARNING_EVENT_BYTES);
                default          -> null;
            };

            b.traits.add(new TraitEntry(traitId, eventData));
        }

        // Entries the game mis-typed as traits but are actually perks — promote them
        b.traits.removeIf(trait -> {
            if ("perk".equals(dict.getType(trait.id))) {
                b.perkIds.add(trait.id);
                return true;
            }
            return false;
        });
    }

    private void parseNameAndLevelData(HexReader reader, Brother b) {
        b.name       = reader.readString();
        b.title      = reader.readString();
        b.lightWound = reader.readFloatLE();
        b.experience = (int) reader.readUInt32LE();
        b.humanBytes  = reader.readHexBytes(SaveFormat.HUMAN_BYTES);
        b.levelTotal  = reader.readUInt8();
        b.perkPoints  = reader.readUInt8();
        b.perkUsed    = reader.readUInt8();
        b.levelPoints = reader.readUInt8();
        b.morale      = reader.readFloatLE();
    }

    private void parseMoraleModifiers(HexReader reader, Brother b) {
        String modCountHex = reader.readHexBytes(1);
        int modCount = Integer.parseInt(modCountHex, 16);
        StringBuilder sb = new StringBuilder(modCountHex);
        for (int i = 0; i < modCount; i++) {
            sb.append(reader.readHexBytes(1));
            int textLength = reader.readUInt16LE();
            // Re-encode textLength as 2-byte LE hex for blob storage
            sb.append(HexUtils.uint16ToHexLE(textLength));
            sb.append(reader.readHexBytes(textLength + SaveFormat.MORALE_ENTRY_TAIL_BYTES));
        }
        sb.append(reader.readHexBytes(SaveFormat.MORALE_BLOCK_TAIL_BYTES));
        b.moraleModifierHex = sb.toString();
    }

    private void parseStarsAndTalents(HexReader reader, Brother b) {
        for (int i = 0; i < 8; i++) {
            b.stars[i] = reader.readUInt8();
        }
        // Each talent group: 1-byte count followed by that many raw talent bytes
        for (int i = 0; i < 8; i++) {
            b.talentPoints   = reader.readUInt8(); // last value persists on the model
            b.talentBytes[i] = reader.readHexBytes(b.talentPoints);
        }
    }

    private static String computeFingerprint(Brother b) {
        String input = fingerprintInput(b.name, b.backgroundHexId, b.stars);
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] hash = md.digest(input.getBytes(StandardCharsets.UTF_8));
            // First 16 bytes → 32 hex chars
            return HexFormat.of().formatHex(hash, 0, 16).toUpperCase();
        } catch (NoSuchAlgorithmException e) {
            return input.hashCode() + "";
        }
    }

    /** Returns true for backgrounds that carry an extra tattoo byte in the save format. */
    static boolean hasTattooByte(String backgroundHexId) {
        return WILDMAN.equals(backgroundHexId) || BARBARIAN.equals(backgroundHexId);
    }

    /** Builds the canonical fingerprint input string from a brother's identity fields. */
    static String fingerprintInput(String name, String backgroundHexId, int[] stars) {
        StringBuilder sb = new StringBuilder(name).append('|').append(backgroundHexId).append('|');
        for (int s : stars) sb.append(s);
        return sb.toString();
    }
}
