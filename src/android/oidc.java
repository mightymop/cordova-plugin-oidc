package de.mopsdom.oidc.cordova;

import android.app.Activity;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
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
import org.json.JSONObject;

import java.util.Arrays;

public class oidc extends CordovaPlugin {

  private String CALLBACK_URL;
  private final static String TAG = oidc.class.getName();

  private final static int REQUEST_ID = 11223344;

  private CallbackContext _defaultCallbackContext;

  @Override
  public void initialize(CordovaInterface cordova, CordovaWebView webView) {
    super.initialize(cordova, webView);

    Log.d(TAG, "initialize");

    CALLBACK_URL = cordova.getActivity().getPackageName()+".RESULT";

    cordova.getActivity().runOnUiThread(new Runnable() {
      @Override
      public void run() {
        // Setze den Debug-Modus
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
          WebView.setWebContentsDebuggingEnabled(true);
        }
      }
    });

  }

  @Override
  public void onActivityResult(int requestCode, int resultCode, Intent intent) {
    super.onActivityResult(requestCode, resultCode, intent);
    Log.d(TAG, "onActivityResult");

    cordova.setActivityResultCallback(null);
    if (intent != null) {
      if (resultCode == Activity.RESULT_OK) {
        _defaultCallbackContext.sendPluginResult(new PluginResult(PluginResult.Status.OK));
      } else {
        String error = "Unbekannter Fehler in onActivityResult!";
        if (intent.hasExtra("error")) {
          error = intent.getStringExtra("error");
        }
        Log.e(TAG, error);
        _defaultCallbackContext.sendPluginResult(new PluginResult(PluginResult.Status.ERROR, error));
      }
    }
  }

  private void login() {

    Log.d(TAG, "login");
    sendAuthRequest("LOGIN");
  }

  private void logout() {

    Log.d(TAG, "logout");
    sendAuthRequest("LOGOUT");
  }

  private void sendAuthRequest(String callbackurlsuffix)
  {
    try {

      Intent i = new Intent("de.berlin.polizei.oidcsso."+callbackurlsuffix);
      i.putExtra("callbackurl", CALLBACK_URL);
      //i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);

      PluginResult result = new PluginResult(PluginResult.Status.NO_RESULT);
      result.setKeepCallback(true);
      _defaultCallbackContext.sendPluginResult(result);

      cordova.setActivityResultCallback(this);
      cordova.startActivityForResult(this, i, REQUEST_ID);
      //cordova.getActivity().startActivity(i);
    } catch (Exception e) {
      PluginResult result = new PluginResult(PluginResult.Status.ERROR, e.getMessage());
      _defaultCallbackContext.sendPluginResult(result);
    }
  }

  private void hasAccount(CallbackContext callbackContext) {

    Log.d(TAG, "hasAccount");
    if (getToken(null))
    {
      PluginResult result = new PluginResult(PluginResult.Status.OK, true);
      callbackContext.sendPluginResult(result);
    }
    else
    {
      PluginResult result = new PluginResult(PluginResult.Status.OK, false);
      callbackContext.sendPluginResult(result);
    }

  }

  private boolean getToken(CallbackContext callbackContext)
  {
    try
    {
      String path = "content://de.berlin.polizei.oidcsso.tokenprovider/token";
      Uri uri = Uri.parse(path);
      Cursor cursor = cordova.getActivity().getContentResolver().query(uri, null, null, null, null);
      if (cursor != null) {

        //Anzahl der Ergebnissezeilen (0 = keine Daten, User abgemeldet oder 1 für Daten wenn angemeldet)
        int anzRows = cursor.getCount();

        if (anzRows==0)
        {
          cursor.close();
          if (callbackContext!=null) {
            PluginResult result = new PluginResult(PluginResult.Status.ERROR, "not logged in");
            callbackContext.sendPluginResult(result);
          }
          return false;
        }

        if (callbackContext!=null) {
          String[] columnNames = cursor.getColumnNames();
          int index_id_token = Arrays.asList(columnNames).indexOf("id_token");
          int index_access_token = Arrays.asList(columnNames).indexOf("access_token");

          cursor.moveToFirst();

          String id_token = cursor.getString(index_id_token);
          String access_token = cursor.getString(index_access_token);

          JSONObject jresult = new JSONObject();
          jresult.put("id_token",id_token);
          jresult.put("access_token",access_token);

          PluginResult result = new PluginResult(PluginResult.Status.OK, jresult.toString());
          callbackContext.sendPluginResult(result);
        }

        cursor.close();
        return true;
      }
      else
      {
        //unbekannter Fehler!
        if (callbackContext!=null) {
          PluginResult result = new PluginResult(PluginResult.Status.ERROR, "unknown error");
          callbackContext.sendPluginResult(result);
        }
        return false;
      }

    } catch (Exception e) {
      Log.e(TAG,e.getMessage(),e);
      if (callbackContext!=null) {
        PluginResult result = new PluginResult(PluginResult.Status.ERROR, e.getMessage());
        callbackContext.sendPluginResult(result);
      }
      return false;
    }
  }

  private boolean getUserInfos(CallbackContext callbackContext)
  {
    try
    {
      String path = "content://de.berlin.polizei.oidcsso.tokenprovider/userinfos";
      Uri uri = Uri.parse(path);
      Cursor cursor = cordova.getActivity().getContentResolver().query(uri, null, null, null, null);
      if (cursor != null) {

        //Anzahl der Ergebnissezeilen (0 = keine Daten, User abgemeldet oder 1 für Daten wenn angemeldet)
        int anzRows = cursor.getCount();

        if (anzRows==0)
        {
          cursor.close();
          if (callbackContext!=null) {
            PluginResult result = new PluginResult(PluginResult.Status.ERROR, "not logged in");
            callbackContext.sendPluginResult(result);
          }
          return false;
        }

        if (callbackContext!=null) {
          String[] columnNames = cursor.getColumnNames();
          int index_userinfos = Arrays.asList(columnNames).indexOf("userinfos");

          cursor.moveToFirst();

          String userinfos = cursor.getString(index_userinfos);

          PluginResult result = new PluginResult(PluginResult.Status.OK, userinfos);
          callbackContext.sendPluginResult(result);
        }

        cursor.close();
        return true;
      }
      else
      {
        //unbekannter Fehler!
        if (callbackContext!=null) {
          PluginResult result = new PluginResult(PluginResult.Status.ERROR, "unknown error");
          callbackContext.sendPluginResult(result);
        }
        return false;
      }

    } catch (Exception e) {
      Log.e(TAG,e.getMessage(),e);
      if (callbackContext!=null) {
        PluginResult result = new PluginResult(PluginResult.Status.ERROR, e.getMessage());
        callbackContext.sendPluginResult(result);
      }
      return false;
    }
  }


  @Override
  public boolean execute(@NonNull final String action, final JSONArray data, final CallbackContext callbackContext) {

    Log.d(TAG, "execute - " + action);

    _defaultCallbackContext = callbackContext;

    switch (action) {

      case "usersinfos":
        getUserInfos(callbackContext);
        break;

      case "login":
        login();
        break;

      case "logout":
        logout();
        break;

      case "hasAccount":
        hasAccount(callbackContext);
        break;

      case "getToken":
        getToken(callbackContext);
        break;

      default:
        return false;
    }

    return true;
  }


}
