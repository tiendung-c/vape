package gg.vape.manager.client;

import gg.vape.config.GlobalSettingsPayload;
import gg.vape.value.BooleanValue;

public class GlobalSettingsController {
    private boolean firstRun = false;
    private GlobalSettingsPayload settings;
    private boolean loadFailed = false;
    private final BooleanValue cacheData = BooleanValue.create(null, "Cache data", false, "Caches data locally in %APPDATA%\\Vape");

    public boolean isFirstRun() {
        return this.firstRun;
    }

    public BooleanValue getCacheData() {
        return this.cacheData;
    }

    public void save() {
        this.settings.setCacheEnabled(this.cacheData.getEffectiveValue());
        this.settings.setFirstRun(false);
    }

    public void load() {
        this.settings = new GlobalSettingsPayload();
        this.settings.initializeDefaults();
        this.loadFailed = false;
        this.firstRun = this.settings.isFirstRun();
        this.cacheData.setValue(this.settings.isCacheEnabled());
    }

    private static Exception propagateException(Exception exception) {
        return exception;
    }
}
