package se.niclas.broledger.ui;

import javafx.animation.PauseTransition;
import javafx.beans.value.ChangeListener;
import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressBar;
import javafx.scene.control.TextField;
import javafx.scene.layout.HBox;
import javafx.stage.DirectoryChooser;
import javafx.stage.FileChooser;
import javafx.stage.Stage;
import javafx.util.Duration;
import se.niclas.broledger.service.AppConfig;
import se.niclas.broledger.service.AssetExtractor;

import java.io.File;
import java.net.URL;
import java.nio.file.Path;
import java.util.ResourceBundle;
import java.util.logging.Logger;

public class SetupController implements Initializable {

    private static final Logger log = Logger.getLogger(SetupController.class.getName());

    @FXML private HBox        titleBar;
    @FXML private TextField   datPathField;
    @FXML private TextField   outDirField;
    @FXML private ProgressBar progressBar;
    @FXML private Label       progressLabel;
    @FXML private Button      extractBtn;

    private double dragOffsetX, dragOffsetY;
    private Runnable onConfigured;

    @Override
    public void initialize(URL url, ResourceBundle rb) {
        titleBar.setOnMousePressed(e -> {
            dragOffsetX = e.getSceneX();
            dragOffsetY = e.getSceneY();
        });
        titleBar.setOnMouseDragged(e -> {
            Stage s = (Stage) titleBar.getScene().getWindow();
            s.setX(e.getScreenX() - dragOffsetX);
            s.setY(e.getScreenY() - dragOffsetY);
        });

        outDirField.setText(Path.of(System.getProperty("user.home"), "game-art").toString());

        ChangeListener<String> updateExtractBtn = (obs, old, val) ->
                extractBtn.setDisable(
                        datPathField.getText().isBlank() || outDirField.getText().isBlank());
        datPathField.textProperty().addListener(updateExtractBtn);
        outDirField.textProperty().addListener(updateExtractBtn);
    }

    @FXML
    private void browseDat() {
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Select data_001.dat");
        chooser.getExtensionFilters().add(
                new FileChooser.ExtensionFilter("Game Data (*.dat)", "*.dat"));
        File file = chooser.showOpenDialog(titleBar.getScene().getWindow());
        if (file != null) datPathField.setText(file.getAbsolutePath());
    }

    @FXML
    private void browseOutDir() {
        DirectoryChooser chooser = new DirectoryChooser();
        chooser.setTitle("Select output folder");
        File dir = chooser.showDialog(titleBar.getScene().getWindow());
        if (dir == null) return;

        if (!dir.getName().equals("game-art")) {
            ButtonType useSubfolder = new ButtonType("Create 'game-art' subfolder");
            ButtonType useAsIs      = new ButtonType("Use selected folder");
            Alert alert = new Alert(Alert.AlertType.NONE,
                    "The selected folder is '" + dir.getName() + "'.\n\n" +
                    "Create a 'game-art' subfolder inside it to keep extracted files organized?",
                    useSubfolder, useAsIs);
            alert.setTitle("Confirm output folder");
            alert.setHeaderText(null);
            alert.initOwner(titleBar.getScene().getWindow());
            alert.showAndWait().ifPresent(bt -> {
                if (bt == useSubfolder) {
                    outDirField.setText(dir.toPath().resolve("game-art").toString());
                } else {
                    outDirField.setText(dir.getAbsolutePath());
                }
            });
        } else {
            outDirField.setText(dir.getAbsolutePath());
        }
    }

    @FXML
    private void selectExisting() {
        DirectoryChooser chooser = new DirectoryChooser();
        chooser.setTitle("Select game-art directory");
        File dir = chooser.showDialog(titleBar.getScene().getWindow());
        if (dir != null) {
            AppConfig.getInstance().gameArtDirectory = dir.getAbsolutePath();
            AppConfig.getInstance().save();
            if (onConfigured != null) onConfigured.run();
            close();
        }
    }

    @FXML
    private void startExtract() {
        Path datFile = Path.of(datPathField.getText());
        Path outDir  = Path.of(outDirField.getText());

        progressBar.setVisible(true);
        progressBar.setManaged(true);
        progressLabel.setVisible(true);
        progressLabel.setManaged(true);
        extractBtn.setDisable(true);

        AssetExtractor extractor = new AssetExtractor();

        Task<AssetExtractor.ExtractionResult> task = new Task<>() {
            @Override
            protected AssetExtractor.ExtractionResult call() throws Exception {
                return extractor.extractWithFallback(datFile, outDir, (done, total, file) -> {
                    updateProgress(done, total);
                    updateMessage(file);
                });
            }
        };

        progressBar.progressProperty().bind(task.progressProperty());
        progressLabel.textProperty().bind(task.messageProperty());

        task.setOnSucceeded(e -> {
            progressBar.progressProperty().unbind();
            progressLabel.textProperty().unbind();
            AssetExtractor.ExtractionResult r = task.getValue();
            progressBar.setProgress(1.0);
            progressLabel.setText("Done — extracted %d/%d files (%d fallback)"
                    .formatted(r.extracted(), r.total(), r.fallback()));
            AppConfig.getInstance().gameArtDirectory = outDir.toString();
            AppConfig.getInstance().save();
            if (onConfigured != null) onConfigured.run();
            PauseTransition pause = new PauseTransition(Duration.seconds(2));
            pause.setOnFinished(ev -> close());
            pause.play();
        });

        task.setOnFailed(e -> {
            progressBar.progressProperty().unbind();
            progressLabel.textProperty().unbind();
            progressLabel.setText("Extraction failed: " + task.getException().toString());
            extractBtn.setDisable(false);
            log.warning("Extraction failed: " + task.getException());
            PauseTransition pause = new PauseTransition(Duration.seconds(3));
            pause.setOnFinished(ev -> close());
            pause.play();
        });

        Thread t = new Thread(task, "asset-extractor");
        t.setDaemon(true);
        t.start();
    }

    @FXML
    private void skip() {
        close();
    }

    private void close() {
        ((Stage) titleBar.getScene().getWindow()).close();
    }

    public void setOnConfigured(Runnable callback) {
        this.onConfigured = callback;
    }
}
