package se.niclas.broledger.util;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class HexUtilsTest {

    // ---- toHexLower --------------------------------------------------------

    @Test
    void toHexLower_emptyArray() {
        assertEquals("", HexUtils.toHexLower(new byte[0]));
    }

    @Test
    void toHexLower_singleByte() {
        assertEquals("0a", HexUtils.toHexLower(new byte[]{0x0A}));
    }

    @Test
    void toHexLower_multipleBytes() {
        assertEquals("deadbeef", HexUtils.toHexLower(new byte[]{(byte)0xDE, (byte)0xAD, (byte)0xBE, (byte)0xEF}));
    }

    @Test
    void toHexLower_producesLowercase() {
        String result = HexUtils.toHexLower(new byte[]{(byte)0xFF});
        assertEquals("ff", result);
        assertEquals(result, result.toLowerCase());
    }

    @Test
    void toHexLower_consistentWithToHex() {
        byte[] bytes = {0x12, (byte) 0xAB, 0x7F};
        assertEquals(HexUtils.toHex(bytes).toLowerCase(), HexUtils.toHexLower(bytes));
    }

    // ---- uint16ToHexLE -----------------------------------------------------

    @Test
    void uint16ToHexLE_zero() {
        assertEquals("0000", HexUtils.uint16ToHexLE(0));
    }

    @Test
    void uint16ToHexLE_roundTrip() {
        // Should be the inverse of uint16LE: parse back to the same int.
        for (int v : new int[]{1, 127, 255, 256, 1000, 65535}) {
            String hex = HexUtils.uint16ToHexLE(v);
            assertEquals(4, hex.length(), "Must be 4 hex chars for value " + v);
            assertEquals(v, HexUtils.uint16LE(hex),
                    "Round-trip failed for " + v + " -> " + hex);
        }
    }

    @Test
    void uint16ToHexLE_knownValue() {
        // 0x0001 in LE should be "0100"
        assertEquals("0100", HexUtils.uint16ToHexLE(1));
    }

    // ---- hexCharSpan -------------------------------------------------------

    @Test
    void hexCharSpan_zero() {
        assertEquals(0, HexUtils.hexCharSpan(0));
    }

    @Test
    void hexCharSpan_one() {
        assertEquals(2, HexUtils.hexCharSpan(1));
    }

    @Test
    void hexCharSpan_four() {
        assertEquals(8, HexUtils.hexCharSpan(4));
    }

    @Test
    void hexCharSpan_alwaysDoubles() {
        for (int n = 0; n <= 10; n++) {
            assertEquals(n * 2, HexUtils.hexCharSpan(n));
        }
    }
}
