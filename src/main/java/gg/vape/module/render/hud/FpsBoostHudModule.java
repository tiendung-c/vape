package gg.vape.module.render.hud;

import gg.vape.module.none.ClientSettings;

/**
 * Low-risk client-side FPS improvement. The expensive GUI blur pass is disabled
 * while this module is enabled; the user's original setting is restored when it
 * is disabled. It does not change Minecraft's world/render-distance settings.
 */
public class FpsBoostHudModule extends HudModule {
    private boolean savedBlurBackground;
    private boolean stateCaptured;

    public FpsBoostHudModule() {
        super("FPS Boost", HudModuleGroup.GAME, "fps_boost");
        this.setSuffix("Disables the expensive GUI blur pass");
    }

    @Override
    public void onFinishModuleInitialization() {
        // This is the default state. A saved profile state is applied afterwards.
        if (!this.isEnabled()) {
            this.setEnabled(true, true);
        }
    }

    @Override
    public void onEnable() {
        ClientSettings settings = ClientSettings.INSTANCE;
        if (settings == null) {
            return;
        }
        this.savedBlurBackground = settings.blurBackground.getEffectiveValue();
        this.stateCaptured = true;
        if (this.savedBlurBackground) {
            settings.blurBackground.setValue(false);
            settings.disableBlurShader();
        }
    }

    @Override
    public void onDisable() {
        if (this.stateCaptured && ClientSettings.INSTANCE != null) {
            ClientSettings.INSTANCE.blurBackground.setValue(this.savedBlurBackground);
        }
        this.stateCaptured = false;
    }
}
