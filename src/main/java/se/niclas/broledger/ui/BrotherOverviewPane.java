package se.niclas.broledger.ui;

import javafx.application.Platform;
import javafx.beans.property.SimpleObjectProperty;
import javafx.collections.ListChangeListener;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.input.ClipboardContent;
import javafx.scene.input.Dragboard;
import javafx.scene.input.TransferMode;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.util.StringConverter;
import se.niclas.broledger.model.Brother;
import se.niclas.broledger.model.DictionaryEntry;
import se.niclas.broledger.model.InventorySlot;
import se.niclas.broledger.model.Role;
import se.niclas.broledger.model.Stat;
import se.niclas.broledger.model.TraitEntry;
import se.niclas.broledger.service.AppConfig;
import se.niclas.broledger.service.ExpectedStatsCalculator;
import se.niclas.broledger.service.StatModifierService;
import se.niclas.broledger.service.StatPotentialCalculator;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashSet;
import java.util.logging.Logger;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Builds a {@link TableView} roster overview.
 *
 * Using TableView means header labels and data cells share the same column
 * objects — alignment is guaranteed by the framework rather than by manually
 * matching pixel widths between two separate HBox rows.
 */
public class BrotherOverviewPane {

    private static final Logger log = Logger.getLogger(BrotherOverviewPane.class.getName());
    private static TableView<Brother> activeTable;

    enum PotentialMode { CURRENT, BOTH, POTENTIAL, CURRENT_EXPECTED, EXPECTED }
    private static final SimpleObjectProperty<PotentialMode> potentialMode =
            new SimpleObjectProperty<>(PotentialMode.CURRENT);
    enum ArmorMode { OFF, DURABILITY, FATIGUE }
    private static final SimpleObjectProperty<ArmorMode> armorMode =
            new SimpleObjectProperty<>(ArmorMode.OFF);
    enum WeaponTierMode { OFF, ON }
    private static final SimpleObjectProperty<WeaponTierMode> weaponTierMode =
            new SimpleObjectProperty<>(WeaponTierMode.OFF);
    static final SimpleObjectProperty<PerkSortMode> perkSortMode =
            new SimpleObjectProperty<>(PerkSortMode.OFF);

    static {
        perkSortMode.addListener((obs, old, nv) -> { if (activeTable != null) activeTable.refresh(); });
    }

    static SimpleObjectProperty<PotentialMode>  potentialModeProperty()  { return potentialMode; }
    static SimpleObjectProperty<ArmorMode>      armorModeProperty()      { return armorMode; }
    static SimpleObjectProperty<WeaponTierMode> weaponTierModeProperty() { return weaponTierMode; }
    static SimpleObjectProperty<PerkSortMode>   perkSortModeProperty()   { return perkSortMode; }

    private static final Stat[] STATS = Stat.values();

    // Equipment columns: armor, helmet, weapon, shield, trinket, quiver, 4×pouch
    private static final int[]    EQUIP_SLOT_IDX  = { 2, 3, 0, 1, 4, 5 };
    private static final String[] EQUIP_SLOT_NAME = { "body", "helmet", "weapon", "shield", "trinket", "quiver" };
    private static final String[] EQUIP_HDR       = { "Arm", "Hlm", "Wpn", "Shd", "Trk", "Quv", "P1", "P2", "P3", "P4" };

    // Column widths (only applied once, to the TableColumn — header and cells share them)
    private static final double W_PORTRAIT = 59;
    private static final double W_NAME     = 165;
    private static final double W_ROLE     = 140;
    private static final double W_LEVEL    = 39;
    private static final double W_STAT     = 48;
    private static final double W_EQUIP    = 42;
    private static final double W_PERK     = 31;
    private static final double W_TRAIT    = 31;

    private final UiContext ctx;
    private Map<String, Long> perkCounts = Map.of();
    private BrotherOverviewPane(UiContext ctx) { this.ctx = ctx; }

    /** Comparator used by both the overview and the left-rail ListView. */
    public static Comparator<Brother> sortComparator(UiContext ctx) {
        return Comparator
                .comparingInt((Brother b) -> frontlineKey(b, ctx))
                .thenComparingInt(b -> {
                    Integer i = (b.fingerprint == null) ? null
                            : ctx.annotation().get(b.fingerprint).sortIndex;
                    return i != null ? i : Integer.MAX_VALUE;
                });
    }

    /**
     * @param onOrderChanged called after a drag-n-drop reorder so the left rail can re-sort;
     *                       may be null if no external sync is needed.
     */
    public static Node build(List<Brother> brothers, Consumer<Brother> onRowClick,
                             Runnable onOrderChanged, UiContext ctx) {
        return new BrotherOverviewPane(ctx).doBuild(brothers, onRowClick, onOrderChanged);
    }

    private Node doBuild(List<Brother> brothers, Consumer<Brother> onRowClick,
                         Runnable onOrderChanged) {
        perkCounts = brothers.stream()
                .flatMap(b -> b.perkIds.stream())
                .collect(Collectors.groupingBy(String::toUpperCase, Collectors.counting()));
        List<Brother> sorted = brothers.stream().sorted(sortComparator(ctx)).toList();

        TableView<Brother> table = new TableView<>(FXCollections.observableArrayList(sorted));
        table.getStyleClass().add("overview-table");
        table.setFixedCellSize(56);

        // Flex-last-column: all fixed columns stay at their specified width;
        // the perks column absorbs any remaining viewport space.
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);

        table.setRowFactory(tv -> {
            TableRow<Brother> row = new TableRow<>() {
                @Override protected void updateItem(Brother b, boolean empty) {
                    super.updateItem(b, empty);
                    getStyleClass().remove("frontline-last");
                    if (empty || b == null) return;
                    int idx = getIndex();
                    var items = getTableView().getItems();
                    if (idx + 1 < items.size()) {
                        Brother next = items.get(idx + 1);
                        if (frontlineKey(b, ctx) != frontlineKey(next, ctx))
                            getStyleClass().add("frontline-last");
                    }
                }
            };

            row.setOnMouseClicked(e -> {
                if (!row.isEmpty()) onRowClick.accept(row.getItem());
            });

            row.setOnDragDetected(e -> {
                if (row.isEmpty() || row.getItem() == null
                        || row.getItem().fingerprint == null) return;
                Dragboard db = row.startDragAndDrop(TransferMode.MOVE);
                ClipboardContent cc = new ClipboardContent();
                cc.putString(row.getItem().fingerprint);
                db.setContent(cc);
                e.consume();
            });

            row.setOnDragOver(e -> {
                if (!e.getDragboard().hasString() || row.isEmpty() || row.getItem() == null) return;
                String sourceFp = e.getDragboard().getString();
                if (sourceFp.equals(row.getItem().fingerprint)) return;
                Brother source = table.getItems().stream()
                        .filter(b -> sourceFp.equals(b.fingerprint))
                        .findFirst().orElse(null);
                if (source != null && frontlineKey(source, ctx) == frontlineKey(row.getItem(), ctx)) {
                    e.acceptTransferModes(TransferMode.MOVE);
                    if (!row.getStyleClass().contains("drag-target"))
                        row.getStyleClass().add("drag-target");
                }
                e.consume();
            });

            row.setOnDragExited(e -> {
                row.getStyleClass().remove("drag-target");
                e.consume();
            });

            row.setOnDragDropped(e -> {
                Dragboard db = e.getDragboard();
                if (!db.hasString()) return;
                String sourceFp = db.getString();
                ObservableList<Brother> items = table.getItems();
                int sourceIdx = -1;
                for (int i = 0; i < items.size(); i++) {
                    if (sourceFp.equals(items.get(i).fingerprint)) { sourceIdx = i; break; }
                }
                if (sourceIdx < 0 || sourceIdx == row.getIndex()) return;
                Brother moved = items.remove(sourceIdx);
                items.add(row.getIndex(), moved);
                List<String> fps = items.stream()
                        .map(b -> b.fingerprint)
                        .filter(Objects::nonNull)
                        .toList();
                ctx.annotation().setOrder(fps);
                if (onOrderChanged != null) onOrderChanged.run();
                e.setDropCompleted(true);
                e.consume();
            });

            return row;
        });

        // Fixed columns — cannot be dragged
        TableColumn<Brother,?> colPortrait = portraitCol();
        TableColumn<Brother,?> colName     = nameCol();
        TableColumn<Brother,?> colRole     = roleCol();
        TableColumn<Brother,?> colLevel    = levelCol();
        colPortrait.setId("portrait"); colPortrait.setReorderable(false);
        colName    .setId("name");     colName    .setReorderable(false);
        colRole    .setId("role");     colRole    .setReorderable(false);
        colLevel   .setId("level");    colLevel   .setReorderable(false);
        table.getColumns().addAll(colPortrait, colName, colRole, colLevel);

        // Stat columns — reorderable within their range (indices 4-11)
        Set<TableColumn<?,?>> statSet = new HashSet<>();
        for (int i = 0; i < 8; i++) {
            var col = statCol(i);
            col.setId("stat-" + STATS[i].abbrev());
            statSet.add(col);
            table.getColumns().add(col);
        }

        // Equipment columns — reorderable within their range (indices 12-21)
        Set<TableColumn<?,?>> equipSet = new HashSet<>();
        for (int i = 0; i < 6; i++) {
            var col = equipCol(i);
            col.setId("equip-" + EQUIP_HDR[i]);
            equipSet.add(col);
            table.getColumns().add(col);
        }
        for (int p = 0; p < 4; p++) {
            var col = pouchCol(p);
            col.setId("equip-" + EQUIP_HDR[6 + p]);
            equipSet.add(col);
            table.getColumns().add(col);
        }

        // Traits column — fixed, to the left of perks
        TableColumn<Brother,?> colTraits = traitsCol();
        colTraits.setId("traits"); colTraits.setReorderable(false);
        table.getColumns().add(colTraits);

        // Perks column — fixed at the end
        TableColumn<Brother,?> colPerks = perksCol();
        colPerks.setId("perks"); colPerks.setReorderable(false);
        table.getColumns().add(colPerks);

        // Restore saved column order (applied before the listener so no enforcement fires)
        List<String> savedOrder = ctx.appConfig().overviewColumnOrder;
        if (savedOrder != null && !savedOrder.isEmpty()) {
            Map<String, TableColumn<Brother,?>> byId = new LinkedHashMap<>();
            for (var col : table.getColumns()) byId.put(col.getId(), col);
            List<TableColumn<Brother,?>> reordered = new ArrayList<>();
            for (String id : savedOrder) {
                TableColumn<Brother,?> col = byId.remove(id);
                if (col != null) reordered.add(col);
            }
            reordered.addAll(byId.values()); // columns absent from saved order go at end
            table.getColumns().setAll(reordered);
        }

        // Enforce group-constrained reordering: snap back if a column crosses a group boundary.
        // On every valid reorder, persist the new order to AppConfig.
        final int STAT_START  = 4;
        final int STAT_END    = 11;
        final int EQUIP_START = 12;
        final int EQUIP_END   = 21;

        AtomicReference<List<TableColumn<Brother,?>>> lastValid =
                new AtomicReference<>(new ArrayList<>(table.getColumns()));
        AtomicBoolean reverting = new AtomicBoolean(false);

        table.getColumns().addListener((ListChangeListener<TableColumn<Brother,?>>) change -> {
            if (reverting.get()) return;
            List<TableColumn<Brother,?>> cols = table.getColumns();
            boolean ok = true;
            for (int i = 0; i < cols.size() && ok; i++) {
                TableColumn<?,?> col = cols.get(i);
                if (statSet .contains(col) && (i < STAT_START  || i > STAT_END))  ok = false;
                if (equipSet.contains(col) && (i < EQUIP_START || i > EQUIP_END)) ok = false;
            }
            if (ok) {
                lastValid.set(new ArrayList<>(cols));
                ctx.appConfig().overviewColumnOrder = cols.stream().map(TableColumn::getId).toList();
                ctx.appConfig().save();
            } else {
                reverting.set(true);
                table.getColumns().setAll(lastValid.get());
                reverting.set(false);
            }
        });

        Runnable logWidths = () -> {
            StringBuilder sb = new StringBuilder("Column widths:");
            for (var col : table.getColumns())
                sb.append("  ").append(col.getId()).append("=").append((int) col.getWidth());
            log.fine(sb.toString());
        };
        for (var col : table.getColumns())
            col.widthProperty().addListener((o, a, b) -> logWidths.run());

        VBox.setVgrow(table, Priority.ALWAYS);
        activeTable = table;
        return table;
    }

    static void resetColumnWidths() {
        if (activeTable == null) return;
        // Defer until after the first layout pass so getWidth() returns real values.
        Platform.runLater(() -> {
            if (activeTable == null) return;
            double tableWidth = activeTable.getWidth();
            if (tableWidth <= 0) return;
            double otherWidth = 0;
            TableColumn<Brother, ?> traitsCol = null, perksCol = null;
            for (var col : activeTable.getColumns()) {
                if      ("traits".equals(col.getId())) traitsCol = col;
                else if ("perks".equals(col.getId()))  perksCol  = col;
                else                                   otherWidth += col.getWidth();
            }
            double traitTarget = 150;
            double perksTarget = tableWidth - otherWidth - traitTarget;
            // resizeColumn triggers the policy synchronously; compute perksTarget
            // before any resize so the delta for perks is correct regardless of
            // what the policy does to perks when we resize traits.
            if (traitsCol != null)
                activeTable.resizeColumn(traitsCol, traitTarget - traitsCol.getWidth());
            if (perksCol != null)
                activeTable.resizeColumn(perksCol,  perksTarget - perksCol.getWidth());
        });
    }

    private static int frontlineKey(Brother b, UiContext ctx) {
        if (b.fingerprint == null) return 0;
        String roleId = ctx.annotation().get(b.fingerprint).roleId;
        Role role = roleId != null ? ctx.roles().getById(roleId) : null;
        return OverviewCalc.frontlineKey(role);
    }

    // ---- column definitions ------------------------------------------------

    private TableColumn<Brother, Brother> portraitCol() {
        var col = fixedBrotherCol("", W_PORTRAIT);
        col.setCellFactory(c -> new TableCell<>() {
            private final ImageView iv = new ImageView();
            { iv.setFitWidth(W_PORTRAIT - 6); iv.setFitHeight(W_PORTRAIT - 6); iv.setPreserveRatio(true); }

            @Override protected void updateItem(Brother b, boolean empty) {
                super.updateItem(b, empty);
                if (empty || b == null) { setGraphic(null); setTooltip(null); return; }
                String rel = ctx.imageMap().resolveHex(b.backgroundHexId);
                Path root = ctx.appConfig().gameArtRoot();
                iv.setImage(rel != null && root != null
                        ? ctx.imageCache().get(root, rel)
                        : ctx.imageCache().getPlaceholder());
                DictionaryEntry bg = ctx.dict().get(b.backgroundHexId);
                setTooltip(bg != null ? new Tooltip(bg.name) : null);
                setGraphic(iv);
                setAlignment(Pos.CENTER);
                setPadding(Insets.EMPTY);
            }
        });
        return col;
    }

    private static TableColumn<Brother, Brother> nameCol() {
        TableColumn<Brother, Brother> col = new TableColumn<>("Name");
        col.setPrefWidth(W_NAME);
        col.setMinWidth(W_NAME);
        col.setResizable(false);
        col.setSortable(false);
        col.setCellValueFactory(d -> new SimpleObjectProperty<>(d.getValue()));
        col.setCellFactory(c -> new TableCell<>() {
            @Override protected void updateItem(Brother b, boolean empty) {
                super.updateItem(b, empty);
                setText(null);
                if (empty || b == null) {
                    setGraphic(null);
                } else {
                    Label nameLabel = new Label(b.name);
                    nameLabel.getStyleClass().add("cell-name");
                    VBox box = new VBox(2, nameLabel);
                    if (b.title != null && !b.title.isBlank()) {
                        Label titleLabel = new Label(b.title);
                        titleLabel.getStyleClass().add("overview-title");
                        titleLabel.setMaxWidth(W_NAME - 8);
                        box.getChildren().add(titleLabel);
                    }
                    box.setAlignment(Pos.CENTER_LEFT);
                    setGraphic(box);
                    setAlignment(Pos.CENTER_LEFT);
                    setPadding(new Insets(0, 0, 0, 4));
                }
            }
        });
        return col;
    }

    private TableColumn<Brother, Brother> levelCol() {
        var col = fixedBrotherCol("Lvl", W_LEVEL);
        col.setCellFactory(c -> new TableCell<>() {
            private final Label   lvlLabel      = new Label();
            private final Label   allottedLabel = new Label();
            private final VBox    box           = new VBox(0, lvlLabel, allottedLabel);
            private       Brother cachedBrother = null;
            {
                lvlLabel.getStyleClass().add("overview-stat-val");
                allottedLabel.getStyleClass().add("level-allotted-label");
                box.setAlignment(Pos.CENTER);
                potentialMode.addListener((obs, o, n) -> {
                    applyAllottedVisibility();
                    if (cachedBrother != null) refreshCell(cachedBrother);
                });
                applyAllottedVisibility();
            }

            private void applyAllottedVisibility() {
                PotentialMode m = potentialMode.get();
                boolean show = m == PotentialMode.CURRENT_EXPECTED || m == PotentialMode.EXPECTED;
                allottedLabel.setVisible(show);
                allottedLabel.setManaged(show);
            }

            private void refreshCell(Brother b) {
                int[] increases = b.fingerprint != null
                        ? ctx.annotation().get(b.fingerprint).statIncreases : null;
                int[] post11inc = b.fingerprint != null
                        ? ctx.annotation().get(b.fingerprint).post11Increases : null;
                OverviewCalc.LevelBudget budget = OverviewCalc.levelBudget(
                        b.levelTotal, ExpectedStatsCalculator.remainingLevels(b),
                        increases, post11inc);
                int usedBudget  = budget.used();
                int totalBudget = budget.totalBudget();

                // Label text + color
                allottedLabel.getStyleClass().removeAll(
                        "level-allotted-green", "level-allotted-post11", "level-allotted-red");
                if (b.levelTotal == 11) {
                    allottedLabel.setText("");
                } else {
                    allottedLabel.setText(OverviewCalc.formatBudgetLabel(budget.post11(), usedBudget, totalBudget));
                    OverviewCalc.BudgetState bs = OverviewCalc.budgetState(usedBudget, totalBudget);
                    if (bs == OverviewCalc.BudgetState.OVER)
                        allottedLabel.getStyleClass().add("level-allotted-red");
                    else if (bs == OverviewCalc.BudgetState.UNDER)
                        allottedLabel.getStyleClass().add("level-allotted-green");
                }

                // Tooltip
                if (b.levelTotal == 11) {
                    setTooltip(styled("Max level reached"));
                } else if (budget.post11()) {
                    if (budget.free() > 0) {
                        setTooltip(styled("Assign post-lv11 increases\n("
                                + usedBudget + " / " + totalBudget + " used)"));
                    } else {
                        setTooltip(styled("Post-lv11 increases fully assigned"));
                    }
                } else if (budget.cap() == 0) {
                    setTooltip(styled("Max level reached"));
                } else if (usedBudget == totalBudget && increases != null) {
                    setTooltip(buildGuideTooltip(b, budget.cap(), increases));
                } else {
                    String pendingNote = b.levelPoints > 0
                            ? b.levelPoints + " unassigned level-up(s) folded into budget.\n" : "";
                    setTooltip(styled(pendingNote + "Assign all increases to see\nthe roll priority guide.\n("
                            + usedBudget + " / " + totalBudget + " used)"));
                }
            }

            @Override protected void updateItem(Brother b, boolean empty) {
                super.updateItem(b, empty);
                if (empty || b == null) { cachedBrother = null; setGraphic(null); return; }
                cachedBrother = b;
                lvlLabel.setText(String.valueOf(b.levelTotal));
                if (b.levelTotal < 11 && b.levelPoints > 0) {
                    if (!lvlLabel.getStyleClass().contains("level-pending-badge"))
                        lvlLabel.getStyleClass().add("level-pending-badge");
                } else {
                    lvlLabel.getStyleClass().remove("level-pending-badge");
                }
                refreshCell(b);
                setGraphic(box);
                setAlignment(Pos.CENTER);
                setPadding(Insets.EMPTY);
            }

            private Tooltip buildGuideTooltip(Brother b, int remaining, int[] rolls) {
                int[] stars = new int[STATS.length];
                for (int i = 0; i < STATS.length; i++) stars[i] = b.stars[STATS[i].starIndex()];
                String roleId = b.fingerprint != null
                        ? ctx.annotation().get(b.fingerprint).roleId : null;
                se.niclas.broledger.model.Role role =
                        roleId != null ? ctx.roles().getById(roleId) : null;
                int[] priority = new int[STATS.length];
                java.util.Arrays.fill(priority, 3);
                if (role != null && role.priority != null)
                    System.arraycopy(role.priority, 0, priority, 0,
                            Math.min(role.priority.length, priority.length));
                List<ExpectedStatsCalculator.PriorityEntry> entries =
                        ExpectedStatsCalculator.rollPriorityGuideEntries(
                                remaining, stars, rolls, priority);
                return TooltipFactory.forLevelPriorityGuide(entries, remaining);
            }
        });
        return col;
    }

    private static Tooltip styled(String text) {
        Tooltip t = new Tooltip(text);
        t.getStyleClass().add("item-tooltip");
        return t;
    }

    private TableColumn<Brother, Brother> roleCol() {
        TableColumn<Brother, Brother> col = new TableColumn<>("Role");
        col.setPrefWidth(W_ROLE);
        col.setMinWidth(W_ROLE);
        col.setMaxWidth(W_ROLE);
        col.setResizable(false);
        col.setSortable(false);
        col.setCellValueFactory(d -> new SimpleObjectProperty<>(d.getValue()));
        col.setCellFactory(c -> new TableCell<>() {
            private final ComboBox<Role> combo = new ComboBox<>();
            private boolean updating = false;
            {
                combo.setMaxWidth(Double.MAX_VALUE);
                combo.getStyleClass().add("role-combo");
                combo.setConverter(new StringConverter<>() {
                    @Override public String toString(Role r) { return r != null ? r.name : ""; }
                    @Override public Role fromString(String s) { return null; }
                });
                // Build the role list once at cell-creation time (not per updateItem call)
                ObservableList<Role> roleItems = FXCollections.observableArrayList((Role) null);
                roleItems.addAll(ctx.roles().getAll());
                combo.setItems(roleItems);
                combo.valueProperty().addListener((obs, oldVal, newVal) -> {
                    if (updating) return;
                    Brother b = getItem();
                    if (b == null || b.fingerprint == null) return;
                    ctx.annotation().setRole(b.fingerprint, newVal != null ? newVal.id : null);
                    if (activeTable != null) activeTable.refresh();
                });
            }

            @Override protected void updateItem(Brother b, boolean empty) {
                super.updateItem(b, empty);
                if (empty || b == null) { setGraphic(null); return; }
                updating = true;
                String roleId = b.fingerprint != null
                        ? ctx.annotation().get(b.fingerprint).roleId : null;
                combo.setValue(roleId != null ? ctx.roles().getById(roleId) : null);
                updating = false;
                setGraphic(combo);
                setPadding(Insets.EMPTY);
            }
        });
        return col;
    }

    private TableColumn<Brother, Brother> statCol(int idx) {
        var col = fixedBrotherCol(STATS[idx].abbrev(), W_STAT);
        col.setCellFactory(c -> new TableCell<>() {
            private final HBox  stars    = new HBox(0);
            private final Label val      = new Label();
            private final Label pot      = new Label();
            private final Label expected = new Label();
            private final VBox  box      = new VBox(0, stars, val, pot, expected);

            // Cached per-item data for tooltip switching on mode change
            private StatModifierService.Breakdown          cachedBd;
            private StatPotentialCalculator.Potential      cachedPot;
            private ExpectedStatsCalculator.Expected       cachedNaive;
            private ExpectedStatsCalculator.Expected       cachedGreedy;
            private int                                    cachedUsedBudget;

            {
                stars.setAlignment(Pos.CENTER);
                val.getStyleClass().add("overview-stat-val");
                pot.getStyleClass().add("overview-potential-val");
                expected.getStyleClass().add("overview-expected-val");
                box.setAlignment(Pos.CENTER);
                applyModeVisibility();
                potentialMode.addListener((obs, o, n) -> applyModeVisibility());
            }

            private void applyModeVisibility() {
                PotentialMode m = potentialMode.get();
                val.setVisible(m == PotentialMode.CURRENT || m == PotentialMode.BOTH
                        || m == PotentialMode.CURRENT_EXPECTED);
                val.setManaged(val.isVisible());
                pot.setVisible(m == PotentialMode.BOTH || m == PotentialMode.POTENTIAL);
                pot.setManaged(pot.isVisible());
                expected.setVisible(m == PotentialMode.CURRENT_EXPECTED || m == PotentialMode.EXPECTED);
                expected.setManaged(expected.isVisible());
                updateTooltip(m);
            }

            private void updateTooltip(PotentialMode m) {
                if (cachedBd == null) return;
                if (m == PotentialMode.CURRENT_EXPECTED || m == PotentialMode.EXPECTED) {
                    setTooltip(TooltipFactory.forStatBreakdownWithExpected(
                            cachedBd, cachedNaive, cachedGreedy, cachedUsedBudget));
                } else {
                    setTooltip(TooltipFactory.forStatBreakdownWithPotential(cachedBd, cachedPot));
                }
            }

            @Override protected void updateItem(Brother b, boolean empty) {
                super.updateItem(b, empty);
                if (empty || b == null) { setGraphic(null); cachedBd = null; return; }
                stars.getChildren().clear();
                int sc = b.stars[STATS[idx].starIndex()];
                for (int i = 0; i < 3; i++) {
                    Label l = new Label(i < sc ? "★" : "☆");
                    l.getStyleClass().add(i < sc ? "overview-star-filled" : "overview-star-empty");
                    stars.getChildren().add(l);
                }
                StatModifierService.Breakdown bd = ctx.statModifier().compute(b, STATS[idx]);
                val.setText(String.valueOf(bd.finalValue()));
                StatPotentialCalculator.Potential p = StatPotentialCalculator.compute(b, STATS[idx]);
                pot.setText(String.valueOf(p.finalPotential()));

                int[] increases = b.fingerprint != null
                        ? ctx.annotation().get(b.fingerprint).statIncreases : null;
                int[] post11inc = b.fingerprint != null
                        ? ctx.annotation().get(b.fingerprint).post11Increases : null;
                boolean cellPost11 = b.levelTotal > 11;
                int usedBudget = cellPost11
                        ? OverviewCalc.sumOrZero(post11inc)
                        : OverviewCalc.sumOrZero(increases);
                ExpectedStatsCalculator.Expected expNaive =
                        ExpectedStatsCalculator.compute(b, STATS[idx], increases, post11inc, ExpectedStatsCalculator.Mode.NAIVE);
                ExpectedStatsCalculator.Expected expGreedy =
                        ExpectedStatsCalculator.compute(b, STATS[idx], increases, post11inc, ExpectedStatsCalculator.Mode.GREEDY);
                ExpectedStatsCalculator.Mode expMode =
                        ExpectedStatsCalculator.Mode.parse(AppConfig.getInstance().expectedStatsMode);
                ExpectedStatsCalculator.Expected expDisplay =
                        (expMode == ExpectedStatsCalculator.Mode.GREEDY) ? expGreedy : expNaive;

                expected.setText(OverviewCalc.expectedDisplay(expDisplay));

                cachedBd         = bd;
                cachedPot        = p;
                cachedNaive      = expNaive;
                cachedGreedy     = expGreedy;
                cachedUsedBudget = usedBudget;
                updateTooltip(potentialMode.get());

                applyPriorityBackground(this, b, idx, ctx);
                setGraphic(box);
                setAlignment(Pos.CENTER);
                setPadding(Insets.EMPTY);
            }
        });
        return col;
    }

    private TableColumn<Brother, Brother> equipCol(int i) {
        var col = fixedBrotherCol(EQUIP_HDR[i], W_EQUIP);
        int arrayIdx    = EQUIP_SLOT_IDX[i];
        String slotName = EQUIP_SLOT_NAME[i];
        boolean showDur = i == 0 || i == 1; // Arm and Hlm only
        boolean showTier = "weapon".equals(slotName);
        col.setCellFactory(c -> showDur
                ? equipCellWithDurability(b -> {
                    InventorySlot s = b.equippedSlots != null && b.equippedSlots.length > arrayIdx
                            ? b.equippedSlots[arrayIdx] : null;
                    return new SlotAndName(s, slotName);
                })
                : showTier
                ? equipCellWithTier(b -> {
                    InventorySlot s = b.equippedSlots != null && b.equippedSlots.length > arrayIdx
                            ? b.equippedSlots[arrayIdx] : null;
                    return new SlotAndName(s, slotName);
                })
                : equipCell(b -> {
                    InventorySlot s = b.equippedSlots != null && b.equippedSlots.length > arrayIdx
                            ? b.equippedSlots[arrayIdx] : null;
                    return new SlotAndName(s, slotName);
                }));
        return col;
    }

    private TableColumn<Brother, Brother> pouchCol(int p) {
        var col = fixedBrotherCol(EQUIP_HDR[6 + p], W_EQUIP);
        col.setCellFactory(c -> equipCellWithTier(b -> {
            List<InventorySlot> pouches = gatherPouches(b);
            return new SlotAndName(p < pouches.size() ? pouches.get(p) : null, "pouch");
        }));
        return col;
    }

    private TableColumn<Brother, Brother> traitsCol() {
        TableColumn<Brother, Brother> col = new TableColumn<>("Traits");
        col.setPrefWidth(150);
        col.setMinWidth(37); // space for at least one icon
        col.setSortable(false);
        col.setCellValueFactory(d -> new SimpleObjectProperty<>(d.getValue()));
        col.setCellFactory(c -> new TableCell<>() {
            @Override protected void updateItem(Brother b, boolean empty) {
                super.updateItem(b, empty);
                if (empty || b == null) { setGraphic(null); return; }
                setGraphic(traitIcons(b));
                setPadding(new Insets(0, 0, 0, 4));
            }
        });
        return col;
    }

    private TableColumn<Brother, Brother> perksCol() {
        // Not fixed — absorbs remaining viewport width via FLEX_LAST_COLUMN policy.
        TableColumn<Brother, Brother> col = new TableColumn<>("Perks");
        col.setPrefWidth(280);
        col.setMinWidth(112);
        col.setSortable(false);
        col.setCellValueFactory(d -> new SimpleObjectProperty<>(d.getValue()));
        col.setCellFactory(c -> new TableCell<>() {
            @Override protected void updateItem(Brother b, boolean empty) {
                super.updateItem(b, empty);
                if (empty || b == null) { setGraphic(null); return; }
                setGraphic(perkIcons(b));
                setPadding(new Insets(0, 0, 0, 4));
            }
        });
        return col;
    }

    // ---- shared factories --------------------------------------------------

    private static void applyPriorityBackground(TableCell<?, ?> cell, Brother b,
                                                int statDisplayIdx, UiContext ctx) {
        cell.getStyleClass().removeAll("stat-cell-p1", "stat-cell-p2");
        Role role = resolveRole(b, ctx);
        if (role == null) return;
        int prio = OverviewCalc.priorityAt(role.priority, statDisplayIdx, 0);
        if (prio == 1)      cell.getStyleClass().add("stat-cell-p1");
        else if (prio == 2) cell.getStyleClass().add("stat-cell-p2");
    }

    private static Role resolveRole(Brother b, UiContext ctx) {
        if (b == null || b.fingerprint == null) return null;
        String roleId = ctx.annotation().get(b.fingerprint).roleId;
        return roleId != null ? ctx.roles().getById(roleId) : null;
    }

    private record SlotAndName(InventorySlot slot, String slotName) {}

    @FunctionalInterface
    private interface OverlayUpdater { void refresh(SlotAndName sn); }

    private TableCell<Brother, Brother> buildEquipCell(
            Function<Brother, SlotAndName> resolver,
            Node overlay,
            OverlayUpdater updater) {
        return new TableCell<>() {
            private final ImageView iv = new ImageView();
            private final Node graphic;
            {
                iv.setFitWidth(W_EQUIP - 6); iv.setFitHeight(W_EQUIP - 6); iv.setPreserveRatio(true);
                graphic = overlay == null ? iv
                        : new javafx.scene.layout.StackPane(iv, overlay);
            }
            @Override protected void updateItem(Brother b, boolean empty) {
                super.updateItem(b, empty);
                if (empty || b == null) {
                    setGraphic(null); setTooltip(null);
                    if (updater != null) updater.refresh(null);
                    return;
                }
                SlotAndName sn = resolver.apply(b);
                iv.setImage(resolveEquipImage(sn.slot(), sn.slotName()));
                setTooltip(sn.slot() != null && !sn.slot().empty
                        ? TooltipFactory.forSlot(sn.slot()) : null);
                if (updater != null) updater.refresh(sn);
                setGraphic(graphic);
                setAlignment(Pos.CENTER);
                setPadding(Insets.EMPTY);
            }
        };
    }

    private TableCell<Brother, Brother> equipCell(Function<Brother, SlotAndName> resolver) {
        return buildEquipCell(resolver, null, null);
    }

    private TableCell<Brother, Brother> equipCellWithDurability(
            Function<Brother, SlotAndName> resolver) {
        Label dur = new Label();
        dur.getStyleClass().add("overview-potential-val");
        javafx.scene.layout.StackPane.setAlignment(dur, Pos.BOTTOM_CENTER);
        dur.visibleProperty().bind(armorMode.isNotEqualTo(ArmorMode.OFF));
        dur.managedProperty().bind(armorMode.isNotEqualTo(ArmorMode.OFF));
        SlotAndName[] last = {null};
        armorMode.addListener((o, a, b) -> applyDurText(dur, last[0]));
        return buildEquipCell(resolver, dur, sn -> { last[0] = sn; applyDurText(dur, sn); });
    }

    private static void applyDurText(Label dur, SlotAndName sn) {
        if (sn == null || sn.slot() == null || sn.slot().empty || sn.slot().stats == null) {
            dur.setText(""); return;
        }
        dur.setText(switch (armorMode.get()) {
            case DURABILITY -> sn.slot().stats.durability > 0
                    ? String.valueOf((int) sn.slot().stats.durability) : "";
            case FATIGUE    -> sn.slot().stats.fatigue != 0
                    ? String.valueOf((int) sn.slot().stats.fatigue) : "";
            case OFF        -> "";
        });
    }

    private TableCell<Brother, Brother> equipCellWithTier(
            Function<Brother, SlotAndName> resolver) {
        javafx.scene.layout.Region dot = new javafx.scene.layout.Region();
        dot.getStyleClass().add("weapon-tier-dot");
        javafx.scene.layout.StackPane.setAlignment(dot, Pos.BOTTOM_RIGHT);
        dot.visibleProperty().bind(weaponTierMode.isEqualTo(WeaponTierMode.ON));
        dot.managedProperty().bind(weaponTierMode.isEqualTo(WeaponTierMode.ON));
        return buildEquipCell(resolver, dot, sn -> {
            dot.getStyleClass().removeAll("tier-1", "tier-2", "tier-3", "tier-named");
            if (sn != null && sn.slot() != null && !sn.slot().empty && sn.slot().itemId != null) {
                String tsc = OverviewCalc.tierStyleClass(
                        sn.slot().itemType, ctx.weaponStats().getTier(sn.slot().itemId));
                if (tsc != null) dot.getStyleClass().add(tsc);
            }
        });
    }

    /** Creates a Brother-typed column with fixed, non-resizable width. */
    private static TableColumn<Brother, Brother> fixedBrotherCol(String header, double width) {
        TableColumn<Brother, Brother> col = new TableColumn<>(header);
        col.setPrefWidth(width);
        col.setMinWidth(width);
        col.setMaxWidth(width);
        col.setResizable(false);
        col.setSortable(false);
        col.setCellValueFactory(d -> new SimpleObjectProperty<>(d.getValue()));
        return col;
    }

    // ---- data helpers ------------------------------------------------------

    private HBox perkIcons(Brother b) {
        HBox h = new HBox(3);
        h.setAlignment(Pos.CENTER_LEFT);
        for (String hexId : sortedPerkIds(b.perkIds)) {
            String rel = ctx.imageMap().resolveHex(hexId);
            Path root  = ctx.appConfig().gameArtRoot();
            Image img  = rel != null && root != null
                    ? ctx.imageCache().get(root, rel)
                    : ctx.imageCache().getPlaceholder();
            ImageView iv = new ImageView(img);
            iv.setFitWidth(W_PERK);
            iv.setFitHeight(W_PERK);
            iv.setPreserveRatio(true);
            Tooltip.install(iv, TooltipFactory.forHex(hexId));
            h.getChildren().add(iv);
        }
        if (b.perkPoints > 0) {
            Label badge = new Label("+" + b.perkPoints);
            badge.getStyleClass().add("level-pending-badge");
            Tooltip tip = new Tooltip(b.perkPoints + " perk point(s) available but not yet spent.");
            tip.getStyleClass().add("item-tooltip");
            Tooltip.install(badge, tip);
            h.getChildren().add(badge);
        }
        return h;
    }

    private List<String> sortedPerkIds(List<String> perkIds) {
        PerkSortMode mode = perkSortMode.get();
        if (mode == PerkSortMode.OFF) return perkIds;
        List<String> sorted = new ArrayList<>(perkIds);
        sorted.sort(OverviewCalc.perkComparator(
                mode,
                id -> OverviewCalc.tierOrMax(ctx.statModifier().getTier(id)),
                id -> perkCounts.getOrDefault(id.toUpperCase(), 0L),
                ctx.dict()::getName));
        return sorted;
    }

    static final java.util.Set<String> TRAIT_NAME_BLOCKLIST =
            java.util.Set.of("Unknown", "Morale");

    private HBox traitIcons(Brother b) {
        HBox h = new HBox(3);
        h.setAlignment(Pos.CENTER_LEFT);
        for (TraitEntry t : b.traits) {
            DictionaryEntry entry = ctx.dict().get(t.id);
            if (entry == null || "internal".equals(entry.type)) continue;
            String name = entry.name;
            if (name == null || TRAIT_NAME_BLOCKLIST.contains(name)) continue;
            String rel = ctx.imageMap().resolveHex(t.id);
            Path root  = ctx.appConfig().gameArtRoot();
            Image img  = rel != null && root != null
                    ? ctx.imageCache().get(root, rel)
                    : ctx.imageCache().getPlaceholder();
            ImageView iv = new ImageView(img);
            iv.setFitWidth(W_TRAIT);
            iv.setFitHeight(W_TRAIT);
            iv.setPreserveRatio(true);
            Tooltip.install(iv, TooltipFactory.forHex(t.id));
            h.getChildren().add(iv);
        }
        return h;
    }

    private static List<InventorySlot> gatherPouches(Brother b) {
        return OverviewCalc.gatherPouches(b.equippedSlots, b.extraPouches);
    }

    private Image resolveEquipImage(InventorySlot slot, String slotName) {
        if (slot == null || slot.empty || slot.itemId == null)
            return ctx.imageCache().getPlaceholder();
        int icon    = slot.stats != null ? slot.stats.icon    : 0;
        String iSet = slot.stats != null ? slot.stats.iconSet : null;
        String rel  = ctx.imageMap().resolve(slotName, slot.itemId, icon, iSet);
        Path root   = ctx.appConfig().gameArtRoot();
        return rel != null && root != null
                ? ctx.imageCache().get(root, rel)
                : ctx.imageCache().getPlaceholder();
    }
}
