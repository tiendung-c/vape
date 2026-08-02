package gg.vape.sync;

import gg.vape.Vape;
import java.util.concurrent.atomic.AtomicBoolean;

public class SyncStoreRequestWorker
implements Runnable {
    private final AtomicBoolean saveRequested = new AtomicBoolean();
    private volatile boolean running = true;

    public void requestSave() {
        this.saveRequested.set(true);
    }

    @Override
    public void run() {
        while (this.running && !Thread.currentThread().isInterrupted() && !Vape.INSTANCE.isEnabled()) {
            try {
                Thread.sleep(100L);
            }
            catch (InterruptedException interruptedException) {
                Thread.currentThread().interrupt();
                break;
            }
            if (!this.saveRequested.get()) continue;
            try {
                Thread.sleep(100L);
            }
            catch (InterruptedException interruptedException) {
                Thread.currentThread().interrupt();
                break;
            }
            Vape.INSTANCE.getSyncThread().saveSettings();
            this.saveRequested.set(false);
        }
    }

    public void stop() {
        this.running = false;
    }
}
