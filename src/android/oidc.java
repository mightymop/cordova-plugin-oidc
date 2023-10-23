package de.mopsdom.oidc.cordova;


import android.accounts.AccountManager;
import android.accounts.OnAccountsUpdateListener;
import android.app.Activity;
import android.content.ComponentName;
import android.content.Intent;
import android.os.Build;
import android.util.Log;
import android.webkit.WebView;

import androidx.annotation.NonNull;

import org.apache.cordova.CallbackContext;
import org.apache.cordova.CordovaInterface;
import org.apache.cordova.CordovaPlugin;
import org.apache.cordova.CordovaWebView;
import org.apache.cordova.PluginResult;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.util.HashMap;

public class oidc extends CordovaPlugin {

  private static final int REQUEST_LOGIN = 10000;
  private static final int REQUEST_LOGOUT = 1000;
  private static final int REQUEST_CONFIG = 100;
  private static final int REQUEST_CONNECTIONCONFIG = 10;
  private CallbackContext _callbackContext;
  private OnAccountsUpdateListener listener;

  @Override
  public void onDestroy() {
    super.onDestroy();
    Log.d(oidc.class.getSimpleName(), "onDestroy");

    if (listener != null) {
      AccountManager accountManager = AccountManager.get(cordova.getContext());
      accountManager.removeOnAccountsUpdatedListener(listener);
      listener = null;
    }
  }

  @Override
  public void initialize(CordovaInterface cordova, CordovaWebView webView) {
    super.initialize(cordova, webView);

    Log.d(oidc.class.getSimpleName(), "initialize");

    cordova.getActivity().runOnUiThread(new Runnable() {
      @Override
      public void run() {
        // Setze den Debug-Modus
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
          WebView.setWebContentsDebuggingEnabled(true);
        }
      }
    });

    this.registerAuthChangeHandler();
  }

  @Override
  public void onActivityResult(int requestCode, int resultCode, Intent intent) {
    super.onActivityResult(requestCode, resultCode, intent);
    Log.d(oidc.class.getSimpleName(), "onActivityResult");

    if (_callbackContext != null && intent != null) {
      if (resultCode == Activity.RESULT_OK) {
        switch (requestCode) {
          case REQUEST_LOGIN:
          case REQUEST_LOGOUT:
            Log.d(oidc.class.getSimpleName(), intent.getDataString());
            _callbackContext.sendPluginResult(new PluginResult(PluginResult.Status.OK, intent.getDataString()));
            break;

          case REQUEST_CONFIG:
          case REQUEST_CONNECTIONCONFIG:
            Log.d(oidc.class.getSimpleName(), intent.getExtras().getString("config"));
            _callbackContext.sendPluginResult(new PluginResult(PluginResult.Status.OK, intent.getExtras().getString("config")));
            break;

          default:
            Log.d(oidc.class.getSimpleName(), "NO DATA - Default");
            _callbackContext.sendPluginResult(new PluginResult(PluginResult.Status.OK));
            break;
        }
      } else {
        String error = "Unbekannter Fehler in onActivityResult!";
        if (intent.hasExtra("error")) {
          error = intent.getStringExtra("error");
        }
        Log.e(oidc.class.getSimpleName(), error);
        _callbackContext.sendPluginResult(new PluginResult(PluginResult.Status.ERROR, error));
      }
    }
  }

  private void refreshNotification() {

    Log.d(oidc.class.getSimpleName(), "refreshNotification");
    try {
      String config = Utils.getConfData(cordova.getContext(), "connectionconfig");
      JSONObject json = new JSONObject(config);

      ComponentName componentName = new ComponentName("de.berlin.polizei.oidcsso", "de.mopsdom.oidc.configapp.NotificationtentService");
      Intent intent = new Intent();
      intent.setComponent(componentName);
      intent.setAction("de.mopsdom.oidc.NOTIFY_MESSAGE");
      intent.putExtra("notify", json.has("notification") && json.getBoolean("notification"));

      String state = Utils.readData(cordova.getContext(), "state");
      intent.putExtra("isauth", state != null);
      if (state != null) {
        JSONObject jstate = new JSONObject(state);
        String id_token = jstate.getString("id_token");
        String picture = (String) Utils.getClaimFromToken(id_token, "picture");
        String persnr = (String) Utils.getClaimFromToken(id_token, "persnr");
        String upn = (String) Utils.getClaimFromToken(id_token, "upn");
        if (picture != null && !picture.isEmpty()) {
          intent.putExtra("picture", picture);
        }
        if (persnr != null && !persnr.isEmpty()) {
          intent.putExtra("persnr", persnr);
        }
        if (upn != null && !upn.isEmpty()) {
          intent.putExtra("upn", upn);
        }
      }

      cordova.getContext().startService(intent);

      _callbackContext.sendPluginResult(new PluginResult(PluginResult.Status.OK));
    } catch (Exception e) {
      PluginResult result = new PluginResult(PluginResult.Status.ERROR, e.getMessage());
      _callbackContext.sendPluginResult(result);
    }
  }

  public void registerAuthChangeHandler() {

    Log.d(oidc.class.getSimpleName(), "registerAuthChangeHandler");
    if (listener == null) {

      listener = accounts -> {
        refreshNotification();
      };
      AccountManager accountManager = AccountManager.get(cordova.getContext());
      accountManager.addOnAccountsUpdatedListener(listener, null, true, new String[]{Utils.getAccountType(cordova.getContext())});
    }
  }

  private void startLoginFlow(JSONArray data) {

    Log.d(oidc.class.getSimpleName(), "startLoginFlow");
    try {
      JSONObject json = data.getJSONObject(0);
      Intent i = new Intent(cordova.getContext(), AuthManagerActivity.class);
      i.putExtra("type", "login");
      i.putExtra("redirect_uri", json.has("redirect_uri") ? json.getString("redirect_uri") : null);
      i.putExtra("endpoint", json.has("endpoint") ? json.getString("endpoint") : null);
      i.putExtra("client_id", json.has("client_id") ? json.getString("client_id") : null);
      i.putExtra("scope", json.has("scope") ? json.getString("scope") : null);
      i.putExtra("prompt", json.has("prompt") ? json.getString("prompt") : null);

      PluginResult result = new PluginResult(PluginResult.Status.NO_RESULT);
      result.setKeepCallback(true);
      _callbackContext.sendPluginResult(result);
      cordova.startActivityForResult(this, i, REQUEST_LOGIN);
    } catch (Exception e) {
      PluginResult result = new PluginResult(PluginResult.Status.ERROR, e.getMessage());
      _callbackContext.sendPluginResult(result);
    }
  }

  private void startLogoutFlow(JSONArray data) {

    Log.d(oidc.class.getSimpleName(), "startLogoutFlow");

    try {
      JSONObject json = data.getJSONObject(0);
      Intent i = new Intent(cordova.getContext(), AuthManagerActivity.class);
      i.putExtra("type", "logout");
      i.putExtra("post_logout_redirect_uri", json.has("post_logout_redirect_uri") ? json.getString("post_logout_redirect_uri") : null);
      i.putExtra("endpoint", json.has("endpoint") ? json.getString("endpoint") : null);
      i.putExtra("id_token_hint", json.has("id_token_hint") ? json.getString("id_token_hint") : null);

      PluginResult result = new PluginResult(PluginResult.Status.NO_RESULT);
      result.setKeepCallback(true);
      _callbackContext.sendPluginResult(result);
      cordova.startActivityForResult(this, i, REQUEST_LOGOUT);
    } catch (Exception e) {
      PluginResult result = new PluginResult(PluginResult.Status.ERROR, e.getMessage());
      _callbackContext.sendPluginResult(result);
    }
  }

  private void setConnectionConfig(JSONArray data) {

    Log.d(oidc.class.getSimpleName(), "setConnectionConfig");
    try {
      boolean result = Utils.setConnectionConfig(cordova.getContext(), data.getJSONObject(0).toString());

      if (result) {
        _callbackContext.sendPluginResult(new PluginResult(PluginResult.Status.OK));
      } else {
        _callbackContext.sendPluginResult(new PluginResult(PluginResult.Status.ERROR, "Konfiguration konnte nicht gespeichert werden."));
      }
    } catch (Exception e) {
      _callbackContext.sendPluginResult(new PluginResult(PluginResult.Status.ERROR, e.getMessage()));
    }
  }

  private void getConnectionConfig() {

    Log.d(oidc.class.getSimpleName(), "getConnectionConfig");
    String result = Utils.getConfData(cordova.getContext(), "connectionconfig");
    if (result != null) {
      _callbackContext.sendPluginResult(new PluginResult(PluginResult.Status.OK, result));
    } else {
      _callbackContext.sendPluginResult(new PluginResult(PluginResult.Status.ERROR, "Fehler beim Laden der Config."));
    }
  }

  private void getOIDCConfig() {

    Log.d(oidc.class.getSimpleName(), "getOIDCConfig");
    String result = Utils.getConfData(cordova.getContext(), "config");
    if (result != null) {
      _callbackContext.sendPluginResult(new PluginResult(PluginResult.Status.OK, result));
    } else {
      _callbackContext.sendPluginResult(new PluginResult(PluginResult.Status.ERROR, "Fehler beim Laden der Config."));
    }
  }

  private void createAccount(JSONArray data) {
    Log.d(oidc.class.getSimpleName(), "createAccount");
    try {
      JSONObject json = data.getJSONObject(0);
      if (Utils.getAccount(cordova.getContext()) != null) {
        removeAccount();
      }

      String id_token = json.getString("id_token");
      String name = Utils.getNameFromToken(cordova.getContext(), id_token);

      Utils.createAccount(cordova.getContext(), name, json.toString());

      PluginResult result = new PluginResult(PluginResult.Status.OK);
      _callbackContext.sendPluginResult(result);
    } catch (Exception e) {
      PluginResult result = new PluginResult(PluginResult.Status.ERROR, e.getMessage());
      _callbackContext.sendPluginResult(result);
    }
  }

  private void removeAccount() {

    Log.d(oidc.class.getSimpleName(), "removeAccount");
    try {
      if (Utils.getAccount(cordova.getContext()) == null) {
        PluginResult result = new PluginResult(PluginResult.Status.ERROR, "Account nicht vorhanden.");
        _callbackContext.sendPluginResult(result);
      }

      Utils.removeAccount(cordova.getContext());

      PluginResult result = new PluginResult(PluginResult.Status.OK);
      _callbackContext.sendPluginResult(result);
    } catch (Exception e) {
      PluginResult result = new PluginResult(PluginResult.Status.ERROR, e.getMessage());
      _callbackContext.sendPluginResult(result);
    }
  }

  private void writeData(JSONArray data) {

    Log.d(oidc.class.getSimpleName(), "writeData");
    try {
      JSONObject json = data.getJSONObject(0);
      if (Utils.getAccount(cordova.getContext()) == null) {
        PluginResult result = new PluginResult(PluginResult.Status.ERROR, "Account nicht vorhanden.");
        _callbackContext.sendPluginResult(result);
      }

      Utils.writeData(cordova.getContext(), json.getString("key"), json.getString("data"));

      PluginResult result = new PluginResult(PluginResult.Status.OK);
      _callbackContext.sendPluginResult(result);
    } catch (Exception e) {
      PluginResult result = new PluginResult(PluginResult.Status.ERROR, e.getMessage());
      _callbackContext.sendPluginResult(result);
    }
  }

  /*
  {
    "body": string,
    "method": string,
    "headers": [{"key":"bla","value": "keks"},... ],
    "endpoint: string "url....."
  }
   */
  private void makeRequest(JSONArray data) {

    cordova.getThreadPool().execute(new Runnable() {
      public void run() {
        try {
          JSONObject params = data.getJSONObject(0);
          HashMap<String, String> headers = new HashMap<>();
          String body = params.has("body") ? params.getString("body") : null;
          int timeout = params.has("timeout") ? params.getInt("timeout") : 10000;

          if (params.has("headers") && params.get("headers") instanceof JSONArray) {
            JSONArray jheaders = params.getJSONArray("headers");
            for (int i = 0; i < jheaders.length(); i++) {
              JSONObject itm = jheaders.getJSONObject(i);
              headers.put(itm.getString("key"), itm.getString("value"));
            }
          } else if (params.has("headers") && params.get("headers") instanceof JSONObject) {
            JSONObject itm = (JSONObject) params.get("headers");
            headers.put(itm.getString("key"), itm.getString("value"));
          }


          String strjson = HttpRequest.sendHttpRequest(params.getString("endpoint"), params.getString("method"), body, timeout, headers);

          _callbackContext.sendPluginResult(new PluginResult(PluginResult.Status.OK, strjson));
        } catch (IOException e2) {
          _callbackContext.sendPluginResult(new PluginResult(PluginResult.Status.ERROR, Utils.getExceptionMessage(-1, e2.getMessage())));
        } catch (JSONException e1) {
          _callbackContext.sendPluginResult(new PluginResult(PluginResult.Status.ERROR, Utils.getExceptionMessage(-1, e1.getMessage())));
        } catch (Exception e) {
          _callbackContext.sendPluginResult(new PluginResult(PluginResult.Status.ERROR, Utils.getExceptionMessage(-1, e.getMessage())));
        }
      }
    });
  }

  private void readData(JSONArray data) {

    Log.d(oidc.class.getSimpleName(), "readData");
    try {
      if (Utils.getAccount(cordova.getContext()) == null) {
        PluginResult result = new PluginResult(PluginResult.Status.ERROR, "Account nicht vorhanden.");
        _callbackContext.sendPluginResult(result);
      }

      JSONObject json = data.getJSONObject(0);
      String state = Utils.readData(cordova.getContext(), json.getString("key"));

      PluginResult result = new PluginResult(PluginResult.Status.OK, state);
      _callbackContext.sendPluginResult(result);
    } catch (Exception e) {
      PluginResult result = new PluginResult(PluginResult.Status.ERROR, e.getMessage());
      _callbackContext.sendPluginResult(result);
    }
  }

  private void clear() {

    Log.d(oidc.class.getSimpleName(), "clear");
    try {
      if (Utils.getAccount(cordova.getContext()) == null) {
        PluginResult result = new PluginResult(PluginResult.Status.ERROR, "Account nicht vorhanden.");
        _callbackContext.sendPluginResult(result);
      }

      Utils.clear(cordova.getContext());

      PluginResult result = new PluginResult(PluginResult.Status.OK);
      _callbackContext.sendPluginResult(result);
    } catch (Exception e) {
      PluginResult result = new PluginResult(PluginResult.Status.ERROR, e.getMessage());
      _callbackContext.sendPluginResult(result);
    }
  }

  @Override
  public boolean execute(@NonNull final String action, final JSONArray data, final CallbackContext callbackContext) {

    Log.d(oidc.class.getSimpleName(), "execute - " + action);

    Log.d(oidc.class.getSimpleName(), data != null ? data.toString() : "NULL");
    _callbackContext = callbackContext;

    switch (action) {

      case "setConnectionConfig":
        setConnectionConfig(data);
        break;

      case "getConnectionConfig":
        getConnectionConfig();
        break;

      case "getOIDCConfig":
        getOIDCConfig();
        break;

      case "startLoginFlow":
        startLoginFlow(data);
        break;

      case "startLogoutFlow":
        startLogoutFlow(data);
        break;

      case "createAccount":
        createAccount(data);
        break;

      case "removeAccount":
        removeAccount();
        break;

      case "writeData":
        writeData(data);
        break;

      case "readData":
        readData(data);
        break;

      case "clear":
        clear();
        break;

      case "getPackageName":
        String packageName = cordova.getContext().getPackageName();
        Log.d(oidc.class.getSimpleName(), "getPackageName: " + packageName);
        callbackContext.sendPluginResult(new PluginResult(PluginResult.Status.OK, packageName));
        break;

      case "refreshNotification":
        refreshNotification();
        break;

      case "makeRequest":
        makeRequest(data);
        break;

      default:

        return false;
    }

    return true;
  }


}
