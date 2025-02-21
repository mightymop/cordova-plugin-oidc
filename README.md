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
error(err:any)   = callback with error message

```
	for usage with private LoginApp only! Version 2.0.0 needed

	login(success,error);
	
	logout(success,error);
	
	getAccessToken(success,error)
	//get current access_token
	//if token is expired, function will try to perform a refresh
	//result: access_token as string
	
	getIDToken(success,error)
	//get current id_token
	//result: id_token as string
	
	hasAccount()
	//checks if user is logged in
		
```
