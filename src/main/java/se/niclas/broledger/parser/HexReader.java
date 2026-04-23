package se.niclas.broledger.parser;

import se.niclas.broledger.util.HexUtils;

import java.nio.charset.StandardCharsets;

/**
 * Cursor-based reader over a hex string. Each read advances the cursor by byteCount*2 chars.
 * Mirrors the JS Entity.readBytes() API exactly.
 */
public class HexReader {

    private final String data;
    private int cursor;

    public HexReader(String hexData, int startOffset) {
        this.data = hexData;
        this.cursor = startOffset;
    }

    public int getCursor() {
        return cursor;
    }

    public void setCursor(int cursor) {
        this.cursor = cursor;
    }

    public void skip(int byteCount) {
        cursor += byteCount * 2;
    }

    /** Read byteCount bytes as a raw uppercase hex string. */
    public String readHexBytes(int byteCount) {
        String result = data.substring(cursor, cursor + byteCount * 2).toUpperCase();
        cursor += byteCount * 2;
        return result;
    }

    /** Peek at absolute char offset without moving the cursor. */
    public String peekAt(int absoluteCharOffset, int byteCount) {
        return data.substring(absoluteCharOffset, absoluteCharOffset + byteCount * 2).toUpperCase();
    }

    public int readUInt8() {
        return HexUtils.uint8(readHexBytes(1));
    }

    public int readInt8() {
        return HexUtils.int8(readHexBytes(1));
    }

    public short readInt16LE() {
        return HexUtils.int16LE(readHexBytes(2));
    }

    public int readUInt16LE() {
        return HexUtils.uint16LE(readHexBytes(2));
    }

    public float readFloatLE() {
        return HexUtils.floatLE(readHexBytes(4));
    }

    public long readUInt32LE() {
        return HexUtils.uint32LE(readHexBytes(4));
    }

    /** Read a length-prefixed UTF-8 string: UInt16LE length, then that many bytes as UTF-8. */
    public String readString() {
        int len = readUInt16LE();
        if (len == 0) return "";
        byte[] bytes = HexUtils.fromHex(readHexBytes(len));
        return new String(bytes, StandardCharsets.UTF_8);
    }
}
