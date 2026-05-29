package se.niclas.broledger.ui;

import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.application.Platform;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;
import javafx.concurrent.Task;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Cursor;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyCodeCombination;
import javafx.scene.input.KeyCombination;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser;
import javafx.stage.Modality;
import javafx.stage.Screen;
import javafx.stage.Stage;
import javafx.stage.WindowEvent;
import javafx.util.Duration;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.fxml.Initializable;

import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.ResourceBundle;
import java.util.logging.Logger;
import se.niclas.broledger.model.Brother;
import se.niclas.broledger.model.DictionaryEntry;
import se.niclas.broledger.parser.SaveParser;
import se.niclas.broledger.service.AnnotationService;
import se.niclas.broledger.service.AnnotationService.LevelUpEvent;
import se.niclas.broledger.service.AppConfig;
import se.niclas.broledger.service.DictionaryService;
import se.niclas.broledger.service.FileWatcherService;
import se.niclas.broledger.tools.BrotherCsvExporter;
import se.niclas.broledger.tools.parser.BrotherSavefileExporter;

public class MainController implements Initializable {

    private static final Logger log = Logger.getLogger(MainController.class.getName());

    @FXML private HBox       titleBar;
    @FXML private HBox       toolbarPane;
    @FXML private Label      statusLabel;
    @FXML private ScrollPane centerPane;

    private double dragOffsetX, dragOffsetY;
    private enum ResizeEdge { NONE, N, S, E, W, NE, NW, SE, SW }
    private ResizeEdge resizeEdge = ResizeEdge.NONE;
    private double resizeStartSX, resizeStartSY, resizeStartW, resizeStartH, resizeStartX, resizeStartY;

    private final UiContext        uiCtx = UiContext.defaults();
    private BrotherCardController cardController;
    private Node                  cardRoot;
    private boolean               showingOverview = false;
    private List<Brother>         brothers = List.of();
    private Brother               selectedBrother;
    private final FileWatcherService watcher = new FileWatcherService();
    private Timeline              reloadCountdown;
    private int                   reloadSecondsElapsed;

    private StringProperty currentFileName;
    private Label          watchStatusLabel;
    private HBox           overviewButtonsBox;

    private record LedDef(String desc, String colorClass, Object value) {}

    @Override
    public void initialize(URL url, ResourceBundle rb) {
        buildToolbar();
        loadCardFxml();

        centerPane.sceneProperty().addListener((obs, oldScene, newScene) -> {
            if (newScene != null) {
                newScene.getAccelerators().put(
                        new KeyCodeCombination(KeyCode.O, KeyCombination.SHORTCUT_DOWN),
                        this::openSave);
                newScene.getAccelerators().put(
                        new KeyCodeCombination(KeyCode.W, KeyCombination.SHORTCUT_DOWN),
                        this::stopWatcher);
                setupWindowControls(newScene);
            }
        });

        String last = AppConfig.getInstance().lastSaveFilePath;
        if (last != null && !last.isBlank()) {
            loadSave(Path.of(last));
        }
    }

    private void buildToolbar() {
        currentFileName = new SimpleStringProperty("No file open");

        watchStatusLabel = new Label("");
        watchStatusLabel.getStyleClass().add("watch-status-label");

        // Left: Open Save button + export buttons + watch status label
        Node openBtn    = fancyActionButton("Open Save", currentFileName, this::openSave);
        Node exportBtn  = fancyActionButton("Export",  null, this::exportSave);
        Node excerptBtn = fancyActionButton("Excerpt", null, this::exportExcerpt);
        HBox left = new HBox(8, openBtn, exportBtn, excerptBtn, watchStatusLabel);
        left.setAlignment(Pos.CENTER_LEFT);

        // Center: Overview toggle buttons (hidden when in brother detail view)
        Node weaponBtn = fancyToggle("Weapon Tier",
                BrotherOverviewPane.weaponTierModeProperty(),
                List.of(
                    new LedDef("Off", "led-red",   BrotherOverviewPane.WeaponTierMode.OFF),
                    new LedDef("On",  "led-green", BrotherOverviewPane.WeaponTierMode.ON)));
        Node armorBtn = fancyToggle("Armor Stats",
                BrotherOverviewPane.armorModeProperty(),
                List.of(
                    new LedDef("Off",        "led-red",    BrotherOverviewPane.ArmorMode.OFF),
                    new LedDef("Durability", "led-yellow", BrotherOverviewPane.ArmorMode.DURABILITY),
                    new LedDef("Fatigue",    "led-yellow", BrotherOverviewPane.ArmorMode.FATIGUE)));
        Node perkBtn = fancyToggle("Perk Sorting",
                BrotherOverviewPane.perkSortModeProperty(),
                List.of(
                    new LedDef("Off",         "led-red",    PerkSortMode.OFF),
                    new LedDef("Commonality", "led-yellow", PerkSortMode.COMMONALITY),
                    new LedDef("Tier",        "led-yellow", PerkSortMode.TIER),
                    new LedDef("Tier + Cmn",  "led-yellow", PerkSortMode.TIER_THEN_COMMON)));
        Node potBtn = fancyToggle("Potential",
                BrotherOverviewPane.potentialModeProperty(),
                List.of(
                    new LedDef("Off",             "led-red",    BrotherOverviewPane.PotentialMode.CURRENT),
                    new LedDef("Current + Pot.",  "led-yellow", BrotherOverviewPane.PotentialMode.BOTH),
                    new LedDef("Pot. Only",       "led-yellow", BrotherOverviewPane.PotentialMode.POTENTIAL),
                    new LedDef("Current + Exp.",  "led-green",  BrotherOverviewPane.PotentialMode.CURRENT_EXPECTED),
                    new LedDef("Exp. Only",       "led-green",  BrotherOverviewPane.PotentialMode.EXPECTED)));
        overviewButtonsBox = new HBox(8, weaponBtn, armorBtn, perkBtn, potBtn);
        overviewButtonsBox.setAlignment(Pos.CENTER);
        overviewButtonsBox.setVisible(false);
        overviewButtonsBox.setManaged(false);

        Runnable saveUiState = () -> {
            AnnotationService.UiState s = new AnnotationService.UiState();
            s.potentialMode  = BrotherOverviewPane.potentialModeProperty().get().name();
            s.armorMode      = BrotherOverviewPane.armorModeProperty().get().name();
            s.weaponTierMode = BrotherOverviewPane.weaponTierModeProperty().get().name();
            s.perkSortMode   = BrotherOverviewPane.perkSortModeProperty().get().name();
            AnnotationService.getInstance().setUiState(s);
        };
        BrotherOverviewPane.potentialModeProperty() .addListener((o, a, b) -> saveUiState.run());
        BrotherOverviewPane.armorModeProperty()     .addListener((o, a, b) -> saveUiState.run());
        BrotherOverviewPane.weaponTierModeProperty().addListener((o, a, b) -> saveUiState.run());
        BrotherOverviewPane.perkSortModeProperty()  .addListener((o, a, b) -> saveUiState.run());

        // Right: App settings buttons
        Node rolesBtn = fancyActionButton("Roles", null, this::openRoleManager);
        Node prefsBtn = fancyActionButton("Preferences", null, this::openPreferences);
        Node resetBtn = fancyActionButton("Reset Layout", null, this::resetLayout);
        HBox right = new HBox(8, rolesBtn, prefsBtn, resetBtn);
        right.setAlignment(Pos.CENTER_RIGHT);

        // Spacers center the overview button group
        Region spacer1 = new Region();
        Region spacer2 = new Region();
        HBox.setHgrow(spacer1, Priority.ALWAYS);
        HBox.setHgrow(spacer2, Priority.ALWAYS);

        toolbarPane.getChildren().addAll(left, spacer1, overviewButtonsBox, spacer2, right);
        toolbarPane.setAlignment(Pos.CENTER_LEFT);
        toolbarPane.setPadding(new Insets(4, 8, 4, 8));
        toolbarPane.setSpacing(0);
    }

    private <E extends Enum<E>> Node fancyToggle(
            String title, SimpleObjectProperty<E> prop, List<LedDef> leds) {
        List<Region> dots = new ArrayList<>();
        HBox ledRow = new HBox(4);
        ledRow.setAlignment(Pos.CENTER);
        for (LedDef def : leds) {
            Region dot = new Region();
            dot.getStyleClass().addAll("btn-led", def.colorClass());
            dots.add(dot);
            ledRow.getChildren().add(dot);
        }

        Label headerLbl = new Label(title);
        headerLbl.getStyleClass().add("btn-header");

        Label descLbl = new Label();
        descLbl.getStyleClass().add("btn-desc");

        VBox box = new VBox(2, ledRow, headerLbl, descLbl);
        box.setAlignment(Pos.CENTER);
        box.getStyleClass().add("fancy-btn");
        box.setCursor(Cursor.HAND);

        Runnable update = () -> {
            E cur = prop.get();
            for (int i = 0; i < leds.size(); i++) {
                boolean active = leds.get(i).value().equals(cur);
                Region dot = dots.get(i);
                dot.getStyleClass().removeAll("led-lit", "led-muted");
                dot.getStyleClass().add(active ? "led-lit" : "led-muted");
                if (active) descLbl.setText(leds.get(i).desc());
            }
        };
        update.run();
        prop.addListener((o, a, b) -> update.run());

        box.setOnMouseClicked(e -> {
            E cur = prop.get();
            for (int i = 0; i < leds.size(); i++) {
                if (leds.get(i).value().equals(cur)) {
                    @SuppressWarnings("unchecked")
                    E next = (E) leds.get((i + 1) % leds.size()).value();
                    prop.set(next);
                    return;
                }
            }
            @SuppressWarnings("unchecked")
            E first = (E) leds.get(0).value();
            prop.set(first);
        });

        return box;
    }

    private Node fancyActionButton(String title, StringProperty desc, Runnable action) {
        Label headerLbl = new Label(title);
        headerLbl.getStyleClass().add("btn-header");

        VBox box = new VBox(2, headerLbl);
        box.setAlignment(Pos.CENTER);
        box.getStyleClass().add("fancy-btn");
        box.setCursor(Cursor.HAND);

        if (desc != null) {
            Label descLbl = new Label();
            descLbl.getStyleClass().add("btn-desc");
            descLbl.textProperty().bind(desc);
            box.getChildren().add(descLbl);
        }

        box.setOnMouseClicked(e -> action.run());
        return box;
    }

    private void loadCardFxml() {
        try {
            URL fxml = getClass().getResource("/se/niclas/broledger/fxml/brother-card.fxml");
            if (fxml == null) return;
            FXMLLoader loader = new FXMLLoader(fxml);
            loader.setControllerFactory(clazz -> {
                if (clazz == BrotherCardController.class) return new BrotherCardController(uiCtx);
                try { return clazz.getDeclaredConstructor().newInstance(); }
                catch (Exception ex) { throw new RuntimeException(ex); }
            });
            cardRoot = loader.load();
            cardController = loader.getController();
            cardController.setOnBack(this::showOverview);
            centerPane.setContent(cardRoot);
        } catch (Exception e) {
            log.warning("Could not load brother-card.fxml: " + e.getMessage());
        }
    }

    private void showOverview() {
        if (brothers.isEmpty()) return;

        Node overview = BrotherOverviewPane.build(brothers, b -> {
            selectedBrother = b;
            showCard();
            if (cardController != null) cardController.populate(b);
        }, this::resortBrotherList, uiCtx);
        centerPane.setContent(overview);
        showingOverview = true;

        if (overviewButtonsBox != null) {
            overviewButtonsBox.setVisible(true);
            overviewButtonsBox.setManaged(true);
        }
    }

    private void showCard() {
        if (cardRoot != null) centerPane.setContent(cardRoot);
        showingOverview = false;

        if (overviewButtonsBox != null) {
            overviewButtonsBox.setVisible(false);
            overviewButtonsBox.setManaged(false);
        }
    }

    @FXML
    public void openSave() {
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Open Battle Brothers Save File");
        chooser.getExtensionFilters().add(
                new FileChooser.ExtensionFilter("Save files (*.sav)", "*.sav"));

        String last = AppConfig.getInstance().lastSaveFilePath;
        if (last != null) {
            File dir = new File(last).getParentFile();
            if (dir != null && dir.isDirectory()) chooser.setInitialDirectory(dir);
        }

        File file = chooser.showOpenDialog(centerPane.getScene().getWindow());
        if (file == null) return;

        AppConfig.getInstance().lastSaveFilePath = file.getAbsolutePath();
        AppConfig.getInstance().save();
        loadSave(file.toPath());
    }

    private void exportSave() {
        String savePath = AppConfig.getInstance().lastSaveFilePath;
        if (savePath == null || savePath.isBlank()) {
            setStatus("No save file loaded — open a save first", true);
            return;
        }
        Path save = Path.of(savePath);
        String base = save.getFileName().toString().replaceAll("\\.[^.]+$", "");
        Path out = save.resolveSibling(base + ".csv");
        try {
            new BrotherCsvExporter(uiCtx.dict(), uiCtx.statModifier(), uiCtx.annotation(), uiCtx.roles())
                    .export(AppConfig.getInstance(), save, out);
            setStatus("Exported to " + out.getFileName(), false);
        } catch (IOException e) {
            setStatus("Export failed: " + e.getMessage(), true);
        }
    }

    private void exportExcerpt() {
        String savePath = AppConfig.getInstance().lastSaveFilePath;
        if (savePath == null || savePath.isBlank()) {
            setStatus("No save file loaded — open a save first", true);
            return;
        }
        Path save = Path.of(savePath);
        String base = save.getFileName().toString().replaceAll("\\.[^.]+$", "");
        Path out = save.resolveSibling(base + ".tsv");
        try {
            new BrotherSavefileExporter(uiCtx.dict()).write(save, out);
            setStatus("Excerpt written to " + out.getFileName(), false);
        } catch (IOException e) {
            setStatus("Excerpt failed: " + e.getMessage(), true);
        }
    }

    private void startReloadCountdown(String note, Stage autoCloseStage) {
        if (reloadCountdown != null) reloadCountdown.stop();
        reloadSecondsElapsed = 0;
        String suffix = note != null ? " • " + note : "";
        watchStatusLabel.setText("Reloaded 0 seconds ago" + suffix);
        reloadCountdown = new Timeline(new KeyFrame(Duration.seconds(1), e -> {
            reloadSecondsElapsed++;
            if (reloadSecondsElapsed < 15) {
                watchStatusLabel.setText("Reloaded " + reloadSecondsElapsed + " seconds ago" + suffix);
            } else {
                watchStatusLabel.setText("Watching");
                reloadCountdown.stop();
                if (autoCloseStage != null && autoCloseStage.isShowing()) autoCloseStage.close();
            }
        }));
        reloadCountdown.setCycleCount(15);
        reloadCountdown.play();
    }

    private void loadSave(Path savePath) {
        if (reloadCountdown != null) { reloadCountdown.stop(); reloadCountdown = null; }
        currentFileName.set(savePath.getFileName().toString());
        watchStatusLabel.setText("");
        statusLabel.setText("");
        statusLabel.getStyleClass().remove("warning");
        brothers = List.of();
        selectedBrother = null;

        AnnotationService.getInstance().loadFor(savePath);
        restoreUiState();

        SaveParser parser = new SaveParser(DictionaryService.getInstance());
        Task<List<Brother>> task = parseTask(parser, savePath);
        task.setOnSucceeded(e -> {
            applyResult(parser, task.getValue());
            showOverview();
            startWatching(savePath);
        });
        task.setOnFailed(e -> showError("Failed to parse save file", task.getException().getMessage()));
        Thread thread = new Thread(task, "save-parser");
        thread.setDaemon(true);
        thread.start();
    }

    private void restoreUiState() {
        AnnotationService.UiState s = AnnotationService.getInstance().getUiState();
        applyEnum(BrotherOverviewPane.potentialModeProperty(),  BrotherOverviewPane.PotentialMode.class,  s.potentialMode);
        applyEnum(BrotherOverviewPane.armorModeProperty(),      BrotherOverviewPane.ArmorMode.class,      s.armorMode);
        applyEnum(BrotherOverviewPane.weaponTierModeProperty(), BrotherOverviewPane.WeaponTierMode.class, s.weaponTierMode);
        applyEnum(BrotherOverviewPane.perkSortModeProperty(),   PerkSortMode.class,                       s.perkSortMode);
    }

    private <E extends Enum<E>> void applyEnum(SimpleObjectProperty<E> prop, Class<E> cls, String name) {
        if (name == null) return;
        try { prop.set(Enum.valueOf(cls, name)); } catch (IllegalArgumentException ignored) {}
    }

    private void startWatching(Path savePath) {
        watchStatusLabel.setText("Watching");
        watcher.watch(savePath, () -> {
            AnnotationService.getInstance().loadFor(savePath);
            loadSaveQuiet(savePath);
        });
    }

    private void loadSaveQuiet(Path savePath) {
        log.fine("loadSaveQuiet triggered");
        Brother selected = selectedBrother;
        SaveParser parser = new SaveParser(DictionaryService.getInstance());
        Task<List<Brother>> task = parseTask(parser, savePath);
        task.setOnSucceeded(e -> {
            List<Brother> oldBrothers = this.brothers;
            List<Brother> newBrothers = task.getValue();
            List<LevelUpEvent> events = AnnotationService.getInstance().reconcileOnReload(oldBrothers, newBrothers);
            applyResult(parser, newBrothers);
            if (selected != null) {
                brothers.stream()
                        .filter(b -> b.fingerprint != null && b.fingerprint.equals(selected.fingerprint))
                        .findFirst()
                        .ifPresent(b -> {
                            selectedBrother = b;
                            if (cardController != null) cardController.populate(b);
                        });
            }
            if (showingOverview) showOverview();
            if (!events.isEmpty()) {
                String lvlMode = AppConfig.getInstance().levelUpModalMode;
                Stage lvlStage = null;
                if (!"OFF".equals(lvlMode)) {
                    lvlStage = openLevelUpModal(events, "AUTO_CLOSE".equals(lvlMode));
                }
                startReloadCountdown(events.size() + " level-up(s) detected", lvlStage);
            } else {
                startReloadCountdown(null, null);
            }
        });
        task.setOnFailed(e ->
            setStatus("Reload failed: " + task.getException().getMessage(), true));
        Thread t = new Thread(task, "save-reload");
        t.setDaemon(true);
        t.start();
    }

    private static Task<List<Brother>> parseTask(SaveParser parser, Path savePath) {
        return new Task<>() {
            @Override
            protected List<Brother> call() throws Exception {
                return parser.parse(savePath).stream().filter(MainController::isValid).toList();
            }
        };
    }

    private void applyResult(SaveParser parser, List<Brother> parsed) {
        brothers = parsed.stream().sorted(BrotherOverviewPane.sortComparator(uiCtx)).toList();
        showWarnings(parser.getWarnings());
        if (cardController != null) cardController.setAllBrothers(brothers);
    }

    static boolean isValid(Brother b) {
        if (b.name == null || b.name.isBlank() || b.name.length() > 60) return false;
        if (b.levelTotal < 1) return false;
        if (b.backgroundHexId == null) return false;
        DictionaryEntry bg = DictionaryService.getInstance().get(b.backgroundHexId);
        return bg != null && "background".equals(bg.type);
    }

    private void setupWindowControls(Scene scene) {
        titleBar.setOnMousePressed(e -> {
            dragOffsetX = e.getSceneX();
            dragOffsetY = e.getSceneY();
        });
        titleBar.setOnMouseDragged(e -> {
            Stage s = (Stage) scene.getWindow();
            if (!s.isMaximized()) {
                s.setX(e.getScreenX() - dragOffsetX);
                s.setY(e.getScreenY() - dragOffsetY);
            }
        });
        titleBar.setOnMouseClicked(e -> {
            if (e.getClickCount() == 2) toggleMaximize();
        });

        final double EDGE = 5;
        scene.addEventFilter(MouseEvent.MOUSE_MOVED, e -> {
            Stage s = (Stage) scene.getWindow();
            if (s == null || s.isMaximized()) { scene.setCursor(Cursor.DEFAULT); return; }
            double x = e.getSceneX(), y = e.getSceneY(), w = scene.getWidth(), h = scene.getHeight();
            boolean n = y < EDGE, south = y > h - EDGE, west = x < EDGE, east = x > w - EDGE;
            if      (n && west)     { resizeEdge = ResizeEdge.NW; scene.setCursor(Cursor.NW_RESIZE); }
            else if (n && east)     { resizeEdge = ResizeEdge.NE; scene.setCursor(Cursor.NE_RESIZE); }
            else if (south && west) { resizeEdge = ResizeEdge.SW; scene.setCursor(Cursor.SW_RESIZE); }
            else if (south && east) { resizeEdge = ResizeEdge.SE; scene.setCursor(Cursor.SE_RESIZE); }
            else if (n)             { resizeEdge = ResizeEdge.N;  scene.setCursor(Cursor.N_RESIZE);  }
            else if (south)         { resizeEdge = ResizeEdge.S;  scene.setCursor(Cursor.S_RESIZE);  }
            else if (west)          { resizeEdge = ResizeEdge.W;  scene.setCursor(Cursor.W_RESIZE);  }
            else if (east)          { resizeEdge = ResizeEdge.E;  scene.setCursor(Cursor.E_RESIZE);  }
            else                    { resizeEdge = ResizeEdge.NONE; scene.setCursor(Cursor.DEFAULT); }
        });
        scene.addEventFilter(MouseEvent.MOUSE_PRESSED, e -> {
            if (resizeEdge == ResizeEdge.NONE) return;
            Stage s = (Stage) scene.getWindow();
            resizeStartSX = e.getScreenX(); resizeStartSY = e.getScreenY();
            resizeStartW  = s.getWidth();   resizeStartH  = s.getHeight();
            resizeStartX  = s.getX();       resizeStartY  = s.getY();
            e.consume();
        });
        scene.addEventFilter(MouseEvent.MOUSE_DRAGGED, e -> {
            if (resizeEdge == ResizeEdge.NONE) return;
            Stage s = (Stage) scene.getWindow();
            if (s.isMaximized()) return;
            double dx = e.getScreenX() - resizeStartSX, dy = e.getScreenY() - resizeStartSY;
            double minW = s.getMinWidth(), minH = s.getMinHeight();
            switch (resizeEdge) {
                case E  -> s.setWidth(Math.max(minW, resizeStartW + dx));
                case S  -> s.setHeight(Math.max(minH, resizeStartH + dy));
                case W  -> { double nw = Math.max(minW, resizeStartW - dx); s.setX(resizeStartX + resizeStartW - nw); s.setWidth(nw); }
                case N  -> { double nh = Math.max(minH, resizeStartH - dy); s.setY(resizeStartY + resizeStartH - nh); s.setHeight(nh); }
                case SE -> { s.setWidth(Math.max(minW, resizeStartW + dx)); s.setHeight(Math.max(minH, resizeStartH + dy)); }
                case SW -> { double nw = Math.max(minW, resizeStartW - dx); s.setX(resizeStartX + resizeStartW - nw); s.setWidth(nw); s.setHeight(Math.max(minH, resizeStartH + dy)); }
                case NE -> { s.setWidth(Math.max(minW, resizeStartW + dx)); double nh = Math.max(minH, resizeStartH - dy); s.setY(resizeStartY + resizeStartH - nh); s.setHeight(nh); }
                case NW -> { double nw = Math.max(minW, resizeStartW - dx); s.setX(resizeStartX + resizeStartW - nw); s.setWidth(nw); double nh = Math.max(minH, resizeStartH - dy); s.setY(resizeStartY + resizeStartH - nh); s.setHeight(nh); }
            }
            e.consume();
        });
        scene.addEventFilter(MouseEvent.MOUSE_RELEASED, e -> {
            if (resizeEdge != ResizeEdge.NONE) { resizeEdge = ResizeEdge.NONE; scene.setCursor(Cursor.DEFAULT); }
        });
    }

    @FXML
    public void minimizeWindow() {
        ((Stage) titleBar.getScene().getWindow()).setIconified(true);
    }

    @FXML
    public void toggleMaximize() {
        Stage s = (Stage) titleBar.getScene().getWindow();
        s.setMaximized(!s.isMaximized());
    }

    @FXML
    public void closeWindow() {
        Stage s = (Stage) titleBar.getScene().getWindow();
        s.fireEvent(new WindowEvent(s, WindowEvent.WINDOW_CLOSE_REQUEST));
    }

    @FXML
    public void resetLayout() {
        AppConfig cfg = AppConfig.getInstance();
        cfg.windowWidth  = 1800;
        cfg.windowHeight = 1320;
        cfg.save();
        Stage stage = (Stage) centerPane.getScene().getWindow();
        stage.setWidth(cfg.windowWidth);
        stage.setHeight(cfg.windowHeight);
        var screens = Screen.getScreensForRectangle(stage.getX(), stage.getY(), 1, 1);
        var screen = screens.isEmpty() ? Screen.getPrimary() : screens.get(0);
        stage.setX(screen.getVisualBounds().getMinX());
        stage.setY(screen.getVisualBounds().getMinY());
        showOverview();
        BrotherOverviewPane.resetColumnWidths();
    }

    @FXML
    public void openRoleManager() {
        try {
            URL fxml = getClass().getResource("/se/niclas/broledger/fxml/role-manager.fxml");
            if (fxml == null) return;
            FXMLLoader loader = new FXMLLoader(fxml);
            Parent root = loader.load();
            RoleManagerController ctrl = loader.getController();
            ctrl.setOnRolesChanged(this::onRolesChanged);

            Stage stage = new Stage();
            stage.initStyle(javafx.stage.StageStyle.UNDECORATED);
            stage.initModality(Modality.APPLICATION_MODAL);
            stage.initOwner(centerPane.getScene().getWindow());
            Scene scene = new Scene(root);
            URL css = getClass().getResource("/se/niclas/broledger/css/keeper.css");
            if (css != null) scene.getStylesheets().add(css.toExternalForm());
            stage.setScene(scene);
            stage.showAndWait();
        } catch (Exception e) {
            log.warning("Could not open role manager: " + e.getMessage());
        }
    }

    private Stage openLevelUpModal(List<LevelUpEvent> events, boolean autoClose) {
        try {
            URL fxml = getClass().getResource("/se/niclas/broledger/fxml/level-up-modal.fxml");
            if (fxml == null) return null;
            FXMLLoader loader = new FXMLLoader(fxml);
            Parent root = loader.load();
            LevelUpModalController ctrl = loader.getController();
            ctrl.setEvents(events);
            javafx.stage.Window owner = centerPane.getScene().getWindow();
            double maxH = owner.getHeight() * 0.9;
            ((Region) root).setMaxHeight(maxH);
            Stage stage = new Stage();
            stage.initStyle(javafx.stage.StageStyle.UNDECORATED);
            stage.initModality(Modality.APPLICATION_MODAL);
            stage.initOwner(owner);
            Scene scene = new Scene(root);
            URL css = getClass().getResource("/se/niclas/broledger/css/keeper.css");
            if (css != null) scene.getStylesheets().add(css.toExternalForm());
            stage.setScene(scene);
            stage.setMaxHeight(maxH);
            if (autoClose) {
                stage.show();
                return stage;
            } else {
                stage.showAndWait();
                return null;
            }
        } catch (Exception e) {
            log.warning("Could not open level-up modal: " + e.getMessage());
            return null;
        }
    }

    public void openPreferences() {
        try {
            URL fxml = getClass().getResource("/se/niclas/broledger/fxml/preferences.fxml");
            if (fxml == null) return;
            FXMLLoader loader = new FXMLLoader(fxml);
            Parent root = loader.load();
            PreferencesController ctrl = loader.getController();
            ctrl.setOnChanged(this::onPreferencesChanged);

            Stage stage = new Stage();
            stage.initStyle(javafx.stage.StageStyle.UNDECORATED);
            stage.initModality(Modality.APPLICATION_MODAL);
            stage.initOwner(centerPane.getScene().getWindow());
            Scene scene = new Scene(root);
            URL css = getClass().getResource("/se/niclas/broledger/css/keeper.css");
            if (css != null) scene.getStylesheets().add(css.toExternalForm());
            stage.setScene(scene);
            stage.showAndWait();
        } catch (Exception e) {
            log.warning("Could not open preferences: " + e.getMessage());
        }
    }

    private void onPreferencesChanged() {
        if (cardController != null && selectedBrother != null) cardController.populate(selectedBrother);
        if (showingOverview && !brothers.isEmpty()) showOverview();
    }

    private void onRolesChanged() {
        if (cardController != null) cardController.refreshRoles();
        if (showingOverview && !brothers.isEmpty()) showOverview();
    }

    private void resortBrotherList() {
        if (brothers.isEmpty()) return;
        brothers = brothers.stream().sorted(BrotherOverviewPane.sortComparator(uiCtx)).toList();
        showOverview();
    }

    private void stopWatcher() {
        watcher.stop();
        if (reloadCountdown != null) { reloadCountdown.stop(); reloadCountdown = null; }
        watchStatusLabel.setText("");
    }

    private void showWarnings(List<String> warnings) {
        if (warnings == null || warnings.isEmpty()) {
            setStatus("", false);
        } else {
            long unique = warnings.stream().distinct().count();
            setStatus(unique + " unknown item type(s) — stats unavailable for those slots", true);
        }
    }

    private void setStatus(String message, boolean warning) {
        statusLabel.setText(message);
        if (warning) {
            if (!statusLabel.getStyleClass().contains("warning"))
                statusLabel.getStyleClass().add("warning");
        } else {
            statusLabel.getStyleClass().remove("warning");
        }
    }

    private void showError(String header, String message) {
        Platform.runLater(() -> {
            Alert alert = new Alert(Alert.AlertType.ERROR);
            alert.setHeaderText(header);
            alert.setContentText(message);
            alert.showAndWait();
        });
    }
}
