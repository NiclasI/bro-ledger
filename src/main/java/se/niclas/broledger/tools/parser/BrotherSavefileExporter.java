package se.niclas.broledger.tools.parser;

import se.niclas.broledger.model.InventorySlot;
import se.niclas.broledger.parser.BrotherLocator;
import se.niclas.broledger.parser.HexReader;
import se.niclas.broledger.parser.ItemParser;
import se.niclas.broledger.service.DictionaryService;
import se.niclas.broledger.util.HexUtils;

import java.io.IOException;
import java.nio.file.*;
import java.util.*;

/**
 * Writes a TSV file with one row per brother. Each column represents a parsed
 * segment; the first row is a marker/header row naming every column.
 *
 * Variable-length sections (inventory, circles, traits) use indexed column names
 * such as inv[0], inv[1].data, circ[0], trt[0]. Brothers with fewer items leave
 * those columns blank, which lets a spreadsheet comparison show the divergence.
 *
 * Parsing is tolerant: when an unknown item ID is encountered the remaining item
 * columns for that brother show "ABORTED" and the fixed sections that follow
 * (name, level, stars…) are left blank rather than crashing.
 *
 * Run from project root:
 *   .\mvnw compile exec:java -Dexec.mainClass=se.niclas.broledger.tools.parser.BrotherSavefileExporter
 *       -Dexec.args="save.sav output.tsv"
 */
public class BrotherSavefileExporter {

    private static final String WILDMAN   = "6DF381C6";
    private static final String BARBARIAN = "CB90AA90";

    private final DictionaryService dict;
    private final ItemParser        itemParser;

    public BrotherSavefileExporter(DictionaryService dict) {
        this.dict       = dict;
        this.itemParser = new ItemParser(dict, new ArrayList<>());
    }

    // ---- entry point -------------------------------------------------------

    public static void main(String[] args) throws Exception {
        if (args.length != 2) {
            System.err.println("Usage: BrotherSavefileExporter <save.sav> <output.tsv>");
            System.exit(1);
        }
        DictionaryService dict = DictionaryService.getInstance();
        dict.loadFromClasspath();
        new BrotherSavefileExporter(dict).write(Path.of(args[0]), Path.of(args[1]));
    }

    // ---- public API --------------------------------------------------------

    public void write(Path savePath, Path outputPath) throws IOException {
        byte[] bytes = Files.readAllBytes(savePath);
        String hex   = HexUtils.toHex(bytes);

        List<Integer> offsets = BrotherLocator.findOffsets(hex);
        if (offsets.isEmpty()) {
            System.err.println("BrotherSavefileExporter: brother signature not found");
            return;
        }

        // Pass 1 — parse each brother into an ordered list of (label, value) steps
        List<List<String[]>> allTraces = new ArrayList<>(offsets.size());
        for (int offset : offsets) {
            HexReader    reader = new HexReader(hex, offset);
            List<String[]> trace  = new ArrayList<>();
            try {
                traceParse(hex, reader, trace);
            } catch (Exception e) {
                trace.add(step("PARSE_EXCEPTION", e.getClass().getSimpleName() + ": " + e.getMessage()));
            }
            allTraces.add(trace);
        }

        // Pass 2 — collect all unique labels in first-appearance order
        LinkedHashSet<String> allLabels = new LinkedHashSet<>();
        for (List<String[]> trace : allTraces)
            for (String[] s : trace) allLabels.add(s[0]);

        // Pass 3 — write TSV
        StringBuilder tsv = new StringBuilder(1 << 20);

        // Marker / header row
        tsv.append("brother");
        for (String lbl : allLabels) tsv.append('\t').append(lbl);
        tsv.append('\n');

        for (int i = 0; i < allTraces.size(); i++) {
            List<String[]> trace = allTraces.get(i);
            // Merge duplicate labels (shouldn't happen for normal columns)
            Map<String, String> row = new LinkedHashMap<>();
            for (String[] s : trace)
                row.merge(s[0], s[1], (a, b) -> a + " | " + b);

            String name = row.getOrDefault("name", "#" + i);
            tsv.append(i).append(':').append(name);
            for (String lbl : allLabels)
                tsv.append('\t').append(row.getOrDefault(lbl, ""));
            tsv.append('\n');
        }

        Files.writeString(outputPath, tsv.toString());
        System.out.println("BrotherSavefileExporter: wrote " + outputPath.toAbsolutePath()
                + " (" + allTraces.size() + " brothers, " + allLabels.size() + " columns)");
    }

    // ---- trace parse -------------------------------------------------------

    private void traceParse(String hex, HexReader r, List<String[]> out) {

        // --- Visual layers ---
        int layerCount = r.readUInt8();
        out.add(step("layerCnt", String.valueOf(layerCount)));
        for (int i = 0; i < layerCount; i++) {
            String key   = r.readString();
            String val12 = r.readHexBytes(12);
            r.skip(53); // fixed separator
            out.add(step("lyr[" + i + "].key",   key));
            out.add(step("lyr[" + i + "].val12", val12));
        }

        // --- Header (member-count + variable blob) ---
        int hdrStart     = r.getCursor();
        int memberCount  = r.readInt16LE();
        for (int i = 0; i < memberCount; i++) {
            int nameLen = r.readInt16LE();
            r.skip(nameLen);
            String mt = r.readHexBytes(1);
            if      ("02".equals(mt)) r.skip(1);
            else if ("03".equals(mt)) { int dl = r.readUInt16LE(); r.skip(dl); }
            else                      r.skip(4);
        }
        out.add(step("hdr", memberCount + "mbrs/" + (r.getCursor() - hdrStart) / 2 + "B"));

        // --- Action points + 8 base stats ---
        out.add(step("ap", String.valueOf(r.readUInt8())));
        String[] statNames = { "hp", "fat", "res", "mel", "rng", "def", "ini", "mor" };
        for (String sn : statNames) out.add(step(sn, String.valueOf(r.readInt16LE())));

        // --- Misc ---
        out.add(step("greed",   r.readHexBytes(6)));
        out.add(step("pouches", String.valueOf(r.readUInt8())));

        // --- Inventory ---
        int invCount = r.readUInt8();
        out.add(step("inv.cnt", String.valueOf(invCount)));

        for (int i = 0; i < invCount; i++) {
            int    slotIdx = r.readUInt8();
            String itemId  = r.readHexBytes(4);
            String name    = dict.getName(itemId);
            String type    = dict.has(itemId) ? dict.getType(itemId) : "?";

            out.add(step("inv[" + i + "]", "s" + slotIdx + "=" + itemId + "(" + name + ")"));

            if (!dict.has(itemId)) {
                out.add(step("inv[" + i + "].data", "ABORTED-unknown-id"));
                return; // cannot recover: item byte length is unknown
            }

            int dataStart = r.getCursor();
            InventorySlot slot = new InventorySlot(slotIdx);
            slot.itemId   = itemId;
            slot.itemType = type;
            itemParser.parse(r, slot);
            String dataHex = hex.substring(dataStart, r.getCursor()).toUpperCase();
            out.add(step("inv[" + i + "].data", dataHex));
        }

        // --- Circles: perks + background ---
        int circleCount = r.readUInt16LE();
        out.add(step("circ.cnt", String.valueOf(circleCount)));

        String backgroundHexId = null;
        int circlesRead = 0;
        for (int i = 0; i < circleCount; i++) {
            String hexId  = r.readHexBytes(4);
            r.skip(1); // isNew byte
            String ctype  = dict.has(hexId) ? dict.getType(hexId) : "?";
            String cname  = dict.getName(hexId);
            out.add(step("circ[" + i + "]", hexId + "(" + ctype + ":" + cname + ")"));
            circlesRead++;
            if ("background".equals(ctype)) {
                backgroundHexId = hexId;
                break;
            }
        }

        // --- Description strings + salary ---
        out.add(step("desc",     trunc(r.readString(), 50)));
        out.add(step("descTpl",  trunc(r.readString(), 40)));
        out.add(step("unk2",     r.readHexBytes(2)));
        out.add(step("salary",   String.format("%.4f", r.readFloatLE())));

        // --- Tattoo (wildman / barbarian only) ---
        if (WILDMAN.equals(backgroundHexId) || BARBARIAN.equals(backgroundHexId)) {
            out.add(step("tattoo", r.readHexBytes(1)));
        }

        // --- Traits (remaining circle entries after perks + background) ---
        int traitIdx = 0;
        for (int i = circlesRead; i < circleCount; i++) {
            String traitId  = r.readHexBytes(4);
            String ttype    = dict.has(traitId) ? dict.getType(traitId) : "?";
            String tname    = dict.getName(traitId);

            if (!"injury".equals(ttype)) r.skip(1); // isNew byte

            int extraBytes = switch (ttype) {
                case "injury"    -> 14;
                case "training"  -> 37;
                case "knowledge" -> 12;
                case "learning"  -> 2;
                default          -> 0;
            };
            String extraHex = extraBytes > 0 ? r.readHexBytes(extraBytes) : "";

            String cell = traitId + "(" + ttype + ":" + tname + ")"
                    + (extraHex.isEmpty() ? "" : "/" + extraHex);
            out.add(step("trt[" + traitIdx++ + "]", cell));
        }

        // --- Name / title / wounds / experience ---
        out.add(step("name",   r.readString()));
        out.add(step("title",  r.readString()));
        out.add(step("lwound", String.format("%.4f", r.readFloatLE())));
        out.add(step("xp",     String.valueOf(r.readUInt32LE())));

        // --- Human bytes + level data ---
        out.add(step("humanB",  r.readHexBytes(6)));
        out.add(step("lvl",     String.valueOf(r.readUInt8())));
        out.add(step("pp",      String.valueOf(r.readUInt8())));
        out.add(step("pu",      String.valueOf(r.readUInt8())));
        out.add(step("lp",      String.valueOf(r.readUInt8())));
        out.add(step("morale",  String.format("%.4f", r.readFloatLE())));

        // --- Morale modifier blob ---
        int modCount = r.readUInt8();
        for (int i = 0; i < modCount; i++) {
            r.skip(1);
            int textLen = r.readUInt16LE();
            r.skip(textLen + 4);
        }
        r.skip(8); // trailing bytes
        out.add(step("moraleMods", String.valueOf(modCount)));

        // --- Stars ---
        StringBuilder stars = new StringBuilder(16);
        for (int i = 0; i < 8; i++) { if (i > 0) stars.append(','); stars.append(r.readUInt8()); }
        out.add(step("stars", stars.toString()));

        // --- Talents ---
        StringBuilder talents = new StringBuilder(128);
        for (int i = 0; i < 8; i++) {
            int    tp  = r.readUInt8();
            String tb  = r.readHexBytes(tp);
            if (i > 0) talents.append('|');
            talents.append(tb.isEmpty() ? "-" : tb);
        }
        out.add(step("talents", talents.toString()));
    }

    // ---- helpers -----------------------------------------------------------

    private static String[] step(String label, String value) {
        return new String[]{ label, value };
    }

    private static String trunc(String s, int max) {
        return s.length() > max ? s.substring(0, max) + "…" : s;
    }
}
