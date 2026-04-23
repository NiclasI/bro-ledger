package se.niclas.broledger.parser;

import se.niclas.broledger.model.DictionaryEntry;
import se.niclas.broledger.model.InventorySlot;
import se.niclas.broledger.model.ItemStats;
import se.niclas.broledger.service.DictionaryService;

import java.util.logging.Logger;

/**
 * Ports the JS Forge object. Each method reads item-specific bytes from the reader
 * and populates the slot's ItemStats. The reader cursor must be positioned immediately
 * after the item's 4-byte ID when these methods are called.
 */
public class ItemParser {

    private static final Logger logger = Logger.getLogger(ItemParser.class.getName());

    private final DictionaryService dict;
    private final java.util.List<String> warnings;

    public ItemParser(DictionaryService dict, java.util.List<String> warnings) {
        this.dict     = dict;
        this.warnings = warnings;
    }

    public void parse(HexReader reader, InventorySlot slot) {
        logger.fine("Parsing item: " + slot.itemId);
        slot.stats = new ItemStats();
        switch (ItemType.fromString(slot.itemType)) {
            case GENERIC_WEAPON -> genericWeapon(reader, slot);
            case NAMED_WEAPON   -> namedWeapon(reader, slot);
            case GENERIC_ARMOR  -> genericArmor(reader, slot);
            case NAMED_ARMOR    -> namedArmor(reader, slot);
            case GENERIC_HELMET -> genericHelmet(reader, slot);
            case NAMED_HELMET   -> namedHelmet(reader, slot);
            case GENERIC_SHIELD -> genericShield(reader, slot);
            case NAMED_SHIELD   -> namedShield(reader, slot);
            case AUXILIARY      -> auxiliary(reader, slot);
            case ATTACHMENT     -> attachment(reader, slot);
            case UNKNOWN        -> warnings.add("Unknown item type '" + slot.itemType + "' (id " + slot.itemId + ")");
        }
        slot.empty = false;
    }

    // -----------------------------------------------------------------------
    // Forge.generic
    // -----------------------------------------------------------------------
    private void generic(HexReader reader, InventorySlot slot) {
        ItemStats s = slot.stats;
        s.repair     = reader.readHexBytes(1);
        s.icon       = reader.readUInt16LE();
        s.durability = reader.readFloatLE();
        reader.skip(SaveFormat.QUANTITY_HEX_BYTES); // one quantityHex ("0000803F")
        s.magicNumber = reader.readUInt8();
        reader.skip(SaveFormat.GENERIC_PADDING_BYTES); // padding added in patch 1.5.1
    }

    // -----------------------------------------------------------------------
    // Forge.genericWeapon
    // -----------------------------------------------------------------------
    private void genericWeapon(HexReader reader, InventorySlot slot) {
        generic(reader, slot);
        slot.stats.ammo = reader.readUInt16LE();
        DictionaryEntry entry = dict.get(slot.itemId);
        if (entry != null && "masterworkBow".equals(entry.subType)) {
            slot.stats.name = reader.readString();
        }
    }

    // -----------------------------------------------------------------------
    // Forge.genericShield
    // -----------------------------------------------------------------------
    private void genericShield(HexReader reader, InventorySlot slot) {
        generic(reader, slot);
        DictionaryEntry entry = dict.get(slot.itemId);
        if (entry != null && "nobleShield".equals(entry.subType)) {
            slot.stats.house = reader.readHexBytes(1);
        }
    }

    // -----------------------------------------------------------------------
    // Forge.attachment (handles both body-armor attachment block and standalone attachments)
    // -----------------------------------------------------------------------
    private void attachment(HexReader reader, InventorySlot slot) {
        ItemStats s = slot.stats;
        DictionaryEntry entry = dict.get(slot.itemId);

        if (entry != null && "body".equals(entry.slot)) {
            readAttachmentBlock(reader, s, AttachmentMode.STANDALONE);
        } else {
            reader.skip(1);  // repair
            s.icon = reader.readUInt16LE();
            reader.skip(SaveFormat.ATTACHMENT_DATA_BYTES);    // quantityHexes, durability, fatigue, magic
            reader.skip(SaveFormat.ATTACHMENT_PADDING_BYTES); // padding
        }
    }

    // -----------------------------------------------------------------------
    // Forge.bodyArmorStats
    // -----------------------------------------------------------------------
    private void bodyArmorStats(HexReader reader, InventorySlot slot) {
        logger.fine("Parsing bodyArmorStats");
        ItemStats s = slot.stats;
        s.fatigue = reader.readFloatLE();

        if (s.baseFatigue == 0 && s.baseDurability == 0) {
            DictionaryEntry entry = dict.get(slot.itemId);
            if ("genericArmor".equals(slot.itemType) && entry != null) {
                s.baseFatigue    = entry.fatigue    != null ? entry.fatigue    : 0;
                s.baseDurability = entry.durability != null ? entry.durability : 0;
            } else {
                s.baseFatigue    = s.fatigue;
                s.baseDurability = s.maxDurability;
            }
        }
    }

    // -----------------------------------------------------------------------
    // Forge.genericArmor
    // -----------------------------------------------------------------------
    private void genericArmor(HexReader reader, InventorySlot slot) {
        logger.fine("Parsing generic armor");
        DictionaryEntry entry = dict.get(slot.itemId);
        ItemStats s = slot.stats;

        if (entry != null && "nobleArmor".equals(entry.subType)) {
            logger.fine("Is Noble armor");
            s.house = reader.readHexBytes(1);
        }

        boolean isBody = entry != null && "body".equals(entry.slot);
        if (isBody) {
            readAttachmentBlock(reader, s, AttachmentMode.BODY_ARMOR_INLINE);
        }

        generic(reader, slot);
        logger.fine("Parsing durability");
        s.durability = reader.readFloatLE(); // second durability read (overwrite)

        if (isBody) {
            bodyArmorStats(reader, slot);
        }

        if (entry != null && "davkul".equals(entry.subType)) {
            s.description = reader.readString();
        }
    }

    // -----------------------------------------------------------------------
    // Forge.genericHelmet — identical to genericArmor, but fatigue is not
    // stored in the save file for helmets; use the dictionary base value.
    // -----------------------------------------------------------------------
    private void genericHelmet(HexReader reader, InventorySlot slot) {
        genericArmor(reader, slot);
        DictionaryEntry entry = dict.get(slot.itemId);
        if (entry != null && entry.fatigue != null) {
            slot.stats.fatigue = entry.fatigue;
        }
    }

    // -----------------------------------------------------------------------
    // Forge.namedWeapon
    // -----------------------------------------------------------------------
    private void namedWeapon(HexReader reader, InventorySlot slot) {
        ItemStats s = slot.stats;
        s.name          = reader.readString();
        s.maxDurability = reader.readFloatLE();
        s.fatigue       = reader.readInt8();
        s.damageMin     = reader.readUInt16LE();
        s.damageMax     = reader.readUInt16LE();
        s.damageArmor   = reader.readFloatLE();
        s.headChance    = reader.readUInt8();
        s.damageShield  = reader.readUInt16LE();
        s.hitBonus      = reader.readUInt16LE();
        s.penetration   = reader.readFloatLE();
        s.fatigueUse    = reader.readInt16LE();
        s.ammoMax       = reader.readInt16LE();
        reader.skip(SaveFormat.NAMED_WEAPON_ZERO_FLOAT_BYTES); // zero float
        genericWeapon(reader, slot);
    }

    // -----------------------------------------------------------------------
    // Forge.namedArmor
    // -----------------------------------------------------------------------
    private void namedArmor(HexReader reader, InventorySlot slot) {
        ItemStats s = slot.stats;
        s.name          = reader.readString();
        s.maxDurability = reader.readFloatLE();
        s.fatigue       = reader.readInt8();
        genericArmor(reader, slot);
    }

    // -----------------------------------------------------------------------
    // Forge.namedHelmet — identical to namedArmor
    // -----------------------------------------------------------------------
    private void namedHelmet(HexReader reader, InventorySlot slot) {
        namedArmor(reader, slot);
    }

    // -----------------------------------------------------------------------
    // Forge.namedShield
    // -----------------------------------------------------------------------
    private void namedShield(HexReader reader, InventorySlot slot) {
        ItemStats s = slot.stats;
        s.maxDurability = reader.readFloatLE();
        genericShield(reader, slot);
        s.name      = reader.readString();
        s.fatigue   = reader.readInt8();
        s.mDef      = reader.readUInt16LE();
        s.rDef      = reader.readUInt16LE();
        s.fatigueUse = reader.readInt16LE();
    }

    // -----------------------------------------------------------------------
    // Forge.auxiliary
    // -----------------------------------------------------------------------
    private void auxiliary(HexReader reader, InventorySlot slot) {
        generic(reader, slot);
        DictionaryEntry entry = dict.get(slot.itemId);
        String subType = entry != null ? entry.subType : null;

        if (subType != null) switch (subType) {
            case "ammo"       -> slot.stats.ammo  = reader.readUInt16LE();
            case "canine"     -> slot.stats.name  = reader.readString();
            case "provisions" -> {
                slot.stats.amount = reader.readFloatLE();
                slot.stats.expiry = reader.readFloatLE();
                slot.stats.bought = reader.readInt16LE();
            }
            case "commodity"  -> slot.stats.bought = reader.readInt16LE();
        }
    }

    // -----------------------------------------------------------------------
    // Shared attachment-block reader
    // -----------------------------------------------------------------------

    private enum AttachmentMode { STANDALONE, BODY_ARMOR_INLINE }

    /**
     * Reads the 4-byte attachment ID and, if present, the icon + base stats.
     *
     * Both call sites consume exactly 12 skipped bytes around the icon read, but
     * the icon sits at a different offset in each mode:
     *   STANDALONE        (attachment() → body-slot item):  skip(1) + icon + skip(9) + skip(2)
     *   BODY_ARMOR_INLINE (genericArmor() → isBody block):  skip(2) + icon + skip(10)
     * The divergence mirrors the JS source and has not been verified against a real
     * save with both item kinds present — do not collapse the branches until confirmed.
     */
    private void readAttachmentBlock(HexReader reader, ItemStats s, AttachmentMode mode) {
        s.attachment = reader.readHexBytes(4);
        if (s.attachment.equals("00000000")) return;
        if (mode == AttachmentMode.STANDALONE) {
            reader.skip(SaveFormat.ATT_STANDALONE_PRE_ICON);
            s.attachmentIcon = reader.readUInt16LE();
            reader.skip(SaveFormat.ATT_STANDALONE_POST_ICON);
            reader.skip(SaveFormat.ATT_STANDALONE_PADDING);
        } else {
            reader.skip(SaveFormat.ATT_INLINE_PRE_ICON);
            s.attachmentIcon = reader.readUInt16LE();
            reader.skip(SaveFormat.ATT_INLINE_POST_ICON);
        }
        s.baseDurability = reader.readFloatLE();
        s.baseFatigue    = (float) reader.readInt16LE();
    }
}
