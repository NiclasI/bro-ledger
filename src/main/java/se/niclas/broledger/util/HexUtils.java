package se.niclas.broledger.util;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

public final class HexUtils {

    private HexUtils() {}

    public static byte[] fromHex(String hex) {
        int len = hex.length();
        byte[] data = new byte[len / 2];
        for (int i = 0; i < len; i += 2) {
            data[i / 2] = (byte) ((Character.digit(hex.charAt(i), 16) << 4)
                    + Character.digit(hex.charAt(i + 1), 16));
        }
        return data;
    }

    public static String toHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) {
            sb.append(String.format("%02X", b));
        }
        return sb.toString();
    }

    public static short int16LE(String hex2bytes) {
        return ByteBuffer.wrap(fromHex(hex2bytes)).order(ByteOrder.LITTLE_ENDIAN).getShort();
    }

    public static int uint16LE(String hex2bytes) {
        return Short.toUnsignedInt(int16LE(hex2bytes));
    }

    public static float floatLE(String hex4bytes) {
        return ByteBuffer.wrap(fromHex(hex4bytes)).order(ByteOrder.LITTLE_ENDIAN).getFloat();
    }

    public static long uint32LE(String hex4bytes) {
        return Integer.toUnsignedLong(
                ByteBuffer.wrap(fromHex(hex4bytes)).order(ByteOrder.LITTLE_ENDIAN).getInt());
    }

    public static int uint8(String hex1byte) {
        return Byte.toUnsignedInt((byte) Integer.parseInt(hex1byte, 16));
    }

    public static int int8(String hex1byte) {
        return (byte) Integer.parseInt(hex1byte, 16);
    }

    /** Converts bytes to a lowercase hex string (e.g. for use with save-file readers). */
    public static String toHexLower(byte[] bytes) {
        StringBuilder sb = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }

    /**
     * Encodes a 16-bit unsigned int as a 4-char uppercase little-endian hex string.
     * Low byte first: {@code uint16ToHexLE(1)} → {@code "0100"}.
     * Inverse of {@link #uint16LE(String)}.
     */
    public static String uint16ToHexLE(int value) {
        return String.format("%02X%02X", value & 0xFF, (value >> 8) & 0xFF);
    }

    /** Converts a byte count to the equivalent number of hex characters (byteCount × 2). */
    public static int hexCharSpan(int byteCount) {
        return byteCount * 2;
    }
}
