package se.niclas.broledger.ui;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.stage.StageStyle;
import javafx.stage.Window;

import java.net.URL;

/**
 * Single source of truth for the app's custom modal chrome (undecorated stage, amber
 * {@code app-frame,modal-frame} border, {@code keeper.css} attached). Every dialog — FXML-loaded
 * or built programmatically — should open through this class so new dialogs are themed correctly
 * by default instead of having to remember the right style classes each time.
 */
public final class Modals {

    private static final String CSS_PATH = "/se/niclas/broledger/css/keeper.css";

    private Modals() {}

    /**
     * Wraps {@code root} in an undecorated, application-modal {@link Stage} with {@code keeper.css}
     * attached, ensuring the root carries {@code app-frame} and {@code modal-frame} (added if
     * missing) so it always gets the signature amber border + glow. Returned unshown so callers
     * can adjust it first (e.g. set a max height) before calling {@code show()}/{@code showAndWait()}.
     */
    public static Stage buildModal(Parent root, Window owner) {
        if (!root.getStyleClass().contains("app-frame"))   root.getStyleClass().add("app-frame");
        if (!root.getStyleClass().contains("modal-frame")) root.getStyleClass().add("modal-frame");

        Stage stage = new Stage();
        stage.initStyle(StageStyle.UNDECORATED);
        stage.initModality(Modality.APPLICATION_MODAL);
        if (owner != null) stage.initOwner(owner);

        Scene scene = new Scene(root);
        URL css = Modals.class.getResource(CSS_PATH);
        if (css != null) scene.getStylesheets().add(css.toExternalForm());
        stage.setScene(scene);
        return stage;
    }

    /** Builds and shows the modal, blocking until it is closed. */
    public static void showModal(Parent root, Window owner) {
        buildModal(root, owner).showAndWait();
    }

    /**
     * Themed replacement for {@code new Alert(Alert.AlertType.CONFIRMATION)}. Shows a modal with
     * the app's title bar (✕ closes with a "cancelled" result) and a right-aligned "Cancel" /
     * {@code okText} button pair. Returns {@code true} only if {@code okText} was clicked.
     */
    public static boolean confirm(Window owner, String title, String header, String body, String okText) {
        return choice(owner, title, header, body, "Cancel", okText) == 1;
    }

    /** Themed replacement for {@code new Alert(Alert.AlertType.ERROR)}: header/body + a single Close button. */
    public static void error(Window owner, String title, String header, String body) {
        choice(owner, title, header, body, "Close");
    }

    /**
     * Builds a themed dialog with an arbitrary set of buttons (left-to-right) and returns the
     * index of the one clicked, or {@code -1} if dismissed via the title-bar ✕. General-purpose
     * building block behind {@link #confirm} and {@link #error} — reuse it directly for dialogs
     * that need more than a simple OK/Cancel (e.g. a 3-way choice).
     */
    public static int choice(Window owner, String title, String header, String body, String... buttonLabels) {
        int[] result = {-1};
        Stage[] stageRef = new Stage[1];

        HBox titleBar = new HBox();
        titleBar.getStyleClass().add("title-bar");
        titleBar.setAlignment(Pos.CENTER_LEFT);
        Label titleLabel = new Label(title);
        titleLabel.getStyleClass().add("title-bar-label");
        Region titleSpacer = new Region();
        HBox.setHgrow(titleSpacer, Priority.ALWAYS);
        Button closeBtn = new Button("✕");
        closeBtn.getStyleClass().addAll("window-btn", "window-btn-close");
        closeBtn.setFocusTraversable(false);
        closeBtn.setOnAction(e -> { result[0] = -1; stageRef[0].close(); });
        titleBar.getChildren().addAll(titleLabel, titleSpacer, closeBtn);

        VBox content = new VBox(10);
        content.setPadding(new Insets(16, 20, 16, 20));
        if (header != null && !header.isBlank()) {
            Label headerLabel = new Label(header);
            headerLabel.setWrapText(true);
            headerLabel.getStyleClass().add("section-header");
            content.getChildren().add(headerLabel);
        }
        if (body != null && !body.isBlank()) {
            Label bodyLabel = new Label(body);
            bodyLabel.setWrapText(true);
            bodyLabel.getStyleClass().add("dim-label");
            content.getChildren().add(bodyLabel);
        }

        HBox buttonRow = new HBox(8);
        buttonRow.setAlignment(Pos.CENTER_RIGHT);
        for (int i = 0; i < buttonLabels.length; i++) {
            int idx = i;
            Button btn = new Button(buttonLabels[i]);
            btn.getStyleClass().add("fancy-btn");
            btn.setOnAction(e -> { result[0] = idx; stageRef[0].close(); });
            buttonRow.getChildren().add(btn);
        }
        content.getChildren().add(buttonRow);

        VBox root = new VBox(titleBar, content);
        root.setMinWidth(380);

        Stage stage = buildModal(root, owner);
        stageRef[0] = stage;
        stage.showAndWait();
        return result[0];
    }
}
