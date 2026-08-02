package gg.vape.sync;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.stream.JsonReader;
import gg.vape.Vape;
import gg.vape.config.Profile;
import gg.vape.config.VapeStorage;
import gg.vape.runtime.NativeBridge;
import gg.vape.utils.Base64Util;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicBoolean;

public class SyncThread {
    private SyncStoreRequestWorker storeRequestWorker;
    private final SyncDebounceWorker debounceWorker;
    private final AtomicBoolean pendingSave = new AtomicBoolean();
    private final Vape vape;
    private long lastSaveTime;
    private Thread storeThread;
    private Thread debounceThread;

    public SyncThread(Vape vape) {
        this.vape = vape;
        this.debounceWorker = new SyncDebounceWorker();
    }

    public void saveSettings() {
        try {
            this.prepareActiveProfileForSave();
            VapeStorage.saveSettings(this.buildSettingsPayload(false));
            VapeStorage.saveProfiles(this.vape.getProfilesManager().getProfiles());
            for (Profile profile : this.vape.getProfilesManager().getProfiles()) {
                profile.setSaveQueued(false);
                profile.setDirty(false);
            }
        }
        catch (Exception exception) {
            Vape.logThrowable(exception);
        }
        finally {
            this.pendingSave.set(false);
        }
    }

    private void prepareActiveProfileForSave() {
        try {
            Profile activeProfile = this.vape.getProfilesManager().getActiveProfile();
            if (activeProfile != null) {
                activeProfile.captureCurrentState();
            }
        }
        catch (Throwable throwable) {
            Vape.logThrowable(throwable);
        }
    }

    public void requestSave() {
        this.lastSaveTime = System.currentTimeMillis();
        this.pendingSave.set(false);
        if (this.storeRequestWorker == null) {
            this.storeRequestWorker = new SyncStoreRequestWorker();
            this.storeThread = new Thread(this.storeRequestWorker, "Vape settings save worker");
            this.storeThread.setDaemon(true);
            this.storeThread.start();
        }
        this.storeRequestWorker.requestSave();
    }

    public boolean hasPendingSave() {
        return this.pendingSave.get();
    }

    public long getLastSaveTime() {
        return this.lastSaveTime;
    }

    public void loadConfig() {
        try {
            this.loadStandaloneConfig();
        }
        catch (Throwable ignored) {
        }
    }

    private void loadStandaloneConfig() {
        JsonObject config = VapeStorage.loadSettings();
        boolean localConfig = config != null;
        if (config == null) {
            String encodedSettings = NativeBridge.gp("all");
            String decodedSettings = encodedSettings == null
                    ? ""
                    : new String(Base64Util.decodeBase64(encodedSettings), StandardCharsets.UTF_8).trim();
            JsonReader reader = new JsonReader(new StringReader(decodedSettings));
            reader.setLenient(true);
            config = new Gson().fromJson(reader, JsonObject.class);
        }
        if (config == null) {
            return;
        }
        JsonObject legacyProfiles = config.get("profiles") != null && config.get("profiles").isJsonObject()
                ? config.get("profiles").getAsJsonObject()
                : null;
        JsonObject standaloneProfiles = VapeStorage.loadProfiles();
        if (localConfig || !standaloneProfiles.entrySet().isEmpty()) {
            config.remove("profiles");
        }
        this.vape.loadConfigData(config, false);
        if (!standaloneProfiles.entrySet().isEmpty()) {
            this.vape.getProfilesManager().loadJson(standaloneProfiles);
        }
        else if (legacyProfiles != null) {
            // Migrate the old monolithic settings.json format on the next save.
            this.vape.getProfilesManager().loadJson(legacyProfiles);
        }
        for (Profile profile : this.vape.getProfilesManager().getProfiles()) {
            profile.setDirty(true);
        }
    }

    public void clearPendingSave() {
        this.pendingSave.set(false);
    }

    public void markDirty() {
        this.pendingSave.set(true);
        this.debounceWorker.markChanged();
    }

    public void start() {
        if (this.debounceThread != null) {
            return;
        }
        this.debounceThread = new Thread(this.debounceWorker, "Vape settings sync worker");
        this.debounceThread.setDaemon(true);
        this.debounceThread.start();
    }

    public void stop() {
        this.debounceWorker.stop();
        if (this.storeRequestWorker != null) {
            this.storeRequestWorker.stop();
        }
        if (this.debounceThread != null) {
            this.debounceThread.interrupt();
        }
        if (this.storeThread != null) {
            this.storeThread.interrupt();
        }
    }

    public JsonObject buildSettingsPayload(boolean splitProfiles) {
        JsonObject payload = new JsonObject();
        payload.add("friends", this.vape.getFriendManager().toJson());
        payload.add(splitProfiles ? "otherData" : "otherdata", this.vape.getSettingsManager().toJson());
        return payload;
    }
}
