package se.niclas.broledger.service;

import javafx.application.Platform;

import java.io.IOException;
import java.nio.file.*;
import java.util.logging.Logger;

/**
 * Watches a save file for external modifications and fires a callback on the FX thread.
 *
 * Stability loop: after detecting ENTRY_MODIFY, polls Files.size() every 250 ms up to
 * 6 times; fires only when two consecutive readings are equal (write has settled).
 */
public class FileWatcherService {

    private static final Logger log = Logger.getLogger(FileWatcherService.class.getName());
    private static final int  POLL_INTERVAL_MS  = 250;
    private static final int  POLL_MAX_ATTEMPTS = 6;
    private static final long DEBOUNCE_MS       = 2_000;

    private WatchService watchService;
    private Thread       watchThread;
    private volatile long lastFiredAt = 0;

    // ---- lifecycle ---------------------------------------------------------

    /**
     * Start watching {@code savePath}. Fires {@code onChange} on the FX thread
     * whenever the file appears to be fully written.
     * Any previous watcher is stopped first.
     */
    public void watch(Path savePath, Runnable onChange) {
        stop();

        Path dir = savePath.getParent();
        String target = savePath.getFileName().toString();

        try {
            watchService = FileSystems.getDefault().newWatchService();
            dir.register(watchService, StandardWatchEventKinds.ENTRY_MODIFY);
        } catch (IOException e) {
            log.warning("FileWatcherService: cannot register watch — " + e.getMessage());
            return;
        }

        watchThread = new Thread(() -> watchLoop(savePath, target, onChange), "file-watcher");
        watchThread.setDaemon(true);
        watchThread.start();
    }

    /** Stop the watcher. Safe to call even if not currently watching. */
    public void stop() {
        lastFiredAt = 0;
        if (watchService != null) {
            try { watchService.close(); } catch (IOException e) { log.fine("FileWatcherService: close — " + e.getMessage()); }
            watchService = null;
        }
        if (watchThread != null) {
            watchThread.interrupt();
            watchThread = null;
        }
    }

    // ---- internal ----------------------------------------------------------

    private void watchLoop(Path savePath, String targetFilename, Runnable onChange) {
        while (!Thread.currentThread().isInterrupted()) {
            WatchKey key;
            try {
                key = watchService.take();
            } catch (InterruptedException | ClosedWatchServiceException e) {
                return;
            }

            boolean relevant = false;
            for (WatchEvent<?> event : key.pollEvents()) {
                if (event.kind() == StandardWatchEventKinds.OVERFLOW) continue;
                Path changed = (Path) event.context();
                if (targetFilename.equals(changed.getFileName().toString())) {
                    relevant = true;
                }
            }
            key.reset();

            if (relevant && hasSettled(savePath)) {
                long now = System.currentTimeMillis();
                if (now - lastFiredAt >= DEBOUNCE_MS) {
                    lastFiredAt = now;
                    Platform.runLater(onChange);
                } else {
                    log.fine("FileWatcherService: suppressed duplicate event within debounce window");
                }
            }
        }
    }

    private boolean hasSettled(Path savePath) {
        long prev   = -1;
        int  stable = 0;
        for (int i = 0; i < POLL_MAX_ATTEMPTS; i++) {
            try {
                Thread.sleep(POLL_INTERVAL_MS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return false;
            }
            long size;
            try {
                size = Files.size(savePath);
            } catch (IOException e) {
                log.fine("FileWatcherService: size poll — " + e.getMessage());
                continue;
            }
            if (size > 0 && size == prev) {
                if (++stable >= 2) return true;
            } else {
                stable = 0;
            }
            prev = size;
        }
        return stable >= 1;
    }
}
