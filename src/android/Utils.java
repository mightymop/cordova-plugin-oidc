package de.mopsdom.oidc.cordova;

import android.accounts.Account;
import android.accounts.AccountManager;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.util.Log;

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

  public static final String AUTHORITY = "de.mopsdom.oidc.configapp.configprovider";

  public final static String TAG = Utils.class.getSimpleName();


  public static void writeConfig(Context context, String json) {
    setConnectionConfig(context, json);
  }

  public static boolean setConnectionConfig(Context context, String data) {

    try {
      Uri uri = Uri.parse("content://" + AUTHORITY + "/connectionconfig");

      ContentResolver contentResolver = context.getContentResolver();

      ContentValues values = new ContentValues();
      values.put("config", data);

      int result = contentResolver.update(uri, values, null, null);

      return result == 1;
    } catch (Exception e) {
      return false;
    }
  }

  public static String getConfData(Context context, String type) {
    try {
      Uri uri = Uri.parse("content://" + AUTHORITY + "/" + type);

      ContentResolver contentResolver = context.getContentResolver();

      Cursor cursor = contentResolver.query(uri, null, null, null, null);

      if (cursor != null) {
        if (cursor.moveToFirst()) {
          int colIndex = cursor.getColumnIndex("result");
          return cursor.getString(colIndex);
        }
        cursor.close();
      }

      return null;
    } catch (Exception e) {
      Log.e(oidc.class.getSimpleName(), e.getMessage());
      return null;
    }
  }

  public static String getVal(Context context, String key) {
    String jsonconfig = getConfData(context, "connectionconfig");
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
    String jsonconfig = getConfData(context, "connectionconfig");

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
      userclaim = getStringRessource(context, "default_userclaim");
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

    try {
      Uri uri = Uri.parse("content://de.mopsdom.oidc.configapp.configprovider/account");

      ContentResolver contentResolver = context.getContentResolver();

      ContentValues values = new ContentValues();
      values.put("name", username);
      values.put("state", state);

      contentResolver.insert(uri, values);

      return true;

    } catch (Exception e) {
      return false;
    }

  }

  public static String getAccountType(Context context) {
    String accounttype = Utils.getVal(context, Utils.KEY_ACCOUNTTYPE);
    if (accounttype == null) {
      accounttype = getStringRessource(context, "account_type");
      Utils.setVal(context, Utils.KEY_ACCOUNTTYPE, accounttype);
    }
    return accounttype;
  }

  public static void writeData(Context context, String key, String data) {

    Uri uri = Uri.parse("content://de.mopsdom.oidc.configapp.configprovider/account");

    ContentResolver contentResolver = context.getContentResolver();

    ContentValues values = new ContentValues();
    values.put("key", key);
    values.put("data", data);

    contentResolver.update(uri, values, null, null);
  }

  public static void clear(Context context) {
    /*Account account = getAccount(context);
    if (account != null) {
      String name = account.name;
      removeAccount(context);
      createAccount(context,name,null);
    }*/
    writeData(context, "state", null);
  }

  public static boolean removeAccount(Context context) {

    Account account = getAccount(context);

    if (account == null) {
      return false;
    }

    try {
      Uri uri = Uri.parse("content://de.mopsdom.oidc.configapp.configprovider/account");

      ContentResolver contentResolver = context.getContentResolver();

      int result = contentResolver.delete(uri, null, null);

      return result == 1;
    } catch (Exception e) {
      return false;
    }
  }

  public static String readData(Context context, String key) {
    Uri uri = Uri.parse("content://de.mopsdom.oidc.configapp.configprovider/account");

    ContentResolver contentResolver = context.getContentResolver();

    Cursor cursor = contentResolver.query(uri, new String[]{key}, null, null);

    try {
      if (cursor != null && cursor.getCount() > 0) {
        if (cursor.moveToNext()) {
          int iCol = cursor.getColumnIndex(key);
          String data = cursor.getString(iCol);
          return data;
        }
      }

      return null;

    } catch (Exception e) {
      Log.e(Utils.class.getSimpleName(), e.getMessage());
      return null;
    } finally {
      if (cursor != null) {
        cursor.close();
      }
    }

  }

  private static AccountManager getAccountManager(Context context) {
    return context.getSystemService(AccountManager.class);
  }

  public static Account getAccount(Context context) {
    Account[] result = getAccountManager(context).getAccountsByType(Utils.getAccountType(context));
    if (result != null && result.length > 0) {
      return result[0];
    }
    return null;
  }

}
