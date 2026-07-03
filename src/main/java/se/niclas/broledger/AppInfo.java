package se.niclas.broledger;

import java.util.Properties;
import java.util.logging.Logger;

/**
 * Build-time app metadata, loaded once from the Maven-filtered
 * {@code version.properties} resource (see {@code pom.xml}'s {@code app.version} /
 * {@code debug.enabled} properties).
 *
 * <p>{@code debug.enabled} defaults to {@code true} for dev/local/CI builds and is
 * forced to {@code false} for tagged releases (see {@code .github/workflows/release.yml}),
 * mirroring how {@code log.file.enabled} gates file logging. Debug-only UI controls and
 * diagnostic features should check {@link #isDebug()} before enabling themselves.</p>
 */
public final class AppInfo {

    private static final Logger log = Logger.getLogger(AppInfo.class.getName());

    private static final Properties PROPS = load();

    private AppInfo() {}

    private static Properties load() {
        Properties props = new Properties();
        try (var in = AppInfo.class.getResourceAsStream("/se/niclas/broledger/version.properties")) {
            if (in != null) props.load(in);
        } catch (Exception e) {
            log.warning("Could not load version.properties: " + e.getMessage());
        }
        return props;
    }

    /** Raw app version string (e.g. "1.5.2.3" for a release, or a build timestamp for dev builds). */
    public static String version() {
        return PROPS.getProperty("version", "");
    }

    /** True unless this is a tagged-release build, which forces debug mode off. */
    public static boolean isDebug() {
        return !"false".equalsIgnoreCase(PROPS.getProperty("debug.enabled"));
    }
}
