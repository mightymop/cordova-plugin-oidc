package de.mopsdom.oidc.cordova;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;

import androidx.appcompat.app.AppCompatActivity;

public class RedirectActivity extends AppCompatActivity {

  @Override
  public void onCreate(Bundle savedInstanceBundle) {
    super.onCreate(savedInstanceBundle);

    startActivity(getCallbackIntent(getIntent().getData()));
    finish();
  }

  private Intent getCallbackIntent(Uri uri)
  {
    Intent authMgrIntent = new Intent(this, AuthManagerActivity.class);
    authMgrIntent.setFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP|Intent.FLAG_ACTIVITY_CLEAR_TOP);
    authMgrIntent.setData(uri);
    return authMgrIntent;
  }
}
