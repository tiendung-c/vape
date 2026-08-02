package gg.vape.account;


public class AccountEntitlements {
    private boolean registered;
    private final boolean licensed;
    private final boolean banned;

    public boolean isLicensed() {
        return this.licensed;
    }

    public boolean isRegistered() {
        return this.registered;
    }

    public void setRegistered(boolean registered) {
        this.registered = registered;
    }

    public boolean isBanned() {
        return this.banned;
    }


    AccountEntitlements(boolean licensed, boolean registered, boolean banned) {
        this.licensed = licensed;
        this.registered = registered;
        this.banned = banned;
    }

}
