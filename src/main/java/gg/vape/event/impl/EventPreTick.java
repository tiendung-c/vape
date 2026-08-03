package gg.vape.event.impl;

import gg.vape.module.none.ClientSettings;
import gg.vape.event.impl.EventRenderWorldPassExecutorDrain;
import gg.vape.event.impl.EventTickBase;

public class EventPreTick
extends EventTickBase {
    @Override
    public boolean fire() {
        // 1.8.9 can reach the normal tick loop before a world-render pass is
        // emitted. Drain the queued frame initialization on the Minecraft
        // thread so the click GUI is available as soon as the client runs.
        if (!ClientSettings.framesInitialized) {
            EventRenderWorldPassExecutorDrain.EXECUTOR.runPending();
        }
        PRE_TICK_EXECUTOR.runPending();
        return super.fire();
    }
}
