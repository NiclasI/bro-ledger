package se.niclas.broledger.model;

public class InventorySlot {
    public final int slotIndex;
    public boolean empty = true;
    public String itemId;       // uppercase 8-char hex
    public String itemType;     // from dictionary: genericWeapon, namedArmor, etc.
    public ItemStats stats;

    public InventorySlot(int slotIndex) {
        this.slotIndex = slotIndex;
    }

    public static InventorySlot empty(int slotIndex) {
        return new InventorySlot(slotIndex);
    }
}
