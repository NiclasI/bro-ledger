package se.niclas.broledger.ui;

import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.control.TextField;
import javafx.scene.input.Clipboard;
import javafx.scene.input.ClipboardContent;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import se.niclas.broledger.model.Role;
import se.niclas.broledger.model.RolePack;
import se.niclas.broledger.model.ShareRecord;
import se.niclas.broledger.service.AppConfig;
import se.niclas.broledger.service.RoleShareService;
import se.niclas.broledger.service.RoleShareService.PublishResult;

import java.net.URL;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.ResourceBundle;

public class RoleShareController implements Initializable {

    /** Sanity cap on the user-entered pack title; the server stores contents opaquely. */
    static final int MAX_TITLE_LENGTH = 60;

    @FXML private HBox      titleBar;
    @FXML private TextField packNameField;
    @FXML private VBox      roleCheckContainer;
    @FXML private VBox      recentBox;
    @FXML private ListView<ShareRecord> recentList;
    @FXML private Button    newCodeButton;
    @FXML private TextField codeField;
    @FXML private Label     statusLabel;
    @FXML private Button    shareButton;

    private final Map<CheckBox, Role> checkboxRoles = new LinkedHashMap<>();

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

        titleBar.sceneProperty().addListener((obs, oldScene, newScene) -> {
            if (newScene != null) {
                newScene.addEventFilter(KeyEvent.KEY_PRESSED, e -> {
                    if (e.getCode() == KeyCode.ESCAPE) { close(); e.consume(); }
                });
            }
        });

        recentList.setCellFactory(lv -> new ListCell<>() {
            @Override protected void updateItem(ShareRecord r, boolean empty) {
                super.updateItem(r, empty);
                setText(empty || r == null ? null : recentCellText(r));
            }
        });
        recentList.getSelectionModel().selectedItemProperty()
                .addListener((obs, old, selected) -> onRecentSelectionChanged(selected));
        refreshRecentList();
    }

    /** Populates the checkbox list with the given roles, all checked by default. */
    public void setRoles(List<Role> roles) {
        checkboxRoles.clear();
        roleCheckContainer.getChildren().clear();
        for (Role r : roles) {
            CheckBox cb = new CheckBox(r.name);
            cb.setSelected(true);
            checkboxRoles.put(cb, r);
            roleCheckContainer.getChildren().add(cb);
        }
    }

    @FXML
    private void selectAllRoles() {
        checkboxRoles.keySet().forEach(cb -> cb.setSelected(true));
    }

    @FXML
    private void selectNoneRoles() {
        checkboxRoles.keySet().forEach(cb -> cb.setSelected(false));
    }

    @FXML
    private void share() {
        List<Role> selected = new ArrayList<>();
        for (Map.Entry<CheckBox, Role> e : checkboxRoles.entrySet()) {
            if (e.getKey().isSelected()) selected.add(e.getValue());
        }
        if (selected.isEmpty()) {
            statusLabel.getStyleClass().setAll("dim-label", "warning");
            statusLabel.setText("Select at least one role to share.");
            return;
        }

        String title = normalizedTitle(packNameField.getText());
        // forSharing strips local provenance so previously imported pack codes never leak.
        RolePack pack = RolePack.forSharing(title, selected);

        ShareRecord target = recentList.getSelectionModel().getSelectedItem();
        boolean updateMode = target != null && target.updatable();

        shareButton.setDisable(true);
        statusLabel.getStyleClass().setAll("dim-label");
        statusLabel.setText(updateMode ? "Updating " + target.code + "…" : "Publishing…");

        Task<PublishResult> task = new Task<>() {
            @Override
            protected PublishResult call() throws Exception {
                RoleShareService service = RoleShareService.getInstance();
                if (updateMode) {
                    service.update(target.code, target.ownerToken, pack);
                    return new PublishResult(target.code, target.ownerToken);
                }
                return service.publish(pack);
            }
        };
        task.setOnSucceeded(e -> {
            PublishResult result = task.getValue();
            recordShare(result, title, selected.size());
            codeField.setText(result.code());
            copyToClipboard(result.code());
            statusLabel.setText(updateMode
                    ? "Updated — everyone with this code gets the new version. Code copied."
                    : "Published — code copied to clipboard.");
            shareButton.setDisable(false);
        });
        task.setOnFailed(e -> {
            statusLabel.getStyleClass().setAll("dim-label", "warning");
            statusLabel.setText((updateMode ? "Update failed: " : "Publish failed: ")
                    + failureMessage(task.getException()));
            shareButton.setDisable(false);
        });
        Thread t = new Thread(task, "role-share-publish");
        t.setDaemon(true);
        t.start();
    }

    @FXML
    private void newCode() {
        recentList.getSelectionModel().clearSelection();
    }

    private void onRecentSelectionChanged(ShareRecord selected) {
        if (selected != null && selected.updatable()) {
            shareButton.setText("Update " + selected.code);
            setNewCodeButtonVisible(true);
            packNameField.setText(selected.title != null ? selected.title : "");
            statusLabel.getStyleClass().setAll("dim-label");
            statusLabel.setText("Publishing will overwrite " + selected.code
                    + " for everyone who has the code.");
        } else {
            shareButton.setText("Share");
            setNewCodeButtonVisible(false);
            if (selected != null) {
                statusLabel.getStyleClass().setAll("dim-label");
                statusLabel.setText("This upload can't be updated — Share publishes a new code.");
            }
        }
    }

    private void setNewCodeButtonVisible(boolean visible) {
        newCodeButton.setVisible(visible);
        newCodeButton.setManaged(visible);
    }

    /** Persists the publish/update in the recent-uploads history and refreshes the list. */
    private void recordShare(PublishResult result, String title, int roleCount) {
        AppConfig cfg = AppConfig.getInstance();
        cfg.recordShare(new ShareRecord(
                result.code(), result.ownerToken(), title, Instant.now().toString(), roleCount));
        cfg.save();
        refreshRecentList();
    }

    private void refreshRecentList() {
        recentList.getSelectionModel().clearSelection();
        List<ShareRecord> shares = AppConfig.getInstance().recentShares;
        recentList.getItems().setAll(shares != null ? shares : List.of());
        boolean any = !recentList.getItems().isEmpty();
        recentBox.setVisible(any);
        recentBox.setManaged(any);
    }

    /** Trimmed, length-capped title, or null when blank (pack stays untitled). */
    static String normalizedTitle(String raw) {
        if (raw == null) return null;
        String title = raw.trim();
        if (title.isEmpty()) return null;
        return title.length() > MAX_TITLE_LENGTH ? title.substring(0, MAX_TITLE_LENGTH) : title;
    }

    /** One line in the recent-uploads list: title, code, size, date, updatability. */
    static String recentCellText(ShareRecord r) {
        String title = r.title != null && !r.title.isEmpty() ? r.title : "Untitled";
        String date  = r.uploadedAt != null && r.uploadedAt.length() >= 10
                ? r.uploadedAt.substring(0, 10) : "";
        StringBuilder sb = new StringBuilder(title).append(" — ").append(r.code)
                .append(" · ").append(r.roleCount).append(" role(s)");
        if (!date.isEmpty()) sb.append(" · ").append(date);
        if (!r.updatable()) sb.append(" (not updatable)");
        return sb.toString();
    }

    /** Null-safe status-label text for a failed share task; also used by the import dialog. */
    static String failureMessage(Throwable ex) {
        if (ex == null) return "unknown error";
        return ex.getMessage() != null ? ex.getMessage() : ex.getClass().getSimpleName();
    }

    @FXML
    private void copyCode() {
        String code = codeField.getText();
        if (code != null && !code.isEmpty()) copyToClipboard(code);
    }

    private void copyToClipboard(String text) {
        ClipboardContent content = new ClipboardContent();
        content.putString(text);
        Clipboard.getSystemClipboard().setContent(content);
    }

    @FXML
    private void close() {
        ((Stage) titleBar.getScene().getWindow()).close();
    }
}
