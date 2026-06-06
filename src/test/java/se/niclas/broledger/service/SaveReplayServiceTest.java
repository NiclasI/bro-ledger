package se.niclas.broledger.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for {@link SaveReplayService}.
 *
 * Uses an injectable base-dir constructor so tests never touch the real ~/.bro-ledger/replay/.
 * AppConfig.replayCaptureEnabled is toggled via a fresh AppConfig instance via reflection.
 */
class SaveReplayServiceTest {

    @TempDir
    Path baseDir;   // synthetic replay root

    @TempDir
    Path saveDir;   // directory that holds the "save file"

    private Path      savePath;
    private AppConfig cfg;

    @BeforeEach
    void setUp() throws Exception {
        savePath = saveDir.resolve("campaign.sav");
        Files.write(savePath, new byte[]{0x01, 0x02, 0x03});

        // Fresh AppConfig instance via reflection (avoids singleton bleed)
        var ctor = AppConfig.class.getDeclaredConstructor();
        ctor.setAccessible(true);
        cfg = (AppConfig) ctor.newInstance();
        cfg.replayCaptureEnabled = false; // disabled by default

        // Point the singleton at our fresh instance
        var field = AppConfig.class.getDeclaredField("instance");
        field.setAccessible(true);
        field.set(null, cfg);
    }

    // ---- replayDir ---------------------------------------------------------

    @Test
    void replayDir_returnsDirUnderBaseDir() {
        SaveReplayService srs = new SaveReplayService(baseDir);
        Path dir = srs.replayDir(savePath);
        assertTrue(dir.startsWith(baseDir), "replay dir must be under baseDir");
        // Must not be the baseDir itself
        assertNotEquals(baseDir, dir);
    }

    @Test
    void replayDir_sameInputSameDir() {
        SaveReplayService srs = new SaveReplayService(baseDir);
        Path d1 = srs.replayDir(savePath);
        Path d2 = srs.replayDir(savePath);
        assertEquals(d1, d2);
    }

    @Test
    void replayDir_differentSavePathDifferentDir() throws Exception {
        Path other = saveDir.resolve("other.sav");
        Files.write(other, new byte[]{0x04});
        SaveReplayService srs = new SaveReplayService(baseDir);
        assertNotEquals(srs.replayDir(savePath), srs.replayDir(other));
    }

    // ---- capture: disabled -------------------------------------------------

    @Test
    void capture_doesNothingWhenDisabled() throws Exception {
        cfg.replayCaptureEnabled = false;
        SaveReplayService srs = new SaveReplayService(baseDir);
        srs.capture(savePath);

        // Give the daemon thread a moment (it shouldn't have started, but be safe)
        Thread.sleep(200);

        assertTrue(srs.snapshots(savePath).isEmpty(),
                "no snapshots should be captured when disabled");
    }

    // ---- capture: enabled --------------------------------------------------

    @Test
    void capture_writesSavSnapshotWhenEnabled() throws Exception {
        cfg.replayCaptureEnabled = true;
        SaveReplayService srs = new SaveReplayService(baseDir);
        srs.capture(savePath);

        awaitSnapshot(srs, savePath, 1, 2000);

        List<Path> snaps = srs.snapshots(savePath);
        assertEquals(1, snaps.size(), "exactly one snapshot should be captured");
        assertArrayEquals(Files.readAllBytes(savePath), Files.readAllBytes(snaps.get(0)),
                "snapshot contents must match the source save");
    }

    @Test
    void capture_pairsAnnotationsWhenPresent() throws Exception {
        cfg.replayCaptureEnabled = true;

        // Write a fake .broledger.json adjacent to the save
        Path annotPath = saveDir.resolve("campaign.sav.broledger.json");
        String annotJson = "{\"version\":1,\"annotations\":{}}";
        Files.writeString(annotPath, annotJson);

        SaveReplayService srs = new SaveReplayService(baseDir);
        srs.capture(savePath);

        awaitSnapshot(srs, savePath, 1, 2000);

        Path snap = srs.snapshots(savePath).get(0);
        Path paired = srs.annotationsFor(snap);
        assertNotNull(paired, "paired .broledger.json must be present");
        assertEquals(annotJson, Files.readString(paired),
                "paired annotation content must match the source");
    }

    @Test
    void capture_writesSourceTxt() throws Exception {
        cfg.replayCaptureEnabled = true;
        SaveReplayService srs = new SaveReplayService(baseDir);
        srs.capture(savePath);

        awaitSnapshot(srs, savePath, 1, 2000);

        Path sourceTxt = srs.replayDir(savePath).resolve("source.txt");
        assertTrue(Files.exists(sourceTxt), "source.txt must be written");
        assertEquals(savePath.toAbsolutePath().toString(),
                Files.readString(sourceTxt).strip());
    }

    @Test
    void capture_skipsDuplicateSave() throws Exception {
        cfg.replayCaptureEnabled = true;
        SaveReplayService srs = new SaveReplayService(baseDir);

        srs.capture(savePath);
        awaitSnapshot(srs, savePath, 1, 2000);

        // Second capture of identical content — should be skipped
        // (small sleep to ensure different timestamp, so if it DOES capture we can count 2)
        Thread.sleep(50);
        srs.capture(savePath);
        Thread.sleep(500);

        assertEquals(1, srs.snapshots(savePath).size(),
                "duplicate capture must be suppressed");
    }

    @Test
    void capture_doesNotSkipWhenContentChanges() throws Exception {
        cfg.replayCaptureEnabled = true;
        SaveReplayService srs = new SaveReplayService(baseDir);

        srs.capture(savePath);
        awaitSnapshot(srs, savePath, 1, 2000);

        // Change save content
        Files.write(savePath, new byte[]{0x0A, 0x0B, 0x0C, 0x0D});
        Thread.sleep(50);
        srs.capture(savePath);
        awaitSnapshot(srs, savePath, 2, 2000);

        assertEquals(2, srs.snapshots(savePath).size());
    }

    // ---- prune -------------------------------------------------------------

    @Test
    void prune_keepsLastNSnapshots() throws Exception {
        cfg.replayCaptureEnabled = true;
        SaveReplayService srs = new SaveReplayService(baseDir);
        Path dir = srs.replayDir(savePath);
        Files.createDirectories(dir);

        // Create 7 fake snapshots manually (different names = different timestamps)
        for (int i = 1; i <= 7; i++) {
            Files.write(dir.resolve("2026010" + i + "T120000_000Z.sav"), new byte[]{(byte) i});
        }

        srs.prune(dir, 5);

        List<Path> remaining = srs.snapshots(savePath);
        assertEquals(5, remaining.size(), "prune must keep exactly 5");
        // Oldest two (indices 1 and 2) should be gone; newest five kept
        assertTrue(remaining.stream().allMatch(p -> {
            String name = p.getFileName().toString();
            return name.compareTo("20260103") > 0; // i=1 and i=2 pruned
        }), "oldest snapshots should be removed");
    }

    @Test
    void prune_removesPairedAnnotationWithSav() throws Exception {
        SaveReplayService srs = new SaveReplayService(baseDir);
        Path dir = srs.replayDir(savePath);
        Files.createDirectories(dir);

        // Create 3 paired sets
        for (int i = 1; i <= 3; i++) {
            String stem = "2026010" + i + "T120000_000Z";
            Files.write(dir.resolve(stem + ".sav"),            new byte[]{(byte) i});
            Files.write(dir.resolve(stem + ".broledger.json"), new byte[]{'{'});
        }

        srs.prune(dir, 2); // remove the oldest one (i=1)

        // i=1's .broledger.json should also be gone
        assertFalse(Files.exists(dir.resolve("20260101T120000_000Z.broledger.json")),
                "paired annotation must be pruned with its .sav");
        // i=2 and i=3 survive
        assertEquals(2, srs.snapshots(savePath).size());
    }

    @Test
    void prune_nopWhenBelowCap() throws Exception {
        SaveReplayService srs = new SaveReplayService(baseDir);
        Path dir = srs.replayDir(savePath);
        Files.createDirectories(dir);

        Files.write(dir.resolve("20260101T120000_000Z.sav"), new byte[]{1});
        Files.write(dir.resolve("20260102T120000_000Z.sav"), new byte[]{2});

        srs.prune(dir, 5); // cap=5, only 2 exist → nothing deleted

        assertEquals(2, srs.snapshots(savePath).size());
    }

    // ---- snapshots ---------------------------------------------------------

    @Test
    void snapshots_emptyForUnknownSave() {
        SaveReplayService srs = new SaveReplayService(baseDir);
        assertTrue(srs.snapshots(savePath).isEmpty());
    }

    @Test
    void snapshots_sortedChronologically() throws Exception {
        SaveReplayService srs = new SaveReplayService(baseDir);
        Path dir = srs.replayDir(savePath);
        Files.createDirectories(dir);

        // Write in reverse order to ensure sorting is not filesystem-order dependent
        Files.write(dir.resolve("20260103T120000_000Z.sav"), new byte[]{3});
        Files.write(dir.resolve("20260101T120000_000Z.sav"), new byte[]{1});
        Files.write(dir.resolve("20260102T120000_000Z.sav"), new byte[]{2});

        List<Path> snaps = srs.snapshots(savePath);
        assertEquals(3, snaps.size());
        assertEquals("20260101T120000_000Z.sav", snaps.get(0).getFileName().toString());
        assertEquals("20260102T120000_000Z.sav", snaps.get(1).getFileName().toString());
        assertEquals("20260103T120000_000Z.sav", snaps.get(2).getFileName().toString());
    }

    // ---- annotationsFor ----------------------------------------------------

    @Test
    void annotationsFor_returnsNullWhenNoPair() throws Exception {
        SaveReplayService srs = new SaveReplayService(baseDir);
        Path dir = srs.replayDir(savePath);
        Files.createDirectories(dir);
        Path snap = dir.resolve("20260101T120000_000Z.sav");
        Files.write(snap, new byte[]{1});

        assertNull(srs.annotationsFor(snap));
    }

    @Test
    void annotationsFor_returnsPairedJson() throws Exception {
        SaveReplayService srs = new SaveReplayService(baseDir);
        Path dir = srs.replayDir(savePath);
        Files.createDirectories(dir);
        Path snap   = dir.resolve("20260101T120000_000Z.sav");
        Path paired = dir.resolve("20260101T120000_000Z.broledger.json");
        Files.write(snap,   new byte[]{1});
        Files.write(paired, new byte[]{'{', '}'});

        assertEquals(paired, srs.annotationsFor(snap));
    }

    // ---- listAll -----------------------------------------------------------

    @Test
    void listAll_emptyWhenBaseDirAbsent() {
        Path missing = baseDir.resolve("nonexistent");
        SaveReplayService srs = new SaveReplayService(missing);
        assertTrue(srs.listAll().isEmpty());
    }

    @Test
    void listAll_returnsOneEntryPerSubDir() throws Exception {
        SaveReplayService srs1 = new SaveReplayService(baseDir);
        SaveReplayService srs2 = new SaveReplayService(baseDir);

        Path save1 = saveDir.resolve("alpha.sav");
        Path save2 = saveDir.resolve("beta.sav");
        Files.write(save1, new byte[]{1});
        Files.write(save2, new byte[]{2});

        // Capture creates sub-dirs
        cfg.replayCaptureEnabled = true;
        srs1.capture(save1); awaitSnapshot(srs1, save1, 1, 2000);
        srs2.capture(save2); awaitSnapshot(srs2, save2, 1, 2000);

        List<SaveReplayService.ReplayEntry> all = srs1.listAll();
        assertEquals(2, all.size());
        // Each entry should have snapshotCount=1
        all.forEach(e -> assertEquals(1, e.snapshotCount()));
    }

    // ---- capture cap -------------------------------------------------------

    @Test
    void capture_prunesOldestBeyondCap() throws Exception {
        cfg.replayCaptureEnabled = true;
        SaveReplayService srs = new SaveReplayService(baseDir);

        // Capture MAX_SNAPSHOTS + 1 distinct saves
        for (int i = 0; i <= SaveReplayService.MAX_SNAPSHOTS; i++) {
            Files.write(savePath, new byte[]{(byte) i});
            srs.capture(savePath);
            awaitSnapshot(srs, savePath, Math.min(i + 1, SaveReplayService.MAX_SNAPSHOTS), 2000);
            Thread.sleep(20); // ensure distinct timestamps
        }

        assertTrue(srs.snapshots(savePath).size() <= SaveReplayService.MAX_SNAPSHOTS,
                "snapshots must be capped at MAX_SNAPSHOTS=" + SaveReplayService.MAX_SNAPSHOTS);
    }

    // ---- helpers -----------------------------------------------------------

    /** Polls until the expected snapshot count is reached or timeout expires. */
    private static void awaitSnapshot(SaveReplayService srs, Path savePath,
                                      int expectedCount, long timeoutMs) throws InterruptedException {
        long deadline = System.currentTimeMillis() + timeoutMs;
        while (System.currentTimeMillis() < deadline) {
            if (srs.snapshots(savePath).size() >= expectedCount) return;
            Thread.sleep(50);
        }
        // Let the assertion in the caller fail with a clear message
    }
}
