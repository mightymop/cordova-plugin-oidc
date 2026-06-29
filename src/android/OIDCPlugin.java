package de.mopsdom.oidc.cordova;

import android.content.Intent;
import android.net.Uri;
import android.util.Base64;
import org.apache.cordova.*;
import org.json.*;
import net.openid.appauth.*;
import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.*;

public class OIDCPlugin extends CordovaPlugin {
    private static final int RC_AUTH = 100;
    private static final int RC_END_SESSION = 101;

    private AuthService authService;
    private CallbackContext callbackContext;
    private AuthorizationService appAuthService;

    @Override
    protected void pluginInitialize() {
        this.authService = AuthService.getInstance(cordova.getActivity());
        this.appAuthService = new AuthorizationService(cordova.getActivity());
    }

    @Override
    public boolean execute(String action, JSONArray args, CallbackContext callbackContext) throws JSONException {

        JSONObject config = args.optJSONObject(0);
        if ("login".equals(action)) {
            login(config,callbackContext);
            return true;
        } else if ("logout".equals(action)) {
            this.callbackContext = callbackContext;
            logout(config);
            return true;
        } else if ("isAuthenticated".equals(action)) {
            isAuthenticated(callbackContext);
            return true;
        } else if ("getToken".equals(action)) {
            getToken(callbackContext);
            return true;
        } else if ("usersinfos".equals(action)) {
            usersinfos(config,callbackContext);
            return true;
        }
        return false;
    }

    private void login(JSONObject config, CallbackContext currentCallbackContext) throws JSONException {
        String issuerStr = config.getString("issuer");
        String clientId = config.getString("clientId");
        String scopesStr = config.getString("scopes");
        boolean prompt = config.optBoolean("prompt", false);
        boolean slo = config.optBoolean("slo", false);
        String redirectURIStr = config.getString("redirectURI");

        Uri issuerUri = Uri.parse(issuerStr);
        Uri redirectUri = Uri.parse(redirectURIStr);
        List<String> scopes = Arrays.asList(scopesStr.split(" "));

        PluginResult pluginResult = new PluginResult(PluginResult.Status.NO_RESULT);
        pluginResult.setKeepCallback(true);
        currentCallbackContext.sendPluginResult(pluginResult);

        this.callbackContext = currentCallbackContext;

        AuthorizationServiceConfiguration.fetchFromIssuer(issuerUri, (serviceConfiguration, ex) -> {
            if (ex != null) {
                android.util.Log.e("OIDC_PROXY", "Discovery Fehler Details: ", ex);
                callbackContext.error("Discovery failed: " + ex.getLocalizedMessage());
                return;
            }

            boolean forcePrompt = slo ? !authService.isSessionValid() || prompt : prompt;
            AuthorizationRequest.Builder requestBuilder = new AuthorizationRequest.Builder(
                    serviceConfiguration, clientId, ResponseTypeValues.CODE, redirectUri)
                    .setScopes(scopes);

            if (forcePrompt) {
                requestBuilder.setPrompt("login");
            }

            AuthorizationRequest request = requestBuilder.build();
            Intent authIntent = appAuthService.getAuthorizationRequestIntent(request);

            // Startet den Custom-Tab/Browser Flow über Cordova
            cordova.startActivityForResult(this, authIntent, RC_AUTH);
        });
    }

    private void logout(JSONObject config) throws JSONException {
        String redirectURIStr = config.getString("redirectURI");
        Uri redirectUri = Uri.parse(redirectURIStr);

        if (authService.getAuthState() == null || authService.getAuthState().getAuthorizationServiceConfiguration() == null) {
            authService.clearState();
            authService.globalStore.setLogout(new Date().getTime());
            callbackContext.success("Logout erfolgreich (Lokal)");
            return;
        }

        AuthorizationServiceConfiguration discovery = authService.getAuthState().getAuthorizationServiceConfiguration();

        // Android AppAuth benötigt oft die manuelle EndSession-Konfiguration, falls nicht im Metadaten-Objekt automatisch gemappt
        if (discovery.endSessionEndpoint == null) {
            authService.clearState();
            authService.globalStore.setLogout(new Date().getTime());
            callbackContext.success("Logout erfolgreich");
            return;
        }

        String idToken = authService.getIdToken();
        EndSessionRequest endSessionRequest = new EndSessionRequest.Builder(discovery)
                .setIdTokenHint(idToken)
                .setPostLogoutRedirectUri(redirectUri)
                .build();

        Intent endSessionIntent = appAuthService.getEndSessionRequestIntent(endSessionRequest);
        cordova.startActivityForResult(this, endSessionIntent, RC_END_SESSION);
    }

    private void isAuthenticated(CallbackContext callbackContext) {
        if (!authService.isSessionValid()) {
            callbackContext.error("Benutzer ist abgemeldet.");
            return;
        }
        callbackContext.sendPluginResult(new PluginResult(PluginResult.Status.OK, authService.isAuthenticated()));
    }

    private void getToken(CallbackContext callbackContext) {
        authService.performAuthenticatedRequest((accessToken, idToken, error) -> {
            if (error != null) {
                callbackContext.error(error.getLocalizedMessage());
                return;
            }
            try {
                JSONObject tokens = new JSONObject();
                if (accessToken != null) tokens.put("access_token", accessToken);
                if (idToken != null) tokens.put("id_token", idToken);
                callbackContext.success(tokens);
            } catch (JSONException e) {
                callbackContext.error(e.getLocalizedMessage());
            }
        });
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (requestCode == RC_AUTH) {
            AuthorizationResponse response = AuthorizationResponse.fromIntent(data);
            AuthorizationException ex = AuthorizationException.fromIntent(data);

            if (response != null) {
                // Token Exchange (Authorization Code grant) ausführen
                appAuthService.performTokenRequest(response.createTokenExchangeRequest(), (tokenResponse, exception) -> {
                    if (tokenResponse != null) {
                        authService.createNewAuthState(response, ex);
                        authService.updateAuthStateAfterTokenResponse(tokenResponse, exception);
                        callbackContext.success("Login erfolgreich");
                    } else {
                        callbackContext.error(exception != null ? exception.getLocalizedMessage() : "Token exchange failed");
                    }
                });
            } else {
                callbackContext.error(ex != null ? ex.getLocalizedMessage() : "Login abgebrochen");
            }
        } else if (requestCode == RC_END_SESSION) {
            // Android EndSession-Rückmeldung validieren
            authService.globalStore.setLogout(new Date().getTime());
            authService.clearState();
            callbackContext.success("Logout erfolgreich");
        }
    }

    // --- USERINFO & LDAP FLOW ---

    private void usersinfos(JSONObject config,CallbackContext callbackContext) throws JSONException {
        String ldapInfoUrlStr = config.optString("ldapinfourl", null);
        String userClaimKey = config.optString("userclaim", "sub");
        String issuerStr = config.optString("issuer", null);

        authService.performAuthenticatedRequest((accessToken, idToken, error) -> {
            if (error != null) {
                callbackContext.error(error.getLocalizedMessage());
                return;
            }
            if (accessToken == null) {
                callbackContext.error("Kein Access Token vorhanden");
                return;
            }

            if (ldapInfoUrlStr != null && !ldapInfoUrlStr.isEmpty()) {
                // LDAP Flow
                String finalLdapUrl = ldapInfoUrlStr;
                if (!finalLdapUrl.endsWith("ldap/lookup")) {
                    finalLdapUrl += finalLdapUrl.endsWith("/") ? "ldap/lookup" : "/ldap/lookup";
                }
                if (idToken == null) {
                    callbackContext.error("Für den LDAP Request wird ein ID Token benötigt");
                    return;
                }

                String filterValue = extractClaim(idToken, "persnr");
                performLDAPRequest(finalLdapUrl, accessToken, filterValue, 3,callbackContext);

            } else if (issuerStr != null) {
                // OIDC Fallback Flow
                performOIDCFallback(issuerStr, accessToken,callbackContext);
            } else {
                callbackContext.error("Weder LDAP-URL noch Issuer-URL gefunden");
            }
        });
    }

    private void performLDAPRequest(String urlStr, String accessToken, String filter, int retriesLeft,CallbackContext callbackContext) {
        cordova.getThreadPool().execute(() -> {
            try {
                URL url = new URL(urlStr);
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("POST");
                conn.setRequestProperty("Authorization", "Bearer " + accessToken);
                conn.setRequestProperty("Content-Type", "application/json; charset=utf-8");
                conn.setRequestProperty("Accept", "application/json");
                conn.setDoOutput(true);

                JSONArray attributes = new JSONArray(Arrays.asList(
                    "samaccountname", "company", "mail", "facsimileTelephoneNumber", "telephoneNumber",
                    "mobile", "streetAddress", "postalCode", "l", "displayName", "sn", "givenName",
                    "department", "extensionAttribute2", "extensionattribute1", "extensionAttribute4",
                    "jpegPhoto", "thumbnailPhoto", "office"
                ));

                JSONObject jsonParams = new JSONObject();
                jsonParams.put("filter", filter);
                jsonParams.put("attributes", attributes);

                try (OutputStream os = conn.getOutputStream()) {
                    byte[] input = jsonParams.toString().getBytes(StandardCharsets.UTF_8);
                    os.write(input, 0, input.length);
                }

                int statusCode = conn.getResponseCode();
                if (statusCode >= 200 && statusCode < 300) {
                    BufferedReader br = new BufferedReader(new InputStreamReader(conn.getInputStream(), StandardCharsets.UTF_8));
                    StringBuilder response = new StringBuilder();
                    String responseLine;
                    while ((responseLine = br.readLine()) != null) {
                        response.append(responseLine.trim());
                    }
                    JSONObject parsedData = parseInfosFromLdap(new JSONObject(response.toString()));
                    callbackContext.success(parsedData.toString());
                } else {
                    throw new IOException("HTTP Error " + statusCode);
                }
            } catch (Exception e) {
                if (retriesLeft > 1) {
                    try { Thread.sleep(3000); } catch (InterruptedException ignored) {}
                    performLDAPRequest(urlStr, accessToken, filter, retriesLeft - 1,callbackContext);
                } else {
                    callbackContext.error("LDAP Error: " + e.getLocalizedMessage());
                }
            }
        });
    }

    private void performOIDCFallback(String issuerStr, String accessToken,CallbackContext callbackContext) {
        cordova.getThreadPool().execute(() -> {
            try {
                URL url = new URL(issuerStr + (issuerStr.endsWith("/") ? "userinfo" : "/userinfo"));
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("GET");
                conn.setRequestProperty("Authorization", "Bearer " + accessToken);
                conn.setRequestProperty("Accept", "application/json");

                int statusCode = conn.getResponseCode();
                InputStream is = (statusCode >= 200 && statusCode < 300) ? conn.getInputStream() : conn.getErrorStream();
                BufferedReader br = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8));
                StringBuilder response = new StringBuilder();
                String line;
                while ((line = br.readLine()) != null) response.append(line.trim());

                if (statusCode >= 200 && statusCode < 300) {
                    callbackContext.success(response.toString());
                } else {
                    callbackContext.error("HTTP " + statusCode + ": " + response.toString());
                }
            } catch (Exception e) {
                callbackContext.error(e.getLocalizedMessage());
            }
        });
    }

    private JSONObject parseInfosFromLdap(JSONObject response) throws JSONException {
        JSONArray results = response.optJSONArray("results");
        if (results == null || results.length() == 0) return new JSONObject();

        JSONObject firstResult = results.getJSONObject(0);
        JSONArray attributes = firstResult.optJSONArray("attributes");
        JSONObject resultDict = new JSONObject();

        if (firstResult.has("id")) {
            resultDict.put("user", firstResult.get("id"));
        }

        if (attributes != null) {
            for (int i = 0; i < attributes.length(); i++) {
                JSONObject attr = attributes.getJSONObject(i);
                String name = attr.optString("name");
                Object value = attr.opt("value");
                if (name != null) {
                    resultDict.put(name.toLowerCase(), value);
                }
            }
        }
        return resultDict;
    }

    private String extractClaim(String jwt, String claimKey) {
        try {
            String[] parts = jwt.split("\\.");
            if (parts.length < 2) return "";
            byte[] decodedBytes = Base64.decode(parts[1], Base64.URL_SAFE);
            JSONObject payload = new JSONObject(new String(decodedBytes, StandardCharsets.UTF_8));
            return payload.optString(claimKey, "");
        } catch (Exception e) {
            return "";
        }
    }
}
