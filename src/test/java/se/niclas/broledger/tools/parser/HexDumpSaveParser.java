package se.niclas.broledger.tools.parser;

import se.niclas.broledger.model.Brother;
import se.niclas.broledger.parser.BrotherLocator;
import se.niclas.broledger.parser.BrotherParser;
import se.niclas.broledger.parser.HexReader;
import se.niclas.broledger.service.DictionaryService;
import se.niclas.broledger.util.HexUtils;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Wraps SaveParser: parses a save file and additionally writes each brother's
 * raw hex segment to a file named {@code <savename>_brother_<index>.hex} in
 * the working directory.
 *
 * <p>Each file contains the hex bytes in lines of {@value #BYTES_PER_LINE} bytes,
 * with an ASCII translation on the line below. Each character is right-adjusted
 * within its two-character hex column: printable ASCII is shown as-is, all
 * other bytes are shown as {@code .}.
 *
 * <pre>
 * 48656c6c6f20576f726c64
 *  H e l l o   W o r l d
 * </pre>
 */
public class HexDumpSaveParser {

    private static final int BYTES_PER_LINE = 128;

    private final DictionaryService dict;

    public HexDumpSaveParser(DictionaryService dict) {
        this.dict = dict;
    }

    public List<Brother> parse(Path savePath) throws IOException {
        byte[] bytes = Files.readAllBytes(savePath);
        String hex = HexUtils.toHex(bytes);

        String fileName = savePath.getFileName().toString();
        int dot = fileName.lastIndexOf('.');
        String baseName = dot > 0 ? fileName.substring(0, dot) : fileName;

        return parseAndDump(hex, baseName);
    }

    /**
     * Formats a hex string as alternating hex / ASCII rows, wrapping every
     * {@value #BYTES_PER_LINE} bytes. Each ASCII character is right-adjusted
     * within the two hex-digit column of its byte (space then char), so the
     * character lands directly below the second hex digit.
     */
    static String formatHexDump(String hex) {
        int charsPerLine = BYTES_PER_LINE * 2;
        StringBuilder sb = new StringBuilder(hex.length() * 2);

        for (int pos = 0; pos < hex.length(); pos += charsPerLine) {
            int lineEnd = Math.min(pos + charsPerLine, hex.length());
            String hexLine = hex.substring(pos, lineEnd);
            sb.append(hexLine).append('\n');

            int byteCount = hexLine.length() / 2;
            for (int i = 0; i < byteCount; i++) {
                int b = Integer.parseInt(hexLine, i * 2, i * 2 + 2, 16);
                char c = (b >= 0x20 && b <= 0x7E) ? (char) b : '.';
                sb.append(' ').append(c);
            }
            sb.append('\n');
        }

        return sb.toString();
    }

    private List<Brother> parseAndDump(String hex, String baseName) throws IOException {
        BrotherParser brotherParser = new BrotherParser(dict, new java.util.ArrayList<>());
        List<Brother> brothers = new ArrayList<>();

        List<Integer> parseOffsets = BrotherLocator.findOffsets(hex);
        if (parseOffsets.isEmpty()) {
            System.err.println("HexDumpSaveParser: brother signature not found");
            return brothers;
        }

        List<Integer> sigStarts = new ArrayList<>(parseOffsets.size());
        for (int offset : parseOffsets) sigStarts.add(offset - BrotherLocator.SIG_DATA_AHEAD);

        Path workDir = Path.of(".");

        // Parsed-length dumps (_brother_N.hex)
        for (int i = 0; i < parseOffsets.size(); i++) {
            int start = parseOffsets.get(i);
            HexReader reader = new HexReader(hex, start);
            Brother b = null;
            try {
                b = brotherParser.parse(reader, hex);
            } catch (Exception e) {
                System.err.println("HexDumpSaveParser: failed parsing brother " + i
                        + " at offset " + start + ": " + e.getMessage());
            } finally {
                Path outFile = workDir.resolve(baseName + "_brother_" + i + ".hex");
                Files.writeString(outFile, formatHexDump(hex.substring(start, reader.getCursor())));
                System.out.println("HexDumpSaveParser: wrote " + outFile.toAbsolutePath());
            }
            if (b != null) brothers.add(b);
        }

        // Signature-to-signature dumps (_brother_NA.hex)
        int sigCount = sigStarts.size();
        int maxSigLength = 0;
        for (int i = 0; i < sigCount - 1; i++) {
            maxSigLength = Math.max(maxSigLength, sigStarts.get(i + 1) - sigStarts.get(i));
        }

        for (int i = 0; i < sigCount; i++) {
            int start = sigStarts.get(i);
            int end = (i < sigCount - 1)
                    ? sigStarts.get(i + 1)
                    : Math.min(start + maxSigLength, hex.length());

            Path outFile = workDir.resolve(baseName + "_brother_" + i + "A.hex");
            Files.writeString(outFile, formatHexDump(hex.substring(start, end)));
            System.out.println("HexDumpSaveParser: wrote " + outFile.toAbsolutePath());
        }

        return brothers;
    }
}
