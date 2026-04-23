package se.niclas.broledger.ui;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import se.niclas.broledger.model.Brother;
import se.niclas.broledger.service.AppConfig;
import se.niclas.broledger.service.ImageCache;
import se.niclas.broledger.service.ImageMapService;

import java.nio.file.Path;

public class BrotherListCell extends ListCell<Brother> {

    private final HBox   root    = new HBox(8);
    private final ImageView portrait = new ImageView();
    private final Label  nameLabel  = new Label();
    private final Label  titleLabel = new Label();

    public BrotherListCell() {
        portrait.setFitWidth(48);
        portrait.setFitHeight(48);
        portrait.setPreserveRatio(true);

        nameLabel.getStyleClass().add("cell-name");
        titleLabel.getStyleClass().add("cell-title");

        VBox text = new VBox(2, nameLabel, titleLabel);
        text.setAlignment(Pos.CENTER_LEFT);

        root.setAlignment(Pos.CENTER_LEFT);
        root.setPadding(new Insets(4));
        root.getChildren().addAll(portrait, text);
    }

    @Override
    protected void updateItem(Brother brother, boolean empty) {
        super.updateItem(brother, empty);
        if (empty || brother == null) {
            setGraphic(null);
            return;
        }

        nameLabel.setText(brother.name);
        titleLabel.setText(brother.title != null ? brother.title : "");
        portrait.setImage(resolvePortrait(brother));
        setGraphic(root);
    }

    private Image resolvePortrait(Brother brother) {
        String relPath = ImageMapService.getInstance().resolveHex(brother.backgroundHexId);
        Path gameArtRoot = AppConfig.getInstance().gameArtRoot();
        if (relPath == null || gameArtRoot == null) {
            return ImageCache.getInstance().getPlaceholder();
        }
        return ImageCache.getInstance().get(gameArtRoot, relPath);
    }
}
