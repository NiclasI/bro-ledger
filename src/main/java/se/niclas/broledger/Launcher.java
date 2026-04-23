package se.niclas.broledger;

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
        App.main(args);
    }
}
