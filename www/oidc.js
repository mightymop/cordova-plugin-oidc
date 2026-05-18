var exec = require('cordova/exec');
var PLUGIN_NAME = 'oidc';

var oidc = {

	login: function(success,error,config) {

		exec(success, error, PLUGIN_NAME, 'login', [config]);
	},
	logout: function (success, error,config) {

		exec(success, error, PLUGIN_NAME, 'logout', [config]);
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
					var json = typeof data ==='string' ? JSON.parse(data): data;
					success(json.access_token);
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
					var json = typeof data ==='string' ? JSON.parse(data): data;
					success(json.id_token);
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
