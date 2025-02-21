var exec = require('cordova/exec');
var PLUGIN_NAME = 'oidc';

var oidc = {
	
	login: function(success,error) {
	
		exec(success, error, PLUGIN_NAME, 'login', []);
	},
	logout: function (success, error) {
	
		exec(success, error, PLUGIN_NAME, 'logout', []);
	},

	hasAccount: function (success,error) {
		try {
			exec(success, error, PLUGIN_NAME, 'hasAccount', []);

		} catch (err) {
			console.error('Error writing to storage:', err);
			error(err)
		}
	},
	getAccessToken: function (success, error) {

		this.hasAccount((result)=>{
			if (result)
			{
				exec((data)=>{
					success(JSON.parse(data).access_token);
				}, error, PLUGIN_NAME, 'getToken', []);
			}
			else
			{
				error('Nutzer nicht angemeldet!');
			}
		},error);
	},
	getIDToken: function (success, error) {
		this.hasAccount((result)=>{
			if (result)
			{
				exec((data)=>{
					success(JSON.parse(data).id_token);
				}, error, PLUGIN_NAME, 'getToken', []);
			}
			else
			{
				error('Nutzer nicht angemeldet!');
			}
		},error);		
	},
	getUserInfos: function (success, error) {
		this.hasAccount((result)=>{
			if (result)
			{
				exec(success, error, PLUGIN_NAME, 'usersinfos', []);
			}
			else
			{
				error('Nutzer nicht angemeldet!');
			}
		},error);		
	}
	

};

module.exports = oidc;