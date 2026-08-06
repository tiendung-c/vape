package gg.vape.account;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import gg.vape.Vape;
import gg.vape.config.VapeStorage;
import gg.vape.mapping.mappings.MSession;
import gg.vape.wrapper.impl.ForgeVersion;
import gg.vape.wrapper.impl.Minecraft;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Local account switcher for supported Minecraft versions. It creates the same offline
 * UUID used by the vanilla client and never contacts Microsoft/Xbox services.
 */
public final class OfflineAccountManager {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final String DEFAULT_ACCOUNT_NAME = "Player";
    private final List<String> accountNames = new ArrayList<String>();
    private int activeIndex;

    public void load() {
        this.accountNames.clear();
        this.activeIndex = 0;
        Path file = VapeStorage.existingOfflineAccountsFile();
        try {
            if (Files.isRegularFile(file)) {
                try (Reader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
                    JsonObject root = GSON.fromJson(reader, JsonObject.class);
                    this.readAccounts(root);
                }
            }
        }
        catch (Exception exception) {
            Vape.debugLog("Offline account file could not be read; using a fresh list");
        }

        if (this.accountNames.isEmpty()) {
            String currentName = this.readCurrentUsername();
            this.addAccount(isValidName(currentName) ? currentName : DEFAULT_ACCOUNT_NAME);
        }

        String activeName = null;
        try {
            if (Files.isRegularFile(file)) {
                try (Reader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
                    JsonObject root = GSON.fromJson(reader, JsonObject.class);
                    if (root != null && root.has("active") && root.get("active").isJsonPrimitive()) {
                        activeName = root.get("active").getAsString();
                    }
                }
            }
        }
        catch (Exception ignored) {
            // The account list is still usable when the active marker is invalid.
        }
        if (activeName != null) {
            for (int index = 0; index < this.accountNames.size(); ++index) {
                if (!this.accountNames.get(index).equalsIgnoreCase(activeName)) continue;
                this.activeIndex = index;
                break;
            }
        }
        this.save();
    }

    private void readAccounts(JsonObject root) {
        if (root == null || !root.has("accounts") || !root.get("accounts").isJsonArray()) {
            return;
        }
        Set<String> seen = new HashSet<String>();
        JsonArray accounts = root.getAsJsonArray("accounts");
        for (JsonElement element : accounts) {
            if (element == null || !element.isJsonObject()) continue;
            JsonElement nameElement = element.getAsJsonObject().get("name");
            if (nameElement == null || !nameElement.isJsonPrimitive()) continue;
            String name = nameElement.getAsString();
            String key = name.toLowerCase(Locale.ROOT);
            if (isValidName(name) && seen.add(key)) {
                this.accountNames.add(name);
            }
        }
    }

    private String readCurrentUsername() {
        try {
            if (!ForgeVersion.MC_1_8_9.d()) return null;
            return Minecraft.Q$src$Lgg_vape_account_MinecraftSessionWrapper_$1ftnn3u().getUsername();
        }
        catch (Throwable ignored) {
            return null;
        }
    }

    public boolean cycleAndApply() {
        if (this.accountNames.isEmpty()) return false;
        this.activeIndex = (this.activeIndex + 1) % this.accountNames.size();
        boolean applied = this.applyActive();
        if (applied) {
            this.save();
        }
        return applied;
    }

    /**
     * Adds an offline account (or selects it when it already exists) and
     * applies it to the current Minecraft session.
     */
    public boolean addAndApply(String name) {
        if (!ForgeVersion.MC_1_8_9.d()) return false;
        if (!isValidName(name)) {
            if (Vape.INSTANCE.getNotificationManager() != null) {
                Vape.INSTANCE.getNotificationManager().showInfo(
                        "Offline account",
                        "Account names must be 1-16 characters and contain only letters, numbers, or underscores.",
                        5000L);
            }
            return false;
        }
        for (int index = 0; index < this.accountNames.size(); ++index) {
            if (!this.accountNames.get(index).equalsIgnoreCase(name)) continue;
            this.activeIndex = index;
            boolean applied = this.applyActive();
            if (applied) this.save();
            return applied;
        }
        this.accountNames.add(name);
        this.activeIndex = this.accountNames.size() - 1;
        boolean applied = this.applyActive();
        if (applied) {
            this.save();
        } else {
            this.accountNames.remove(this.accountNames.size() - 1);
            this.activeIndex = Math.max(0, this.accountNames.size() - 1);
        }
        return applied;
    }

    /** Selects an existing account from the Accounts screen. */
    public boolean selectAndApply(String name) {
        if (!ForgeVersion.MC_1_8_9.d() || name == null) return false;
        for (int index = 0; index < this.accountNames.size(); ++index) {
            if (!this.accountNames.get(index).equalsIgnoreCase(name)) continue;
            this.activeIndex = index;
            boolean applied = this.applyActive();
            if (applied) this.save();
            return applied;
        }
        return false;
    }

    public boolean applyActive() {
        if (!ForgeVersion.MC_1_8_9.d() || this.accountNames.isEmpty()) {
            return false;
        }
        String name = this.accountNames.get(this.activeIndex);
        try {
            UUID uuid = offlineUuid(name);
            String rawUuid = uuid.toString().replace("-", "");
            Object session;
            if (ForgeVersion.MC_1_21_10.d()) {
                session = MSession.modern(Vape.INSTANCE.getMappings().hw, name, uuid, "",
                        Optional.empty(), Optional.empty());
            } else if (ForgeVersion.MC_1_20_6.d()) {
                session = MSession.B(Vape.INSTANCE.getMappings().hw, name, uuid, "",
                        Optional.empty(), Optional.empty(), Vape.INSTANCE.getMappingsMapperCompat().sessionType.getLegacy());
            } else {
                session = MSession.t(Vape.INSTANCE.getMappings().hw, name, rawUuid, "", "legacy");
            }
            Minecraft.w(new MinecraftSessionWrapper(session));
            if (Vape.INSTANCE.getNotificationManager() != null) {
                Vape.INSTANCE.getNotificationManager().showInfo(
                        "Offline account",
                        name + " selected. Reconnect to apply it to the current world.",
                        5000L);
            }
            return true;
        }
        catch (Throwable error) {
            Vape.debugLog("Offline account could not be applied: " + error.getClass().getSimpleName());
            if (Vape.INSTANCE.getNotificationManager() != null) {
                Vape.INSTANCE.getNotificationManager().showInfo(
                        "Offline account",
                        "Could not switch to " + name + ". Check the runtime mapping.",
                        5000L);
            }
            return false;
        }
    }

    public List<String> getAccountNames() {
        return new ArrayList<String>(this.accountNames);
    }

    public String getActiveName() {
        return this.accountNames.isEmpty() ? null : this.accountNames.get(this.activeIndex);
    }

    public static UUID offlineUuid(String name) {
        return UUID.nameUUIDFromBytes(("OfflinePlayer:" + name).getBytes(StandardCharsets.UTF_8));
    }

    private void addAccount(String name) {
        if (!isValidName(name)) return;
        for (String existing : this.accountNames) {
            if (existing.equalsIgnoreCase(name)) return;
        }
        this.accountNames.add(name);
    }

    private void save() {
        if (this.accountNames.isEmpty()) return;
        JsonObject root = new JsonObject();
        root.addProperty("active", this.getActiveName());
        JsonArray accounts = new JsonArray();
        for (String name : this.accountNames) {
            JsonObject account = new JsonObject();
            account.addProperty("name", name);
            account.addProperty("uuid", offlineUuid(name).toString());
            accounts.add(account);
        }
        root.add("accounts", accounts);
        try {
            Path file = VapeStorage.offlineAccountsFile();
            Files.createDirectories(file.getParent());
            try (Writer writer = Files.newBufferedWriter(file, StandardCharsets.UTF_8)) {
                GSON.toJson(root, writer);
            }
        }
        catch (Exception exception) {
            Vape.debugLog("Offline account file could not be saved");
        }
    }

    private static boolean isValidName(String name) {
        return name != null && name.matches("[A-Za-z0-9_]{1,16}");
    }

}
