package de.mopsdom.oidc.cordova;


import android.accounts.AccountManager;
import android.accounts.OnAccountsUpdateListener;
import android.app.Activity;
import android.content.ComponentName;
import android.content.Intent;

import androidx.annotation.NonNull;

import org.apache.cordova.CallbackContext;
import org.apache.cordova.CordovaInterface;
import org.apache.cordova.CordovaPlugin;
import org.apache.cordova.CordovaWebView;
import org.apache.cordova.PluginResult;
import org.json.JSONArray;
import org.json.JSONObject;

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
    if (listener != null) {
      AccountManager accountManager = AccountManager.get(cordova.getContext());
      accountManager.removeOnAccountsUpdatedListener(listener);
      listener = null;
    }
  }

  @Override
  public void initialize(CordovaInterface cordova, CordovaWebView webView) {
    super.initialize(cordova, webView);

    this.registerAuthChangeHandler();
  }

  @Override
  public void onActivityResult(int requestCode, int resultCode, Intent intent) {
    super.onActivityResult(requestCode, resultCode, intent);

    if (_callbackContext != null && intent != null) {
      if (resultCode == Activity.RESULT_OK) {
        switch (requestCode) {
          case REQUEST_LOGIN:
          case REQUEST_LOGOUT:
            _callbackContext.sendPluginResult(new PluginResult(PluginResult.Status.OK, intent.getDataString()));
            break;

          case REQUEST_CONFIG:
          case REQUEST_CONNECTIONCONFIG:
            _callbackContext.sendPluginResult(new PluginResult(PluginResult.Status.OK, intent.getExtras().getString("config")));
            break;

          default:
            _callbackContext.sendPluginResult(new PluginResult(PluginResult.Status.OK));
            break;
        }
      } else {
        String error = "Unbekannter Fehler in onActivityResult!";
        if (intent.hasExtra("error")) {
          error = intent.getStringExtra("error");
        }

        _callbackContext.sendPluginResult(new PluginResult(PluginResult.Status.ERROR, error));
      }
    }
  }

  private void refreshNotification() {
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

    if (listener == null) {

      listener = accounts -> {
        refreshNotification();
      };
      AccountManager accountManager = AccountManager.get(cordova.getContext());
      accountManager.addOnAccountsUpdatedListener(listener, null, true, new String[]{Utils.getAccountType(cordova.getContext())});
    }
  }

  private void startLoginFlow(JSONArray data) {

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
    String result = Utils.getConfData(cordova.getContext(), "connectionconfig");
    if (result != null) {
      _callbackContext.sendPluginResult(new PluginResult(PluginResult.Status.OK, result));
    } else {
      _callbackContext.sendPluginResult(new PluginResult(PluginResult.Status.ERROR, "Fehler beim Laden der Config."));
    }
  }

  private void getOIDCConfig() {
    String result = Utils.getConfData(cordova.getContext(), "config");
    if (result != null) {
      _callbackContext.sendPluginResult(new PluginResult(PluginResult.Status.OK, result));
    } else {
      _callbackContext.sendPluginResult(new PluginResult(PluginResult.Status.ERROR, "Fehler beim Laden der Config."));
    }
  }

  private void createAccount(JSONArray data) {
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

  private void readData(JSONArray data) {
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
        callbackContext.sendPluginResult(new PluginResult(PluginResult.Status.OK, cordova.getContext().getPackageName()));
        break;

      case "refreshNotification":
        refreshNotification();
        break;

      default:

        return false;
    }

    return true;
  }


}
