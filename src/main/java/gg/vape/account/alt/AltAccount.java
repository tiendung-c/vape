package gg.vape.account.alt;

public class AltAccount {
    private String refreshToken;
    private String accessToken;
    private String username;
    private String uuid;
    private long unban;

    public AltAccount(String refreshToken, String accessToken, String username, String uuid, long unban) {
        this.refreshToken = refreshToken;
        this.accessToken = accessToken;
        this.username = username;
        this.uuid = uuid;
        this.unban = unban;
    }

    public AltAccount(String refreshToken, String accessToken, String username) {
        this(refreshToken, accessToken, username, "", 0L);
    }

    public String getRefreshToken() { return refreshToken; }
    public String getAccessToken() { return accessToken; }
    public String getUsername() { return username; }
    public String getUuid() { return uuid; }
    public long getUnban() { return unban; }

    public void setRefreshToken(String refreshToken) { this.refreshToken = refreshToken; }
    public void setAccessToken(String accessToken) { this.accessToken = accessToken; }
    public void setUsername(String username) { this.username = username; }
    public void setUuid(String uuid) { this.uuid = uuid; }
    public void setUnban(long unban) { this.unban = unban; }
}
