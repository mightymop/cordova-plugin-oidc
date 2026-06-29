
### 1. Add plugin
cordova plugin add https://github.com/mightymop/cordova-plugin-oidc.git

### 2. For Typescript add following code to main ts file: 
/// &lt;reference types="cordova-plugin-oidc" /&gt;<br/>

### 3. Usage:

in confix.xml add:

<edit-config file="app/src/main/AndroidManifest.xml" mode="merge" target="/manifest/application">
  <application android:networkSecurityConfig="@xml/network_security_config" />
</edit-config>

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
	
	@deprecated
	hasAccount(success(boolean)=>{},error(e)=>{})
	//checks if user is logged in
	//use isAuthenticated(success(boolean)=>{},error(e)=>{}) instead!
	
	- add config json to login() and logout() as parameter...

	e.g. 
	{
	  issuer : "https://dc2019.poldom.local/adfs",
	  clientId: "client-UUID-ID",
	  scopes: "openid profile email offline_access api",
	  redirectURI: "myapp.custom.com://",
	  prompt: true
	};	
```

# iOS

- add the AppGroup group.plugin.cordova.oidc for SSO and SLO (optional)

