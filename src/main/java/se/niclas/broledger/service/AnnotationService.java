package se.niclas.broledger.service;

import se.niclas.broledger.model.Brother;
import se.niclas.broledger.model.BrotherAnnotation;
import se.niclas.broledger.model.Stat;
import java.util.Collections;
import java.util.EnumMap;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

import java.util.logging.Logger;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Persists per-brother role and target-stat annotations adjacent to the save file,
 * with a fallback location in ~/.bro-ledger/.
 *
 * Storage format:
 *   {"version":1,"annotations":{"<fingerprint>":{"roleId":"<uuid>"}}}
 */
public class AnnotationService {

    private static final Logger log = Logger.getLogger(AnnotationService.class.getName());
    private static final ObjectMapper MAPPER = JsonMapper.builder()
            .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
            .build();

    private static final Path FALLBACK_DIR = Path.of(System.getProperty("user.home"), ".bro-ledger");

    private static AnnotationService instance;

    // Current in-memory store: fingerprint → annotation
    private final Map<String, BrotherAnnotation> store = new LinkedHashMap<>();

    // Per-save UI toggle state
    private UiState uiState = new UiState();

    // Resolved paths for the current save
    private Path primaryPath;    // <save>.keeper.json
    private Path fallbackPath;   // ~/.bro-ledger/annotations-<hash>.json

    private AnnotationService() {}

    public static AnnotationService getInstance() {
        if (instance == null) instance = new AnnotationService();
        return instance;
    }

    // ---- lifecycle ---------------------------------------------------------

    /**
     * Call after a save file is selected/reloaded.
     * Loads annotations from the primary path if it exists, else the fallback.
     */
    public void loadFor(Path savePath) {
        primaryPath  = savePath.resolveSibling(savePath.getFileName() + ".broledger.json");
        fallbackPath = FALLBACK_DIR.resolve("annotations-" + sha256hex(savePath.toAbsolutePath().toString()) + ".json");

        store.clear();
        uiState = new UiState();
        if (Files.exists(primaryPath)) {
            read(primaryPath);
        } else if (Files.exists(fallbackPath)) {
            read(fallbackPath);
        }
    }

    // ---- API ---------------------------------------------------------------

    public BrotherAnnotation get(String fingerprint) {
        return store.computeIfAbsent(fingerprint, BrotherAnnotation::new);
    }

    /** Store the role UUID for this brother (null = no role assigned). */
    public void setRole(String fingerprint, String roleId) {
        get(fingerprint).roleId = roleId;
        flush();
    }

    /** Store the planned level-up increases (lv 1–11) for this brother (null = clear all). */
    public void setStatIncreases(String fingerprint, int[] increases) {
        get(fingerprint).statIncreases = increases;
        flush();
    }

    /** Store the post-lv11 increases already taken for this brother (null = clear all). */
    public void setPost11Increases(String fingerprint, int[] increases) {
        get(fingerprint).post11Increases = increases;
        flush();
    }

    public UiState getUiState() { return uiState; }

    public void setUiState(UiState state) {
        uiState = state;
        flush();
    }

    /** Persist the display order of brothers; assigns sortIndex 0..n-1 and flushes once. */
    public void setOrder(List<String> fingerprintsInOrder) {
        for (int i = 0; i < fingerprintsInOrder.size(); i++) {
            get(fingerprintsInOrder.get(i)).sortIndex = i;
        }
        flush();
    }

    // ---- persistence -------------------------------------------------------

    private void flush() {
        if (primaryPath == null) return;
        Path dest = resolveDestination();
        if (dest == null) return;
        write(dest);
    }

    private Path resolveDestination() {
        try {
            Files.createDirectories(primaryPath.getParent());
            return primaryPath;
        } catch (IOException e) {
            try {
                Files.createDirectories(fallbackPath.getParent());
                return fallbackPath;
            } catch (IOException ex) {
                log.warning("AnnotationService: cannot create directory — " + ex.getMessage());
                return null;
            }
        }
    }

    private void write(Path dest) {
        try {
            AnnotationsFile file = new AnnotationsFile();
            file.uiState = uiState;
            for (var entry : store.entrySet()) {
                BrotherAnnotation a = entry.getValue();
                AnnotationsFile.AnnotationEntry ae = new AnnotationsFile.AnnotationEntry();
                ae.roleId         = a.roleId;
                ae.sortIndex      = a.sortIndex;
                ae.statIncreases  = a.statIncreases;
                ae.post11Increases = a.post11Increases;
                file.annotations.put(entry.getKey(), ae);
            }
            MAPPER.writerWithDefaultPrettyPrinter().writeValue(dest.toFile(), file);
        } catch (Exception e) {
            log.warning("AnnotationService: write failed — " + e.getMessage());
        }
    }

    private void read(Path src) {
        try {
            AnnotationsFile file = MAPPER.readValue(src.toFile(), AnnotationsFile.class);
            if (file.uiState != null) uiState = file.uiState;
            if (file.annotations != null) {
                file.annotations.forEach((fp, ae) -> {
                    BrotherAnnotation a = new BrotherAnnotation(fp);
                    a.roleId          = ae.roleId;
                    a.sortIndex       = ae.sortIndex;
                    a.statIncreases   = ae.statIncreases;
                    a.post11Increases = ae.post11Increases;
                    store.put(fp, a);
                });
            }
        } catch (Exception e) {
            log.warning("AnnotationService: read failed — " + e.getMessage());
        }
    }

    // ---- Jackson wrapper ---------------------------------------------------

    public static class UiState {
        public String potentialMode;
        public String armorMode;
        public String weaponTierMode;
        public String perkSortMode;
    }

    static class AnnotationsFile {
        public int version = 1;
        public UiState uiState;
        public Map<String, AnnotationEntry> annotations = new LinkedHashMap<>();

        static class AnnotationEntry {
            public String  roleId;
            public Integer sortIndex;
            public int[]   statIncreases;
            public int[]   post11Increases;
        }
    }

    // ---- level-up reconciliation -------------------------------------------

    private static final String GIFTED_PERK_ID = "9899E380";

    public record LevelUpEvent(
            String name,
            boolean adjusted,
            int levelsAssigned,
            Map<Stat, Integer> statDeltas,
            List<Stat> adjustedStats,
            Map<Stat, Integer> consumedIncreases,
            List<String> addedPerkIds,
            boolean post11
    ) {}

    record Reconciliation(
            List<Stat> adjustedStats,
            Map<Stat, Integer> consumedIncreases,
            int[] updatedStatIncreases,
            int[] updatedPost11Increases,
            boolean changed) {}

    /**
     * Compares old vs new brother lists after a save reload and reduces
     * statIncreases[] for any brother whose levelPoints count decreased
     * (i.e. they assigned a level-up in-game).
     */
    public List<LevelUpEvent> reconcileOnReload(List<Brother> oldList, List<Brother> newList) {
        List<LevelUpEvent> events = new ArrayList<>();
        log.fine("reconcileOnReload: checking " + newList.size() + " brothers against " + oldList.size() + " old");
        for (Brother nb : newList) {
            if (nb.fingerprint == null) {
                log.fine("  skip [" + nb.name + "]: no fingerprint");
                continue;
            }
            Brother ob = findMatch(oldList, nb.fingerprint);
            if (ob == null) {
                log.fine("  skip [" + nb.name + "]: no match in old list (new brother?)");
                continue;
            }
            log.fine("  [" + nb.name + "] old.levelPoints=" + ob.levelPoints + "  new.levelPoints=" + nb.levelPoints);
            LevelUpEvent e = reconcileBrother(ob, nb);
            if (e != null) events.add(e);
        }
        log.fine("reconcileOnReload: done — " + events.size() + " level-up event(s)");
        return events;
    }

    private LevelUpEvent reconcileBrother(Brother ob, Brother nb) {
        List<String> addedPerkIds = addedPerks(ob, nb);
        if (!addedPerkIds.isEmpty()) {
            log.fine("  [" + nb.name + "]: " + addedPerkIds.size() + " perk(s) added: " + addedPerkIds);
        }

        boolean levelPointsDecreased = nb.levelPoints < ob.levelPoints;
        boolean giftedSelected       = addedPerkIds.contains(GIFTED_PERK_ID);

        if (!levelPointsDecreased && addedPerkIds.isEmpty()) {
            log.fine("  [" + nb.name + "]: no level assigned and no perks added — skipping");
            return null;
        }

        if (levelPointsDecreased) {
            log.fine("  [" + nb.name + "]: " + (ob.levelPoints - nb.levelPoints) + " level(s) assigned in-game");
        }
        if (giftedSelected) {
            log.fine("  [" + nb.name + "]: Gifted bonus round — treating as extra level for reconciliation");
        }

        int levels               = levelsAssigned(ob, nb, giftedSelected);
        BrotherAnnotation a      = get(nb.fingerprint);
        Map<Stat, Integer> deltas = statDeltas(ob, nb);
        boolean post11            = levelPointsDecreased && isPost11Reconcile(ob, nb);
        Reconciliation r;

        if (post11) {
            r = reconcilePost11(a, deltas);
            if (r.changed()) {
                log.fine("  [" + nb.name + "]: post-lv11 increases recorded: " + deltas);
                setPost11Increases(nb.fingerprint, r.updatedPost11Increases());
            } else {
                log.fine("  [" + nb.name + "]: post-lv11 level-up but no stat deltas detected");
            }
        } else if (levelPointsDecreased || giftedSelected) {
            r = consumePlannedIncreases(a, nb, deltas);
            if (a.statIncreases == null) {
                log.fine("  [" + nb.name + "]: no planned statIncreases — recording level-up without adjustment");
            } else if (r.changed()) {
                log.fine("  [" + nb.name + "]: saving updated statIncreases");
                setStatIncreases(nb.fingerprint, r.updatedStatIncreases());
            } else {
                log.fine("  [" + nb.name + "]: leveled up but no planned increases were affected");
            }
        } else {
            r = new Reconciliation(List.of(), Map.of(), null, null, false);
        }

        return new LevelUpEvent(
                nb.name,
                r.changed(),
                levels,
                Collections.unmodifiableMap(deltas),
                r.adjustedStats(),
                r.consumedIncreases(),
                Collections.unmodifiableList(addedPerkIds),
                post11
        );
    }

    // ---- static reconciliation helpers -------------------------------------

    static Map<Stat, Integer> statDeltas(Brother ob, Brother nb) {
        Map<Stat, Integer> deltas = new EnumMap<>(Stat.class);
        for (Stat s : Stat.values()) {
            int si = s.statIndex();
            if (si >= nb.stats.length || si >= ob.stats.length) continue;
            int d = nb.stats[si] - ob.stats[si];
            if (d > 0) deltas.put(s, d);
        }
        return deltas;
    }

    static int raisesLowerBound(int statDelta, int statIndex, int stars) {
        StatPotentialCalculator.Range range = StatPotentialCalculator.rangeForStars(statIndex, stars);
        int maxPerRoll = (range != null) ? range.max() : statDelta;
        return (int) Math.ceil((double) statDelta / maxPerRoll);
    }

    static List<String> addedPerks(Brother ob, Brother nb) {
        return nb.perkIds.stream().filter(id -> !ob.perkIds.contains(id)).toList();
    }

    static boolean isPost11Reconcile(Brother ob, Brother nb) {
        return ob.levelTotal >= 11 && nb.levelTotal > 11;
    }

    static Brother findMatch(List<Brother> oldList, String fingerprint) {
        return oldList.stream()
                .filter(b -> fingerprint.equals(b.fingerprint))
                .findFirst().orElse(null);
    }

    static int levelsAssigned(Brother ob, Brother nb, boolean gifted) {
        int normal = Math.max(0, ob.levelPoints - nb.levelPoints);
        return normal + (gifted ? 1 : 0);
    }

    static Reconciliation reconcilePost11(BrotherAnnotation a, Map<Stat, Integer> deltas) {
        if (deltas.isEmpty()) return new Reconciliation(List.of(), Map.of(), null, null, false);
        int[] post11 = a.post11Increases == null
                ? new int[Stat.values().length]
                : Arrays.copyOf(a.post11Increases, Stat.values().length);
        for (Map.Entry<Stat, Integer> entry : deltas.entrySet()) {
            post11[entry.getKey().ordinal()] += entry.getValue();
        }
        return new Reconciliation(List.of(), Map.of(), null, post11, true);
    }

    static Reconciliation consumePlannedIncreases(BrotherAnnotation a, Brother nb, Map<Stat, Integer> deltas) {
        if (a.statIncreases == null) return new Reconciliation(List.of(), Map.of(), null, null, false);
        int[] statIncreases = Arrays.copyOf(a.statIncreases, a.statIncreases.length);
        List<Stat> adjustedStats = new ArrayList<>();
        Map<Stat, Integer> consumedIncreases = new EnumMap<>(Stat.class);
        for (Map.Entry<Stat, Integer> entry : deltas.entrySet()) {
            Stat s       = entry.getKey();
            int ord      = s.ordinal();
            int si       = s.statIndex();
            int delta    = entry.getValue();
            int planned  = ord < statIncreases.length ? statIncreases[ord] : 0;
            if (planned <= 0) continue;
            int stars    = (si < nb.stars.length) ? nb.stars[s.starIndex()] : 0;
            int toConsume = Math.min(raisesLowerBound(delta, si, stars), planned);
            statIncreases[ord] = planned - toConsume;
            consumedIncreases.put(s, toConsume);
            adjustedStats.add(s);
        }
        if (adjustedStats.isEmpty()) return new Reconciliation(List.of(), Map.of(), null, null, false);
        return new Reconciliation(
                Collections.unmodifiableList(adjustedStats),
                Collections.unmodifiableMap(consumedIncreases),
                statIncreases,
                null,
                true
        );
    }

    // ---- utility -----------------------------------------------------------

    private static String sha256hex(String input) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] hash = md.digest(input.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (Exception e) {
            return Integer.toHexString(input.hashCode());
        }
    }
}
