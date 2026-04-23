package se.niclas.broledger.model;

public class TraitEntry {
    public final String id;
    public final String eventData; // nullable; raw hex for injury/training/knowledge/learning

    public TraitEntry(String id, String eventData) {
        this.id = id;
        this.eventData = eventData;
    }
}
