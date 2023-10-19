package de.mopsdom.oidc.cordova;

import static android.content.Context.MODE_PRIVATE;

import android.accounts.Account;
import android.accounts.AccountManager;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.content.SharedPreferences;
import android.net.Uri;
import android.util.Log;

import org.apache.cordova.PluginResult;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.Base64;

public class Utils {

  public final static String KEY_ISSUER = "ISSUER";
  public final static String KEY_CLIENT_ID = "CLIENT_ID";
  public final static String KEY_REDIRECT_URI = "REDIRECT_URI";
  public final static String KEY_LOGOUT_REDIRECT_URI = "LOGOUT_REDIRECT_URI";

  public final static String KEY_USERCLAIM = "USERCLAIM";
  public final static String KEY_ACCOUNTTYPE = "ACCOUNTTYPE";
  public final static String KEY_SCOPE = "SCOPE";
  public final static String KEY_NOTIFICATION = "NOTIFICATION";

  public final static String TAG = Utils.class.getSimpleName();


  public static void writeConfig(Context context, String json) {
    SharedPreferences authPrefs = context.getSharedPreferences("auth", MODE_PRIVATE);
    authPrefs.edit().putString("config", json).apply();
  }

  public static String readConfig(Context context) {
    SharedPreferences authPrefs = context.getSharedPreferences("auth", MODE_PRIVATE);
    String config = authPrefs.getString("config", null);

    if (config == null) {
      try {
        JSONObject jconf = new JSONObject();

        jconf.put(Utils.KEY_CLIENT_ID.toLowerCase(), Utils.getStringRessource(context, "default_client_id"));
        jconf.put(Utils.KEY_REDIRECT_URI.toLowerCase(), Utils.getStringRessource(context, "default_redirect_uri"));
        jconf.put(Utils.KEY_SCOPE.toLowerCase(), Utils.getStringRessource(context, "default_scope"));
        jconf.put(Utils.KEY_USERCLAIM.toLowerCase(), Utils.getStringRessource(context, "default_userclaim"));
        jconf.put(Utils.KEY_ISSUER.toLowerCase(), Utils.getStringRessource(context, "default_issuer"));
        jconf.put(Utils.KEY_LOGOUT_REDIRECT_URI.toLowerCase(), Utils.getStringRessource(context, "default_redirect_uri_logout"));
		    jconf.put(Utils.KEY_ACCOUNTTYPE.toLowerCase(), Utils.getStringRessource(context, "account_type"));
        jconf.put(Utils.KEY_NOTIFICATION.toLowerCase(), "true");

        writeConfig(context, jconf.toString());
      } catch (Exception e) {
        Log.e(TAG, e.getMessage(), e);
      }

      config = authPrefs.getString("config", null);
    }

    return config;
  }

  public static String getVal(Context context, String key) {
    String jsonconfig = readConfig(context);
    if (jsonconfig != null) {
      try {
        JSONObject config = new JSONObject(jsonconfig);
        return config.has(key.toLowerCase()) ? config.getString(key.toLowerCase()) : null;
      } catch (Exception e) {
        Log.e(TAG, e.getMessage(), e);
        return null;
      }
    }

    return null;
  }

  public static void setVal(Context context, String key, String val) {
    String jsonconfig = readConfig(context);

    JSONObject config;
    if (jsonconfig == null) {
      config = new JSONObject();
    } else {
      try {
        config = new JSONObject(jsonconfig);
      } catch (Exception e) {
        Log.e(TAG, e.getMessage(), e);
        config = new JSONObject();
      }
    }

    try {
      config.put(key.toLowerCase(), val);
    } catch (JSONException e) {
      Log.e(TAG, e.getMessage(), e);
    }

    writeConfig(context, config.toString());
  }

  public static String getStringRessource(Context context, String resourceName) {
    int resourceId = context.getResources().getIdentifier(resourceName, "string", context.getPackageName());

    if (resourceId != 0) {
      return context.getResources().getString(resourceId);
    } else {
      return null;
    }
  }

  public static String getNameFromToken(Context context, String id_token) {
    String userclaim = Utils.getVal(context, Utils.KEY_USERCLAIM);
    if (userclaim == null) {
      userclaim = getStringRessource(context,"default_userclaim");
      Utils.setVal(context, Utils.KEY_USERCLAIM, userclaim);
    }
    return (String) getClaimFromToken(id_token, userclaim);
  }

  public static Object getClaimFromToken(String id_token, String claim) {
    JSONObject payload = Utils.getPayload(id_token);
    try {
      return payload.get(claim);
    } catch (Exception e) {
      Log.e(TAG, e.getMessage());
      return null;
    }
  }

  public static JSONObject getPayload(String token) {

    String[] parts = token.split("\\.");
    String decodedString = decodeBase64(parts[1]);

    JSONObject payload = null;
    try {
      payload = new JSONObject(decodedString);
    } catch (JSONException e) {
      Log.e(TAG, e.getMessage());
      return null;
    }

    return payload;
  }

  private static String decodeBase64(String data) {
    byte[] result = null;
    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
      result = Base64.getDecoder().decode(data);
    } else {
      result = android.util.Base64.decode(data, android.util.Base64.DEFAULT);
    }
    return new String(result);
  }



  public static boolean createAccount(Context context, String username, String state) {

    Account account = getAccount(context);

    if (account != null) {
      return false;
    }

    try
    {
      Uri uri = Uri.parse("content://de.mopsdom.oidc.configapp.configprovider/account");

      ContentResolver contentResolver = context.getContentResolver();

      ContentValues values = new ContentValues();
      values.put("name", username);

      int result = contentResolver.update(uri, values,null,null);

      if (result == 1) {
        return true;
      } else {
        return false;
      }
    }
    catch (Exception e)
    {
      return false;
    }

  }

  public static String getAccountType(Context context) {
    String accounttype = Utils.getVal(context, Utils.KEY_ACCOUNTTYPE);
    if (accounttype == null) {
      accounttype = getStringRessource(context,"account_type");
      Utils.setVal(context, Utils.KEY_ACCOUNTTYPE, accounttype);
    }
    return accounttype;
  }

  public static void writeData(Context context, String key, String data) {
    AccountManager accountManager = getAccountManager(context);
    Account account = getAccount(context);
    if (account != null) {
      accountManager.setUserData(account, key, data);
    }
  }

  public static void clear(Context context) {
    AccountManager accountManager = getAccountManager(context);
    Account account = getAccount(context);
    if (account != null) {
      String name = account.name;
      removeAccount(context);
      createAccount(context,name,null);
    }
  }

  public static boolean removeAccount(Context context) {

    Account account = getAccount(context);

    if (account == null) {
      return false;
    }

    try
    {
      Uri uri = Uri.parse("content://de.mopsdom.oidc.configapp.configprovider/account");

      ContentResolver contentResolver = context.getContentResolver();

      int result = contentResolver.delete(uri, null,null);

      if (result == 1) {
        return true;
      } else {
        return false;
      }
    }
    catch (Exception e)
    {
      return false;
    }
  }

  public static String readData(Context context, String key) {
    AccountManager accountManager = getAccountManager(context);
    Account account = getAccount(context);
    if (account != null) {
      return accountManager.getUserData(account, key);
    }
    return null;
  }

  private static AccountManager getAccountManager(Context context) {
    return context.getSystemService(AccountManager.class);
  }

  public static Account getAccount(Context context) {
    Account result[] = getAccountManager(context).getAccountsByType(Utils.getAccountType(context));
    if (result != null && result.length > 0) {
      return result[0];
    }
    return null;
  }

}
