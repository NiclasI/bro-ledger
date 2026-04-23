package se.niclas.broledger.ui;

import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.RadioButton;
import javafx.scene.control.ToggleGroup;
import javafx.scene.layout.HBox;
import javafx.stage.Stage;
import se.niclas.broledger.service.AppConfig;
import se.niclas.broledger.service.ExpectedStatsCalculator;

import java.net.URL;
import java.util.ResourceBundle;

public class PreferencesController implements Initializable {

    @FXML private HBox        titleBar;
    @FXML private RadioButton naiveRadio;
    @FXML private RadioButton greedyRadio;
    @FXML private ToggleGroup modeGroup;

    private double dragOffsetX, dragOffsetY;
    private Runnable onChanged;

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

        // Initialize from AppConfig
        ExpectedStatsCalculator.Mode mode =
                ExpectedStatsCalculator.Mode.parse(AppConfig.getInstance().expectedStatsMode);
        if (mode == ExpectedStatsCalculator.Mode.GREEDY) {
            greedyRadio.setSelected(true);
        } else {
            naiveRadio.setSelected(true);
        }

        // Save on change
        modeGroup.selectedToggleProperty().addListener((obs, old, selected) -> {
            if (selected == greedyRadio) {
                AppConfig.getInstance().expectedStatsMode = "GREEDY";
            } else {
                AppConfig.getInstance().expectedStatsMode = "NAIVE";
            }
            AppConfig.getInstance().save();
            if (onChanged != null) onChanged.run();
        });
    }

    public void setOnChanged(Runnable callback) {
        this.onChanged = callback;
    }

    @FXML
    private void close() {
        ((Stage) naiveRadio.getScene().getWindow()).close();
    }
}
