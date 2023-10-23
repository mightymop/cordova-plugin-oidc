package de.mopsdom.oidc.cordova;

import android.util.Log;

import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.security.cert.X509Certificate;
import java.util.HashMap;

import javax.net.ssl.HostnameVerifier;
import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSession;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;

public class HttpRequest {

  public static void disableSSLCertificateValidation() throws Exception {
    TrustManager[] trustAllCertificates = new TrustManager[]{
      new X509TrustManager() {
        public X509Certificate[] getAcceptedIssuers() {
          return null;
        }

        public void checkClientTrusted(X509Certificate[] certs, String authType) {
        }

        public void checkServerTrusted(X509Certificate[] certs, String authType) {
        }
      }
    };

    SSLContext sc = SSLContext.getInstance("TLS");
    sc.init(null, trustAllCertificates, new java.security.SecureRandom());
    HttpsURLConnection.setDefaultSSLSocketFactory(sc.getSocketFactory());

    HttpsURLConnection.setDefaultHostnameVerifier(new HostnameVerifier() {
      public boolean verify(String hostname, SSLSession session) {
        return true;
      }
    });
  }


  public static String sendHttpRequest(String requestUrl, String method, String requestBody, int timeout, HashMap<String, String> headers) throws Exception {

    HttpURLConnection connection = null;
    try {
      disableSSLCertificateValidation();
      URL url = new URL(requestUrl);
      connection = (HttpURLConnection) url.openConnection();
      connection.setConnectTimeout(timeout);
      connection.setReadTimeout(timeout);
      connection.setRequestMethod(method);

      for (String key : headers.keySet()) {
        connection.setRequestProperty(key, headers.get(key));
      }

      // Wenn die Methode POST ist, sende den Request-Body
      if (method.equals("POST")) {
        connection.setDoOutput(true);
        OutputStream os = connection.getOutputStream();
        os.write(requestBody.getBytes(StandardCharsets.UTF_8));
        os.close();
      }
    } catch (Exception e) {
      throw new Exception(Utils.getExceptionMessage(-1, e.getMessage()));
    }

    // Lese die Antwort
    int responseCode = connection.getResponseCode();
    if (responseCode == HttpURLConnection.HTTP_OK) {
      BufferedReader reader = new BufferedReader(new InputStreamReader(connection.getInputStream()));
      String line;
      StringBuilder response = new StringBuilder();
      while ((line = reader.readLine()) != null) {
        response.append(line);
      }
      reader.close();
      JSONObject result = new JSONObject();
      result.put("status", responseCode);
      result.put("result", response.toString());
      return result.toString();
    } else {
      throw new Exception(Utils.getExceptionMessage(responseCode, connection.getResponseMessage()));
    }
  }
}
