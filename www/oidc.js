var exec = require('cordova/exec');
var PLUGIN_NAME = 'oidc';

var oidc = {
	maxRetries : 3, 
	retryDelay : 1000,
	clockskew: 30,
	timeout: 10000,
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
		console.log("OIDC","startLoginFlow",param);
		exec((auchcodeUrl)=>{
			console.log("startLoginFlow - success",auchcodeUrl);
			const url = new URL(auchcodeUrl);
  
			const params = new URLSearchParams(url.search);
			const code = params.get('code');
			
			if (code) {
				this.getOIDCConfig((config)=>{
					this.performTokenRequest(code, param.client_id, param.redirect_uri, success, error,config);
				},
				()=>{
					this.performTokenRequest(code, param.client_id, param.redirect_uri, success, error);	
				});
				
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
			let data = this.convertToObject(config);
			
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
				this.storage.setItem(key, data);
			}
			else
			{
				this.accountStorage.setItem(key, data);
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
			if (value!==null)
			{
				return typeof value==='string'?JSON.parse(value):value;
			}
			else 
			{
				return null;
			}
		} catch (error) {
			console.error('Error reading from storage:', error);
			return null;
		}
	},
	setOIDCConfigLocal: function (config){
			try {
			sessionStorage.setItem('config', typeof config!=='string'?JSON.stringify(config):config);
		} catch (error) {
			console.error('Error writing to storage:', error);
		}
	},
	useSessionStorage: function() {
		this.getData('state',(data)=>{
			this.clearStorage();
			data = this.convertToObject(data);
			this.storage = sessionStorage;
			this.saveData('state',data);
		},(err)=>{
			console.error(err);
		});	
	},
	useLocalStorage: function() {
		this.getData('state',(data)=>{
			this.clearStorage();
			data = this.convertToObject(data);
			this.storage = localStorage;
			this.saveData('state',data);
		},(err)=>{
			console.error(err);
		});	
	},
	useAccountStorage: function() {
		this.getData('state',(data)=>{
			this.clearStorage();
			data = this.convertToObject(data);
			this.storage = this.accountStorage;
			this.saveData('state',data);
		},(err)=>{
			console.error(err);
		});	
	},
	mergeData: function(refreshedData) {
		this.getData('state',(state)=>{
			let local = this.convertToObject(state);
			local.access_token = refreshedData.access_token;
			local.expirese = refreshedData.expirese;
			local.id_token = refreshedData.id_token;
			local.scope = refreshedData.scope?refreshedData.scope:local.scope;
			local.refresh_token = refreshedData.refresh_token?refreshedData.refresh_token:local.refresh_token;
			local.refresh_token_expirese = refreshedData.refresh_token_expirese?refreshedData.refresh_token_expirese:local.refresh_token_expirese;
			
			this.saveData('state',local);
		},(err)=>{
			console.error(err);
		});	
	},
	getAccessToken: function(success,error,timeout) {
		let debugstop = true;
		console.debug(`getAccessToken DEBUGSTOP = ${!debugstop}`);
		if (debugstop!==true)
		{
			error("Abbruch von getAccessToken() durch DEBUGSTOP Variable");
			return;
		}
		
		this.getData('state',(state)=>{
			this.hasValidAccessToken(()=>{
				let local = this.convertToObject(state);
				success(local.access_token);
				return;
			},()=>{
				this.getConnectionConfig((res)=>{
					let data = this.convertToObject(state);
					let config = this.convertToObject(res);
					this.getOIDCConfig((openidconfig)=>{
						this.performRefreshRequest(timeout,config.issuer,config.client_id, config.scope, data.refresh_token, (res2)=>{
							let result = this.convertToObject(res2);
							success(result.access_token);
						}, error,openidconfig);	
					},
					()=>{
						this.performRefreshRequest(timeout,config.issuer,config.client_id, config.scope, data.refresh_token, (res2)=>{
							let result = this.convertToObject(res2);
							success(result.access_token);
						}, error);	
					});
					
				}, error); 		
			},state);
			
		},(err)=>{
			console.error(`Fehler beim Aufruf von getState()... ${JSON.stringify(err)}`);
			error('Nutzer nicht angemeldet!');
		});	
		
	},
	getIDToken: function(success,error) {
		this.getData('state',(state)=>{
			console.debug(state);
			if (state)
			{
				let data = this.convertToObject(state);
				success(data.id_token);				
			}
			else 
			{
				console.error('state is unavailable');
				error('Nutzer nicht angemeldet!');
			}
		},(err)=>{
			console.warn(err);
			error('Nutzer nicht angemeldet!');
		});	
	},
	hasValidAccessToken: function (success, error, state) {
		const ERROR_INVALID_TOKEN = 'Ungültiges Token';
		const ERROR_PARTS_COUNT = `${ERROR_INVALID_TOKEN}: Falsche Anzahl von Teilen`;
		const ERROR_MISSING_INFO = `${ERROR_INVALID_TOKEN}: Fehlende Informationen im Payload`;
		const ERROR_NOT_VALID_YET = 'Token ist noch nicht gültig (nbf ist in der Zukunft)';
		const ERROR_EXPIRED = 'Token ist abgelaufen (exp ist in der Vergangenheit)';
		const ERROR_DECODING = 'Fehler beim Decodieren oder Validieren';
		const ERROR_USER_NOT_LOGGED = 'Nutzer nicht angemeldet!';
	
		if (state === undefined || state === false || state === '') {
			this.getData('state', (retrievedState) => {
				this.hasValidAccessToken(success, error, retrievedState);
			}, (err) => {
				console.error(`getData - Fehler: ${JSON.stringify(err)}`);
				error(ERROR_USER_NOT_LOGGED);
			});
	
			return;
		}
	
		const currentTime = Math.floor(Date.now() / 1000);
		state = typeof state ==='object' ? state: JSON.parse(state);
		
		try {
			// Das JWT Access Token besteht aus drei Teilen: Header.Payload.Signature
			const parts = state.access_token.split('.');
			if (parts.length !== 3) {
				console.error(ERROR_PARTS_COUNT);
				error(ERROR_PARTS_COUNT);
				return;
			}
	
			// Den Payload-Teil decodieren
			const payload = JSON.parse(this.base64UrlDecode(parts[1]));
	
			if (!payload || !payload.exp || !payload.nbf) {
				console.error(ERROR_MISSING_INFO);
				error(ERROR_MISSING_INFO);
				return;
			}
	
			// Überprüfen, ob das Token bereits gültig ist
			if (currentTime < payload.nbf) {
				console.error(ERROR_NOT_VALID_YET);
				error(ERROR_NOT_VALID_YET);
				return;
			}
	
			// Überprüfen, ob das Token abgelaufen ist
			if (currentTime + this.clockskew > payload.exp) {
				console.error(ERROR_EXPIRED);
				error(ERROR_EXPIRED);
				return;
			}
	
			success();
		} catch (err) {
			console.error('Error decoding or validating Access Token:', err);
			error(ERROR_DECODING);
		}
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
		console.log("performTokenRequest",authCode,client_id,redirect_uri,config);
		let openIDConfig = config?config:this.getOIDCConfigLocal();
		console.log("openIDConfig",openIDConfig);
		this.getPackageName((packagename)=>{
			console.log("packagename",packagename);
			let params = {
				grant_type: 'authorization_code',
				code: authCode,
				redirect_uri: redirect_uri.replace(/\*/g, packagename),
				client_id: client_id
			};
			console.log("params",params);
			this.makeRequest(openIDConfig.token_endpoint,params,'POST')
			.then(tokens => {
				exec(()=>{
					this.saveData('state',tokens);
					success(tokens);	
					
					this.scheduleTokenRefresh(tokens.access_token, () => {
					  console.log('Refreshing access token...');
					  
					  this.performRefreshRequest(timeout     , config.issuer, config.client_id, config.scope, tokens.refresh_token, (success)=>{
						console.log("token refreshed");
					  }, (err)=>{
						console.error("error while tokenrefresh",err);
					  }, config);
					});
					
				}, fkterror, PLUGIN_NAME, 'createAccount', [tokens]);
			})
			.catch(error => {
				console.error('Error obtaining tokens:', error);
				fkterror(error);
			});
		},(err)=>{
			fkterror(err);
		});	
	},
	performRefreshRequest: function(timeout, issuer, client_id, scope, refresh_token, success, fkterror,config) {
		let params = {
			grant_type: 'refresh_token',
			client_id: client_id,
			refresh_token: refresh_token,
			scope: scope,
			timeout: timeout?timeout:this.timeout
		  };
			  
		let openIDConfig = config?config:this.getOIDCConfigLocal();
		if (!openIDConfig)
		{
			this.getOIDCConfigFromIssuer(issuer,(config)=>{
				openIDConfig = this.convertToObject(config);
				
				this.makeRequest(openIDConfig.token_endpoint,params,'POST')
				.then(tokens => {
					this.mergeData(tokens);
					success(tokens);
					
					this.scheduleTokenRefresh(tokens.access_token, () => {
					  console.log('Refreshing access token...');
					  
					  this.performRefreshRequest(timeout     , config.issuer, config.client_id, config.scope, tokens.refresh_token, (success)=>{
						console.log("token refreshed");
					  }, (err)=>{
						console.error("error while tokenrefresh",err);
					  }, config);
					});
				})
				.catch(error => {
					console.error('Error obtaining tokens:', error);
					// Hier können Sie die Fehlerbehandlung durchführen
					fkterror(error);
				});
			},fkterror);
		}
		else
		{
			this.makeRequest(openIDConfig.token_endpoint,params,'POST')
			.then(tokens => {
				this.mergeData(tokens);
				success(tokens);
				
				this.scheduleTokenRefresh(tokens.access_token, () => {
					  console.log('Refreshing access token...');
					  
					  this.performRefreshRequest(timeout     , config.issuer, config.client_id, config.scope, tokens.refresh_token, (success)=>{
						console.log("token refreshed");
					  }, (err)=>{
						console.error("error while tokenrefresh",err);
					  }, config);
				});
			})
			.catch(error => {
				console.error('Error obtaining tokens:', error);
				// Hier können Sie die Fehlerbehandlung durchführen
				fkterror(error);
			});
		}
	},
	getOIDCConfigFromIssuer: function(issuer, success, fkterror) {
	
		let params = {};
		
		let url = `${issuer}/.well-known/openid-configuration`;
		  
		this.makeRequest(url,params,'GET',{})
		.then(config => {
			this.setOIDCConfigLocal(config);
			success(config);
		})
		.catch(error => {
			console.error('Error obtaining config:', error);
			// Hier können Sie die Fehlerbehandlung durchführen
			fkterror(error);
		});
	},
	removeAccount: function(success,error) {
		exec(success,error,PLUGIN_NAME,'removeAccount',[]);
	},
	convertToObject: function(data) {
		while (typeof data==='string')
		{
			data = JSON.parse(data);
		}
		
		return data;
	},
	makeRequest: function(endpoint, params, method, headers) {
		/*
		{
			"body": string,
			"method": string,
			"headers": [{"key":"bla","value": "keks"},... ],
			"endpoint: string "url.....",
			"timeout: 10000
		}
		*/
	
		return new Promise((resolve,reject)=>{
			let retryCount = 0;
	
			let formattedEndpoint = method.toUpperCase() === 'POST' ? endpoint : `${endpoint}?${new URLSearchParams(params)}`;
			
			let request_params = {
				body: (new URLSearchParams(params)).toString(),
				method: method,
				endpoint: formattedEndpoint,
				headers: headers && Object.keys(headers).length > 0 ? headers : [{
					key: 'Content-Type', value: 'application/x-www-form-urlencoded'
				}],
				timeout: params.timeout ? params.timeout : this.timeout
			};
			
			const makeRequestWithRetry = ()=>{
				exec(
				(result)=>{
					let res = this.convertToObject(result);
					console.log('RESULT: ',res.status);
					resolve(this.convertToObject(res.result));
				},
				(error)=>{
					let errorjson = this.convertToObject(error);
					if (errorjson.status >= 500 && errorjson.status <= 500 || errorjson.status === -1) {
						if (retryCount < this.maxRetries) {
							// Wiederhole den Aufruf bis zu 3 Mal
							retryCount++;
							setTimeout(()=>{
								makeRequestWithRetry();
							}, this.retryDelay);
							
						} else {
							reject(new Error('Failed to receive data after maximum retries'));
						}
					} else {
						reject(new Error(error));
					}
				}, PLUGIN_NAME,'makeRequest',[request_params]);
			};
			
			makeRequestWithRetry();
		});
	},
	refreshNotification: function (success, error) {
		exec(success, error, PLUGIN_NAME, 'refreshNotification', []);
	},
	registerAccountListener: function (success,error) {
		exec(success, error, PLUGIN_NAME, 'registerAccountListener', []);
	},
	scheduleTokenRefresh: function(access_token, refreshFunction) {
		try 
		{
			const decodedToken = this.decodeAccessToken(access_token);
			const expirationTime = decodedToken.exp * 1000; // Multipliziere mit 1000, um Millisekunden zu erhalten
			const refreshThreshold = expirationTime - 120 * 1000; // 120 Sekunden vor Ablauf
			const currentTime = Date.now();

			if (refreshThreshold > currentTime) {
			  const delay = refreshThreshold - currentTime;
			  console.log(`Scheduled token refresh in ${delay / 1000} seconds.`);

			  setTimeout(() => {
				// Starte den Aktualisierungsvorgang
				refreshFunction();
			  }, delay);
			}
		} catch (error) {
			console.error('Error decoding access token:', error);
		}
	},
	decodeAccessToken: function(access_token) {
	  try {
		const parts = access_token.split('.');
		const payload = JSON.parse(this.base64UrlDecode(parts[1]));
		return payload;
	  } catch (error) {
		console.error('Error decoding access token:', error);
		throw error;
	  }
	}

};

module.exports = oidc;