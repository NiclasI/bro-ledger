package se.niclas.broledger.tools;

import se.niclas.broledger.model.Brother;
import se.niclas.broledger.model.Stat;
import se.niclas.broledger.model.TraitEntry;
import se.niclas.broledger.parser.SaveParser;
import se.niclas.broledger.service.AnnotationService;
import se.niclas.broledger.service.AppConfig;
import se.niclas.broledger.service.DictionaryService;
import se.niclas.broledger.service.RoleService;
import se.niclas.broledger.service.StatModifierService;
import se.niclas.broledger.service.StatPotentialCalculator;

import java.io.IOException;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Exports all brothers from the last-loaded save file to a tab-separated CSV.
 * Output is written next to the save file using the same base name (e.g. autosave.csv).
 *
 * Run:
 *   .\mvnw compile exec:java -Dexec.mainClass=se.niclas.broledger.tools.BrotherCsvExporter
 * Optional output path argument:
 *   .\mvnw compile exec:java -Dexec.mainClass=se.niclas.broledger.tools.BrotherCsvExporter -Dexec.args="C:\path\to\output.csv"
 */
public class BrotherCsvExporter {

    private static final String SEP = ";";

    private static final Map<String, Integer> TRAIT_TYPE_ORDER = Map.of(
            "training",  0,
            "knowledge", 1,
            "learning",  2,
            "injury",    3
    );

    private final DictionaryService   dict;
    private final StatModifierService mods;
    private final AnnotationService   annotations;
    private final RoleService         roles;

    public BrotherCsvExporter(DictionaryService dict, StatModifierService mods,
                       AnnotationService annotations, RoleService roles) {
        this.dict        = dict;
        this.mods        = mods;
        this.annotations = annotations;
        this.roles       = roles;
    }

    // ---- entry point -------------------------------------------------------

    public static void main(String[] args) throws Exception {
        AppConfig config = AppConfig.getInstance();
        config.load();

        String savePath = config.lastSaveFilePath;
        if (savePath == null || savePath.isBlank()) {
            System.err.println("BrotherCsvExporter: no last save file in ~/.bro-ledger/config.json — open the app once to set it");
            System.exit(1);
        }

        DictionaryService dict = DictionaryService.getInstance();
        dict.loadFromClasspath();
        StatModifierService mods = StatModifierService.getInstance();
        mods.loadFromClasspath();

        Path save = Path.of(savePath);
        Path out  = args.length > 0 ? Path.of(args[0]) : save.resolveSibling(baseName(save) + ".csv");

        new BrotherCsvExporter(dict, mods, AnnotationService.getInstance(), RoleService.getInstance())
                .export(config, save, out);
    }

    // ---- public API --------------------------------------------------------

    public void export(AppConfig config, Path savePath, Path outputPath) throws IOException {
        SaveParser parser = new SaveParser(dict);
        List<Brother> brothers = parser.parse(savePath);

        if (brothers.isEmpty()) {
            System.err.println("BrotherCsvExporter: no brothers found in " + savePath);
            return;
        }

        annotations.loadFor(savePath);
        Stat[] statOrder = config.orderedStats();

        // Sort each brother's perks once; track the max column count for the header.
        List<List<String>> allPerkNames = brothers.stream()
                .map(this::sortedPerkNames)
                .toList();
        int maxPerks = allPerkNames.stream().mapToInt(List::size).max().orElse(0);

        try (PrintWriter w = new PrintWriter(outputPath.toFile(), StandardCharsets.UTF_8)) {
            w.println(buildHeader(statOrder, maxPerks));
            for (int i = 0; i < brothers.size(); i++) {
                w.println(buildRow(brothers.get(i), statOrder, allPerkNames.get(i), maxPerks));
            }
        }

        System.out.println("BrotherCsvExporter: wrote " + brothers.size() + " brothers to " + outputPath.toAbsolutePath());
        parser.getWarnings().forEach(warn -> System.out.println("  warn: " + warn));
    }

    // ---- row building ------------------------------------------------------

    private String buildHeader(Stat[] statOrder, int maxPerks) {
        List<String> cols = new ArrayList<>();
        cols.add("Name");
        cols.add("Title");
        cols.add("Role");
        cols.add("Background");
        cols.add("Level");
        cols.add("XP");
        for (Stat s : statOrder) {
            cols.add(s.abbrev());
            cols.add(s.abbrev() + "*");
            cols.add(s.abbrev() + " pot");
        }
        for (int i = 1; i <= maxPerks; i++) cols.add("Perk " + i);
        cols.add("Traits");
        return toLine(cols);
    }

    private String buildRow(Brother b, Stat[] statOrder, List<String> perkNames, int maxPerks) {
        List<String> cols = new ArrayList<>();
        cols.add(b.name);
        cols.add(b.title);
        String roleId = annotations.get(b.fingerprint).roleId;
        cols.add(roleId != null && roles.getById(roleId) != null ? roles.getById(roleId).name : "");
        cols.add(dict.getName(b.backgroundHexId));
        cols.add(String.valueOf(b.levelTotal));
        cols.add(String.valueOf(b.experience));
        for (Stat s : statOrder) {
            cols.add(String.valueOf(b.stats[s.statIndex()]));
            cols.add("*".repeat(b.stars[s.starIndex()]));
            cols.add(String.valueOf(StatPotentialCalculator.compute(b, s).finalPotential()));
        }
        for (int i = 0; i < maxPerks; i++) cols.add(i < perkNames.size() ? perkNames.get(i) : "");
        cols.add(formatTraits(b));
        return toLine(cols);
    }

    // ---- formatting --------------------------------------------------------

    /** Perks sorted by tier (ascending, untiered last), then by name within each tier. */
    private List<String> sortedPerkNames(Brother b) {
        return b.perkIds.stream()
                .sorted(Comparator
                        .comparing((String id) -> {
                            Integer t = mods.getTier(id);
                            return t != null ? t : Integer.MAX_VALUE;
                        })
                        .thenComparing(dict::getName))
                .map(id -> {
                    Integer tier = mods.getTier(id);
                    String  name = dict.getName(id);
                    return tier != null ? "[T" + tier + "] " + name : name;
                })
                .toList();
    }

    /** Traits sorted by type order (training → knowledge → learning → injury), then by name.
     *  Entries not found in the dictionary are excluded. */
    private String formatTraits(Brother b) {
        return b.traits.stream()
                .filter(t -> dict.has(t.id))
                .sorted(Comparator
                        .comparing((TraitEntry t) ->
                                TRAIT_TYPE_ORDER.getOrDefault(dict.getType(t.id), 99))
                        .thenComparing(t -> dict.getName(t.id)))
                .map(t -> dict.getName(t.id))
                .collect(Collectors.joining("; "));
    }

    // ---- helpers -----------------------------------------------------------

    private static String baseName(Path path) {
        String name = path.getFileName().toString();
        int dot = name.lastIndexOf('.');
        return dot > 0 ? name.substring(0, dot) : name;
    }

    private static String toLine(List<String> values) {
        return values.stream().map(BrotherCsvExporter::sanitize).collect(Collectors.joining(SEP));
    }

    /** Strips characters that would break CSV structure. */
    private static String sanitize(String value) {
        if (value == null) return "";
        return value.replace(";", " ").replace("\n", " ").replace("\r", "");
    }
}
