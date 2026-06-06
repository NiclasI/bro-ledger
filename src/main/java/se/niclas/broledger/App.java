package se.niclas.broledger;

import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.image.Image;
import javafx.scene.text.Font;
import javafx.stage.DirectoryChooser;
import javafx.stage.Modality;
import javafx.stage.Screen;
import javafx.stage.Stage;
import javafx.stage.StageStyle;
import se.niclas.broledger.service.AppConfig;
import se.niclas.broledger.service.AssetExtractor;
import se.niclas.broledger.ui.SetupController;
import se.niclas.broledger.service.DictionaryService;
import se.niclas.broledger.service.ImageMapService;
import se.niclas.broledger.service.StatModifierService;
import se.niclas.broledger.service.WeaponStatsService;

import java.io.File;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.logging.FileHandler;
import java.util.logging.Level;
import java.util.logging.LogManager;
import java.util.logging.Logger;
import java.util.logging.SimpleFormatter;

public class App extends Application {

    private static final Logger log = Logger.getLogger(App.class.getName());

    @Override
    public void start(Stage stage) throws Exception {
        setupFileLogging();
        AppConfig config = AppConfig.getInstance();
        config.load();

        Font.loadFont(App.class.getResourceAsStream("/se/niclas/broledger/assets/Cinzel-Regular.otf"), 0);
        Font.loadFont(App.class.getResourceAsStream("/se/niclas/broledger/assets/Cinzel-Bold.otf"), 0);

        DictionaryService.getInstance().loadFromClasspath();
        ImageMapService.getInstance().loadFromClasspath();
        StatModifierService.getInstance().loadFromClasspath();
        WeaponStatsService.getInstance().loadFromClasspath();

        stage.initStyle(StageStyle.UNDECORATED);
        promptForGameArtIfNeeded(stage, config);

        FXMLLoader loader = new FXMLLoader(
                App.class.getResource("/se/niclas/broledger/fxml/main.fxml"));
        Scene scene = new Scene(loader.load(), config.windowWidth, config.windowHeight);

        URL cssUrl = App.class.getResource("/se/niclas/broledger/css/keeper.css");
        if (cssUrl != null) {
            scene.getStylesheets().add(cssUrl.toExternalForm());
        }

        Image icon = new Image(App.class.getResourceAsStream("/se/niclas/broledger/assets/BroLedger.png"));
        stage.getIcons().add(icon);
        stage.setTitle("Bro Ledger");
        stage.setMinWidth(1100);
        stage.setMinHeight(900);
        stage.setScene(scene);

        stage.widthProperty().addListener((o, a, b) ->
                log.fine("Window size: %.0f x %.0f".formatted(stage.getWidth(), stage.getHeight())));
        stage.heightProperty().addListener((o, a, b) ->
                log.fine("Window size: %.0f x %.0f".formatted(stage.getWidth(), stage.getHeight())));

        if (config.windowX != null && config.windowY != null) {
            boolean visible = !Screen.getScreensForRectangle(
                    config.windowX, config.windowY, config.windowWidth, config.windowHeight).isEmpty();
            if (visible) {
                stage.setX(config.windowX);
                stage.setY(config.windowY);
            }
        }

        stage.setOnCloseRequest(e -> {
            config.windowWidth  = (int) stage.getWidth();
            config.windowHeight = (int) stage.getHeight();
            config.windowX      = (int) stage.getX();
            config.windowY      = (int) stage.getY();
            config.save();
        });

        stage.show();
    }

    /**
     * Called by JavaFX on every exit path (including force-quit and implicit-exit
     * when the last window closes without a WINDOW_CLOSE_REQUEST event).
     * Re-saves config so window geometry is never lost even when the close handler
     * is bypassed.
     */
    @Override
    public void stop() {
        AppConfig.getInstance().save();
    }

    private static void promptForGameArtIfNeeded(Stage owner, AppConfig config) {
        boolean noGameArt = !config.hasGameArtDirectory();
        boolean outdated  = !noGameArt && AssetExtractor.isGameArtOutdated(config.gameArtRoot());
        if (!noGameArt && !outdated) return;

        try {
            URL fxml = App.class.getResource("/se/niclas/broledger/fxml/setup.fxml");
            if (fxml == null) {
                log.warning("setup.fxml not found — falling back to DirectoryChooser");
                DirectoryChooser chooser = new DirectoryChooser();
                chooser.setTitle("Select game-art directory (cancel to skip)");
                File dir = chooser.showDialog(owner);
                if (dir != null) { config.gameArtDirectory = dir.getAbsolutePath(); config.save(); }
                return;
            }

            FXMLLoader loader = new FXMLLoader(fxml);
            Parent root = loader.load();
            SetupController ctrl = loader.getController();
            ctrl.setOutdated(outdated);

            Stage dialog = new Stage();
            dialog.initStyle(StageStyle.UNDECORATED);
            dialog.initModality(Modality.APPLICATION_MODAL);
            dialog.initOwner(owner);

            Scene scene = new Scene(root);
            URL css = App.class.getResource("/se/niclas/broledger/css/keeper.css");
            if (css != null) scene.getStylesheets().add(css.toExternalForm());
            dialog.setScene(scene);
            dialog.showAndWait();

        } catch (Exception e) {
            log.warning("Could not open setup dialog: " + e.getMessage());
        }
    }

    public static void main(String[] args) {
        launch(args);
    }

    private static void setupFileLogging() {
        if (!"true".equalsIgnoreCase(LogManager.getLogManager().getProperty("log.file.enabled"))) return;
        try {
            Path logDir = Path.of(System.getProperty("user.home"), ".bro-ledger", "logs");
            Files.createDirectories(logDir);
            String ts = DateTimeFormatter.ofPattern("yyyyMMddHHmmss").format(LocalDateTime.now());
            Path logFile = logDir.resolve("bro-ledger-" + ts + ".log");
            FileHandler fh = new FileHandler(logFile.toString());
            fh.setLevel(Level.ALL);
            fh.setFormatter(new SimpleFormatter());
            Logger.getLogger("se.niclas.broledger").addHandler(fh);
            log.info("Logging to file: " + logFile);
        } catch (Exception e) {
            log.warning("Could not set up file logging: " + e.getMessage());
        }
    }
}
