package se.niclas.broledger.service;

import se.niclas.broledger.AppInfo;
import se.niclas.broledger.util.HexUtils;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.logging.Logger;
import java.util.stream.Stream;

/**
 * Captures timestamped snapshots of the active save file (and its current planned-increase
 * annotations) into {@code ~/.bro-ledger/replay/<hash>/} whenever an overwrite is detected,
 * so that the sequence can be replayed offline via {@link se.niclas.broledger.tools.ReplayAnalyzer}.
 *
 * <p>Capture is off by default and is gated on {@link AppConfig#replayCaptureEnabled}, and is
 * additionally disabled outright in non-debug (tagged-release) builds — see {@link AppInfo#isDebug()}.</p>
 *
 * <p>At most {@link #MAX_SNAPSHOTS} save snapshots are kept per save file; older ones
 * (plus their paired {@code .broledger.json} annotation files) are pruned automatically.</p>
 */
public class SaveReplayService {

    /** Maximum number of .sav snapshots retained per save file. */
    public static final int MAX_SNAPSHOTS = 5;

    private static final Logger log = Logger.getLogger(SaveReplayService.class.getName());

    /** Timestamp format: lexicographically sortable, unambiguous UTC. */
    static final DateTimeFormatter TS_FMT =
            DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss'_'SSS'Z'").withZone(ZoneOffset.UTC);

    private static SaveReplayService instance;

    /** Root directory that holds one sub-directory per captured save path. */
    private final Path baseDir;

    /** Default constructor — stores snapshots under ~/.bro-ledger/replay/. */
    private SaveReplayService() {
        this.baseDir = Path.of(System.getProperty("user.home"), ".bro-ledger", "replay");
    }

    /** Package-private constructor for tests — uses the supplied base directory. */
    SaveReplayService(Path baseDir) {
        this.baseDir = baseDir;
    }

    public static SaveReplayService getInstance() {
        if (instance == null) instance = new SaveReplayService();
        return instance;
    }

    // ---- public API --------------------------------------------------------

    /** Replay directory for a given save path, keyed by SHA-256 of its absolute path. */
    public Path replayDir(Path savePath) {
        return baseDir.resolve(HexUtils.sha256Hex(savePath.toAbsolutePath().toString()));
    }

    /**
     * Captures the current save file into the replay directory if
     * {@link AppConfig#replayCaptureEnabled} is true and this is a debug build.
     *
     * <p>The annotation bytes are read <em>synchronously</em> on the calling thread
     * (the FX thread) so they reflect the pre-reconciliation state. The actual file
     * copy is done on a daemon thread to keep the FX thread responsive.</p>
     *
     * <p>Safe to call even when capture is disabled — returns immediately.</p>
     */
    public void capture(Path savePath) {
        if (!AppInfo.isDebug() || !AppConfig.getInstance().replayCaptureEnabled) return;

        // Read annotation bytes NOW (pre-reconciliation state, FX thread) before spawning
        // the daemon thread that may race with reconcileOnReload writing to disk.
        byte[] annotBytes = readCurrentAnnotationBytes(savePath);

        Thread t = new Thread(() -> doCapture(savePath, annotBytes), "replay-capture");
        t.setDaemon(true);
        t.start();
    }

    /** Sorted list of {@code .sav} snapshot paths for a given save (chronological, ascending). */
    public List<Path> snapshots(Path savePath) {
        return listSavSnapshots(replayDir(savePath));
    }

    /**
     * Returns the {@code .broledger.json} paired with a snapshot file (same timestamp stem),
     * or {@code null} if none was captured.
     */
    public Path annotationsFor(Path snapshot) {
        String name = snapshot.getFileName().toString();
        String stem = name.endsWith(".sav") ? name.substring(0, name.length() - 4) : name;
        Path paired = snapshot.resolveSibling(stem + ".broledger.json");
        return Files.exists(paired) ? paired : null;
    }

    /** Describes one captured save entry (one replay sub-directory). */
    public record ReplayEntry(Path dir, String sourceSavePath, int snapshotCount) {}

    /**
     * Lists all replay entries across all captured save files, sorted by source save path.
     * Each entry corresponds to one sub-directory under {@link #baseDir}.
     */
    public List<ReplayEntry> listAll() {
        if (!Files.isDirectory(baseDir)) return List.of();
        try (Stream<Path> stream = Files.list(baseDir)) {
            return stream
                    .filter(Files::isDirectory)
                    .map(this::entryFor)
                    .sorted(Comparator.comparing(ReplayEntry::sourceSavePath))
                    .toList();
        } catch (IOException e) {
            log.warning("SaveReplayService: listAll failed — " + e.getMessage());
            return List.of();
        }
    }

    /**
     * Prunes {@code dir} so that at most {@code keep} {@code .sav} snapshots remain,
     * deleting the oldest first along with their paired {@code .broledger.json} files.
     */
    public void prune(Path dir, int keep) {
        List<Path> savs = listSavSnapshots(dir);
        int toDelete = savs.size() - keep;
        for (int i = 0; i < toDelete; i++) {
            Path sav = savs.get(i);
            try {
                Files.deleteIfExists(sav);
                Path paired = annotationsFor(sav);
                if (paired != null) Files.deleteIfExists(paired);
            } catch (IOException e) {
                log.fine("SaveReplayService: prune delete failed — " + e.getMessage());
            }
        }
    }

    // ---- internal ----------------------------------------------------------

    private void doCapture(Path savePath, byte[] annotBytes) {
        try {
            Path dir = replayDir(savePath);
            Files.createDirectories(dir);

            // Skip exact duplicates (same bytes as the most recent snapshot).
            List<Path> existing = listSavSnapshots(dir);
            if (!existing.isEmpty()) {
                Path last = existing.get(existing.size() - 1);
                byte[] saveBytes = Files.readAllBytes(savePath);
                if (Files.size(last) == saveBytes.length &&
                        Arrays.equals(Files.readAllBytes(last), saveBytes)) {
                    return;
                }
            }

            String ts      = TS_FMT.format(Instant.now());
            Path   destSav = dir.resolve(ts + ".sav");
            Files.copy(savePath, destSav);

            if (annotBytes != null) {
                Files.write(dir.resolve(ts + ".broledger.json"), annotBytes);
            }

            // Write/refresh source.txt so the interactive analyzer can show a human-readable name.
            Files.writeString(dir.resolve("source.txt"),
                    savePath.toAbsolutePath().toString());

            prune(dir, MAX_SNAPSHOTS);

        } catch (Exception e) {
            log.warning("SaveReplayService: capture failed — " + e.getMessage());
        }
    }

    /**
     * Reads the current (pre-reconciliation) annotation bytes from disk.
     * Prefers the primary path adjacent to the save; falls back to the ~/.bro-ledger fallback.
     * Returns null if neither exists.
     */
    private byte[] readCurrentAnnotationBytes(Path savePath) {
        String hash     = HexUtils.sha256Hex(savePath.toAbsolutePath().toString());
        Path primary    = savePath.resolveSibling(savePath.getFileName() + ".broledger.json");
        Path fallback   = Path.of(System.getProperty("user.home"), ".bro-ledger",
                                  "annotations-" + hash + ".json");
        Path source     = Files.exists(primary) ? primary
                        : Files.exists(fallback) ? fallback
                        : null;
        if (source == null) return null;
        try {
            return Files.readAllBytes(source);
        } catch (IOException e) {
            log.fine("SaveReplayService: could not read annotation bytes — " + e.getMessage());
            return null;
        }
    }

    private ReplayEntry entryFor(Path dir) {
        Path sourceTxt = dir.resolve("source.txt");
        String sourcePath = "<unknown>";
        if (Files.exists(sourceTxt)) {
            try { sourcePath = Files.readString(sourceTxt).strip(); } catch (IOException ignored) {}
        }
        return new ReplayEntry(dir, sourcePath, listSavSnapshots(dir).size());
    }

    private static List<Path> listSavSnapshots(Path dir) {
        if (!Files.isDirectory(dir)) return List.of();
        try (Stream<Path> stream = Files.list(dir)) {
            return stream
                    .filter(p -> p.getFileName().toString().endsWith(".sav"))
                    .sorted()
                    .toList();
        } catch (IOException e) {
            return List.of();
        }
    }
}
