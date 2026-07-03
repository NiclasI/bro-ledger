package se.niclas.broledger.ui;

import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.*;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import se.niclas.broledger.model.Role;
import se.niclas.broledger.model.Stat;
import se.niclas.broledger.service.AppConfig;
import se.niclas.broledger.service.RoleService;

import java.net.URL;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.ResourceBundle;

public class RoleManagerController implements Initializable {

    @FXML private HBox            titleBar;
    @FXML private ListView<Role>  roleList;
    @FXML private TextField       nameField;
    @FXML private RadioButton     frontlineRadio;
    @FXML private RadioButton     backlineRadio;
    @FXML private ToggleGroup     positionGroup;
    @FXML private GridPane        statEditorGrid;
    @FXML private VBox            perkPlanContainer;
    @FXML private Label           perkPlanBadgeLabel;

    private final TextField[]   targetFields = new TextField[8];
    private final ToggleGroup[] prioGroups   = new ToggleGroup[8];

    private PerkPlanPane perkPlanPane;

    private double dragOffsetX, dragOffsetY;
    private Runnable onRolesChanged;
    private UiContext uiContext;

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

        buildStatEditorGrid();

        roleList.setCellFactory(lv -> new ListCell<>() {
            @Override protected void updateItem(Role r, boolean empty) {
                super.updateItem(r, empty);
                setText(empty || r == null ? null : r.name);
            }
        });

        roleList.getSelectionModel().selectedItemProperty()
                .addListener((obs, old, selected) -> populateForm(selected));

        refreshList(null);
        setFormDisabled(true);

        // Build the perk plan pane with default singletons; MainController may call
        // setUiContext() afterwards to supply the same context — that is a no-op.
        setUiContext(UiContext.defaults());
    }

    public void setOnRolesChanged(Runnable callback) {
        this.onRolesChanged = callback;
    }

    public void setUiContext(UiContext ctx) {
        this.uiContext = ctx;
        if (perkPlanPane != null) return; // pane already built; singletons are the same
        perkPlanPane = new PerkPlanPane(ctx);
        perkPlanPane.setOnChange(newStatus -> {
            Role selected = roleList.getSelectionModel().getSelectedItem();
            if (selected == null) return;
            selected.perkPlanTemplate = newStatus.isEmpty() ? null : new LinkedHashMap<>(newStatus);
            RoleService.getInstance().update(selected);
            updatePerkBadge(selected.perkPlanTemplate);
            notifyChanged();
        });
        perkPlanContainer.getChildren().add(perkPlanPane);
    }

    // ---- FXML actions ------------------------------------------------------

    @FXML
    private void addRole() {
        Role r = RoleService.getInstance().add("New Role", true);
        refreshList(r);
        notifyChanged();
    }

    @FXML
    private void deleteRole() {
        Role selected = roleList.getSelectionModel().getSelectedItem();
        if (selected == null) return;
        RoleService.getInstance().delete(selected.id);
        refreshList(null);
        setFormDisabled(true);
        notifyChanged();
    }

    @FXML
    private void moveRoleUp() {
        Role selected = roleList.getSelectionModel().getSelectedItem();
        if (selected == null) return;
        int idx = roleList.getItems().indexOf(selected);
        if (idx <= 0) return;
        RoleService.getInstance().move(selected.id, idx - 1);
        refreshList(selected);
        notifyChanged();
    }

    @FXML
    private void moveRoleDown() {
        Role selected = roleList.getSelectionModel().getSelectedItem();
        if (selected == null) return;
        int idx = roleList.getItems().indexOf(selected);
        if (idx < 0 || idx >= roleList.getItems().size() - 1) return;
        RoleService.getInstance().move(selected.id, idx + 1);
        refreshList(selected);
        notifyChanged();
    }

    @FXML
    private void saveRole() {
        Role selected = roleList.getSelectionModel().getSelectedItem();
        if (selected == null) return;

        String name = nameField.getText().trim();
        if (name.isEmpty()) {
            nameField.requestFocus();
            return;
        }

        selected.name     = name;
        selected.frontline = frontlineRadio.isSelected();
        for (int i = 0; i < 8; i++) {
            String t = targetFields[i].getText().trim();
            selected.targetStats[i] = t.isEmpty() ? 0 : parseIntSafe(t, 0);
            Toggle sel = prioGroups[i].getSelectedToggle();
            selected.priority[i] = sel != null ? (int) sel.getUserData() : 3;
        }

        RoleService.getInstance().update(selected);
        refreshList(selected);
        notifyChanged();
    }

    @FXML
    private void close() {
        ((Stage) nameField.getScene().getWindow()).close();
    }

    // ---- helpers -----------------------------------------------------------

    private void buildStatEditorGrid() {
        // Column headers
        Label hStat   = new Label("Stat");     hStat.getStyleClass().add("field-label");
        Label hTarget = new Label("Target");   hTarget.getStyleClass().add("field-label");
        Label hPrio   = new Label("Priority"); hPrio.getStyleClass().add("field-label");
        GridPane.setRowIndex(hStat,   0); GridPane.setColumnIndex(hStat,   0);
        GridPane.setRowIndex(hTarget, 0); GridPane.setColumnIndex(hTarget, 1);
        GridPane.setRowIndex(hPrio,   0); GridPane.setColumnIndex(hPrio,   2);
        statEditorGrid.getChildren().addAll(hStat, hTarget, hPrio);

        // targetFields and prioGroups are indexed by stat ordinal so save/load stays
        // canonical regardless of display order. Visual order comes from orderedStats().
        Stat[] ordered = AppConfig.getInstance().orderedStats();
        for (int visualRow = 0; visualRow < ordered.length; visualRow++) {
            Stat s   = ordered[visualRow];
            int  idx = s.ordinal();
            int  row = visualRow + 1;

            Label name = new Label(s.displayName());
            name.getStyleClass().add("stat-label");
            name.setMinWidth(100);

            TextField tf = new TextField();
            tf.getStyleClass().add("target-field");
            tf.setPrefWidth(60);
            tf.setPromptText("—");
            targetFields[idx] = tf;

            ToggleGroup tg = new ToggleGroup();
            prioGroups[idx] = tg;
            HBox prioBox = new HBox(2);
            for (int p = 1; p <= 3; p++) {
                ToggleButton tb = new ToggleButton("P" + p);
                tb.setToggleGroup(tg);
                tb.setUserData(p);
                tb.getStyleClass().add("prio-toggle");
                tb.setPrefWidth(30);
                prioBox.getChildren().add(tb);
            }
            tg.selectToggle((ToggleButton) prioBox.getChildren().get(2)); // default P3

            GridPane.setRowIndex(name,    row); GridPane.setColumnIndex(name,    0);
            GridPane.setRowIndex(tf,      row); GridPane.setColumnIndex(tf,      1);
            GridPane.setRowIndex(prioBox, row); GridPane.setColumnIndex(prioBox, 2);
            statEditorGrid.getChildren().addAll(name, tf, prioBox);
        }
    }

    private void populateForm(Role r) {
        if (r == null) { setFormDisabled(true); return; }
        setFormDisabled(false);

        nameField.setText(r.name);
        if (r.frontline) frontlineRadio.setSelected(true);
        else             backlineRadio.setSelected(true);

        for (int i = 0; i < 8; i++) {
            int t = r.targetStats != null && r.targetStats.length > i ? r.targetStats[i] : 0;
            targetFields[i].setText(t > 0 ? String.valueOf(t) : "");
            int p = r.priority != null && r.priority.length > i ? r.priority[i] : 3;
            final int pFinal = p;
            prioGroups[i].getToggles().stream()
                    .filter(toggle -> Objects.equals(toggle.getUserData(), pFinal))
                    .findFirst()
                    .ifPresent(prioGroups[i]::selectToggle);
        }

        if (perkPlanPane != null) {
            perkPlanPane.setStatus(r.perkPlanTemplate);
        }
        updatePerkBadge(r.perkPlanTemplate);
    }

    private void updatePerkBadge(Map<String, String> planStatus) {
        long planned  = planStatus == null ? 0 : planStatus.values().stream().filter("PLANNED"::equals).count();
        long optional = planStatus == null ? 0 : planStatus.values().stream().filter("OPTIONAL"::equals).count();
        if (planned == 0 && optional == 0) {
            perkPlanBadgeLabel.setVisible(false);
            perkPlanBadgeLabel.setManaged(false);
        } else {
            perkPlanBadgeLabel.setText("P: " + planned + "  ·  O: " + optional);
            perkPlanBadgeLabel.setManaged(true);
            perkPlanBadgeLabel.setVisible(true);
        }
    }

    private void setFormDisabled(boolean disabled) {
        nameField.setDisable(disabled);
        frontlineRadio.setDisable(disabled);
        backlineRadio.setDisable(disabled);
        for (int i = 0; i < 8; i++) {
            targetFields[i].setDisable(disabled);
            prioGroups[i].getToggles().forEach(t -> ((ToggleButton) t).setDisable(disabled));
        }
        if (perkPlanPane != null) perkPlanPane.setDisable(disabled);
        if (disabled) {
            nameField.clear();
            frontlineRadio.setSelected(true);
            for (int i = 0; i < 8; i++) {
                targetFields[i].clear();
                prioGroups[i].selectToggle(prioGroups[i].getToggles().get(2)); // P3
            }
            if (perkPlanPane != null) perkPlanPane.setStatus(null);
            updatePerkBadge(null);
        }
    }

    private void refreshList(Role toSelect) {
        roleList.getItems().setAll(RoleService.getInstance().getAll());
        if (toSelect != null) {
            for (Role r : roleList.getItems()) {
                if (r.id.equals(toSelect.id)) {
                    roleList.getSelectionModel().select(r);
                    break;
                }
            }
        }
    }

    private void notifyChanged() {
        if (onRolesChanged != null) onRolesChanged.run();
    }

    private static int parseIntSafe(String s, int fallback) {
        try { return Integer.parseInt(s); } catch (NumberFormatException e) { return fallback; }
    }
}
