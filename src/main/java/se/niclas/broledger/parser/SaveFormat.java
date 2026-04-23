package se.niclas.broledger.parser;

/**
 * Named byte-count constants for the Battle Brothers save-file binary format.
 * All values are in bytes. Callers are in {@link BrotherParser} and {@link ItemParser}.
 */
final class SaveFormat {

    private SaveFormat() {}

    // ---- Visual layers (BrotherParser.parseVisualLayers) -------------------
    /** Bytes per visual-layer value field (colour + transform data). */
    static final int VISUAL_LAYER_VALUE_BYTES = 12;
    /** Separator bytes following each visual-layer value. */
    static final int VISUAL_LAYER_SEP_BYTES   = 53;

    // ---- Stats area (BrotherParser.parseActionPointsAndStats) --------------
    /** Bytes for the greed-and-gluttony blob. */
    static final int GREED_GLUTTONY_BYTES = 6;

    // ---- Trait event data (BrotherParser.parseTraits) ----------------------
    static final int INJURY_EVENT_BYTES    = 14;
    static final int TRAINING_EVENT_BYTES  = 37;
    static final int KNOWLEDGE_EVENT_BYTES = 12;
    static final int LEARNING_EVENT_BYTES  =  2;

    // ---- Post-name area (BrotherParser.parseNameAndLevelData) --------------
    /** Byte blob following experience (human-type marker). */
    static final int HUMAN_BYTES = 6;

    // ---- Morale modifiers (BrotherParser.parseMoraleModifiers) -------------
    /** Trailing bytes after the text payload in each morale-modifier entry. */
    static final int MORALE_ENTRY_TAIL_BYTES = 4;
    /** Trailing bytes after the last morale-modifier entry. */
    static final int MORALE_BLOCK_TAIL_BYTES = 8;

    // ---- Generic item block (ItemParser.generic) ---------------------------
    /** One quantityHex field (float 1.0 encoded as "0000803F"). */
    static final int QUANTITY_HEX_BYTES    = 4;
    /** Padding appended to the generic item block since patch 1.5.1. */
    static final int GENERIC_PADDING_BYTES = 2;

    // ---- Non-body attachment (ItemParser.attachment — else branch) ----------
    /** Combined skip for quantityHexes + durability + fatigue + magic-number fields. */
    static final int ATTACHMENT_DATA_BYTES    = 15;
    /** Trailing padding bytes for a non-body attachment record. */
    static final int ATTACHMENT_PADDING_BYTES =  2;

    // ---- Attachment block (ItemParser.readAttachmentBlock) -----------------
    /** Bytes before the icon in STANDALONE mode (repair byte). */
    static final int ATT_STANDALONE_PRE_ICON  =  1;
    /** Bytes after the icon in STANDALONE mode (2×quantityHex + magicNumber). */
    static final int ATT_STANDALONE_POST_ICON =  9;
    /** Padding bytes at end of STANDALONE attachment data. */
    static final int ATT_STANDALONE_PADDING   =  2;
    /** Bytes before the icon in BODY_ARMOR_INLINE mode. */
    static final int ATT_INLINE_PRE_ICON      =  2;
    /** Bytes after the icon in BODY_ARMOR_INLINE mode. */
    static final int ATT_INLINE_POST_ICON     = 10;

    // ---- Named weapon (ItemParser.namedWeapon) -----------------------------
    /** Zero-float padding preceding the genericWeapon call in a named-weapon record. */
    static final int NAMED_WEAPON_ZERO_FLOAT_BYTES = 4;
}
