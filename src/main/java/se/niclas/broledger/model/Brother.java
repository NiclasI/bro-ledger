package se.niclas.broledger.model;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class Brother {

    // Visual layers from save (key = layer name, value = raw hex data)
    public Map<String, VisualLayer> visualLayerMap = new LinkedHashMap<>();

    // Header blob (opaque hex, kept for potential future use)
    public String header;

    // 8 base stats, read in statOrder:
    // [0]=health [1]=resolve [2]=fatigue [3]=meleeSkill [4]=rangedSkill
    // [5]=meleeDefense [6]=rangedDefense [7]=initiative
    public int[] stats = new int[8];

    // 8 talent stars, read in talentOrder:
    // [0]=Health [1]=Resolve [2]=Fatigue [3]=Initiative [4]=MeleeSkill
    // [5]=RangedSkill [6]=MeleeDefense [7]=RangedDefense
    public int[] stars = new int[8];

    // Raw talent allocation bytes per stat (hex strings), same talentOrder indexing
    public String[] talentBytes = new String[8];

    public int talentPoints;

    public int actionPoints;

    // 7 fixed equipment slots: [0]=weapon [1]=shield [2]=body [3]=helmet
    //                           [4]=trinket [5]=quiver [6]=pouch
    public InventorySlot[] equippedSlots = new InventorySlot[7];

    // Extra pouch slots (from Bags & Belts perk)
    public List<InventorySlot> extraPouches = new ArrayList<>();

    public int pouchCount; // 2 or 4

    public String backgroundHexId; // uppercase 8-char hex

    public List<String> perkIds = new ArrayList<>();
    public List<TraitEntry> traits = new ArrayList<>();

    public String name;
    public String title;
    public String description;
    public String descriptionTemplate;

    public int experience;
    public int levelTotal;
    public int perkPoints;
    public int perkUsed;
    // Each levelPoint = one whole unassigned level-up (3 stat increases, not yet applied to stats).
    public int levelPoints;

    public float morale;
    public float lightWound;
    public float salaryMultiplier;

    // Opaque blobs
    public String greedAndGluttony; // 6-byte raw hex
    public String unknown;          // 2-byte raw hex
    public String humanBytes;       // 6-byte raw hex
    public String moraleModifierHex;
    public String tattoo;           // 1-byte raw hex, wildman/barbarian only

    // Computed after parse
    public String fingerprint;

    public static class VisualLayer {
        public final String value;      // 12-byte hex
        public final String separator;  // 53-byte hex

        public VisualLayer(String value, String separator) {
            this.value = value;
            this.separator = separator;
        }
    }

    public Brother() {
        for (int i = 0; i < 7; i++) {
            equippedSlots[i] = InventorySlot.empty(i);
        }
        for (int i = 0; i < 8; i++) {
            talentBytes[i] = "";
        }
    }
}
