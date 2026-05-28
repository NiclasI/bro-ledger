package se.niclas.broledger.ui;

import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import se.niclas.broledger.model.Stat;
import se.niclas.broledger.service.AnnotationService.LevelUpEvent;

import java.net.URL;
import java.util.List;
import java.util.ResourceBundle;

public class LevelUpModalController implements Initializable {

    @FXML private HBox titleBar;
    @FXML private VBox eventsContainer;

    private double dragOffsetX, dragOffsetY;

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
    }

    public void setEvents(List<LevelUpEvent> events) {
        eventsContainer.getChildren().clear();
        for (LevelUpEvent ev : events) {
            eventsContainer.getChildren().add(buildCard(ev));
        }
    }

    private VBox buildCard(LevelUpEvent ev) {
        VBox card = new VBox(4);
        card.getStyleClass().add("card-section");

        Label nameLabel = new Label(ev.name());
        nameLabel.getStyleClass().add("section-header");
        card.getChildren().add(nameLabel);

        for (Stat s : Stat.values()) {
            Integer delta = ev.statDeltas().get(s);
            if (delta == null || delta <= 0) continue;
            Label statLine = new Label(s.displayName() + "  +" + delta);
            statLine.getStyleClass().add("stat-label");
            card.getChildren().add(statLine);
        }

        String statusText = ev.adjusted()
                ? "Planned increases adjusted ✓"
                : "No planned increases to adjust";
        Label statusLabel = new Label(statusText);
        statusLabel.getStyleClass().add("stat-label");
        if (ev.adjusted()) statusLabel.setStyle("-fx-text-fill: #78c87a;");
        card.getChildren().add(statusLabel);

        return card;
    }

    @FXML
    private void close() {
        ((Stage) titleBar.getScene().getWindow()).close();
    }
}
