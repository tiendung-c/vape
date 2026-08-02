package gg.vape.notification;

import gg.vape.Vape;
import gg.vape.notification.SoundClip;
import java.util.concurrent.atomic.AtomicReference;

public class NotificationSoundPlayer {
    private static int[] controlFlowMarker;
    private final AtomicReference<SoundClip> pendingSound = new AtomicReference<SoundClip>();
    private volatile boolean running = true;
    private Thread soundThread;

    public NotificationSoundPlayer() {
        this.startSoundThread();
    }

    static {
        if (NotificationSoundPlayer.getControlFlowMarker() == null) {
            NotificationSoundPlayer.setControlFlowMarker(new int[3]);
        }
    }

    public void playPendingSound() {
        if (this.pendingSound.get() != null) {
            SoundClip sound = this.pendingSound.get();
            this.pendingSound.set(null);
            if (!this.isMuted()) {
                sound.play(this.getVolumePercent());
            }
        }
    }

    public boolean isMuted() {
        return Vape.INSTANCE.getPublicProfileSettings().muted.getEffectiveValue();
    }

    public static int[] getControlFlowMarker() {
        return controlFlowMarker;
    }

    public static void setControlFlowMarker(int[] marker) {
        controlFlowMarker = marker;
    }


    public float getVolumePercent() {
        return ((Double)Vape.INSTANCE.getPublicProfileSettings().volume.getValue()).floatValue();
    }

    public void queue(SoundClip sound) {
        this.pendingSound.set(sound);
    }

    public void startSoundThread() {
        this.soundThread = new Thread(this::runSoundLoop, "Vape notification sound player");
        this.soundThread.setDaemon(true);
        this.soundThread.start();
    }

    private void runSoundLoop() {
        while (this.running && !Thread.currentThread().isInterrupted() && !Vape.INSTANCE.enabled) {
            try {
                Thread.sleep(100L);
                this.playPendingSound();
            }
            catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                break;
            }
            catch (Exception exception) {
                Vape.logThrowable(exception);
            }
        }
    }

    public void stop() {
        this.running = false;
        this.pendingSound.set(null);
        if (this.soundThread != null) {
            this.soundThread.interrupt();
        }
        NotificationSounds.closeAll();
    }
}
