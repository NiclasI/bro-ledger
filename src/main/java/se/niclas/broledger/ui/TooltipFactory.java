package se.niclas.broledger.ui;

import javafx.geometry.Pos;
import javafx.scene.control.Label;
import javafx.scene.control.Tooltip;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import se.niclas.broledger.model.DictionaryEntry;
import se.niclas.broledger.model.InventorySlot;
import se.niclas.broledger.model.ItemStats;
import se.niclas.broledger.model.StatModifier;
import se.niclas.broledger.service.DictionaryService;
import se.niclas.broledger.service.ExpectedStatsCalculator;
import se.niclas.broledger.service.StatModifierService;
import se.niclas.broledger.service.StatPotentialCalculator;
import se.niclas.broledger.service.WeaponStatsService;
import se.niclas.broledger.model.WeaponStats;

/** Builds dark-styled tooltips for all displayable item types. */
public class TooltipFactory {

    private static final Map<se.niclas.broledger.model.Stat, String> STAT_ICONS;
    static {
        STAT_ICONS = new EnumMap<>(se.niclas.broledger.model.Stat.class);
        STAT_ICONS.put(se.niclas.broledger.model.Stat.HEALTH,         "icon-health.png");
        STAT_ICONS.put(se.niclas.broledger.model.Stat.RESOLVE,        "icon-resolve.png");
        STAT_ICONS.put(se.niclas.broledger.model.Stat.FATIGUE,        "icon-fatigue.png");
        STAT_ICONS.put(se.niclas.broledger.model.Stat.INITIATIVE,     "icon-initiative.png");
        STAT_ICONS.put(se.niclas.broledger.model.Stat.MELEE_SKILL,    "icon-mSkill.png");
        STAT_ICONS.put(se.niclas.broledger.model.Stat.RANGED_SKILL,   "icon-rSkill.png");
        STAT_ICONS.put(se.niclas.broledger.model.Stat.MELEE_DEFENSE,  "icon-mDefense.png");
        STAT_ICONS.put(se.niclas.broledger.model.Stat.RANGED_DEFENSE, "icon-rDefense.png");
    }

    /** Tooltip for an equipped item slot. Returns null for empty slots. */
    public static Tooltip forSlot(InventorySlot slot) {
        if (slot == null || slot.empty || slot.itemId == null) return null;
        DictionaryService dict = DictionaryService.getInstance();
        String name = dict.getName(slot.itemId);
        ItemStats s = slot.stats;

        if (s != null && s.name != null && !s.name.isBlank()) name = s.name;

        String body;
        String type = slot.itemType;
        if (isWeaponType(type)) {
            body = weaponText(name, s, slot.itemId, type);
        } else if (isArmorType(type)) {
            body = armorText(name, s, dict);
        } else if (isShieldType(type)) {
            body = shieldText(name, s);
        } else {
            body = name;
        }
        return styled(body);
    }

    /** Tooltip for a perk, trait, or background (hex ID lookup). */
    public static Tooltip forHex(String hexId) {
        if (hexId == null) return null;
        String name = DictionaryService.getInstance().getName(hexId);
        StatModifier mod = StatModifierService.getInstance().byHexId(hexId);
        if (mod != null && mod.description != null && !mod.description.isBlank()) {
            return styled(name + "\n" + mod.description);
        }
        return styled(name);
    }

    /** Tooltip showing a stat's base value, modifiers, and final result. */
    public static Tooltip forStatBreakdown(StatModifierService.Breakdown bd) {
        StringBuilder sb = new StringBuilder();
        appendBreakdown(sb, bd);
        return styled(sb.toString());
    }

    private static void appendBreakdown(StringBuilder sb, StatModifierService.Breakdown bd) {
        sb.append("Base: ").append(bd.base());
        appendContributions(sb, bd.contributions());
        sb.append("\n= ").append(bd.finalValue());
    }

    private static void appendContributions(StringBuilder sb,
                                            List<StatModifierService.Contribution> contributions) {
        for (StatModifierService.Contribution c : contributions) {
            sb.append("\n").append(c.name()).append(": ");
            if (c.points()     != null) sb.append(c.points() > 0 ? "+" : "").append(c.points());
            if (c.percentage() != null) sb.append(c.percentage() > 0 ? "+" : "").append(c.percentage()).append("%");
        }
    }

    private static void appendPotentialBlock(StringBuilder sb,
                                             StatPotentialCalculator.Potential pot,
                                             StatModifierService.Breakdown bd) {
        if (pot.remainingLevels() == 0) {
            sb.append("\n(already at max level)");
        } else {
            sb.append("\nBase: ").append(pot.basePotential());
            appendContributions(sb, bd.contributions());
            sb.append("\n= ").append(pot.finalPotential());
            sb.append("\nRange: ").append(pot.minBasePotential())
              .append("–").append(pot.maxBasePotential());
            sb.append("\n(").append(pot.remainingLevels()).append(" rolls remaining)");
        }
    }

    /**
     * Tooltip combining a stat's current breakdown with its potential at level 11.
     * Always shown (replaces the prior "only when contributions are non-empty" guard).
     */
    public static Tooltip forStatBreakdownWithPotential(
            StatModifierService.Breakdown bd,
            StatPotentialCalculator.Potential pot) {
        StringBuilder sb = new StringBuilder();
        appendBreakdown(sb, bd);
        sb.append("\n\nPotential at lv 11");
        appendPotentialBlock(sb, pot, bd);
        return styled(sb.toString());
    }

    /** Tooltip for a potential value label in the brother card. */
    public static Tooltip forStatPotential(StatPotentialCalculator.Potential pot,
                                           StatModifierService.Breakdown bd) {
        StringBuilder sb = new StringBuilder("Potential at lv 11");
        appendPotentialBlock(sb, pot, bd);
        return styled(sb.toString());
    }

    /**
     * Tooltip combining a stat's current breakdown with expected projection details.
     * Shows both naive and greedy values; greedy is shown only when the allocation is complete.
     */
    public static Tooltip forStatBreakdownWithExpected(
            StatModifierService.Breakdown bd,
            ExpectedStatsCalculator.Expected naive,
            ExpectedStatsCalculator.Expected greedy,
            int usedBudget) {
        StringBuilder sb = new StringBuilder();
        appendBreakdown(sb, bd);
        sb.append("\n\nExpected at lv 11");
        appendExpectedBlock(sb, naive, greedy, usedBudget);
        return styled(sb.toString());
    }

    /** Tooltip for an expected value label in the brother card (no current-stat breakdown prefix). */
    public static Tooltip forStatExpected(
            ExpectedStatsCalculator.Expected naive,
            ExpectedStatsCalculator.Expected greedy,
            int usedBudget) {
        StringBuilder sb = new StringBuilder("Expected at lv 11");
        appendExpectedBlock(sb, naive, greedy, usedBudget);
        return styled(sb.toString());
    }

    /**
     * Graphical tooltip for the level cell showing the roll-priority guide.
     * Uses stat icons and names for readability.
     * Call when the brother's allocation is complete.
     *
     * @param entries  structured guide from {@link ExpectedStatsCalculator#rollPriorityGuideEntries}
     * @param remaining remaining level-ups
     */
    public static Tooltip forLevelPriorityGuide(
            List<ExpectedStatsCalculator.PriorityEntry> entries, int remaining) {
        VBox root = new VBox(4);
        root.getStyleClass().add("priority-guide-tooltip");

        Label header = new Label("Roll Priority Guide  (" + remaining + " levels remaining)");
        header.getStyleClass().add("priority-guide-header");
        root.getChildren().add(header);

        boolean hadFixed = false;
        boolean hadFlex  = false;
        for (ExpectedStatsCalculator.PriorityEntry e : entries) {
            if (e.fixed() && !hadFixed) {
                Label sec = new Label("Always pick:");
                sec.getStyleClass().add("priority-guide-section");
                root.getChildren().add(sec);
                hadFixed = true;
            }
            if (!e.fixed() && !hadFlex) {
                Label sec = new Label("Pick by roll outcome (higher = better):");
                sec.getStyleClass().add("priority-guide-section");
                root.getChildren().add(sec);
                hadFlex = true;
            }
            HBox row = new HBox(6);
            row.setAlignment(Pos.CENTER_LEFT);
            ImageView icon = statIcon(e.stat());
            Label name = new Label(e.stat().displayName());
            name.getStyleClass().add("priority-guide-name");
            row.getChildren().addAll(icon, name);
            if (!e.fixed()) {
                Label roll = new Label(String.valueOf(e.rollValue()));
                roll.getStyleClass().add("priority-guide-roll");
                Label dev = new Label(String.format("%+.1f", e.deviation()));
                dev.getStyleClass().add(e.deviation() >= 0 ? "priority-guide-dev-pos"
                                                            : "priority-guide-dev-neg");
                row.getChildren().addAll(roll, dev);
            }
            root.getChildren().add(row);
        }

        Tooltip t = new Tooltip();
        t.getStyleClass().add("item-tooltip");
        t.setGraphic(root);
        return t;
    }

    /**
     * Plain-text tooltip for the level cell when allocation is incomplete.
     */
    public static Tooltip forLevelAllocationIncomplete(int usedBudget, int totalBudget) {
        return styled("Assign all increases to see\nthe roll priority guide.\n("
                + usedBudget + " / " + totalBudget + " used)");
    }

    private static ImageView statIcon(se.niclas.broledger.model.Stat stat) {
        String file = STAT_ICONS.get(stat);
        ImageView iv = new ImageView();
        if (file != null) {
            try {
                var stream = TooltipFactory.class.getResourceAsStream(
                        "/se/niclas/broledger/assets/" + file);
                if (stream != null) iv.setImage(new Image(stream));
            } catch (Exception ignored) {}
        }
        iv.setFitWidth(16);
        iv.setFitHeight(16);
        iv.setPreserveRatio(true);
        return iv;
    }

    private static void appendExpectedBlock(StringBuilder sb,
                                             ExpectedStatsCalculator.Expected naive,
                                             ExpectedStatsCalculator.Expected greedy,
                                             int usedBudget) {
        int remaining   = naive.remainingLevels();
        int totalBudget = 3 * remaining;
        int count       = naive.count();

        if (remaining == 0) {
            if (count == 0) {
                sb.append("\n(lv11 baseline — no post-lv11 gains on this stat)");
            } else {
                sb.append("\n−").append(count).append(" post-lv11 increase")
                  .append(count == 1 ? "" : "s");
                sb.append("\n= ").append(naive.finalExpected());
            }
            return;
        }
        if (count == 0) {
            sb.append("\nNo increases allotted");
            sb.append("\nAvailable: ").append(totalBudget - usedBudget)
              .append(" / ").append(totalBudget);
        } else {
            sb.append("\nAllotted: ").append(count).append(" / ").append(remaining).append(" max");
            appendExpectedRow(sb, "Naive ", naive);
            if (usedBudget == totalBudget) {
                if (greedy.finalExpected() == naive.finalExpected()) {
                    sb.append("\nGreedy: same");
                } else {
                    appendExpectedRow(sb, "Greedy", greedy);
                }
            } else {
                sb.append("\nGreedy: n/a (allocation incomplete)");
            }
            sb.append("\nBudget: ").append(usedBudget).append(" / ").append(totalBudget).append(" used");
        }
        sb.append("\n(").append(remaining).append(" levels remaining)");
    }

    private static void appendExpectedRow(StringBuilder sb, String label,
                                           ExpectedStatsCalculator.Expected exp) {
        sb.append("\n").append(label).append(": ");
        if (exp.baseExpected() != exp.finalExpected()) {
            sb.append("base ").append(exp.baseExpected())
              .append(" → ").append(exp.finalExpected());
        } else {
            sb.append(exp.finalExpected());
        }
    }

    // ---- private builders ----

    private static String weaponText(String name, ItemStats s, String hexId, String itemType) {
        StringBuilder sb = new StringBuilder(name);
        if (s != null) {
            if (s.damageMin > 0 || s.damageMax > 0)
                sb.append("\nDamage: ").append(s.damageMin).append(" – ").append(s.damageMax);
            if (s.damageArmor > 0)
                sb.append("\nArmor%: ").append(pct(s.damageArmor));
            if (s.penetration > 0)
                sb.append("\nPenetration: ").append(pct(s.penetration));
            if (s.fatigueUse > 0)
                sb.append("\nFatigue: ").append(s.fatigueUse);
            if (s.hitBonus != 0)
                sb.append("\nHit: ").append(s.hitBonus > 0 ? "+" : "").append(s.hitBonus);
            if (s.headChance > 0)
                sb.append("\nHead%: ").append(s.headChance);
        }
        if ("namedWeapon".equals(itemType)) {
            sb.append("\nNamed weapon");
        } else {
            WeaponStats ws = WeaponStatsService.getInstance().get(hexId);
            if (ws != null && ws.weaponClass != null) {
                sb.append("\nClass: ").append(ws.weaponClass);
                if (ws.tier != null) sb.append("\nTier: ").append(ws.tier);
            }
        }
        return sb.toString();
    }

    private static String armorText(String name, ItemStats s, DictionaryService dict) {
        if (s == null) return name;
        StringBuilder sb = new StringBuilder(name);
        if (s.durability > 0)
            sb.append("\nDurability: ").append((int) s.durability);
        if (s.fatigue != 0)
            sb.append("\nFatigue: ").append((int) s.fatigue);
        if (s.hasAttachment()) {
            DictionaryEntry att = dict.get(s.attachment);
            if (att != null) sb.append("\n+ ").append(att.name);
        }
        return sb.toString();
    }

    private static String shieldText(String name, ItemStats s) {
        if (s == null) return name;
        StringBuilder sb = new StringBuilder(name);
        if (s.durability > 0)
            sb.append("\nDurability: ").append((int) s.durability);
        if (s.mDef != 0)
            sb.append("\nMelee Def: +").append(s.mDef);
        if (s.rDef != 0)
            sb.append("\nRanged Def: +").append(s.rDef);
        if (s.fatigueUse > 0)
            sb.append("\nFatigue: ").append(s.fatigueUse);
        return sb.toString();
    }

    private static Tooltip styled(String text) {
        Tooltip t = new Tooltip(text != null ? text : "");
        t.getStyleClass().add("item-tooltip");
        return t;
    }

    private static String pct(double v) {
        return String.format("%.0f%%", v * 100);
    }

    private static boolean isWeaponType(String t) {
        return t != null && t.toLowerCase().contains("weapon");
    }

    private static boolean isArmorType(String t) {
        return t != null && (t.toLowerCase().contains("armor") || t.toLowerCase().contains("helmet"));
    }

    private static boolean isShieldType(String t) {
        return t != null && t.toLowerCase().contains("shield");
    }
}
