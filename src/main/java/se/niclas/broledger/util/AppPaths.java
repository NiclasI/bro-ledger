package se.niclas.broledger.util;

import java.nio.file.Path;

/**
 * Central location for the app's per-user data directory (normally {@code ~/.bro-ledger}).
 * Overridable via {@code -Dbroledger.dataDir=<path>} so multiple independent app instances
 * (e.g. an exporter and importer session for manual role-share testing) can run on the same
 * machine without sharing config, roles, or annotations. See AGENTS.md "Testing role sharing
 * with two sessions".
 */
public final class AppPaths {

    private AppPaths() {
    }

    public static Path dataDir() {
        String override = System.getProperty("broledger.dataDir");
        if (override != null && !override.isBlank()) {
            return Path.of(override);
        }
        return Path.of(System.getProperty("user.home"), ".bro-ledger");
    }
}
