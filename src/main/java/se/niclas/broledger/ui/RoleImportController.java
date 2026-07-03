package se.niclas.broledger.ui;

import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import se.niclas.broledger.model.Role;
import se.niclas.broledger.model.RolePack;
import se.niclas.broledger.service.RoleService;
import se.niclas.broledger.service.RoleShareService;

import java.net.URL;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.ResourceBundle;

public class RoleImportController implements Initializable {

    @FXML private HBox      titleBar;
    @FXML private TextField codeField;
    @FXML private Button    fetchButton;
    @FXML private Label     statusLabel;
    @FXML private Label     packTitleLabel;
    @FXML private VBox      roleCheckContainer;
    @FXML private Button    importButton;

    private final Map<CheckBox, Role> checkboxRoles = new LinkedHashMap<>();
    private RolePack fetchedPack;
    /** The normalized code the current {@link #fetchedPack} was fetched with (provenance). */
    private String fetchedCode;
    private Runnable onImported;

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
    }

    public void setOnImported(Runnable callback) {
        this.onImported = callback;
    }

    @FXML
    private void fetch() {
        String code = RoleShareService.normalizeCode(codeField.getText());
        if (code == null) {
            statusLabel.getStyleClass().setAll("dim-label", "warning");
            statusLabel.setText("Enter a valid share code — 8 letters/digits, e.g. AB3D9F2K.");
            return;
        }

        fetchedPack = null;
        checkboxRoles.clear();
        roleCheckContainer.getChildren().clear();
        showPackTitle(null);
        fetchButton.setDisable(true);
        importButton.setDisable(true);
        statusLabel.getStyleClass().setAll("dim-label");
        statusLabel.setText("Fetching…");

        Task<RolePack> task = new Task<>() {
            @Override
            protected RolePack call() throws Exception {
                return RoleShareService.getInstance().fetch(code);
            }
        };
        task.setOnSucceeded(e -> {
            fetchedPack = task.getValue();
            fetchedCode = code;
            populateRoleList(fetchedPack);
            showPackTitle(packTitleText(fetchedPack, code));
            statusLabel.setText("Found " + fetchedPack.roles.size() + " role(s)"
                    + (fetchedPack.truncated() ? " (oversized pack — extra roles were dropped)" : "") + ".");
            fetchButton.setDisable(false);
            updateImportButtonState();
        });
        task.setOnFailed(e -> {
            statusLabel.getStyleClass().setAll("dim-label", "warning");
            statusLabel.setText("Fetch failed: " + RoleShareController.failureMessage(task.getException()));
            fetchButton.setDisable(false);
        });
        Thread t = new Thread(task, "role-share-fetch");
        t.setDaemon(true);
        t.start();
    }

    private void populateRoleList(RolePack pack) {
        checkboxRoles.clear();
        roleCheckContainer.getChildren().clear();
        for (Role r : pack.roles) {
            boolean updates = RoleService.getInstance().findBySource(fetchedCode, r.id) != null;
            CheckBox cb = new CheckBox(importCheckboxLabel(r.name, updates));
            cb.setSelected(true);
            cb.selectedProperty().addListener((obs, old, sel) -> updateImportButtonState());
            checkboxRoles.put(cb, r);
            roleCheckContainer.getChildren().add(cb);
        }
    }

    /** Checkbox text for one fetched role; flags roles that will update a previous import. */
    static String importCheckboxLabel(String name, boolean updatesExisting) {
        return updatesExisting ? name + "  (updates existing)" : name;
    }

    private void updateImportButtonState() {
        boolean anySelected = checkboxRoles.keySet().stream().anyMatch(CheckBox::isSelected);
        importButton.setDisable(!anySelected);
    }

    /** Shows the fetched pack's title above the role list, or hides the label when null. */
    private void showPackTitle(String text) {
        packTitleLabel.setText(text != null ? text : "");
        packTitleLabel.setVisible(text != null);
        packTitleLabel.setManaged(text != null);
    }

    /** The fetched pack's display title; untitled packs fall back to showing the code. */
    static String packTitleText(RolePack pack, String code) {
        return pack.label != null && !pack.label.isEmpty() ? pack.label : "Untitled pack (" + code + ")";
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
    private void doImport() {
        if (fetchedPack == null) return;
        List<Role> selected = new ArrayList<>();
        for (Map.Entry<CheckBox, Role> e : checkboxRoles.entrySet()) {
            if (e.getKey().isSelected()) selected.add(e.getValue());
        }
        if (selected.isEmpty()) return;

        RoleService.getInstance().importPack(fetchedPack, selected, fetchedCode);
        if (onImported != null) onImported.run();
        close();
    }

    @FXML
    private void close() {
        ((Stage) titleBar.getScene().getWindow()).close();
    }
}
