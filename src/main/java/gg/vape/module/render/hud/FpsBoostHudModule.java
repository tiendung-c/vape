package gg.vape.module.render.hud;

import gg.vape.module.none.ClientSettings;

/**
 * Low-risk client-side FPS improvement. The expensive GUI blur pass is disabled
 * while this module is enabled; the user's original setting is restored when it
 * is disabled. It does not change Minecraft's world/render-distance settings.
 */
public class FpsBoostHudModule extends HudModule {
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
        // FPS Boost intentionally owns this setting: GUI blur is disabled while
        // the performance module is active and is not restored by this module.
        settings.blurBackground.setValue(false);
        settings.disableBlurShader();
    }

    @Override
    public void onDisable() {
        // Keep blur disabled; restoring it here reintroduces the shader failure.
        if (ClientSettings.INSTANCE != null) {
            ClientSettings.INSTANCE.blurBackground.setValue(false);
            ClientSettings.INSTANCE.disableBlurShader();
        }
    }
}
