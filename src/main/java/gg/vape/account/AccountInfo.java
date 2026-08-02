package gg.vape.account;

import java.util.Date;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class AccountInfo {
    private final long userId;
    private final Date accountCreationDate;
    private final boolean profilesEnabled;
    private final AccountEntitlements entitlements;
    @Nullable
    private String username;


    public void setUsername(@NotNull String username) {
        this.username = username;
    }

    public boolean hasProfilesEnabled() {
        return this.profilesEnabled;
    }

    @Nullable
    public String getUsername() {
        return this.username;
    }

    public long getUserId() {
        return this.userId;
    }

    public AccountEntitlements getEntitlements() {
        return this.entitlements;
    }

    public Date getAccountCreationDate() {
        return this.accountCreationDate;
    }

    public static AccountInfo offline() {
        return new AccountInfo(-1L, "Offline", new Date(0L), false,
                new AccountEntitlements(false, false, false));
    }

    AccountInfo(long userId, @Nullable String username, Date accountCreationDate, boolean profilesEnabled,
                AccountEntitlements entitlements) {
        this.userId = userId;
        this.username = username;
        this.accountCreationDate = accountCreationDate;
        this.profilesEnabled = profilesEnabled;
        this.entitlements = entitlements;
    }
}
