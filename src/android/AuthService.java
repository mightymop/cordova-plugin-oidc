package de.mopsdom.oidc.cordova;

import android.content.Context;
import android.net.Uri;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import net.openid.appauth.*;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class AuthService {
    private static AuthService instance;
    private AuthState authState;
    private final EncryptedTokenStore tokenStore;
    public final GlobalAuthStore globalStore;
    private final AuthorizationService authService;

    private AuthService(Context context) {
        this.tokenStore = new EncryptedTokenStore(context);
        this.globalStore = new GlobalAuthStore(context);
        this.authService = new AuthorizationService(context);
        this.authState = tokenStore.load();
    }

    public static synchronized AuthService getInstance(Context context) {
        if (instance == null) {
            instance = new AuthService(context.getApplicationContext());
        }
        return instance;
    }

    public boolean isSessionValid() {
        long login = globalStore.getLogin();
        if (login == 0) return false;
        long logout = globalStore.getLogout();
        return logout <= login;
    }

    public boolean isAuthenticated() {
        return authState != null && authState.isAuthorized();
    }

    public AuthState getAuthState() { return authState; }

    public void updateAuthStateAfterTokenResponse(@Nullable TokenResponse response, @Nullable AuthorizationException ex) {
        if (authState != null) {
            authState.update(response, ex);
            tokenStore.save(authState);
        }
    }

    public void createNewAuthState(AuthorizationResponse response, AuthorizationException ex) {
        this.authState = new AuthState(response, ex);
        tokenStore.save(authState);
        globalStore.setLogin(new Date().getTime());
    }

    public String getIdToken() {
        return authState != null ? authState.getIdToken() : null;
    }

    public void clearState() {
        this.authState = null;
        tokenStore.clear();
    }

    public interface TokenCallback {
        void onTokensReady(@Nullable String accessToken, @Nullable String idToken, @Nullable Exception error);
    }

    public void performAuthenticatedRequest(TokenCallback callback) {
        if (authState == null || !authState.isAuthorized()) {
            clearState();
            callback.onTokensReady(null, null, new Exception("Session invalid (global logout)"));
            return;
        }

        // Automatisiertes Token-Refreshment falls abgelaufen
        authState.performActionWithFreshTokens(authService, (accessToken, idToken, ex) -> {
            if (ex != null) {
                callback.onTokensReady(null, null, ex);
            } else {
                tokenStore.save(authState); // Speichert den neuen Refresh-Token-Zustand falls aktualisiert
                callback.onTokensReady(accessToken, idToken, null);
            }
        });
    }
}
