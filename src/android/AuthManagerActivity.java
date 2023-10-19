package de.mopsdom.oidc.cordova;

import android.app.Activity;
import android.content.ComponentName;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;

import androidx.appcompat.app.AppCompatActivity;
import androidx.browser.customtabs.CustomTabsCallback;
import androidx.browser.customtabs.CustomTabsClient;
import androidx.browser.customtabs.CustomTabsIntent;
import androidx.browser.customtabs.CustomTabsServiceConnection;
import androidx.browser.customtabs.CustomTabsSession;

import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.UUID;

public class AuthManagerActivity extends AppCompatActivity {

  private CustomTabsServiceConnection connection;
  private CustomTabsSession session;
  private CustomTabsClient customTabsClient;
  private CustomTabsCallback callback;

  private FLOW_TYPE type;

  private String state;

  enum FLOW_TYPE {
    LOGIN(1),
    LOGOUT(2),
    NOT_SET (0);

     private final int value;
     FLOW_TYPE (int val) {
       this.value=val;
     }

     public int getVal() {
       return this.value;
     }
  }

  @Override
  public void onCreate(Bundle savedInstanceBundle) {
    super.onCreate(savedInstanceBundle);

    this.type=getIntent().getExtras()!=null&&
              getIntent().hasExtra("type")&&
              getIntent().getStringExtra("type").equalsIgnoreCase("logout") ?
              FLOW_TYPE.LOGOUT :
              (getIntent().getStringExtra("type").equalsIgnoreCase("login") ?   FLOW_TYPE.LOGIN : FLOW_TYPE.NOT_SET);

    if (this.type == FLOW_TYPE.NOT_SET)
    {
      setResult(Activity.RESULT_CANCELED,getErrorIntent(1,"type must be 'login' or 'logout'!"));
      finish();
      return;
    }

    String endpoint = getIntent().hasExtra("endpoint")?getIntent().getStringExtra("endpoint") : null;
    if (endpoint==null)
    {
      setResult(Activity.RESULT_CANCELED,getErrorIntent(3,"endpoint = null"));
      finish();
      return;
    }

    if (this.type==FLOW_TYPE.LOGIN)
    {
      String redirect_uri = getIntent().hasExtra("redirect_uri")?getIntent().getStringExtra("redirect_uri") : null;
      if (redirect_uri==null||redirect_uri.isEmpty())
      {
        setResult(Activity.RESULT_CANCELED,getErrorIntent(2,"redirect_uri = null"));
        finish();
        return;
      }
      else {
        redirect_uri = redirect_uri.replace("*", getPackageName());
      }

      String client_id = getIntent().hasExtra("client_id") ? getIntent().getStringExtra("client_id") : null;
      if (client_id == null||client_id.isEmpty()) {
        setResult(Activity.RESULT_CANCELED, getErrorIntent(4, "client_id = null"));
        finish();
        return;
      }

      String scope = getIntent().hasExtra("scope") ? getIntent().getStringExtra("scope") : null;
      if (scope == null||scope.isEmpty()) {
        setResult(Activity.RESULT_CANCELED, getErrorIntent(5, "scope = null"));
        finish();
        return;
      }

      boolean prompt = getIntent().hasExtra("prompt") && getIntent().getStringExtra("prompt").equalsIgnoreCase("true") ? true : false;
      state = UUID.randomUUID().toString();

      String strUri = null;
      try {
        strUri = endpoint+"?redirect_uri="+ URLEncoder.encode(redirect_uri, StandardCharsets.UTF_8.toString()) +
          "&client_id="+URLEncoder.encode(client_id, StandardCharsets.UTF_8.toString())+
          "&scope="+URLEncoder.encode(scope, StandardCharsets.UTF_8.toString())+
          "&prompt="+(prompt?"login":"none")+
          "&response_type=code"+
          "&response_mode=query"+
          "&state="+state;

        Uri uri = Uri.parse(strUri);
        process(uri);
      } catch (UnsupportedEncodingException e) {
        setResult(Activity.RESULT_CANCELED, getErrorIntent(6, e.getMessage()));
        finish();
      }
    }
    else
    {
      String id_token_hint = getIntent().hasExtra("id_token_hint") ? getIntent().getStringExtra("id_token_hint") : null;
      if (id_token_hint == null||id_token_hint.isEmpty()) {
        setResult(Activity.RESULT_CANCELED, getErrorIntent(7, "id_token_hint = null"));
        finish();
        return;
      }

      String post_logout_redirect_uri = getIntent().hasExtra("post_logout_redirect_uri") ? getIntent().getStringExtra("post_logout_redirect_uri") : null;
      if (post_logout_redirect_uri == null||post_logout_redirect_uri.isEmpty()) {
        setResult(Activity.RESULT_CANCELED, getErrorIntent(8, "post_logout_redirect_uri = null"));
        finish();
        return;
      }
      else {
        post_logout_redirect_uri = post_logout_redirect_uri.replace("*", getPackageName());
      }

      String sid = id_token_hint!=null?(String)Utils.getClaimFromToken(id_token_hint,"sid"):null;

      String strUri = null;
      try {
        strUri = endpoint+"?post_logout_redirect_uri="+ URLEncoder.encode(post_logout_redirect_uri, StandardCharsets.UTF_8.toString()) +
          "&id_token_hint="+id_token_hint;

        if (sid!=null)
        {
          strUri+="&sid="+sid;
        }

        Uri uri = Uri.parse(strUri);
        process(uri);
      } catch (UnsupportedEncodingException e) {
        setResult(Activity.RESULT_CANCELED, getErrorIntent(6, e.getMessage()));
        finish();
      }
    }
  }

  @Override
  public void onNewIntent(Intent data)
  {
    super.onNewIntent(data);

    if (data!=null&&data.getData()!=null) {
      setResult(Activity.RESULT_OK, data);
    }
    else
    {
      setResult(Activity.RESULT_CANCELED,getErrorIntent(9,"No result from Chrome Tab!"));
    }
    finish();
  }

  private Intent getErrorIntent(int code, String description)
  {
    Intent result = new Intent();
    result.putExtra("code",code);
    result.putExtra("error",description);
    return result;
  }

  private CustomTabsIntent getChromeTabIntent()
  {
    CustomTabsIntent.Builder builder = new CustomTabsIntent.Builder();
    builder.setShowTitle(true);
    builder.setUrlBarHidingEnabled(true);

    CustomTabsIntent customTabsIntent = builder.build();

    customTabsIntent.intent.addFlags(Intent.FLAG_ACTIVITY_NO_HISTORY);
    customTabsIntent.intent.addFlags(Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS);
    customTabsIntent.intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
    customTabsIntent.intent.addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP);

    return customTabsIntent;
  }

  private void process(Uri uri) {
    CustomTabsIntent chromeTabIntent = getChromeTabIntent();

    connection = new CustomTabsServiceConnection() {
      @Override
      public void onCustomTabsServiceConnected(ComponentName componentName, CustomTabsClient cTabsClient) {
        customTabsClient = cTabsClient;

        callback = new CustomTabsCallback() {
          @Override
          public void onNavigationEvent(int navigationEvent, Bundle extras) {

            if (navigationEvent == TAB_HIDDEN) {
              Log.w(AuthManagerActivity.class.getSimpleName(),"TAB_HIDDEN");
            } else if (navigationEvent == NAVIGATION_FAILED) {
              Log.w(AuthManagerActivity.class.getSimpleName(),"NAVIGATION_FAILED");
              setResult(Activity.RESULT_CANCELED,getErrorIntent(10,"NAVIGATION_FAILED"));
              finish();
            }
            else if (navigationEvent == NAVIGATION_FINISHED) {
              Log.w(AuthManagerActivity.class.getSimpleName(),"NAVIGATION_FINISHED");
            }
          }
        };

        customTabsClient.warmup(0L);
        session = customTabsClient.newSession(callback);

        session.mayLaunchUrl(uri, null, null);

        chromeTabIntent.intent.setData(uri);

        //startActivity(chromeTabIntent.intent);
        chromeTabIntent.launchUrl(AuthManagerActivity.this, uri);
      }

      @Override
      public void onServiceDisconnected(ComponentName name) {
        customTabsClient = null;
      }
    };

    CustomTabsClient.bindCustomTabsService(this, "com.android.chrome", connection);
  }


}
