package se.niclas.broledger.parser;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Locates brother data blocks within a save-file hex string.
 *
 * <p>All parsers that scan for brothers (SaveParser, and the tools in
 * se.niclas.broledger.tools.parser) delegate here so the signature, byte
 * offsets, and location logic live in exactly one place.
 */
public class BrotherLocator {

    private static final Pattern SIG = Pattern.compile(
            "000000000000007d2c10000000000000d4c4a7e9000100000102000000",
            Pattern.CASE_INSENSITIVE);

    // Byte distances relative to each signature match start (in hex-char units).
    // COUNT_BACK: 1-byte brother count sits this many hex-chars before the match.
    // DATA_AHEAD: each brother's data block starts this many hex-chars after the match.
    public static final int SIG_COUNT_BACK = 38;
    public static final int SIG_DATA_AHEAD = 58;

    /** Returns the number of brothers encoded in the save, or 0 if the signature is absent. */
    public static int countBrothers(String hex) {
        Matcher m = SIG.matcher(hex);
        if (!m.find()) return 0;
        return Integer.parseInt(
                hex.substring(m.start() - SIG_COUNT_BACK, m.start() - SIG_COUNT_BACK + 2), 16);
    }

    /**
     * Returns the hex-char positions of each brother's data block start
     * (i.e. signature start + {@link #SIG_DATA_AHEAD}).
     * Returns an empty list if the signature is absent.
     */
    public static List<Integer> findOffsets(String hex) {
        Matcher m = SIG.matcher(hex);
        if (!m.find()) return List.of();
        int count = Integer.parseInt(
                hex.substring(m.start() - SIG_COUNT_BACK, m.start() - SIG_COUNT_BACK + 2), 16);
        m.reset();
        List<Integer> offsets = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            if (!m.find()) break;
            offsets.add(m.start() + SIG_DATA_AHEAD);
        }
        return offsets;
    }
}
