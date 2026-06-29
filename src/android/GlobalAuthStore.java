package de.mopsdom.oidc.cordova;

import android.content.Context;
import android.content.SharedPreferences;

public class GlobalAuthStore {
    private static final String PREFS_NAME = "group.plugin.cordova.oidc";
    private static final String KEY_LOGIN = "globalLoginDate";
    private static final String KEY_LOGOUT = "globalLogoutDate";
    private final SharedPreferences prefs;

    public GlobalAuthStore(Context context) {
        this.prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }

    public void setLogin(long timestamp) { prefs.edit().putLong(KEY_LOGIN, timestamp).apply(); }
    public long getLogin() { return prefs.getLong(KEY_LOGIN, 0); }
    public void setLogout(long timestamp) { prefs.edit().putLong(KEY_LOGOUT, timestamp).apply(); }
    public long getLogout() { return prefs.getLong(KEY_LOGOUT, 0); }
}
