# Android:

### 1. Add plugin
cordova plugin add https://github.com/mightymop/cordova-plugin-oidc.git
### 2. For Typescript add following code to main ts file: 
/// &lt;reference types="cordova-plugin-oidc" /&gt;<br/>

### 3. Build And Install Authenticator for Android and configure
https://github.com/mightymop/cordova-plugin-oidc/tree/master

### 4. Usage:

methods:

success = callback
error   = callback

```
	startLoginFlow({
					redirect_uri: string,
					endpoint: string,
					client_id: string,
					scope: string,
					prompt: "true"|"none"
				},success,error);
				
	//	result - will be saved with saveData() to key "state"
	//	{
	//		"access_token": "eyJ0eXAiOiJKV1QiLCJhbGciOiJSUzI1NiIsIng1dCI6Ik5HVEZ2ZEstZnl0aEV1Q...",
	//		"token_type": "Bearer",
	//		"expires_in": 3599,
	//		"refresh_token": "AwABAAAAvPM1KaPlrEqdFSBzjqfTGAMxZGUTdM0t4B4...",
	//		"refresh_token_expires_in": 28800,
	//		"id_token": "eyJ0eXAiOiJKV1QiLCJhbGciOiJub25lIn0.eyJhdWQiOiIyZDRkMTFhMi1mODE0LTQ2YTctOD...",
	//	}
		
	startLogoutFlow({
					post_logout_redirect_uri: string,
					endpoint: string,
					id_token_hint: string
				},success,error);
	
				
	getOIDCConfig(success,error) 
	//Ask the plugin 'cordova-plugin-oidc-configapp' for configuration data. If the request is successful, it will be saved using 'setOIDCConfigLocal'.
	
	setConnectionConfig(param,success, error)
	//Send Params to 'cordova-plugin-oidc-configapp'
	
	getConnectionConfig(success, error)
	//get connectionconfig params from 'cordova-plugin-oidc-configapp' for startLoginFlow
	
	saveData(key,data)
	//saves data to storage
	//token data will be saved to key "state"
	
	getData(key,success,error)
	//get data from storage
	//token data will be loaded from key "state"
	
	clearStorage()
	//clears the storage
	
	getOIDCConfigLocal() : string
	//get local saved openid-configuration data from sessionstorage
	
	setOIDCConfigLocal(config) 
	//set local saved openid-configuration data in sessionstorage
	
	getAccessToken(success,error)
	//get current access_token
	//if token is expired, function will try to perform a refresh
	//result: access_token as string
	
	getIDToken(success,error)
	//get current id_token
	//result: id_token as string
	
	getOIDCConfigFromIssuer(issuer, success, fkterror)
	//load openid-configuration from issuer (automatically saved with setOIDCConfigLocal)
	
	refreshNotification()
	//tries to refresh the notification in 'cordova-plugin-oidc-configapp'
	
	//to check if a user is authenticated try to load "state"-data with getData("state",success,error) 
```
