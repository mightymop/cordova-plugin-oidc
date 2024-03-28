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
import java.util.ArrayList;
import java.util.HashMap;

public class oidc extends CordovaPlugin {

  private static final int REQUEST_LOGIN = 10000;
  private static final int REQUEST_LOGOUT = 1000;

  private CallbackContext _defaultCallbackContext;
  private CallbackContext _accountListenerCallbackContext;
  private CallbackContext _loginLogoutCallbackContext;
  private OnAccountsUpdateListener listener;

  private ArrayList<Intent> _listNotificationIntents = new ArrayList<>();

  private boolean isInBackground = false;

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

    if (intent != null) {
      if (resultCode == Activity.RESULT_OK) {
        switch (requestCode) {
          case REQUEST_LOGIN:
          case REQUEST_LOGOUT:
            Log.d(oidc.class.getSimpleName(), intent.getDataString());
            if (_loginLogoutCallbackContext!=null) {
              _loginLogoutCallbackContext.sendPluginResult(new PluginResult(PluginResult.Status.OK, intent.getDataString()));
            }
            else
            if (_defaultCallbackContext!=null) {
              _defaultCallbackContext.sendPluginResult(new PluginResult(PluginResult.Status.OK, intent.getDataString()));
            }
            else
            {
              Log.e(oidc.class.getSimpleName(), "NO CALLBACK CONTEXT FOUNDN!!!");
            }
            break;

          default:
            Log.d(oidc.class.getSimpleName(), "NO DATA - Default");
            _defaultCallbackContext.sendPluginResult(new PluginResult(PluginResult.Status.OK));
            break;
        }
      } else {
        String error = "Unbekannter Fehler in onActivityResult!";
        if (intent.hasExtra("error")) {
          error = intent.getStringExtra("error");
        }
        Log.e(oidc.class.getSimpleName(), error);
        _defaultCallbackContext.sendPluginResult(new PluginResult(PluginResult.Status.ERROR, error));
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

      if (!this.isAppInBackground()) {
        cordova.getContext().startService(intent);
      }
      else
      {
        this._listNotificationIntents.add(intent);
      }

    } catch (Exception e) {
      Log.e(oidc.class.getSimpleName(), e.getMessage(),e);
    }
  }

  @Override
  public void onResume(boolean multitasking) {
    super.onResume(multitasking);
    isInBackground = false;
    while (this._listNotificationIntents.size()>0)
    {
      Intent first = this._listNotificationIntents.get(0);
      this._listNotificationIntents.remove(0);
      try {
        cordova.getContext().startService(first);
      }
      catch (Exception e)
      {

      }
    }
  }

  @Override
  public void onPause(boolean multitasking) {
    super.onPause(multitasking);
    isInBackground = true;
  }

  public boolean isAppInBackground() {
    return isInBackground;
  }

  public void registerAuthChangeHandler() {

    Log.d(oidc.class.getSimpleName(), "registerAuthChangeHandler");
    if (listener == null) {

      listener = accounts -> {
        refreshNotification();
        if (this._accountListenerCallbackContext!=null)
        {
          if (accounts!=null&&accounts.length>0) {
            PluginResult res = new PluginResult(PluginResult.Status.OK);
            res.setKeepCallback(true);
            this._accountListenerCallbackContext.sendPluginResult(res);
          }
          else
          if (accounts!=null&&accounts.length==0||accounts==null) {
            PluginResult res = new PluginResult(PluginResult.Status.ERROR);
            res.setKeepCallback(true);
            this._accountListenerCallbackContext.sendPluginResult(res);
          }
        }
      };
      AccountManager accountManager = AccountManager.get(cordova.getContext());
      accountManager.addOnAccountsUpdatedListener(listener, null, true, new String[]{Utils.getAccountType(cordova.getContext())});
    }
  }

  private void registerAccountListener(CallbackContext callbackContext) {
    this._accountListenerCallbackContext = callbackContext;
  }

  private void startLoginFlow(JSONArray data,CallbackContext callbackContext) {

    Log.d(oidc.class.getSimpleName(), "startLoginFlow");
    this._loginLogoutCallbackContext = callbackContext;
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
      callbackContext.sendPluginResult(result);
      cordova.startActivityForResult(this, i, REQUEST_LOGIN);
    } catch (Exception e) {
      PluginResult result = new PluginResult(PluginResult.Status.ERROR, e.getMessage());
      callbackContext.sendPluginResult(result);
    }
  }

  private void startLogoutFlow(JSONArray data,CallbackContext callbackContext) {

    Log.d(oidc.class.getSimpleName(), "startLogoutFlow");
    this._loginLogoutCallbackContext = callbackContext;
    try {
      JSONObject json = data.getJSONObject(0);
      Intent i = new Intent(cordova.getContext(), AuthManagerActivity.class);
      i.putExtra("type", "logout");
      i.putExtra("post_logout_redirect_uri", json.has("post_logout_redirect_uri") ? json.getString("post_logout_redirect_uri") : null);
      i.putExtra("endpoint", json.has("endpoint") ? json.getString("endpoint") : null);
      i.putExtra("id_token_hint", json.has("id_token_hint") ? json.getString("id_token_hint") : null);

      PluginResult result = new PluginResult(PluginResult.Status.NO_RESULT);
      result.setKeepCallback(true);
      callbackContext.sendPluginResult(result);
      cordova.startActivityForResult(this, i, REQUEST_LOGOUT);
    } catch (Exception e) {
      PluginResult result = new PluginResult(PluginResult.Status.ERROR, e.getMessage());
      callbackContext.sendPluginResult(result);
    }
  }

  private void setConnectionConfig(JSONArray data,CallbackContext callbackContext) {

    Log.d(oidc.class.getSimpleName(), "setConnectionConfig");
    try {
      boolean result = Utils.setConnectionConfig(cordova.getContext(), data.getJSONObject(0).toString());

      if (result) {
        callbackContext.sendPluginResult(new PluginResult(PluginResult.Status.OK));
      } else {
        callbackContext.sendPluginResult(new PluginResult(PluginResult.Status.ERROR, "Konfiguration konnte nicht gespeichert werden."));
      }
    } catch (Exception e) {
      callbackContext.sendPluginResult(new PluginResult(PluginResult.Status.ERROR, e.getMessage()));
    }
  }

  private void getConnectionConfig(CallbackContext callbackContext) {

    Log.d(oidc.class.getSimpleName(), "getConnectionConfig");
    String result = Utils.getConfData(cordova.getContext(), "connectionconfig");
    if (result != null) {
      callbackContext.sendPluginResult(new PluginResult(PluginResult.Status.OK, result));
    } else {
      callbackContext.sendPluginResult(new PluginResult(PluginResult.Status.ERROR, "Fehler beim Laden der Config."));
    }
  }

  private void getOIDCConfig(CallbackContext callbackContext) {

    Log.d(oidc.class.getSimpleName(), "getOIDCConfig");
    String result = Utils.getConfData(cordova.getContext(), "config");
    if (result != null) {
      callbackContext.sendPluginResult(new PluginResult(PluginResult.Status.OK, result));
    } else {
      callbackContext.sendPluginResult(new PluginResult(PluginResult.Status.ERROR, "Fehler beim Laden der Config."));
    }
  }

  private void createAccount(JSONArray data,CallbackContext callbackContext) {
    Log.d(oidc.class.getSimpleName(), "createAccount");
    try {
      JSONObject json = data.getJSONObject(0);
      if (Utils.getAccount(cordova.getContext()) != null) {
        removeAccount(null);
      }

      String id_token = json.getString("id_token");
      String name = Utils.getNameFromToken(cordova.getContext(), id_token);

      Utils.createAccount(cordova.getContext(), name, json.toString());

      if (callbackContext!=null)
      {
        PluginResult result = new PluginResult(PluginResult.Status.OK);
        callbackContext.sendPluginResult(result);
      }
    } catch (Exception e) {
      if (callbackContext!=null)
      {
        PluginResult result = new PluginResult(PluginResult.Status.ERROR, e.getMessage());
        callbackContext.sendPluginResult(result);
      }
    }
  }

  private void removeAccount(CallbackContext callbackContext) {

    Log.d(oidc.class.getSimpleName(), "removeAccount");
    try {
      if (Utils.getAccount(cordova.getContext()) == null) {
        if (callbackContext!=null)
        {
          PluginResult result = new PluginResult(PluginResult.Status.ERROR, "Account nicht vorhanden.");
          callbackContext.sendPluginResult(result);
        }
      }

      Utils.removeAccount(cordova.getContext());

      if (callbackContext!=null)
      {
        PluginResult result = new PluginResult(PluginResult.Status.OK);
        callbackContext.sendPluginResult(result);
      }
    } catch (Exception e) {
      if (callbackContext!=null)
      {
        PluginResult result = new PluginResult(PluginResult.Status.ERROR, e.getMessage());
        callbackContext.sendPluginResult(result);
      }
    }
  }

  private void writeData(JSONArray data,CallbackContext callbackContext) {

    Log.d(oidc.class.getSimpleName(), "writeData");
    try {
      JSONObject json = data.getJSONObject(0);
      if (Utils.getAccount(cordova.getContext()) == null) {
        PluginResult result = new PluginResult(PluginResult.Status.ERROR, "Account nicht vorhanden.");
        callbackContext.sendPluginResult(result);
      }

      Utils.writeData(cordova.getContext(), json.getString("key"), json.getString("data"));

      PluginResult result = new PluginResult(PluginResult.Status.OK);
      callbackContext.sendPluginResult(result);
    } catch (Exception e) {
      PluginResult result = new PluginResult(PluginResult.Status.ERROR, e.getMessage());
      callbackContext.sendPluginResult(result);
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
  private void makeRequest(JSONArray data, CallbackContext callbackContext) {

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

          JSONObject json  = new JSONObject(strjson);
          if (json.getInt("status")==200)
          {
            callbackContext.sendPluginResult(new PluginResult(PluginResult.Status.OK, strjson));
          }
          else
          {
            callbackContext.sendPluginResult(new PluginResult(PluginResult.Status.ERROR, Utils.getExceptionMessage(json.getInt("status"),json.getString("result"))));
          }
        } catch (IOException e2) {
          callbackContext.sendPluginResult(new PluginResult(PluginResult.Status.ERROR, Utils.getExceptionMessage(-1, e2.getMessage())));
        } catch (JSONException e1) {
          callbackContext.sendPluginResult(new PluginResult(PluginResult.Status.ERROR, Utils.getExceptionMessage(-1, e1.getMessage())));
        } catch (Exception e) {
          callbackContext.sendPluginResult(new PluginResult(PluginResult.Status.ERROR, Utils.getExceptionMessage(-1, e.getMessage())));
        }
      }
    });
  }

  private void readData(JSONArray data, CallbackContext callbackContext) {

    Log.d(oidc.class.getSimpleName(), "readData");
    try {
      if (Utils.getAccount(cordova.getContext()) == null) {
        PluginResult result = new PluginResult(PluginResult.Status.ERROR, "Account nicht vorhanden.");
        callbackContext.sendPluginResult(result);
      }

      JSONObject json = data.getJSONObject(0);
      String state = Utils.readData(cordova.getContext(), json.getString("key"));

      PluginResult result = new PluginResult(PluginResult.Status.OK, state);
      callbackContext.sendPluginResult(result);
    } catch (Exception e) {
      PluginResult result = new PluginResult(PluginResult.Status.ERROR, e.getMessage());
      callbackContext.sendPluginResult(result);
    }
  }

  private void clear(CallbackContext callbackContext) {

    Log.d(oidc.class.getSimpleName(), "clear");
    try {
      if (Utils.getAccount(cordova.getContext()) == null) {
        PluginResult result = new PluginResult(PluginResult.Status.ERROR, "Account nicht vorhanden.");
        callbackContext.sendPluginResult(result);
      }

      Utils.clear(cordova.getContext());

      PluginResult result = new PluginResult(PluginResult.Status.OK);
      callbackContext.sendPluginResult(result);
    } catch (Exception e) {
      PluginResult result = new PluginResult(PluginResult.Status.ERROR, e.getMessage());
      callbackContext.sendPluginResult(result);
    }
  }

  @Override
  public boolean execute(@NonNull final String action, final JSONArray data, final CallbackContext callbackContext) {

    Log.d(oidc.class.getSimpleName(), "execute - " + action);

    Log.d(oidc.class.getSimpleName(), data != null ? data.toString() : "NULL");
    _defaultCallbackContext = callbackContext;

    switch (action) {

      case "setConnectionConfig":
        setConnectionConfig(data,callbackContext);
        break;

      case "getConnectionConfig":
        getConnectionConfig(callbackContext);
        break;

      case "getOIDCConfig":
        getOIDCConfig(callbackContext);
        break;

      case "startLoginFlow":
        startLoginFlow(data,callbackContext);
        break;

      case "startLogoutFlow":
        startLogoutFlow(data,callbackContext);
        break;

      case "createAccount":
        createAccount(data,callbackContext);
        break;

      case "removeAccount":
        removeAccount(callbackContext);
        break;

      case "writeData":
        writeData(data,callbackContext);
        break;

      case "readData":
        readData(data,callbackContext);
        break;

      case "clear":
        clear(callbackContext);
        break;

      case "getPackageName":
        String packageName = cordova.getContext().getPackageName();
        Log.d(oidc.class.getSimpleName(), "getPackageName: " + packageName);
        callbackContext.sendPluginResult(new PluginResult(PluginResult.Status.OK, packageName));
        break;

      case "refreshNotification":
        refreshNotification();
        callbackContext.sendPluginResult(new PluginResult(PluginResult.Status.OK));
        break;

      case "registerAccountListener":
        registerAccountListener(callbackContext);
        break;

      case "makeRequest":
        makeRequest(data,callbackContext);
        break;

      default:

        return false;
    }

    return true;
  }


}
