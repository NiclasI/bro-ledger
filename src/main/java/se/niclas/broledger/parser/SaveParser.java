package se.niclas.broledger.parser;

import se.niclas.broledger.model.Brother;
import se.niclas.broledger.service.DictionaryService;
import se.niclas.broledger.util.HexUtils;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;

public class SaveParser {

    private static final Logger log = Logger.getLogger(SaveParser.class.getName());
    private final DictionaryService dict;
    private final List<String> warnings = new ArrayList<>();

    public SaveParser(DictionaryService dict) {
        this.dict = dict;
    }

    /** Warnings collected during the most recent parse (unknown item types, etc.). */
    public List<String> getWarnings() {
        return warnings;
    }

    public List<Brother> parse(Path savePath) throws IOException {
        byte[] bytes = Files.readAllBytes(savePath);
        // Convert to lowercase hex string — same as fs.readFile(path, "hex") in Node
        String hex = HexUtils.toHexLower(bytes);
        return parseHex(hex);
    }

    public List<Brother> parseHex(String hex) {
        warnings.clear();
        BrotherParser brotherParser = new BrotherParser(dict, warnings);
        List<Brother> brothers = new ArrayList<>();

        List<Integer> offsets = BrotherLocator.findOffsets(hex);
        if (offsets.isEmpty()) {
            log.warning("SaveParser: brother signature not found");
            return brothers;
        }

        for (int offset : offsets) {
            try {
                HexReader reader = new HexReader(hex, offset);
                Brother b = brotherParser.parse(reader, hex);
                brothers.add(b);
            } catch (Exception e) {
                log.warning("SaveParser: failed to parse brother at offset "
                        + offset + ": " + e.getMessage());
            }
        }

        return brothers;
    }

}
