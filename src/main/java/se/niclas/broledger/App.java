package se.niclas.broledger;

import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.scene.text.Font;
import javafx.stage.DirectoryChooser;
import javafx.stage.Screen;
import javafx.stage.Stage;
import javafx.stage.StageStyle;
import se.niclas.broledger.service.AppConfig;
import se.niclas.broledger.service.DictionaryService;
import se.niclas.broledger.service.ImageMapService;
import se.niclas.broledger.service.StatModifierService;
import se.niclas.broledger.service.WeaponStatsService;

import java.io.File;
import java.net.URL;
import java.util.logging.Logger;

public class App extends Application {

    private static final Logger log = Logger.getLogger(App.class.getName());

    @Override
    public void start(Stage stage) throws Exception {
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

    private static void promptForGameArtIfNeeded(Stage owner, AppConfig config) {
        if (config.hasGameArtDirectory()) return;

        DirectoryChooser chooser = new DirectoryChooser();
        chooser.setTitle("Select game-art directory (cancel to skip)");
        File dir = chooser.showDialog(owner);
        if (dir != null) {
            config.gameArtDirectory = dir.getAbsolutePath();
            config.save();
        }
    }

    public static void main(String[] args) {
        launch(args);
    }
}
