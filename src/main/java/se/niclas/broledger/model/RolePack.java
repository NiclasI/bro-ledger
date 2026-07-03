package se.niclas.broledger.model;

import java.util.ArrayList;
import java.util.List;

/** A shareable collection of roles, published to and fetched from the role-share server. */
public class RolePack {

    /** Highest pack schema this build can understand; fetched packs above it are rejected. */
    public static final int SCHEMA_VERSION = 1;

    public int         schemaVersion = SCHEMA_VERSION;
    public String      label;
    public List<Role>  roles = new ArrayList<>();

    // Set during sanitization when roles beyond the cap were dropped. Private with
    // non-bean accessors so Jackson never serializes it.
    private boolean truncated;

    public RolePack() {}

    public RolePack(String label, List<Role> roles) {
        this.label = label;
        this.roles = roles != null ? roles : new ArrayList<>();
    }

    /**
     * Builds a pack for publishing. Roles are copied — keeping their catalogue ids, which
     * recipients record as provenance so a re-import of the same code updates in place —
     * but the local provenance fields are stripped, so the code of a pack the sender once
     * imported from never leaks into a pack they publish.
     */
    public static RolePack forSharing(String label, List<Role> roles) {
        List<Role> copies = new ArrayList<>();
        if (roles != null) {
            for (Role src : roles) {
                if (src == null) continue;
                Role copy = new Role();
                copy.id = src.id;
                Role.copyContentInto(src, copy);
                copies.add(copy);
            }
        }
        return new RolePack(label, copies);
    }

    public boolean truncated() { return truncated; }

    public void markTruncated() { this.truncated = true; }
}
