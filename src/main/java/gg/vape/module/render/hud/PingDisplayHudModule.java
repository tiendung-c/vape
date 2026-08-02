package gg.vape.module.render.hud;

import gg.vape.ui.click.frame.impl.hud.PingDisplayHudFrame;

public class PingDisplayHudModule extends HudModule {
    public PingDisplayHudModule() {
        super("Ping", HudModuleGroup.HUD, "ping", PingDisplayHudFrame.class);
        this.setSuffix("Shows your current server latency");
    }
}
