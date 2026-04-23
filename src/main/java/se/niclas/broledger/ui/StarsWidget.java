package se.niclas.broledger.ui;

import javafx.geometry.Pos;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;

/** Displays 0–3 filled stars for a single stat's talent rating. */
public class StarsWidget extends HBox {

    public StarsWidget(int stars) {
        setAlignment(Pos.CENTER_LEFT);
        setSpacing(1);
        for (int i = 0; i < 3; i++) {
            Label l = new Label(i < stars ? "★" : "☆");
            l.getStyleClass().add(i < stars ? "star-filled" : "star-empty");
            getChildren().add(l);
        }
    }
}
