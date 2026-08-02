package gg.vape.module.render.hud;

import gg.vape.event.EventHandler;
import gg.vape.event.impl.EventPreRenderTick;

/**
 * Keeps the renderer's hand FOV modifiers neutral so the camera stays at the
 * value configured in Minecraft's Game Settings. The configured FOV itself is
 * never overwritten, so changing it in the normal settings menu still works.
 */
public class FovLockHudModule extends HudModule {
    private float savedFovModifier;
    private float savedPreviousFovModifier;
    private boolean stateCaptured;

    public FovLockHudModule() {
        super("FOV Lock", HudModuleGroup.GAME, "fov_lock");
        this.setSuffix("Locks the renderer FOV to the Minecraft Game Settings value");
    }

    @Override
    public void onEnable() {
        this.stateCaptured = false;
    }

    @EventHandler
    public void onPreRenderTick(EventPreRenderTick event) {
        if (event.getWorld().isNull() || event.getThePlayer().isNull()) {
            return;
        }
        if (!this.stateCaptured) {
            this.savedFovModifier = event.getEntityRenderer().b();
            this.savedPreviousFovModifier = event.getEntityRenderer().s();
            this.stateCaptured = true;
        }
        // Keep the user's Game Settings FOV; neutralize only renderer modifiers.
        event.getEntityRenderer().V(1.0f);
        event.getEntityRenderer().r(1.0f);
    }

    @Override
    public void onDisable() {
        if (!this.stateCaptured) {
            return;
        }
        try {
            gg.vape.wrapper.impl.EntityRenderer renderer =
                    gg.vape.wrapper.impl.Minecraft.m$src$Lgg_vape_wrapper_impl_EntityRenderer_$13begmf();
            renderer.V(this.savedFovModifier);
            renderer.r(this.savedPreviousFovModifier);
        }
        finally {
            this.stateCaptured = false;
        }
    }
}
