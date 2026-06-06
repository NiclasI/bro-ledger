package se.niclas.broledger;

import java.io.InputStream;
import java.util.logging.LogManager;

/**
 * Fat-JAR entry point.
 *
 * JavaFX requires its modules to be on the JVM module path, but
 * maven-shade-plugin places everything on the classpath. When the
 * JVM starts a class that directly extends Application, the JavaFX
 * bootstrapper runs before main() and fails because it cannot find
 * its modules — producing "JavaFX runtime components are missing".
 *
 * By launching from a plain (non-Application) class first, the
 * bootstrapper is bypassed. Application.launch() called from App
 * then works correctly off the classpath.
 */
public class Launcher {
    public static void main(String[] args) {
        // When running via `javafx:run`, -Djava.util.logging.config.file is set and
        // JUL has already loaded logging.properties from disk. For the fat JAR there
        // is no such flag, so we load the bundled copy from the classpath instead.
        if (System.getProperty("java.util.logging.config.file") == null) {
            try (InputStream is = Launcher.class.getResourceAsStream("/logging.properties")) {
                if (is != null) LogManager.getLogManager().readConfiguration(is);
            } catch (Exception ignored) {}
        }
        App.main(args);
    }
}
