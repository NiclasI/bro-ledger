package se.niclas.broledger.service;

import se.niclas.broledger.model.Role;
import se.niclas.broledger.model.RolePack;
import se.niclas.broledger.util.AppPaths;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.logging.Logger;

// Role display order is persisted in AppConfig.roleOrder (list of UUIDs).

/**
 * Manages the global role catalogue, persisted to ~/.bro-ledger/roles.json.
 * Roles are universal across save files.
 */
public class RoleService {

    private static final ObjectMapper MAPPER = JsonMapper.builder()
            .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
            .build();

    private static final Path DEFAULT_FILE = AppPaths.dataDir().resolve("roles.json");

    private static final Logger log = Logger.getLogger(RoleService.class.getName());
    private static RoleService instance;

    // Insertion-order map: id → Role
    private final Map<String, Role> store = new LinkedHashMap<>();
    private final Path rolesFile;

    private RoleService(Path file) {
        this.rolesFile = file;
        load();
    }

    public static RoleService getInstance() {
        if (instance == null) instance = new RoleService(DEFAULT_FILE);
        return instance;
    }

    /** For tests only — creates a fresh instance backed by a custom file path. */
    static RoleService createForTest(Path file) {
        instance = new RoleService(file);
        return instance;
    }

    /** For tests only — resets the singleton so the next getInstance() call starts fresh. */
    static void resetInstance() {
        instance = null;
    }

    // ---- query -------------------------------------------------------------

    /**
     * All roles in user-defined order (AppConfig.roleOrder).
     * Falls back to insertion order when no custom order is configured.
     */
    public List<Role> getAll() {
        List<String> order = AppConfig.getInstance().roleOrder;
        if (order == null || order.isEmpty()) {
            return new ArrayList<>(store.values());
        }
        List<Role> result = new ArrayList<>();
        Set<String> inOrder = new HashSet<>(order);
        for (String id : order) {
            Role r = store.get(id);
            if (r != null) result.add(r);
        }
        // Append any roles not yet in roleOrder (e.g. migrated from old data)
        for (Role r : store.values()) {
            if (!inOrder.contains(r.id)) result.add(r);
        }
        return result;
    }

    public Role getById(String id) {
        return id != null ? store.get(id) : null;
    }

    /**
     * The local role previously imported from {@code packCode} as the sender's
     * {@code sourceRoleId}, or null when there is no such import (or either arg is null).
     */
    public Role findBySource(String packCode, String sourceRoleId) {
        if (packCode == null || sourceRoleId == null) return null;
        for (Role r : store.values()) {
            if (packCode.equals(r.sourcePackCode) && sourceRoleId.equals(r.sourceRoleId)) {
                return r;
            }
        }
        return null;
    }

    // ---- mutations ---------------------------------------------------------

    public Role add(String name, boolean frontline) {
        Role r = new Role();
        r.id        = UUID.randomUUID().toString();
        r.name      = name != null ? name : "New Role";
        r.frontline = frontline;
        store.put(r.id, r);
        AppConfig cfg = AppConfig.getInstance();
        if (cfg.roleOrder == null) cfg.roleOrder = new ArrayList<>();
        cfg.roleOrder.add(r.id);
        cfg.save();
        flush();
        return r;
    }

    /**
     * Imports the {@code selected} subset of a fetched role pack. A role previously imported
     * from the same pack (matched by {@code sourcePackCode} + the sender's role id) is
     * updated <em>in place</em> — keeping its local UUID, so brothers' assignments follow the
     * update. Otherwise a new role is created with a fresh UUID (never reusing the sender's
     * id, so name collisions are harmless) and its provenance recorded for future re-imports.
     */
    public List<Role> importPack(RolePack pack, List<Role> selected, String sourcePackCode) {
        List<Role> affected = new ArrayList<>();
        if (pack == null || selected == null || selected.isEmpty()) return affected;

        AppConfig cfg = AppConfig.getInstance();
        if (cfg.roleOrder == null) cfg.roleOrder = new ArrayList<>();

        for (Role src : selected) {
            Role existing = findBySource(sourcePackCode, src.id);
            if (existing != null) {
                Role.copyContentInto(src, existing);
                affected.add(existing);
                continue;
            }
            Role r = new Role();
            r.id = UUID.randomUUID().toString();
            Role.copyContentInto(src, r);
            r.sourcePackCode = sourcePackCode;
            r.sourceRoleId   = src.id;
            store.put(r.id, r);
            cfg.roleOrder.add(r.id);
            affected.add(r);
        }
        cfg.save();
        flush();
        return affected;
    }

    public void update(Role role) {
        if (role == null || role.id == null) return;
        store.put(role.id, role);
        flush();
    }

    public void delete(String id) {
        store.remove(id);
        AppConfig cfg = AppConfig.getInstance();
        if (cfg.roleOrder != null) {
            cfg.roleOrder.remove(id);
            cfg.save();
        }
        flush();
    }

    /** Moves the role with the given id to {@code newIndex} in the display order. */
    public void move(String id, int newIndex) {
        // Build from getAll() so the working list is always in sync with the displayed order,
        // regardless of whether roleOrder is null, empty, or only partially populated.
        List<String> order = new ArrayList<>();
        for (Role r : getAll()) order.add(r.id);
        int current = order.indexOf(id);
        if (current < 0) return;
        order.remove(current);
        newIndex = Math.max(0, Math.min(newIndex, order.size()));
        order.add(newIndex, id);
        AppConfig cfg = AppConfig.getInstance();
        cfg.roleOrder = order;
        cfg.save();
    }

    // ---- persistence -------------------------------------------------------

    private void load() {
        if (!Files.exists(rolesFile)) return;
        try {
            Wrapper w = MAPPER.readValue(rolesFile.toFile(), Wrapper.class);
            if (w.roles != null) {
                for (Role r : w.roles) {
                    if (r.id != null) store.put(r.id, r);
                }
            }
        } catch (Exception e) {
            log.warning("RoleService: load failed — " + e.getMessage());
        }
    }

    private void flush() {
        try {
            Files.createDirectories(rolesFile.getParent());
            Wrapper w = new Wrapper();
            w.roles = new ArrayList<>(store.values());
            MAPPER.writerWithDefaultPrettyPrinter().writeValue(rolesFile.toFile(), w);
        } catch (Exception e) {
            log.warning("RoleService: flush failed — " + e.getMessage());
        }
    }

    // ---- Jackson wrapper ---------------------------------------------------

    public static class Wrapper {
        public int        version = 1;
        public List<Role> roles   = new ArrayList<>();
    }
}
