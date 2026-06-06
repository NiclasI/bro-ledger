package se.niclas.broledger.tools;

import se.niclas.broledger.model.Brother;
import se.niclas.broledger.model.DictionaryEntry;
import se.niclas.broledger.model.Stat;
import se.niclas.broledger.parser.SaveParser;
import se.niclas.broledger.service.AnnotationService;
import se.niclas.broledger.service.AnnotationService.LevelUpEvent;
import se.niclas.broledger.service.DictionaryService;
import se.niclas.broledger.service.SaveReplayService;
import se.niclas.broledger.service.SaveReplayService.ReplayEntry;

import java.io.PrintStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Scanner;
import java.util.stream.Collectors;

/**
 * Replays the snapshot sequence captured by {@link SaveReplayService} and prints detected
 * changes (stat deltas, perks, level-ups) plus adjustments (planned-increase consumption)
 * for each transition between consecutive save snapshots.
 *
 * <p>Usage:</p>
 * <pre>
 *   .\mvnw compile exec:java -Dexec.mainClass=se.niclas.broledger.tools.ReplayAnalyzer
 *        [-Dexec.args="[&lt;save.sav&gt;|--dir &lt;dir&gt;] [&lt;transition&gt;] [&lt;output.txt&gt;]"]
 *
 *   &lt;save.sav&gt;   — path to the live save; the tool resolves its replay directory.
 *               — or --dir &lt;replayDir&gt; to point directly at a replay directory.
 *               — omit to select interactively.
 *   &lt;transition&gt; — 1-based index, or "all" (default when a save path is given).
 *               — omit to select interactively.
 *   &lt;output&gt;    — optional output file path; stdout if omitted.
 * </pre>
 */
public class ReplayAnalyzer {

    /** A consecutive pair of snapshot files to analyze. */
    record Transition(int idx, Path prev, Path next) {}

    /** Display format for snapshot timestamps parsed from filename stems. */
    private static final DateTimeFormatter DISPLAY_FMT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS 'UTC'").withZone(ZoneOffset.UTC);

    /** Parse format matching {@link SaveReplayService#TS_FMT} (the snapshot filename pattern). */
    private static final DateTimeFormatter PARSE_FMT =
            DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss'_'SSS'Z'").withZone(ZoneOffset.UTC);

    // ---- entry point -------------------------------------------------------

    public static void main(String[] args) throws Exception {
        DictionaryService dict = DictionaryService.getInstance();
        dict.loadFromClasspath();
        SaveReplayService srs = SaveReplayService.getInstance();

        // Parse positional args (all optional)
        boolean useDir        = false;
        Path    resolvedDir   = null;
        String  savArg        = null;
        String  transitionArg = null;
        Path    outputPath    = null;

        for (int i = 0; i < args.length; i++) {
            if ("--dir".equals(args[i]) && i + 1 < args.length) {
                useDir      = true;
                resolvedDir = Path.of(args[++i]);
            } else if (savArg == null && !args[i].startsWith("--")) {
                savArg = args[i];
            } else if (transitionArg == null && !args[i].startsWith("--")) {
                transitionArg = args[i];
            } else if (outputPath == null && !args[i].startsWith("--")) {
                outputPath = Path.of(args[i]);
            }
        }

        Scanner in = new Scanner(System.in);

        // ---- resolve replay directory ----------------------------------------
        if (!useDir) {
            if (savArg != null) {
                resolvedDir = srs.replayDir(Path.of(savArg));
            } else {
                resolvedDir = pickReplayDirInteractively(srs, in);
                if (resolvedDir == null) {
                    System.err.println("No replay data found. Enable 'Update Replay' in Preferences first.");
                    return;
                }
            }
        }

        // ---- load snapshots --------------------------------------------------
        List<Path> snapshots = listSavSnapshots(resolvedDir);
        if (snapshots.size() < 2) {
            System.err.println("Need at least 2 snapshots to form a transition (found "
                    + snapshots.size() + " in " + resolvedDir + ").");
            return;
        }

        List<Transition> allTransitions = new ArrayList<>();
        for (int i = 0; i < snapshots.size() - 1; i++) {
            allTransitions.add(new Transition(i + 1, snapshots.get(i), snapshots.get(i + 1)));
        }

        // ---- resolve which transitions to analyze ----------------------------
        List<Transition> toAnalyze;
        if (transitionArg != null) {
            toAnalyze = resolveTransitionArg(transitionArg, allTransitions);
            if (toAnalyze == null) {
                System.err.println("Invalid transition argument: '" + transitionArg
                        + "'. Use a 1-based index (1.." + allTransitions.size() + ") or 'all'.");
                return;
            }
        } else if (savArg != null || useDir) {
            toAnalyze = allTransitions;  // default to all when source is from args
        } else {
            toAnalyze = pickTransitionsInteractively(allTransitions, in);
            if (toAnalyze == null) return;
        }

        // ---- set up output ---------------------------------------------------
        PrintStream out = System.out;
        if (outputPath != null) {
            out = new PrintStream(Files.newOutputStream(outputPath));
        }

        // ---- run analysis ----------------------------------------------------
        analyzeTransitions(toAnalyze, allTransitions, snapshots, dict, srs, out);

        if (outputPath != null) {
            out.close();
            System.out.println("Report written to " + outputPath.toAbsolutePath());
        }
    }

    // ---- interactive helpers -----------------------------------------------

    private static Path pickReplayDirInteractively(SaveReplayService srs, Scanner in) {
        List<ReplayEntry> entries = srs.listAll()
                .stream()
                .filter(e -> e.snapshotCount() >= 2)
                .toList();
        if (entries.isEmpty()) return null;

        System.out.println("Captured save files:");
        for (int i = 0; i < entries.size(); i++) {
            ReplayEntry e = entries.get(i);
            System.out.printf("  [%d] %s  (%d snapshots)%n",
                    i + 1, e.sourceSavePath(), e.snapshotCount());
        }
        System.out.print("Select save file (1–" + entries.size() + "): ");

        while (in.hasNextLine()) {
            String line = in.nextLine().strip();
            try {
                int choice = Integer.parseInt(line);
                if (choice >= 1 && choice <= entries.size()) {
                    return entries.get(choice - 1).dir();
                }
            } catch (NumberFormatException ignored) {}
            System.out.print("Invalid choice. Enter a number 1–" + entries.size() + ": ");
        }
        return null;
    }

    private static List<Transition> pickTransitionsInteractively(
            List<Transition> allTransitions, Scanner in) {

        System.out.println("\nAvailable transitions:");
        for (Transition t : allTransitions) {
            System.out.printf("  [%d] %s  →  %s%n",
                    t.idx(), displayTs(stemOf(t.prev())), displayTs(stemOf(t.next())));
        }
        System.out.println("  [A] All transitions");
        System.out.print("Select transition (1–" + allTransitions.size() + " or A): ");

        while (in.hasNextLine()) {
            String line = in.nextLine().strip();
            if ("a".equalsIgnoreCase(line) || "all".equalsIgnoreCase(line)) {
                return allTransitions;
            }
            try {
                int choice = Integer.parseInt(line);
                if (choice >= 1 && choice <= allTransitions.size()) {
                    return List.of(allTransitions.get(choice - 1));
                }
            } catch (NumberFormatException ignored) {}
            System.out.print("Invalid. Enter 1–" + allTransitions.size() + " or A: ");
        }
        return null;
    }

    private static List<Transition> resolveTransitionArg(String arg, List<Transition> all) {
        if ("all".equalsIgnoreCase(arg)) return all;
        try {
            int idx = Integer.parseInt(arg);
            if (idx >= 1 && idx <= all.size()) return List.of(all.get(idx - 1));
        } catch (NumberFormatException ignored) {}
        return null;
    }

    // ---- core analysis -----------------------------------------------------

    private static void analyzeTransitions(
            List<Transition> toAnalyze,
            List<Transition> allTransitions,
            List<Path> allSnapshots,
            DictionaryService dict,
            SaveReplayService srs,
            PrintStream out) {

        boolean analyzeAll = toAnalyze.size() == allTransitions.size()
                && toAnalyze.size() > 1;

        // Carried planned-increase state, updated across transitions in all-mode.
        Map<String, int[]> carriedPlanned     = new HashMap<>();
        Map<String, int[]> carriedPost11      = new HashMap<>();
        boolean            carriedInitialized = false;

        int totalLevelUps  = 0;
        int totalAdjusted  = 0;
        int totalAnomalies = 0;
        int transitionsDone = 0;

        AnnotationService annotSvc = AnnotationService.getInstance();

        for (Transition t : toAnalyze) {
            out.println();
            out.println("=".repeat(70));
            out.printf("Transition %d/%d:  %s  →  %s%n",
                    t.idx(), allTransitions.size(),
                    displayTs(stemOf(t.prev())), displayTs(stemOf(t.next())));
            out.println("=".repeat(70));

            // Parse both snapshots
            List<Brother> prevBrothers = parseSave(t.prev(), dict);
            List<Brother> nextBrothers = parseSave(t.next(), dict);

            // Pre-reconciliation planned increases for this transition are stored in
            // the annotations paired with t.next() (captured before reconciliation ran).
            Path nextAnnotPath = srs.annotationsFor(t.next());
            Map<String, int[]> transPlanned = new HashMap<>();
            Map<String, int[]> transPost11  = new HashMap<>();
            AnnotationService.readAnnotationMaps(nextAnnotPath, transPlanned, transPost11);

            // Initialize carried state from the very first snapshot's annotations
            if (analyzeAll && !carriedInitialized) {
                Path prevAnnotPath = srs.annotationsFor(t.prev());
                AnnotationService.readAnnotationMaps(prevAnnotPath, carriedPlanned, carriedPost11);
                carriedInitialized = true;
            }

            // ---- Roster changes -----------------------------------------------
            List<String> anomalies = new ArrayList<>();
            reportRosterChanges(prevBrothers, nextBrothers, out, anomalies);

            // ---- Per-brother changes and adjustments ---------------------------
            // Use per-transition maps (accurate pre-state for this exact overwrite)
            Map<String, int[]> localPlanned = deepCopy(transPlanned);
            Map<String, int[]> localPost11  = deepCopy(transPost11);
            List<LevelUpEvent> events =
                    annotSvc.previewReconcile(prevBrothers, nextBrothers, localPlanned, localPost11);

            if (events.isEmpty() && anomalies.isEmpty()) {
                out.println("  (no changes detected)");
            }

            for (LevelUpEvent e : events) {
                reportLevelUpEvent(e, out, dict);
                totalLevelUps++;
                if (e.adjusted()) totalAdjusted++;
            }

            // ---- Anomaly detection --------------------------------------------
            detectAnomalies(prevBrothers, nextBrothers, anomalies);

            // ---- Cross-check (all-mode only, when the next snapshot exists) ---
            if (analyzeAll) {
                // Advance carried state with our computed result
                Map<String, int[]> carriedCopy  = deepCopy(carriedPlanned);
                Map<String, int[]> carried11Copy = deepCopy(carriedPost11);
                annotSvc.previewReconcile(prevBrothers, nextBrothers, carriedCopy, carried11Copy);

                // t.idx() is 1-based; allSnapshots.get(t.idx()) is the snapshot after t.next()
                int afterIdx = t.idx(); // e.g. idx=1 → allSnapshots.get(1) = second snapshot
                if (afterIdx < allSnapshots.size()) {
                    Path afterSnap      = allSnapshots.get(afterIdx);
                    Path afterAnnotPath = srs.annotationsFor(afterSnap);
                    Map<String, int[]> actualPlanned = new HashMap<>();
                    Map<String, int[]> actualPost11  = new HashMap<>();
                    AnnotationService.readAnnotationMaps(afterAnnotPath, actualPlanned, actualPost11);

                    List<String> crossIssues = crossCheck(
                            nextBrothers, carriedCopy, carried11Copy, actualPlanned, actualPost11);
                    if (!crossIssues.isEmpty()) {
                        out.println();
                        out.println("  *** RECONCILIATION CROSS-CHECK MISMATCHES ***");
                        crossIssues.forEach(s -> out.println("  ! " + s));
                        anomalies.addAll(crossIssues);
                    }
                }

                // Persist carried state for next iteration
                carriedPlanned.clear(); carriedPlanned.putAll(carriedCopy);
                carriedPost11 .clear(); carriedPost11 .putAll(carried11Copy);
            }

            // ---- Print anomalies ----------------------------------------------
            if (!anomalies.isEmpty()) {
                out.println();
                out.println("  Anomalies:");
                anomalies.forEach(s -> out.println("    ! " + s));
                totalAnomalies += anomalies.size();
            }

            transitionsDone++;
        }

        // ---- Summary ----------------------------------------------------------
        out.println();
        out.println("=".repeat(70));
        out.printf("Summary: %d transition(s) analyzed, %d level-up event(s), "
                + "%d adjusted, %d anomaly/anomalies%n",
                transitionsDone, totalLevelUps, totalAdjusted, totalAnomalies);
        out.println("=".repeat(70));
    }

    // ---- report helpers ----------------------------------------------------

    static void reportRosterChanges(
            List<Brother> prev, List<Brother> next,
            PrintStream out, List<String> anomalies) {

        Map<String, Brother> prevByFp = byFingerprint(prev);
        Map<String, Brother> nextByFp = byFingerprint(next);

        List<String> added   = nextByFp.keySet().stream()
                .filter(fp -> !prevByFp.containsKey(fp)).toList();
        List<String> removed = prevByFp.keySet().stream()
                .filter(fp -> !nextByFp.containsKey(fp)).toList();

        if (!added.isEmpty() || !removed.isEmpty()) {
            out.println();
            out.println("  Roster changes:");
            added  .forEach(fp -> out.println("    + " + nextByFp.get(fp).name + " (new)"));
            removed.forEach(fp -> out.println("    - " + prevByFp.get(fp).name + " (removed)"));
        }

        if (prev.size() != next.size()) {
            anomalies.add("Brother count changed: " + prev.size() + " → " + next.size());
        }
    }

    static void reportLevelUpEvent(LevelUpEvent e, PrintStream out, DictionaryService dict) {
        out.println();
        out.println("  --- " + e.name() + " ---");

        if (e.levelsAssigned() > 0) {
            out.println("  Level-up: " + e.levelsAssigned() + " assigned"
                    + (e.post11() ? " (post-11)" : ""));
        }

        if (!e.statDeltas().isEmpty()) {
            String deltas = e.statDeltas().entrySet().stream()
                    .map(en -> en.getKey().abbrev() + " +" + en.getValue())
                    .collect(Collectors.joining(", "));
            out.println("  Stats:    " + deltas);
        }

        if (!e.addedPerkIds().isEmpty()) {
            String perks = e.addedPerkIds().stream()
                    .map(id -> {
                        DictionaryEntry de = dict.get(id);
                        return de != null ? de.name : id;
                    })
                    .collect(Collectors.joining(", "));
            out.println("  Perks:    " + perks);
        }

        if (e.adjusted() && !e.consumedIncreases().isEmpty()) {
            String adj = e.consumedIncreases().entrySet().stream()
                    .map(en -> en.getKey().abbrev() + ": " + en.getValue() + " consumed")
                    .collect(Collectors.joining(", "));
            out.println("  Adjusted: " + adj);
        } else if (e.adjusted() && e.post11()) {
            out.println("  Adjusted: post-11 increases recorded");
        }
    }

    static void detectAnomalies(
            List<Brother> prev, List<Brother> next, List<String> anomalies) {

        Map<String, Brother> prevByFp = byFingerprint(prev);
        Map<String, Brother> nextByFp = byFingerprint(next);

        for (Map.Entry<String, Brother> entry : nextByFp.entrySet()) {
            String  fp = entry.getKey();
            Brother nb = entry.getValue();
            Brother ob = prevByFp.get(fp);
            if (ob == null) continue;

            for (Stat s : Stat.values()) {
                int si = s.statIndex();
                if (si >= ob.stats.length || si >= nb.stats.length) continue;
                int delta = nb.stats[si] - ob.stats[si];
                if (delta < 0) {
                    anomalies.add(nb.name + ": " + s.abbrev() + " decreased by " + (-delta));
                }
            }
        }
    }

    /**
     * Compares our carried-forward reconciliation result against the actual on-disk state
     * captured at the start of the next overwrite. Returns a (possibly empty) list of
     * mismatch descriptions that indicate the live reconciler behaved differently.
     */
    static List<String> crossCheck(
            List<Brother> brothers,
            Map<String, int[]> computedPlanned,
            Map<String, int[]> computedPost11,
            Map<String, int[]> actualPlanned,
            Map<String, int[]> actualPost11) {

        List<String> issues = new ArrayList<>();
        for (Brother b : brothers) {
            String fp = b.fingerprint;
            if (fp == null) continue;
            checkArrayMismatch(b.name, "planned",
                    computedPlanned.get(fp), actualPlanned.get(fp), issues);
            checkArrayMismatch(b.name, "post11",
                    computedPost11.get(fp),  actualPost11.get(fp),  issues);
        }
        return issues;
    }

    private static void checkArrayMismatch(
            String name, String label,
            int[] computed, int[] actual,
            List<String> issues) {

        if (isNullOrZero(computed) && isNullOrZero(actual)) return;
        int[] c = nullToZero(computed);
        int[] a = nullToZero(actual);
        if (!Arrays.equals(c, a)) {
            issues.add(name + " " + label + " mismatch — computed: "
                    + Arrays.toString(c) + "  actual: " + Arrays.toString(a));
        }
    }

    // ---- parse helpers -----------------------------------------------------

    private static List<Brother> parseSave(Path savPath, DictionaryService dict) {
        try {
            return new SaveParser(dict).parse(savPath).stream()
                    .filter(ReplayAnalyzer::isValid)
                    .toList();
        } catch (Exception e) {
            System.err.println("WARNING: parse failed for "
                    + savPath.getFileName() + " — " + e.getMessage());
            return List.of();
        }
    }

    /** Replicates MainController.isValid locally to avoid a UI dependency. */
    static boolean isValid(Brother b) {
        if (b.name == null || b.name.isBlank() || b.name.length() > 60) return false;
        if (b.levelTotal < 1) return false;
        if (b.backgroundHexId == null) return false;
        DictionaryEntry bg = DictionaryService.getInstance().get(b.backgroundHexId);
        return bg != null && "background".equals(bg.type);
    }

    // ---- formatting helpers ------------------------------------------------

    /**
     * Converts a snapshot filename stem (e.g. {@code 20260613T120000_000Z}) to a
     * human-readable UTC string. Returns the stem unchanged if parsing fails.
     */
    static String displayTs(String stem) {
        if (stem == null || stem.length() < 19) return stem != null ? stem : "";
        try {
            Instant ts = Instant.from(PARSE_FMT.parse(stem));
            return DISPLAY_FMT.format(ts);
        } catch (Exception e) {
            return stem;
        }
    }

    static String stemOf(Path snapshot) {
        String name = snapshot.getFileName().toString();
        return name.endsWith(".sav") ? name.substring(0, name.length() - 4) : name;
    }

    // ---- collection helpers ------------------------------------------------

    private static Map<String, Brother> byFingerprint(List<Brother> brothers) {
        Map<String, Brother> map = new LinkedHashMap<>();
        for (Brother b : brothers) {
            if (b.fingerprint != null) map.put(b.fingerprint, b);
        }
        return map;
    }

    private static Map<String, int[]> deepCopy(Map<String, int[]> src) {
        Map<String, int[]> copy = new HashMap<>();
        src.forEach((k, v) -> copy.put(k, v.clone()));
        return copy;
    }

    private static boolean isNullOrZero(int[] arr) {
        if (arr == null) return true;
        for (int v : arr) if (v != 0) return false;
        return true;
    }

    private static int[] nullToZero(int[] arr) {
        return arr != null ? arr : new int[Stat.values().length];
    }

    private static List<Path> listSavSnapshots(Path dir) {
        if (dir == null || !Files.isDirectory(dir)) return List.of();
        try (var stream = Files.list(dir)) {
            return stream
                    .filter(p -> p.getFileName().toString().endsWith(".sav"))
                    .sorted()
                    .toList();
        } catch (Exception e) {
            return List.of();
        }
    }
}
