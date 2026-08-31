package gg.vape.account.alt;

import com.google.gson.*;
import gg.vape.Vape;
import gg.vape.config.VapeStorage;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Manages persisted alt accounts. File: %APPDATA%/Vape/accounts/microsoft-accounts.json
 * Compatible with ksyzov/AccountManager accounts.json schema.
 */
public final class AltManager {
    private static final String FILE_NAME = "microsoft-accounts.json";
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final AltManager INSTANCE = new AltManager();

    private final List<AltAccount> accounts = Collections.synchronizedList(new ArrayList<>());

    private AltManager() {}

    public static AltManager getInstance() { return INSTANCE; }

    public List<AltAccount> getAccounts() {
        synchronized (accounts) {
            return new ArrayList<>(accounts);
        }
    }

    public List<AltAccount> getAccountsUnsafe() {
        return accounts;
    }

    public int size() { return accounts.size(); }

    public void load() {
        synchronized (accounts) {
            accounts.clear();
            Path path = getPath();
            if (!Files.isRegularFile(path)) {
                Vape.debugLog("[AltManager] no accounts file at " + path);
                return;
            }
            try {
                String json = new String(Files.readAllBytes(path), StandardCharsets.UTF_8);
                if (json == null || json.trim().isEmpty()) return;
                JsonElement element = new JsonParser().parse(json);
                if (!(element instanceof JsonArray)) {
                    Vape.debugLog("[AltManager] accounts.json not an array");
                    return;
                }
                JsonArray arr = element.getAsJsonArray();
                for (JsonElement e : arr) {
                    if (!e.isJsonObject()) continue;
                    JsonObject o = e.getAsJsonObject();
                    String refresh = optString(o, "refreshToken");
                    String access = optString(o, "accessToken");
                    String username = optString(o, "username");
                    String uuid = optString(o, "uuid");
                    // legacy field "unban" preserved
                    long unban = 0L;
                    if (o.has("unban") && o.get("unban").isJsonPrimitive()) {
                        try { unban = o.get("unban").getAsLong(); } catch (Exception ignored) {}
                    }
                    if (uuid == null) uuid = "";
                    // migrate: if uuid empty try to extract from access? keep empty
                    accounts.add(new AltAccount(refresh, access, username, uuid, unban));
                }
                Vape.debugLog("[AltManager] loaded " + accounts.size() + " accounts");
            } catch (Exception ex) {
                Vape.logThrowable(ex);
            }
        }
    }

    public void save() {
        synchronized (accounts) {
            Path path = getPath();
            try {
                Files.createDirectories(path.getParent());
                JsonArray arr = new JsonArray();
                for (AltAccount acc : accounts) {
                    JsonObject o = new JsonObject();
                    o.addProperty("refreshToken", acc.getRefreshToken());
                    o.addProperty("accessToken", acc.getAccessToken());
                    o.addProperty("username", acc.getUsername());
                    if (acc.getUuid() != null && !acc.getUuid().isEmpty()) {
                        o.addProperty("uuid", acc.getUuid());
                    }
                    o.addProperty("unban", acc.getUnban());
                    arr.add(o);
                }
                String json = GSON.toJson(arr);
                Path tmp = path.resolveSibling(FILE_NAME + ".tmp");
                Files.write(tmp, json.getBytes(StandardCharsets.UTF_8),
                        StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.WRITE);
                try {
                    Files.move(tmp, path, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
                } catch (AtomicMoveNotSupportedException ignored) {
                    Files.move(tmp, path, StandardCopyOption.REPLACE_EXISTING);
                }
            } catch (IOException ex) {
                Vape.logThrowable(ex);
            }
        }
    }

    public void addAccount(AltAccount acc) {
        synchronized (accounts) {
            // dedup by username
            accounts.removeIf(a -> a.getUsername() != null && a.getUsername().equalsIgnoreCase(acc.getUsername()));
            accounts.add(acc);
        }
        save();
    }

    public void removeAccount(int idx) {
        synchronized (accounts) {
            if (idx >= 0 && idx < accounts.size()) {
                accounts.remove(idx);
            }
        }
        save();
    }

    public static Path getPath() {
        return VapeStorage.accountsDirectory().resolve(FILE_NAME);
    }

    private static String optString(JsonObject o, String key) {
        if (!o.has(key) || o.get(key).isJsonNull()) return "";
        try { return o.get(key).getAsString(); } catch (Exception e) { return ""; }
    }
}
