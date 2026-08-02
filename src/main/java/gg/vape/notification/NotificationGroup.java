package gg.vape.notification;

public enum NotificationGroup {
    NONE;

    private static final NotificationGroup[] cachedValues;

    static {
        String[] groupNames = new String[]{"NONE"};


        cachedValues = new NotificationGroup[]{NONE};
    }

}
