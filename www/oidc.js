var exec = require('cordova/exec');
var PLUGIN_NAME = 'oidc';

var oidc = {
	maxRetries : 3, 
	retryDelay : 1000,
	timeout : 10000,
	clockskrew: 30,
	accountStorage: {
		getItem:function(key,success,error) {
			exec(success, error, PLUGIN_NAME, 'readData', [{key}]);
		},
		setItem:function(key, data){
			exec(()=>{
				console.debug('Daten geschrieben');
			}, (err)=>{
				console.error(err);
			}, PLUGIN_NAME, 'writeData', [{key:key,data:data}]);
		},
		clear:function(){
			exec(()=>{
				console.debug('Daten gelöscht');
			}, (err)=>{
				console.error(err);
			}, PLUGIN_NAME, 'clear', []);
		}
	},
	storage: this.accountStorage,
	startLoginFlow: function (param,success, error) {
		exec((auchcodeUrl)=>{
			const url = new URL(auchcodeUrl);
  
			const params = new URLSearchParams(url.search);
			const code = params.get('code');
	  
			if (code) {
				this.performTokenRequest(code, param.client_id, param.redirect_uri, success, error);
			} else {
				console.log('Der "code"-Parameter wurde nicht gefunden.');
				error('Der "code"-Parameter wurde nicht gefunden.');
				return;
			}
			
		}, error, PLUGIN_NAME, 'startLoginFlow', [param]);
	},
	startLogoutFlow: function (param,success, error) {
		exec((res)=>{
			exec(()=>{success(res);},error,PLUGIN_NAME,'removeAccount',[]);
		}, (err)=>{
			exec(()=>{success();},error,PLUGIN_NAME,'removeAccount',[]);
		}, PLUGIN_NAME, 'startLogoutFlow', [param]);
	},
	getOIDCConfig: function (success, error) {
		exec((config)=>{
			let data = typeof config==='string'?JSON.parse(config):config;
			
			this.setOIDCConfigLocal(data);
			success(data);
		}, error, PLUGIN_NAME, 'getOIDCConfig', []);
	},
	setConnectionConfig: function (param,success, error) {
		exec(success, error, PLUGIN_NAME, 'setConnectionConfig', [param]);
	},
	getConnectionConfig: function (success, error) {
		exec(success, error, PLUGIN_NAME, 'getConnectionConfig', []);
	},
	getPackageName: function (success, error) {
		exec(success, error, PLUGIN_NAME, 'getPackageName', []);
	},
	saveData: function (key,data) {
		try {
			if (this.storage===sessionStorage||this.storage===localStorage)
			{
				this.storage.setItem(key, JSON.stringify(data));
			}
			else
			{
				this.accountStorage.setItem(key, JSON.stringify(data));
			}
			
		} catch (error) {
			console.error('Error writing to storage:', error);
		}
	},
	getData: function (key,success,error) {
		try {
			if (this.storage===sessionStorage||this.storage===localStorage)
			{
				const value = this.storage.getItem(key);
				if (value){
					success(JSON.parse(value));
				}
				else {
					error();
				}
			}
			else
			{
				this.accountStorage.getItem(key,success,error);
			}
			
		} catch (err) {
			console.error('Error reading from storage:', err);
			error();
		}
	},
	clearStorage: function () {
		try {
			if (this.storage===sessionStorage||this.storage===localStorage)
			{
				this.storage.clear();
			}
			else
			{
				this.accountStorage.clear();
			}
		} catch (error) {
			console.error('Error clearing storage:', error);
		}
	},
	getOIDCConfigLocal: function (){
		try {
			const value = sessionStorage.getItem('config');
			return value ? JSON.parse(value) : null;
		} catch (error) {
			console.error('Error reading from storage:', error);
			return null;
		}
	},
	setOIDCConfigLocal: function (config){
			try {
			sessionStorage.setItem('config', JSON.stringify(config));
		} catch (error) {
			console.error('Error writing to storage:', error);
		}
	},
	useSessionStorage: function() {
		this.getData('state',(data)=>{
			this.clearStorage();
			this.storage = sessionStorage;
			this.saveData('state',data);
		},(err)=>{
			console.error(err);
		});	
	},
	useLocalStorage: function() {
		this.getData('state',(data)=>{
			this.clearStorage();
			this.storage = localStorage;
			this.saveData('state',data);
		},(err)=>{
			console.error(err);
		});	
	},
	useAccountStorage: function() {
		this.getData('state',(data)=>{
			this.clearStorage();
			this.storage = this.accountStorage;
			this.saveData('state',data);
		},(err)=>{
			console.error(err);
		});	
	},
	mergeData: function(refreshedData) {
		this.getData('state',(state)=>{
			local.access_token = refreshedData.access_token;
			local.expires_in = refreshedData.expires_in;
			local.id_token = refreshedData.id_token;
			local.scope = refreshedData.scope?refreshedData.scope:local.scope;
			local.refresh_token = refreshedData.refresh_token?refreshedData.refresh_token:local.refresh_token;
			local.refresh_token_expires_in = refreshedData.refresh_token_expires_in?refreshedData.refresh_token_expires_in:local.refresh_token_expires_in;
			this.saveData('state',local);
		},(err)=>{
			console.error(err);
		});	
	},
	getAccessToken: function(success,error) {
		this.getData('state',(state)=>{
			
			this.hasValidAccessToken(()=>{
				let local = typeof state==='string'?JSON.parse(state):state;
				success(local.access_token);
				return;
			},()=>{
				
				this.getConnectionConfig((res)=>{
					let data = typeof local==='string'?JSON.parse(local):local;
					let config = typeof res==='string'?JSON.parse(res):res;
					this.performRefreshRequest(config.client_id, config.scope, data.refresh_token, (res)=>{
						let result = typeof res==='string'?JSON.parse(res):res;
						success(result.access_token);
					}, error);	
				}, error); 		
			});
				
		},(err)=>{
			console.error(err);
			error('Nutzer nicht angemeldet!');
		});	
	},
	getIDToken: function(success,error) {
		this.getData('state',(state)=>{
			if (state)
			{
				let data = typeof state==='string'?JSON.parse(state):state;
				success(data.id_token);				
			}
			else 
			{
				console.error('state is unavailable');
				error('Nutzer nicht angemeldet!');
			}
		},(err)=>{
			console.error(err);
			error('Nutzer nicht angemeldet!');
		});	
	},
	hasValidAccessToken: function(success,error) {
		this.getData('state',(state)=>{
			const currentTime = Math.floor(Date.now() / 1000);

			let local = typeof state==='string'?JSON.parse(state):state;
			try {
			// Das JWT Access Token besteht aus drei Teilen: Header.Payload.Signature
				const parts = local.access_token.split('.');
				if (parts.length !== 3) {
				  error('Ungültiges Token');
				  return;
				}

				// Den Payload-Teil decodieren
				const payload = JSON.parse(this.base64UrlDecode(parts[1]));

				if (!payload || !payload.exp || !payload.nbf) {
				  error('Ungültiges Token');
				  return;
				}

				// Überprüfen, ob das Token bereits gültig ist
				if (currentTime < payload.nbf) {
					error('Token ist noch nicht gültig (nbf ist in der Zukunft)');
					return;  
				}

				// Überprüfen, ob das Token abgelaufen ist
				if (currentTime + this.clockskrew > payload.exp) {
					error('Token ist abgelaufen (exp ist in der Vergangenheit)');
					return;  
				}

				success();
			} catch (err) {
				console.error('Error decoding or validating Access Token:', err);
				error('Fehler beim Decodieren oder Validieren');
				return;  
			}
			
		},(err)=>{
			console.error(err);
			error('Nutzer nicht angemeldet!');
		});	
	},
	base64UrlDecode: function (base64Url) {
	  // Padding entfernen und Zeichen ersetzen, um base64-dekodierbar zu sein
	  const base64 = base64Url.replace(/-/g, '+').replace(/_/g, '/');
	  // Base64 dekodieren
	  const rawData = atob(base64);
	  // Als UTF-8 interpretieren
	  return decodeURIComponent(escape(rawData));
	},
	performTokenRequest: function(authCode, client_id, redirect_uri, success, fkterror,config) {
		let openIDConfig = config?config:this.getOIDCConfigLocal();
		this.getPackageName((packagename)=>{
			let params = {
				grant_type: 'authorization_code',
				code: authCode,
				redirect_uri: redirect_uri.replace(/\*/g, packagename),
				client_id: client_id
			};
			  
			this.makeRequest(openIDConfig.token_endpoint,params,'POST')
			.then(tokens => {
				let data = typeof tokens==='string'?JSON.parse(tokens):tokens;
				
				exec(()=>{
					this.saveData('state',data);
					success(tokens);	
				}, fkterror, PLUGIN_NAME, 'createAccount', [data]);
			})
			.catch(error => {
				console.error('Error obtaining tokens:', error);
				fkterror(error);
			});
		},(err)=>{
			fkterror(err);
		});	
	},
	performRefreshRequest: function(client_id, scope, refresh_token, success, fkterror,config) {
		
		let openIDConfig = config?config:this.getOIDCConfigLocal();
		let params = {
				grant_type: 'refresh_token',
				client_id: client_id,
				refresh_token: refresh_token,
				scope: scope
			  };
	  
		this.makeRequest(openIDConfig.token_endpoint,params,'POST')
		.then(tokens => {
			console.log('Obtained tokens:', tokens);
			let data = typeof tokens==='string'?JSON.parse(tokens):tokens;
			this.mergeData(data);
			success(tokens);
		})
		.catch(error => {
			console.error('Error obtaining tokens:', error);
			// Hier können Sie die Fehlerbehandlung durchführen
			fkterror(error);
		});
	},
	getOIDCConfigFromIssuer: function(issuer, success, fkterror) {
	
		let params = {};
		
		let url = `${issuer}/.well-known/openid-configuration`;
		  
		this.makeRequest(url,params,'GET',{})
		.then(config => {
			let data = typeof config==='string'?JSON.parse(config):config;
			this.setOIDCConfigLocal(data);
			success(data);
		})
		.catch(error => {
			console.error('Error obtaining config:', error);
			// Hier können Sie die Fehlerbehandlung durchführen
			fkterror(error);
		});
	},
	makeRequest: function(endpoint, params, method, headers) {
	  return new Promise(async (resolve, reject) => {
		for (let retry = 0; retry < this.maxRetries; retry++) {
		  try {
			  
			let options = method.toUpperCase()==='POST' ? {
			  method: method,
			  headers: headers?headers:{
				'Content-Type': 'application/x-www-form-urlencoded',
			  },
			  body: new URLSearchParams(params),
			}: {
			  method: method
			};
			
			endpoint=method.toUpperCase()==='POST' ? endpoint : `${endpoint}?${new URLSearchParams(params)}`;
			
			const fetchPromise = fetch(endpoint, options);

			const timeoutPromise = new Promise((_, reject) =>
			  setTimeout(() => reject(new Error('Request timeout')), this.timeout)
			);

			const response = await Promise.race([fetchPromise, timeoutPromise]);

			if (response.ok) {
			  const data = await response.json();
			  resolve(data);
			  return;
			} else if (response.status >= 400 && response.status <= 499) {
			  throw new Error(`Failed to receive data - Code: ${response.status} Message: ${response.statusText}`);
			}
		  } catch (error) {
			console.error(`Error in attempt ${retry + 1}: ${JSON.stringify(error)}`);
		  }

		  if (retry < this.maxRetries - 1) {
			// Wait for the specified delay before retrying
			await new Promise(resolve => setTimeout(resolve, this.retryDelay));
		  }
		}

		reject(new Error('Failed to receive data after maximum retries'));
	  });
	},
	refreshNotification: function (success, error) {
		exec(success, error, PLUGIN_NAME, 'refreshNotification', []);
	}

};

module.exports = oidc;
