package de.mopsdom.oidc.cordova;

import android.content.Context;
import android.content.SharedPreferences;
import androidx.security.crypto.EncryptedSharedPreferences;
import androidx.security.crypto.MasterKeys;
import net.openid.appauth.AuthState;
import org.json.JSONException;

public class EncryptedTokenStore {
    private static final String PREFS_NAME = "oidc_secure_prefs";
    private static final String KEY_AUTH_STATE = "auth_state";
    private SharedPreferences sharedPreferences;

    public EncryptedTokenStore(Context context) {
        try {
            String masterKeyAlias = MasterKeys.getOrCreate(MasterKeys.AES256_GCM_SPEC);
            sharedPreferences = EncryptedSharedPreferences.create(
                PREFS_NAME,
                masterKeyAlias,
                context,
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
            );
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void save(AuthState state) {
        if (sharedPreferences != null) {
            sharedPreferences.edit().putString(KEY_AUTH_STATE, state.jsonSerializeString()).apply();
        }
    }

    public AuthState load() {
        if (sharedPreferences == null) return null;
        String json = sharedPreferences.getString(KEY_AUTH_STATE, null);
        if (json == null) return null;
        try {
            return AuthState.jsonDeserialize(json);
        } catch (JSONException e) {
            return null;
        }
    }

    public void clear() {
        if (sharedPreferences != null) {
            sharedPreferences.edit().remove(KEY_AUTH_STATE).apply();
        }
    }
}
