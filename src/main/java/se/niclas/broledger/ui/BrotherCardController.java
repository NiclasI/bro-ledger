package se.niclas.broledger.ui;

import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.geometry.HPos;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.Tooltip;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.util.StringConverter;
import se.niclas.broledger.model.Brother;
import se.niclas.broledger.model.DictionaryEntry;
import se.niclas.broledger.model.InventorySlot;
import se.niclas.broledger.model.Role;
import se.niclas.broledger.model.Stat;
import se.niclas.broledger.parser.ArmorCalculator;
import se.niclas.broledger.service.AppConfig;
import se.niclas.broledger.service.ExpectedStatsCalculator;
import se.niclas.broledger.service.StatModifierService;
import se.niclas.broledger.service.StatPotentialCalculator;

import java.net.URL;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.ResourceBundle;
import java.util.Set;
import java.util.stream.Collectors;

public class BrotherCardController implements Initializable {

    // ---- display order: stats[stat.statIndex()] and stars[stat.starIndex()] ----
    private record StatRow(String label, Stat stat, String iconResource) {}

    private static final StatRow[] STAT_ROWS = {
        new StatRow("Health",       Stat.HEALTH,        "icon-health.png"),
        new StatRow("Resolve",      Stat.RESOLVE,       "icon-resolve.png"),
        new StatRow("Fatigue",      Stat.FATIGUE,       "icon-fatigue.png"),
        new StatRow("Initiative",   Stat.INITIATIVE,    "icon-initiative.png"),
        new StatRow("Melee Skill",  Stat.MELEE_SKILL,   "icon-mSkill.png"),
        new StatRow("Ranged Skill", Stat.RANGED_SKILL,  "icon-rSkill.png"),
        new StatRow("Melee Def",    Stat.MELEE_DEFENSE, "icon-mDefense.png"),
        new StatRow("Ranged Def",   Stat.RANGED_DEFENSE,"icon-rDefense.png"),
    };

    private static final String[] SLOT_NAMES = {
        "weapon", "shield", "body", "helmet", "trinket", "quiver", "pouch"
    };

    // Grid layout constants
    private static final int STAT_HEADER_ROW       = 0;
    private static final int FIRST_STAT_ROW        = 1;
    private static final int EXPECTED_COL          = 5;
    private static final int INCREASE_EDITOR_COL   = 6;
    private static final int PERSISTENT_TARGET_COL = 7;
    private static final int FIRST_EXTRA_COL       = 8;
    private static final double EXTRA_COL_WIDTH    = 70.0;
    private static final double LEFT_SECTION_WIDTH = 410.0;

    @FXML private Button     backBtn;
    @FXML private ImageView  portraitView;
    @FXML private Label      nameLabel;
    @FXML private Label      titleLabel;
    @FXML private Label      backgroundLabel;
    @FXML private FlowPane        traitsPane;
    @FXML private ComboBox<Role>  roleCombo;
    @FXML private Button          clearRoleBtn;
    @FXML private Button          resetRolesBtn;
    @FXML private Button          showAllRolesBtn;
    @FXML private Button          frontlineRolesBtn;
    @FXML private Button          backlineRolesBtn;
    @FXML private GridPane        statsGrid;
    private Label                 increasesRemainingLabel;
    @FXML private Button          autoAssignBtn;
    @FXML private FlowPane   perksPane;
    @FXML private Button     perkSortBtn;
    @FXML private Label      perkPointsLabel;
    @FXML private HBox       equipmentPane;
    @FXML private Label      armorLabel;
    @FXML private Label      fatigueLabel;
    @FXML private Label      damageLabel;

    private final UiContext ctx;
    private Runnable onBack;
    private Brother currentBrother;
    private List<Brother> allBrothers = List.of();
    private List<javafx.scene.Node> originalPerkOrder = new ArrayList<>();
    private final List<Role> extraRoles = new ArrayList<>();
    private int maxExtras = 0;
    private Label persistentRoleHeader;

    // Typed maps populated by buildStatRows / rebuildExtraColumns.
    // Eliminates per-update grid scans and userData string parsing.
    private final Map<Integer, StarsWidget>         starsByRow          = new HashMap<>();
    private final Map<Integer, Label>               valLabels           = new HashMap<>();
    private final Map<Integer, Label>               potLabels           = new HashMap<>();
    private final Map<Integer, Label>               expectedLabels      = new HashMap<>();
    private final Map<Integer, Label>               increaseCountLabels = new HashMap<>();
    private final Map<Integer, Button>              incPlusBtns         = new HashMap<>();
    private final Map<Integer, Button>              incMinusBtns        = new HashMap<>();
    private final Map<Integer, Label>               targetLabels        = new HashMap<>();
    private final Map<Integer, Map<Integer, Label>> extraTargetsByRow   = new HashMap<>();

    public BrotherCardController(UiContext ctx) {
        this.ctx = ctx;
    }

    public void setOnBack(Runnable r) { this.onBack = r; }

    @Override
    public void initialize(URL url, ResourceBundle rb) {
        backBtn.setOnAction(e -> { if (onBack != null) onBack.run(); });
        buildStatRows();
        buildPerksPane();
        setupPerkSortButton();
        setupRoleCombo();
        setupRoleButtons();
        setupWidthWatcher();
    }

    /** Called by MainController after the role manager makes changes. */
    public void refreshRoles() {
        Role current = roleCombo.getValue();
        roleCombo.getItems().setAll(ctx.roles().getAll());
        if (current != null) {
            roleCombo.getItems().stream()
                    .filter(r -> r.id.equals(current.id))
                    .findFirst()
                    .ifPresentOrElse(roleCombo::setValue, () -> roleCombo.setValue(null));
        }
        // Re-resolve each extra role by ID; skip null placeholders and drop deleted roles
        List<Role> refreshed = new ArrayList<>();
        for (Role extra : extraRoles) {
            if (extra == null) continue;
            Role resolved = ctx.roles().getById(extra.id);
            if (resolved != null) refreshed.add(resolved);
        }
        extraRoles.clear();
        extraRoles.addAll(refreshed);
        rebuildExtraColumns();
    }

    // ---- public API --------------------------------------------------------

    public void populate(Brother b) {
        if (b == null) return;
        currentBrother = b;

        // 1. Header
        portraitView.setImage(loadPortrait(b));
        nameLabel.setText(b.name);
        titleLabel.setText(b.title != null ? b.title : "");
        DictionaryEntry bg = ctx.dict().get(b.backgroundHexId);
        String bgName = bg != null ? bg.name : "";
        backgroundLabel.setText(bgName + ", level " + b.levelTotal);

        // 2. Traits
        buildTraitsPane(b);

        // 3. Role — load from AnnotationService
        String roleId = b.fingerprint != null
                ? ctx.annotation().get(b.fingerprint).roleId : null;
        roleCombo.setValue(roleId != null ? ctx.roles().getById(roleId) : null);

        // 4. Stats
        updateStatValues(b);

        // 5. Perks — update opacity and unspent badge
        updatePerkOpacity(b);
        if (b.perkPoints > 0) {
            perkPointsLabel.setText("(+" + b.perkPoints + " unspent)");
            perkPointsLabel.setVisible(true);
            perkPointsLabel.setManaged(true);
            Tooltip tip = new Tooltip(b.perkPoints + " perk point(s) available\nbut not yet spent in-game.");
            tip.getStyleClass().add("item-tooltip");
            Tooltip.install(perkPointsLabel, tip);
        } else {
            perkPointsLabel.setVisible(false);
            perkPointsLabel.setManaged(false);
        }

        // 6. Equipment
        buildEquipmentPane(b);

        // 7. Footer
        armorLabel.setText("Armor: " + ArmorCalculator.effectiveArmor(b));
        fatigueLabel.setText("Fatigue: " + ArmorCalculator.totalFatiguePenalty(b));
        damageLabel.setText("Damage: " + ArmorCalculator.weaponDamageRange(b));
    }

    /** Called by MainController after a new save is loaded so COMMONALITY counts are up to date. */
    public void setAllBrothers(List<Brother> brothers) {
        allBrothers = brothers != null ? brothers : List.of();
        PerkSortMode mode = BrotherOverviewPane.perkSortMode.get();
        if (mode == PerkSortMode.COMMONALITY || mode == PerkSortMode.TIER_THEN_COMMON) resortPerks();
    }

    // ---- initialization (once) --------------------------------------------

    private void buildStatRows() {
        statsGrid.getChildren().clear();
        starsByRow.clear();
        valLabels.clear();
        potLabels.clear();
        expectedLabels.clear();
        increaseCountLabels.clear();
        incPlusBtns.clear();
        incMinusBtns.clear();
        targetLabels.clear();
        int[] displayOrder = computeStatDisplayOrder();
        for (int displayPos = 0; displayPos < displayOrder.length; displayPos++) {
            int canonIdx = displayOrder[displayPos];  // index into STAT_ROWS / role.targetStats
            StatRow sr = STAT_ROWS[canonIdx];
            int gridRow = FIRST_STAT_ROW + displayPos;

            // Col 0: StarsWidget — tracked in starsByRow map
            StarsWidget stars = new StarsWidget(0);
            starsByRow.put(canonIdx, stars);
            GridPane.setRowIndex(stars, gridRow);
            GridPane.setColumnIndex(stars, 0);

            // Col 1: stat icon
            ImageView icon = loadStatIcon(sr.iconResource());
            icon.setFitWidth(20);
            icon.setFitHeight(20);
            icon.setPreserveRatio(true);
            GridPane.setRowIndex(icon, gridRow);
            GridPane.setColumnIndex(icon, 1);

            // Col 2: label
            Label lbl = new Label(sr.label());
            lbl.getStyleClass().add("stat-label");
            GridPane.setRowIndex(lbl, gridRow);
            GridPane.setColumnIndex(lbl, 2);

            // Col 3: value — tracked in valLabels map
            Label val = new Label("0");
            val.getStyleClass().add("stat-value");
            val.setMinWidth(28);
            val.setAlignment(Pos.CENTER_RIGHT);
            valLabels.put(canonIdx, val);
            GridPane.setRowIndex(val, gridRow);
            GridPane.setColumnIndex(val, 3);

            // Col 4: potential value — tracked in potLabels map
            Label pot = new Label("");
            pot.getStyleClass().add("stat-potential");
            pot.setMinWidth(28);
            pot.setAlignment(Pos.CENTER_RIGHT);
            potLabels.put(canonIdx, pot);
            GridPane.setRowIndex(pot, gridRow);
            GridPane.setColumnIndex(pot, 4);

            // Col 5: expected value — tracked in expectedLabels map
            Label exp = new Label("—");
            exp.getStyleClass().add("stat-expected");
            exp.setMinWidth(28);
            exp.setAlignment(Pos.CENTER_RIGHT);
            expectedLabels.put(canonIdx, exp);
            GridPane.setRowIndex(exp, gridRow);
            GridPane.setColumnIndex(exp, EXPECTED_COL);

            // Col 6: increase editor [− count +] — tracked in increaseCountLabels, incMinusBtns, incPlusBtns
            final int capturedIdx = canonIdx;
            Button minusBtn = new Button("−");
            minusBtn.getStyleClass().add("increase-btn");
            minusBtn.setOnAction(e -> onIncreaseChange(capturedIdx, -1));
            Label countLbl = new Label("0");
            countLbl.getStyleClass().add("increase-count");
            countLbl.setMinWidth(18);
            countLbl.setAlignment(Pos.CENTER);
            Button plusBtn = new Button("+");
            plusBtn.getStyleClass().add("increase-btn");
            plusBtn.setOnAction(e -> onIncreaseChange(capturedIdx, +1));
            HBox editor = new HBox(2, minusBtn, countLbl, plusBtn);
            editor.setAlignment(Pos.CENTER);
            increaseCountLabels.put(canonIdx, countLbl);
            incMinusBtns.put(canonIdx, minusBtn);
            incPlusBtns.put(canonIdx, plusBtn);
            GridPane.setRowIndex(editor, gridRow);
            GridPane.setColumnIndex(editor, INCREASE_EDITOR_COL);

            // Col 7: persistent-role target — tracked in targetLabels map
            Label target = new Label();
            target.getStyleClass().add("stat-target");
            target.setMinWidth(28);
            target.setAlignment(Pos.CENTER_RIGHT);
            targetLabels.put(canonIdx, target);
            GridPane.setRowIndex(target, gridRow);
            GridPane.setColumnIndex(target, PERSISTENT_TARGET_COL);

            statsGrid.getChildren().addAll(stars, icon, lbl, val, pot, exp, editor, target);
        }

        // Header row: column labels for val / pot / exp
        for (int[] colDef : new int[][]{{3, 0}, {4, 1}, {EXPECTED_COL, 2}}) {
            String text = colDef[1] == 0 ? "Val" : colDef[1] == 1 ? "Pot" : "Exp";
            Label hdr = new Label(text);
            hdr.getStyleClass().add("stat-col-header");
            hdr.setAlignment(Pos.CENTER_RIGHT);
            hdr.setMaxWidth(Double.MAX_VALUE);
            GridPane.setRowIndex(hdr, STAT_HEADER_ROW);
            GridPane.setColumnIndex(hdr, colDef[0]);
            GridPane.setHalignment(hdr, HPos.RIGHT);
            statsGrid.getChildren().add(hdr);
        }

        // Header row: increases-remaining label above the editor column
        increasesRemainingLabel = new Label();
        increasesRemainingLabel.getStyleClass().add("increases-remaining-label");
        increasesRemainingLabel.setAlignment(Pos.CENTER);
        GridPane.setRowIndex(increasesRemainingLabel, STAT_HEADER_ROW);
        GridPane.setColumnIndex(increasesRemainingLabel, INCREASE_EDITOR_COL);
        statsGrid.getChildren().add(increasesRemainingLabel);

        // Header row: role name for the persistent column (no dropdown — that lives above the grid)
        persistentRoleHeader = new Label("");
        persistentRoleHeader.getStyleClass().add("field-label");
        persistentRoleHeader.setMaxWidth(Double.MAX_VALUE);
        GridPane.setRowIndex(persistentRoleHeader, STAT_HEADER_ROW);
        GridPane.setColumnIndex(persistentRoleHeader, PERSISTENT_TARGET_COL);
        GridPane.setHalignment(persistentRoleHeader, HPos.RIGHT);
        statsGrid.getChildren().add(persistentRoleHeader);
    }

    private int[] computeStatDisplayOrder() {
        Stat[] ordered = ctx.appConfig().orderedStats();
        int[] result = new int[ordered.length];
        for (int i = 0; i < ordered.length; i++) result[i] = ordered[i].ordinal();
        return result;
    }

    private void buildPerksPane() {
        perksPane.getChildren().clear();
        List<Map.Entry<String, DictionaryEntry>> allPerks =
                ctx.dict().getAllByType("perk");
        for (Map.Entry<String, DictionaryEntry> e : allPerks) {
            String hexId = e.getKey();

            ImageView iv = new ImageView(loadHexImage(hexId));
            iv.setFitWidth(40);
            iv.setFitHeight(40);
            iv.setPreserveRatio(true);
            iv.setOpacity(0.3);
            iv.setUserData("perk-" + hexId);
            Tooltip.install(iv, TooltipFactory.forHex(hexId));
            perksPane.getChildren().add(iv);
        }
        originalPerkOrder = new ArrayList<>(perksPane.getChildren());
    }

    private void setupPerkSortButton() {
        perkSortBtn.setText(BrotherOverviewPane.perkSortMode.get().label);
        perkSortBtn.setOnAction(e ->
                BrotherOverviewPane.perkSortMode.set(BrotherOverviewPane.perkSortMode.get().next()));
        BrotherOverviewPane.perkSortMode.addListener((obs, old, newMode) -> {
            perkSortBtn.setText(newMode.label);
            resortPerks();
        });
    }

    private void resortPerks() {
        PerkSortMode perkSortMode = BrotherOverviewPane.perkSortMode.get();
        if (perkSortMode == PerkSortMode.OFF) {
            perksPane.getChildren().setAll(originalPerkOrder);
            return;
        }
        List<javafx.scene.Node> sorted = new ArrayList<>(originalPerkOrder);
        if (perkSortMode == PerkSortMode.TIER) {
            sorted.sort((a, b) -> {
                String hexA = ((String) a.getUserData()).substring(5);
                String hexB = ((String) b.getUserData()).substring(5);
                int ta = tierOf(hexA);
                int tb = tierOf(hexB);
                if (ta != tb) return Integer.compare(ta, tb);
                return String.CASE_INSENSITIVE_ORDER.compare(
                        ctx.dict().getName(hexA), ctx.dict().getName(hexB));
            });
        } else {
            Map<String, Long> counts = allBrothers.stream()
                    .flatMap(br -> br.perkIds.stream())
                    .collect(Collectors.groupingBy(String::toUpperCase, Collectors.counting()));
            if (perkSortMode == PerkSortMode.COMMONALITY) {
                sorted.sort((a, b) -> {
                    String hexA = ((String) a.getUserData()).substring(5);
                    String hexB = ((String) b.getUserData()).substring(5);
                    long cA = counts.getOrDefault(hexA, 0L);
                    long cB = counts.getOrDefault(hexB, 0L);
                    if (cA != cB) return Long.compare(cB, cA);
                    return String.CASE_INSENSITIVE_ORDER.compare(
                            ctx.dict().getName(hexA), ctx.dict().getName(hexB));
                });
            } else { // TIER_THEN_COMMON
                sorted.sort((a, b) -> {
                    String hexA = ((String) a.getUserData()).substring(5);
                    String hexB = ((String) b.getUserData()).substring(5);
                    int ta = tierOf(hexA);
                    int tb = tierOf(hexB);
                    if (ta != tb) return Integer.compare(ta, tb);
                    long cA = counts.getOrDefault(hexA, 0L);
                    long cB = counts.getOrDefault(hexB, 0L);
                    if (cA != cB) return Long.compare(cB, cA);
                    return String.CASE_INSENSITIVE_ORDER.compare(
                            ctx.dict().getName(hexA), ctx.dict().getName(hexB));
                });
            }
        }
        perksPane.getChildren().setAll(sorted);
    }

    private int tierOf(String hexId) {
        Integer t = ctx.statModifier().getTier(hexId);
        return t != null ? t : Integer.MAX_VALUE;
    }

    // ---- per-brother updates -----------------------------------------------

    private void buildTraitsPane(Brother b) {
        traitsPane.getChildren().clear();
        for (var trait : b.traits) {
            String hexId = trait.id;
            DictionaryEntry entry = ctx.dict().get(hexId);
            String type = entry != null ? entry.type : "";

            if ("perk".equals(type)) continue;
            String name = ctx.dict().getName(hexId);
            if (name == null || BrotherOverviewPane.TRAIT_NAME_BLOCKLIST.contains(name)) continue;

            ImageView iv = new ImageView(loadHexImage(hexId));
            iv.setFitWidth(40);
            iv.setFitHeight(40);
            iv.setPreserveRatio(true);
            Tooltip.install(iv, TooltipFactory.forHex(hexId));
            traitsPane.getChildren().add(iv);
        }
    }

    private void updateStatValues(Brother b) {
        Role role = resolveRole(b);
        // StarsWidget is immutable — swap each one in the grid and update the map reference.
        // Snapshot keys first so we can call put() on the map without iterator issues.
        for (int row : new ArrayList<>(starsByRow.keySet())) {
            StarsWidget oldSw = starsByRow.get(row);
            StarsWidget newSw = new StarsWidget(b.stars[STAT_ROWS[row].stat().starIndex()]);
            int idx = statsGrid.getChildren().indexOf(oldSw);
            GridPane.setRowIndex(newSw, GridPane.getRowIndex(oldSw));
            GridPane.setColumnIndex(newSw, 0);
            statsGrid.getChildren().set(idx, newSw);
            starsByRow.put(row, newSw);
        }
        for (var entry : valLabels.entrySet()) {
            int row = entry.getKey();
            StatModifierService.Breakdown bd = ctx.statModifier().compute(b, STAT_ROWS[row].stat());
            Label val = entry.getValue();
            val.setText(String.valueOf(bd.finalValue()));
            val.setTooltip(!bd.contributions().isEmpty() ? TooltipFactory.forStatBreakdown(bd) : null);
        }
        for (var entry : potLabels.entrySet()) {
            int row = entry.getKey();
            Label pot = entry.getValue();
            StatPotentialCalculator.Potential p =
                    StatPotentialCalculator.compute(b, STAT_ROWS[row].stat());
            if (p.remainingLevels() == 0) {
                pot.setText(String.valueOf(p.finalPotential()));
                pot.setTooltip(null);
            } else {
                pot.setText(String.valueOf(p.finalPotential()));
                StatModifierService.Breakdown potBd = ctx.statModifier().computeWithBase(
                        b, STAT_ROWS[row].stat(), p.basePotential());
                pot.setTooltip(TooltipFactory.forStatPotential(p, potBd));
            }
        }
        refreshIncreaseUi();
        for (var entry : targetLabels.entrySet()) {
            applyTargetLabel(entry.getValue(), b, role, entry.getKey());
        }
        for (int i = 0; i < extraRoles.size(); i++) {
            applyTargetColumn(i);
        }
    }

    private void applyPersistentTargetColumn() {
        if (currentBrother == null) return;
        Role role = resolveRole(currentBrother);
        for (var entry : targetLabels.entrySet()) {
            applyTargetLabel(entry.getValue(), currentBrother, role, entry.getKey());
        }
    }

    private void onIncreaseChange(int canonIdx, int delta) {
        Brother b = currentBrother;
        if (b == null || b.fingerprint == null) return;

        if (b.levelTotal > 11) {
            int postLevels = b.levelTotal - 11;
            int[] post11 = ctx.annotation().get(b.fingerprint).post11Increases;
            post11 = post11 == null ? new int[STAT_ROWS.length] : Arrays.copyOf(post11, STAT_ROWS.length);
            int current = post11[canonIdx];
            int total   = Arrays.stream(post11).sum();
            int budget  = 3 * postLevels - total;
            if (delta > 0 && (current >= postLevels || budget <= 0)) return;
            if (delta < 0 && current <= 0) return;
            post11[canonIdx] = current + delta;
            ctx.annotation().setPost11Increases(b.fingerprint, post11);
        } else {
            int remaining = ExpectedStatsCalculator.remainingLevels(b);
            if (remaining == 0) return;
            int[] increases = ctx.annotation().get(b.fingerprint).statIncreases;
            increases = increases == null ? new int[STAT_ROWS.length] : Arrays.copyOf(increases, STAT_ROWS.length);
            int current = increases[canonIdx];
            int total   = Arrays.stream(increases).sum();
            int budget  = 3 * remaining - total;
            if (delta > 0 && (current >= remaining || budget <= 0)) return;
            if (delta < 0 && current <= 0) return;
            increases[canonIdx] = current + delta;
            ctx.annotation().setStatIncreases(b.fingerprint, increases);
        }
        refreshIncreaseUi();
    }

    private void refreshIncreaseUi() {
        Brother b = currentBrother;
        if (b == null) return;

        boolean isPost11   = b.levelTotal > 11;
        int remaining      = isPost11 ? 0 : ExpectedStatsCalculator.remainingLevels(b);
        int postLevels     = isPost11 ? (b.levelTotal - 11) : 0;
        int cap            = isPost11 ? postLevels : remaining;
        int totalBudgetMax = 3 * cap;

        int[] increases = b.fingerprint != null
                ? ctx.annotation().get(b.fingerprint).statIncreases : null;
        if (increases == null) increases = new int[STAT_ROWS.length];

        int[] post11inc = b.fingerprint != null
                ? ctx.annotation().get(b.fingerprint).post11Increases : null;
        if (post11inc == null) post11inc = new int[STAT_ROWS.length];

        int[] activeArr = isPost11 ? post11inc : increases;
        int total = Arrays.stream(activeArr).sum();
        int budget = totalBudgetMax - total;

        // Budget label: "-X/-Y" (grey/red) for post-11, "X/Y" (green/grey/red) for pre-11
        increasesRemainingLabel.getStyleClass().removeAll(
                "increases-remaining-green", "increases-remaining-red", "increases-remaining-post11");
        if (b.levelTotal == 11) {
            increasesRemainingLabel.setText("Lv 11");
        } else if (isPost11) {
            increasesRemainingLabel.setText("-" + total + "/-" + totalBudgetMax);
            if (budget < 0)      increasesRemainingLabel.getStyleClass().add("increases-remaining-red");
            else if (budget > 0) increasesRemainingLabel.getStyleClass().add("increases-remaining-green");
        } else {
            String budgetText = total + " / " + totalBudgetMax;
            if (b.levelPoints > 0) budgetText += "  (+" + b.levelPoints + " unassigned)";
            increasesRemainingLabel.setText(budgetText);
            if (budget < 0)      increasesRemainingLabel.getStyleClass().add("increases-remaining-red");
            else if (budget > 0) increasesRemainingLabel.getStyleClass().add("increases-remaining-green");
        }

        ExpectedStatsCalculator.Mode mode =
                ExpectedStatsCalculator.Mode.parse(AppConfig.getInstance().expectedStatsMode);

        final int[] post11Snap = post11inc;
        for (var entry : expectedLabels.entrySet()) {
            int canonIdx = entry.getKey();
            Label expLbl = entry.getValue();
            Stat stat    = STAT_ROWS[canonIdx].stat();

            int count = isPost11
                    ? (canonIdx < post11Snap.length ? post11Snap[canonIdx] : 0)
                    : (canonIdx < increases.length  ? increases[canonIdx]  : 0);

            ExpectedStatsCalculator.Expected expNaive =
                    ExpectedStatsCalculator.compute(b, stat, increases, post11Snap, ExpectedStatsCalculator.Mode.NAIVE);
            ExpectedStatsCalculator.Expected expGreedy =
                    ExpectedStatsCalculator.compute(b, stat, increases, post11Snap, ExpectedStatsCalculator.Mode.GREEDY);
            ExpectedStatsCalculator.Expected exp = (mode == ExpectedStatsCalculator.Mode.GREEDY)
                    ? expGreedy : expNaive;

            if (exp.remainingLevels() == 0) {
                expLbl.setText(String.valueOf(exp.finalExpected()));
            } else if (exp.count() == 0) {
                expLbl.setText("—");
            } else {
                expLbl.setText(String.valueOf(exp.finalExpected()));
            }
            expLbl.setTooltip(TooltipFactory.forStatExpected(expNaive, expGreedy, total));

            if (increaseCountLabels.containsKey(canonIdx)) {
                increaseCountLabels.get(canonIdx).setText(String.valueOf(count));
            }
            boolean editorDisabled = cap == 0;
            if (incPlusBtns.containsKey(canonIdx)) {
                incPlusBtns.get(canonIdx).setDisable(editorDisabled || count >= cap || budget <= 0);
            }
            if (incMinusBtns.containsKey(canonIdx)) {
                incMinusBtns.get(canonIdx).setDisable(count <= 0);
            }
        }

        if (autoAssignBtn != null) {
            autoAssignBtn.setDisable(cap == 0 || resolveRole(b) == null);
        }
    }

    private void applyTargetLabel(Label target, Brother b, Role role, int row) {
        target.getStyleClass().removeAll(
                "stat-target-met", "stat-target-potential", "stat-target-unmet",
                "stat-target-p2", "stat-target-p3");
        int tgt = (role != null && role.targetStats != null && row < role.targetStats.length)
                ? role.targetStats[row] : 0;
        if (tgt <= 0) {
            target.setText("");
            return;
        }
        // Priority weight: P1 = bold (default), P2 = normal, P3 = light
        int prio = (role.priority != null && row < role.priority.length) ? role.priority[row] : 2;
        if (prio == 2) target.getStyleClass().add("stat-target-p2");
        else if (prio == 3) target.getStyleClass().add("stat-target-p3");

        target.setText(String.valueOf(tgt));
        StatModifierService.Breakdown bd =
                ctx.statModifier().compute(b, STAT_ROWS[row].stat());
        if (bd.finalValue() >= tgt) {
            target.getStyleClass().add("stat-target-met");
        } else {
            StatPotentialCalculator.Potential pot =
                    StatPotentialCalculator.compute(b, STAT_ROWS[row].stat());
            target.getStyleClass().add(pot.finalPotential() >= tgt
                    ? "stat-target-potential" : "stat-target-unmet");
        }
    }

    // ---- multi-role extra columns ------------------------------------------

    /** Ensures exactly one null placeholder at the end of extraRoles if there is room. */
    private void ensurePlaceholder() {
        // Drop all trailing nulls, then add exactly one back if there is room
        while (!extraRoles.isEmpty() && extraRoles.get(extraRoles.size() - 1) == null) {
            extraRoles.remove(extraRoles.size() - 1);
        }
        if (extraRoles.size() < maxExtras) {
            extraRoles.add(null);
        }
    }

    private void rebuildExtraColumns() {
        ensurePlaceholder();
        extraTargetsByRow.clear();
        // Remove all extra nodes (userData still used for the removeIf predicate)
        statsGrid.getChildren().removeIf(n -> {
            Object tag = n.getUserData();
            if (tag == null) return false;
            String s = tag.toString();
            return s.startsWith("extra-target-") || s.startsWith("extra-combo-");
        });

        for (int i = 0; i < extraRoles.size(); i++) {
            int colIdx = FIRST_EXTRA_COL + i;
            final int extraIdx = i;
            Role initialRole = extraRoles.get(i);

            // Role name label shown below the combo
            Label roleNameLbl = new Label(initialRole != null ? initialRole.name : "");
            roleNameLbl.getStyleClass().add("field-label");
            roleNameLbl.setMaxWidth(Double.MAX_VALUE);
            roleNameLbl.setAlignment(Pos.CENTER_RIGHT);

            // Combo always shows "Choose" as button text; popup still lists role names.
            // setValue before addListener so the initial programmatic set does not fire the listener.
            ComboBox<Role> combo = buildRoleCombo();
            combo.setValue(initialRole);
            combo.setButtonCell(new ListCell<>() {
                @Override protected void updateItem(Role item, boolean empty) {
                    super.updateItem(item, empty);
                    setText("Choose");
                }
            });
            combo.setMaxWidth(EXTRA_COL_WIDTH);
            combo.setPrefWidth(EXTRA_COL_WIDTH);
            combo.valueProperty().addListener((obs, oldV, newV) -> {
                extraRoles.set(extraIdx, newV);
                rebuildExtraColumns();
            });

            boolean altBg = (i % 2 == 0);

            VBox header = new VBox(2, combo, roleNameLbl);
            header.setUserData("extra-combo-" + i);
            if (altBg) header.getStyleClass().add("stat-col-alt");
            GridPane.setRowIndex(header, STAT_HEADER_ROW);
            GridPane.setColumnIndex(header, colIdx);

            statsGrid.getChildren().add(header);

            // Target labels for each stat row — tracked in extraTargetsByRow map
            int[] displayOrder = computeStatDisplayOrder();
            Map<Integer, Label> colTargets = new HashMap<>();
            extraTargetsByRow.put(i, colTargets);
            for (int displayPos = 0; displayPos < displayOrder.length; displayPos++) {
                int canonIdx = displayOrder[displayPos];
                Label target = new Label();
                target.getStyleClass().add("stat-target");
                if (altBg) target.getStyleClass().add("stat-col-alt");
                target.setMinWidth(28);
                target.setPrefWidth(EXTRA_COL_WIDTH);
                target.setAlignment(Pos.CENTER_RIGHT);
                target.setUserData("extra-target-" + canonIdx + "-" + i);
                colTargets.put(canonIdx, target);
                GridPane.setRowIndex(target, FIRST_STAT_ROW + displayPos);
                GridPane.setColumnIndex(target, colIdx);
                statsGrid.getChildren().add(target);
            }

            applyTargetColumn(i);
        }
    }

    private void applyTargetColumn(int extraIdx) {
        if (currentBrother == null || extraIdx >= extraRoles.size()) return;
        Role role = extraRoles.get(extraIdx);
        Map<Integer, Label> targets = extraTargetsByRow.get(extraIdx);
        if (targets == null) return;
        for (var entry : targets.entrySet()) {
            applyTargetLabel(entry.getValue(), currentBrother, role, entry.getKey());
        }
    }

    // ---- width watcher -----------------------------------------------------

    private void setupWidthWatcher() {
        statsGrid.widthProperty().addListener((obs, ov, nv) -> recomputeMaxExtraColumns());
    }

    private void recomputeMaxExtraColumns() {
        double available = statsGrid.getWidth() - LEFT_SECTION_WIDTH;
        int newMax = Math.max(0, (int) (available / EXTRA_COL_WIDTH));
        if (newMax == maxExtras) return;
        maxExtras = newMax;
        while (extraRoles.size() > maxExtras) extraRoles.remove(extraRoles.size() - 1);
        rebuildExtraColumns();
    }

    // ---- button handlers ---------------------------------------------------

    private void setupRoleButtons() {
        clearRoleBtn.setOnAction(e -> roleCombo.setValue(null));
        autoAssignBtn.setOnAction(e -> {
            Brother b = currentBrother;
            if (b == null || b.fingerprint == null) return;
            Role role = resolveRole(b);
            if (role == null) return;
            if (b.levelTotal > 11) {
                int[] post11 = ExpectedStatsCalculator.autoAssignPost11ByRole(b, role);
                ctx.annotation().setPost11Increases(b.fingerprint, post11);
            } else {
                int[] increases = ExpectedStatsCalculator.autoAssignByRole(b, role);
                ctx.annotation().setStatIncreases(b.fingerprint, increases);
            }
            refreshIncreaseUi();
        });
        autoAssignBtn.setDisable(true);
        resetRolesBtn.setOnAction(e -> { extraRoles.clear(); rebuildExtraColumns(); });
        showAllRolesBtn.setOnAction(e -> setExtras(ctx.roles().getAll()));
        frontlineRolesBtn.setOnAction(e -> setExtras(
                ctx.roles().getAll().stream().filter(r -> r.frontline).toList()));
        backlineRolesBtn.setOnAction(e -> setExtras(
                ctx.roles().getAll().stream().filter(r -> !r.frontline).toList()));
    }

    private void setExtras(List<Role> roles) {
        Role persistent = roleCombo.getValue();
        List<Role> filtered = roles.stream()
                .filter(r -> persistent == null || !r.id.equals(persistent.id))
                .limit(maxExtras)
                .toList();
        extraRoles.clear();
        extraRoles.addAll(filtered);
        rebuildExtraColumns();
    }

    private void updatePerkOpacity(Brother b) {
        Set<String> owned = Set.copyOf(b.perkIds);
        for (var node : perksPane.getChildren()) {
            Object tag = node.getUserData();
            if (tag == null || !tag.toString().startsWith("perk-")) continue;
            String hexId = tag.toString().substring(5);
            node.setOpacity(owned.contains(hexId) ? 1.0 : 0.3);
        }
    }

    private void buildEquipmentPane(Brother b) {
        equipmentPane.getChildren().clear();
        if (b.equippedSlots == null) return;

        for (int i = 0; i < Math.min(b.equippedSlots.length, SLOT_NAMES.length); i++) {
            InventorySlot slot = b.equippedSlots[i];
            VBox cell = buildEquipmentCell(slot, SLOT_NAMES[i]);
            equipmentPane.getChildren().add(cell);
        }
    }

    private VBox buildEquipmentCell(InventorySlot slot, String slotName) {
        Image img;
        if (slot == null || slot.empty || slot.itemId == null) {
            img = ctx.imageCache().getPlaceholder();
        } else {
            int icon = slot.stats != null ? slot.stats.icon : 0;
            String iconSet = slot.stats != null ? slot.stats.iconSet : null;
            String rel = ctx.imageMap().resolve(slotName, slot.itemId, icon, iconSet);
            Path root = ctx.appConfig().gameArtRoot();
            img = (rel != null && root != null)
                    ? ctx.imageCache().get(root, rel)
                    : ctx.imageCache().getPlaceholder();
        }

        ImageView iv = new ImageView(img);
        iv.setFitWidth(64);
        iv.setFitHeight(64);
        iv.setPreserveRatio(true);

        if (slot != null && !slot.empty) {
            Tooltip tt = TooltipFactory.forSlot(slot);
            if (tt != null) Tooltip.install(iv, tt);
        }

        Label lbl = new Label(slotName);
        lbl.getStyleClass().add("equip-slot-label");

        VBox cell = new VBox(4, iv, lbl);
        cell.setAlignment(Pos.TOP_CENTER);
        cell.getStyleClass().add("equip-cell");
        return cell;
    }

    // ---- helpers ------------------------------------------------------------

    private void setupRoleCombo() {
        roleCombo.setConverter(new StringConverter<>() {
            @Override public String toString(Role r) { return r != null ? r.name : ""; }
            @Override public Role fromString(String s) { return null; }
        });
        roleCombo.getStyleClass().add("role-combo");
        roleCombo.setPromptText("assign a role…");
        roleCombo.getItems().setAll(ctx.roles().getAll());
        roleCombo.valueProperty().addListener((obs, oldRole, newRole) -> {
            persistentRoleHeader.setText(newRole != null ? newRole.name : "");
            applyPersistentTargetColumn();
            Brother b = currentBrother;
            if (b != null && b.fingerprint != null) {
                ctx.annotation().setRole(b.fingerprint, newRole != null ? newRole.id : null);
            }
        });
    }

    private ComboBox<Role> buildRoleCombo() {
        ComboBox<Role> combo = new ComboBox<>();
        combo.setConverter(new StringConverter<>() {
            @Override public String toString(Role r) { return r != null ? r.name : ""; }
            @Override public Role fromString(String s) { return null; }
        });
        combo.getStyleClass().add("role-combo");
        combo.setPromptText("role…");
        combo.getItems().setAll(ctx.roles().getAll());
        return combo;
    }

    private Role resolveRole(Brother b) {
        if (b == null || b.fingerprint == null) return null;
        String roleId = ctx.annotation().get(b.fingerprint).roleId;
        return roleId != null ? ctx.roles().getById(roleId) : null;
    }

    private Image loadPortrait(Brother b) {
        String rel = ctx.imageMap().resolveHex(b.backgroundHexId);
        Path root = ctx.appConfig().gameArtRoot();
        if (rel == null || root == null) return ctx.imageCache().getPlaceholder();
        return ctx.imageCache().get(root, rel);
    }

    private Image loadHexImage(String hexId) {
        String rel = ctx.imageMap().resolveHex(hexId);
        Path root = ctx.appConfig().gameArtRoot();
        if (rel == null || root == null) return unknownTraitImage();
        return ctx.imageCache().get(root, rel);
    }

    private Image unknownTraitImage() {
        try (var is = getClass().getResourceAsStream(
                "/se/niclas/broledger/assets/unknown-trait.png")) {
            return is != null ? new Image(is) : ctx.imageCache().getPlaceholder();
        } catch (Exception e) {
            return ctx.imageCache().getPlaceholder();
        }
    }

    private static ImageView loadStatIcon(String filename) {
        try (var is = BrotherCardController.class.getResourceAsStream(
                "/se/niclas/broledger/assets/" + filename)) {
            if (is != null) return new ImageView(new Image(is));
        } catch (Exception ignored) {}
        return new ImageView();
    }
}
