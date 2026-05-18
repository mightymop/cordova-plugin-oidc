import Foundation
import Cordova
import AppAuth

@objc(OIDCPlugin)
class OIDCPlugin: CDVPlugin {
    
    let authService = AuthService.shared
    
    @objc(login:)
    func login(command: CDVInvokedUrlCommand) {
        guard let config = command.arguments.first as? [String: Any],
              let issuerStr = config["issuer"] as? String,
              let issuer = URL(string: issuerStr),
              let clientId = config["clientId"] as? String,
              let scopesStr = config["scopes"] as? String,
              let redirectURIStr = config["redirectURI"] as? String,
              let redirectURI = URL(string: redirectURIStr) else {
            
            let result = CDVPluginResult(status: CDVCommandStatus_ERROR, messageAs: "Ungültige Konfigurationsparameter")
            self.commandDelegate.send(result, callbackId: command.callbackId)
            return
        }
        
        let scopes = scopesStr.components(separatedBy: " ")
        
        // ViewController von Cordova nutzen
        authService.login(presenting: self.viewController, issuer: issuer, clientId: clientId, scopes: scopes, redirectURI: redirectURI) { success in
            let result = CDVPluginResult(status: success ? CDVCommandStatus_OK : CDVCommandStatus_ERROR, messageAs: success ? "Login erfolgreich" : "Login fehlgeschlagen")
            self.commandDelegate.send(result, callbackId: command.callbackId)
        }
    }
    
    @objc(logout:)
    func logout(command: CDVInvokedUrlCommand) {
        guard let config = command.arguments.first as? [String: Any],
              let redirectURIStr = config["redirectURI"] as? String,
              let redirectURI = URL(string: redirectURIStr) else {
            let result = CDVPluginResult(status: CDVCommandStatus_ERROR, messageAs: "redirectURI fehlt in Konfiguration")
            self.commandDelegate.send(result, callbackId: command.callbackId)
            return
        }
        
        // idToken holen für ADFS Logout
        let idToken = authService.getIdToken()
        
        authService.logout(presenting: self.viewController, idToken: idToken, postLogoutRedirectURI: redirectURI) {
            let result = CDVPluginResult(status: CDVCommandStatus_OK)
            self.commandDelegate.send(result, callbackId: command.callbackId)
        }
    }
    
    @objc(hasAccount:)
    func hasAccount(command: CDVInvokedUrlCommand) {
        let isAuthenticated = authService.isAuthenticated
        let result = CDVPluginResult(status: CDVCommandStatus_OK, messageAs: isAuthenticated)
        self.commandDelegate.send(result, callbackId: command.callbackId)
    }
    
    @objc(getToken:)
    func getToken(command: CDVInvokedUrlCommand) {
        authService.performAuthenticatedRequest { accessToken, idToken, error in
            if let error = error {
                let result = CDVPluginResult(status: CDVCommandStatus_ERROR, messageAs: error.localizedDescription)
                self.commandDelegate.send(result, callbackId: command.callbackId)
                return
            }
            
            var tokens: [String: String] = [:]
            if let accToken = accessToken { tokens["access_token"] = accToken }
            if let idTkn = idToken { tokens["id_token"] = idTkn }
            
            let result = CDVPluginResult(status: CDVCommandStatus_OK, messageAs: tokens)
            self.commandDelegate.send(result, callbackId: command.callbackId)
        }
    }
    
    @objc(usersinfos:)
    func usersinfos(command: CDVInvokedUrlCommand) {
        // Optional: Wenn du den Token entschlüsseln willst (JWT) oder einen Request an /userinfo machst.
        // Hier als Platzhalter:
        let result = CDVPluginResult(status: CDVCommandStatus_ERROR, messageAs: "userinfos endpoint noch nicht implementiert")
        self.commandDelegate.send(result, callbackId: command.callbackId)
    }
    
    // SEHR WICHTIG: Fängt die Callback-URL ab, wenn die AppAuth-Webview zurück zur App leitet
    override func handleOpenURL(_ notification: Notification) {
        guard let url = notification.object as? URL else { return }
        _ = authService.resumeExternalUserAgentFlow(with: url)
    }
}