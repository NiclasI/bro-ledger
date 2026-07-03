package se.niclas.broledger.ui;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Label;
import javafx.scene.control.Tooltip;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.input.MouseButton;
import javafx.scene.layout.HBox;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.shape.Circle;
import se.niclas.broledger.model.DictionaryEntry;
import se.niclas.broledger.model.PerkPlanStatus;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.function.Consumer;

/**
 * Shared perk plan editing component used in both the brother detail card and the Role Manager.
 * Shows all perks grouped by tier row (alphabetical within tier). LMB click cycles
 * NOT → PLANNED → OPTIONAL → NOT. Fires a change callback on each edit.
 *
 * In the brother card context, call setOwnedPerks() to apply owned/not-owned opacity.
 * In the Role Manager context, skip setOwnedPerks() — all icons display at full opacity.
 */
public class PerkPlanPane extends VBox {

    private static final double ICON_SIZE   = 40;
    private static final double CIRCLE_R    = 23;
    private static final double SLOT_SIZE   = 50;

    private final UiContext ctx;
    private Consumer<Map<String, String>> onChange;

    private Map<String, String> planStatus = new LinkedHashMap<>();
    private Set<String> ownedPerks = null; // null = no ownership context (Role Manager)

    private final Map<String, StackPane> slots = new LinkedHashMap<>();

    public PerkPlanPane(UiContext ctx) {
        this.ctx = ctx;
        setSpacing(2);
        build();
    }

    public void setOnChange(Consumer<Map<String, String>> cb) {
        this.onChange = cb;
    }

    /** Replace the current plan and refresh all slot visuals. */
    public void setStatus(Map<String, String> status) {
        this.planStatus = status != null ? new LinkedHashMap<>(status) : new LinkedHashMap<>();
        refreshAll();
    }

    /**
     * Apply owned-state opacity (1.0 owned / 0.3 not owned).
     * Call on brother change; do not call in Role Manager context.
     */
    public void setOwnedPerks(Set<String> owned) {
        this.ownedPerks = owned;
        refreshAllOpacity();
    }

    /** Current plan map (unmodifiable snapshot). */
    public Map<String, String> getStatus() {
        return Collections.unmodifiableMap(planStatus);
    }

    // ---- build (once) -------------------------------------------------------

    private void build() {
        // Group all perks by tier, alphabetical within tier
        List<Map.Entry<String, DictionaryEntry>> allPerks = ctx.dict().getAllByType("perk");
        TreeMap<Integer, List<Map.Entry<String, DictionaryEntry>>> byTier = new TreeMap<>();
        for (Map.Entry<String, DictionaryEntry> e : allPerks) {
            Integer tier = ctx.statModifier().getTier(e.getKey());
            int t = (tier != null) ? tier : 99;
            byTier.computeIfAbsent(t, k -> new ArrayList<>()).add(e);
        }
        Comparator<Map.Entry<String, DictionaryEntry>> byName = Comparator.comparing(e -> {
            String n = e.getValue().name;
            return n != null ? n.toLowerCase() : "";
        });
        for (Map.Entry<Integer, List<Map.Entry<String, DictionaryEntry>>> tierEntry : byTier.entrySet()) {
            List<Map.Entry<String, DictionaryEntry>> list = tierEntry.getValue();
            if (tierEntry.getKey() == 4) {
                list.sort(Comparator.<Map.Entry<String, DictionaryEntry>>comparingInt(
                        e -> OverviewCalc.masteryOrderRank(e.getValue().name)).thenComparing(byName));
            } else {
                list.sort(byName);
            }
        }

        // Build one HBox row per tier
        for (Map.Entry<Integer, List<Map.Entry<String, DictionaryEntry>>> tierEntry : byTier.entrySet()) {
            int tier = tierEntry.getKey();
            HBox row = new HBox(2);
            row.setAlignment(Pos.CENTER_LEFT);

            Label tierLbl = new Label(tier == 99 ? "?" : "T" + tier);
            tierLbl.getStyleClass().add("perk-tier-label");
            tierLbl.setMinWidth(26);
            tierLbl.setAlignment(Pos.CENTER_RIGHT);
            row.getChildren().add(tierLbl);

            for (Map.Entry<String, DictionaryEntry> pe : tierEntry.getValue()) {
                String hexId = pe.getKey();
                StackPane slot = buildSlot(hexId);
                slots.put(hexId, slot);
                row.getChildren().add(slot);
            }

            VBox.setMargin(row, new Insets(1, 0, 1, 0));
            getChildren().add(row);
        }
    }

    private StackPane buildSlot(String hexId) {
        Circle circle = new Circle(CIRCLE_R);
        circle.getStyleClass().add("perk-status-circle");
        circle.setVisible(false);

        ImageView iv = new ImageView(loadHexImage(ctx, hexId));
        iv.setFitWidth(ICON_SIZE);
        iv.setFitHeight(ICON_SIZE);
        iv.setPreserveRatio(true);
        // Default: full opacity (no ownership context). setOwnedPerks() corrects this for brother card.
        iv.setOpacity(1.0);

        StackPane sp = new StackPane(circle, iv);
        sp.setPrefSize(SLOT_SIZE, SLOT_SIZE);
        sp.setMinSize(SLOT_SIZE, SLOT_SIZE);
        sp.setMaxSize(SLOT_SIZE, SLOT_SIZE);
        sp.setAlignment(Pos.CENTER);
        sp.setStyle("-fx-cursor: hand;");

        Tooltip.install(sp, TooltipFactory.forHex(hexId));

        sp.setOnMouseClicked(e -> {
            if (e.getButton() != MouseButton.PRIMARY) return;
            PerkPlanStatus current = PerkPlanStatus.fromString(planStatus.get(hexId));
            PerkPlanStatus next = current.next();
            if (next == PerkPlanStatus.NOT) {
                planStatus.remove(hexId);
            } else {
                planStatus.put(hexId, next.name());
            }
            applySlotStatus(hexId);
            if (onChange != null) onChange.accept(Collections.unmodifiableMap(planStatus));
        });

        return sp;
    }

    // ---- refresh ------------------------------------------------------------

    private void refreshAll() {
        for (String hexId : slots.keySet()) {
            applySlotStatus(hexId);
        }
    }

    private void refreshAllOpacity() {
        for (Map.Entry<String, StackPane> entry : slots.entrySet()) {
            applyOpacity(entry.getKey(), entry.getValue());
        }
    }

    private void applySlotStatus(String hexId) {
        StackPane sp = slots.get(hexId);
        if (sp == null) return;
        PerkPlanStatus status = PerkPlanStatus.fromString(planStatus.get(hexId));
        Circle circle = (Circle) sp.getChildren().get(0);
        circle.getStyleClass().removeAll("perk-status-optional", "perk-status-planned");
        switch (status) {
            case NOT -> circle.setVisible(false);
            case OPTIONAL -> {
                circle.setVisible(true);
                circle.getStyleClass().add("perk-status-optional");
            }
            case PLANNED -> {
                circle.setVisible(true);
                circle.getStyleClass().add("perk-status-planned");
            }
        }
        applyOpacity(hexId, sp);
    }

    private void applyOpacity(String hexId, StackPane sp) {
        if (ownedPerks == null) return; // no ownership context — leave at 1.0
        ImageView iv = (ImageView) sp.getChildren().get(1);
        iv.setOpacity(ownedPerks.contains(hexId) ? 1.0 : 0.3);
    }

    // ---- shared static helpers (used by TooltipFactory for the popup) ------

    /**
     * Load the icon image for a perk hex ID, falling back to the unknown-trait placeholder.
     * Package-private so TooltipFactory can build popup icons lazily.
     */
    static Image loadHexImage(UiContext ctx, String hexId) {
        String rel = ctx.imageMap().resolveHex(hexId);
        Path root = ctx.appConfig().gameArtRoot();
        if (rel != null && root != null) return ctx.imageCache().get(root, rel);
        try (var is = PerkPlanPane.class.getResourceAsStream(
                "/se/niclas/broledger/assets/unknown-trait.png")) {
            return is != null ? new Image(is) : ctx.imageCache().getPlaceholder();
        } catch (Exception e) {
            return ctx.imageCache().getPlaceholder();
        }
    }

    /**
     * Build a display-only icon StackPane: status ring (circle) + perk image.
     * Package-private so TooltipFactory can call it when building the guide popup.
     *
     * @param iconSize  ImageView fit width/height
     * @param circleR   Circle radius
     * @param slotSize  StackPane pref/min/max size
     */
    static StackPane buildStatusIcon(UiContext ctx, String hexId, PerkPlanStatus status,
                                     double iconSize, double circleR, double slotSize) {
        Circle circle = new Circle(circleR);
        circle.getStyleClass().add("perk-status-circle");
        switch (status) {
            case PLANNED  -> circle.getStyleClass().add("perk-status-planned");
            case OPTIONAL -> circle.getStyleClass().add("perk-status-optional");
            default       -> circle.setVisible(false);
        }

        ImageView iv = new ImageView(loadHexImage(ctx, hexId));
        iv.setFitWidth(iconSize);
        iv.setFitHeight(iconSize);
        iv.setPreserveRatio(true);

        StackPane sp = new StackPane(circle, iv);
        sp.setPrefSize(slotSize, slotSize);
        sp.setMinSize(slotSize, slotSize);
        sp.setMaxSize(slotSize, slotSize);
        sp.setAlignment(Pos.CENTER);
        return sp;
    }
}
