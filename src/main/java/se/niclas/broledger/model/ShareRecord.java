package se.niclas.broledger.model;

/**
 * One entry in the local "recent uploads" history ({@code AppConfig.recentShares}):
 * a pack published from this machine, plus the owner token that authorizes
 * updating it on the server. The token is a machine-local secret — it is never
 * part of a {@link RolePack} and never shown to recipients of the code.
 */
public class ShareRecord {

    public String code;
    /** Server-issued update secret; null when the server didn't return one. */
    public String ownerToken;
    /** The pack's title ({@code RolePack.label}) at publish/update time; may be null. */
    public String title;
    /** ISO-8601 instant of the most recent publish or update. */
    public String uploadedAt;
    public int    roleCount;

    public ShareRecord() {}

    public ShareRecord(String code, String ownerToken, String title, String uploadedAt, int roleCount) {
        this.code = code;
        this.ownerToken = ownerToken;
        this.title = title;
        this.uploadedAt = uploadedAt;
        this.roleCount = roleCount;
    }

    /** True when this entry carries the secret needed to update its code on the server. */
    public boolean updatable() {
        return ownerToken != null && !ownerToken.isEmpty();
    }
}
