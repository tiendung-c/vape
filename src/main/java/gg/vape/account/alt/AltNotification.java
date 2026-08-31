package gg.vape.account.alt;

public class AltNotification {
    private final String message;
    private final long duration;
    private final long startTime;

    public AltNotification(String message, long duration) {
        this.message = message;
        this.duration = duration;
        this.startTime = System.currentTimeMillis();
    }

    public String getMessage() { return message; }
    public boolean isExpired() { return duration >= 0 && System.currentTimeMillis() - startTime > duration; }
}
