import Foundation
import Cordova
import AppAuth

@objc(OIDCPlugin)
class OIDCPlugin: CDVPlugin {
    
    let authService = AuthService.shared

	func validSession(command: CDVInvokedUrlCommand) {

		AuthService.shared.validSession { result in

			var pluginResult: CDVPluginResult


			switch result {

			case .valid:

				pluginResult = CDVPluginResult(
					status: CDVCommandStatus_OK,
					messageAs: true
				)


			case .invalid:

				pluginResult = CDVPluginResult(
					status: CDVCommandStatus_OK,
					messageAs: false
				)


			case .unavailable:

				pluginResult = CDVPluginResult(
					status: CDVCommandStatus_ERROR,
					messageAs: "Network unavailable"
				)
			}


			self.commandDelegate.send(
				pluginResult,
				callbackId: command.callbackId
			)
		}
	}
    
    @objc(login:)
    func login(command: CDVInvokedUrlCommand) {
        guard let config = command.arguments.first as? [String: Any],
              let issuerStr = config["issuer"] as? String,
              let issuer = URL(string: issuerStr),
              let clientId = config["clientId"] as? String,
              let scopesStr = config["scopes"] as? String,
			  let prompt = config["prompt"] as? Bool,
			  let slo = config["slo"] as? Bool,
              let redirectURIStr = config["redirectURI"] as? String,
              let redirectURI = URL(string: redirectURIStr) else {
            
            let result = CDVPluginResult(status: CDVCommandStatus_ERROR, messageAs: "Ungültige Konfigurationsparameter")
            self.commandDelegate.send(result, callbackId: command.callbackId)
            return
        }
        
        let scopes = scopesStr.components(separatedBy: " ")
        
        // ViewController von Cordova nutzen
        authService.login(presenting: self.viewController, issuer: issuer, clientId: clientId, scopes: scopes, redirectURI: redirectURI, prompt: prompt, slo: slo) { success in
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
        
        authService.logout(presenting: self.viewController, idToken: idToken, postLogoutRedirectURI: redirectURI) { success in
          let result = CDVPluginResult(status: success ? CDVCommandStatus_OK : CDVCommandStatus_ERROR, messageAs: success ? "Logout erfolgreich" : "Logout fehlgeschlagen")
          self.commandDelegate.send(result, callbackId: command.callbackId)
		}
    }
    
    @objc(hasAccount:)
    func hasAccount(command: CDVInvokedUrlCommand) {
        
	if !authService.isSessionValid() {

		// Session ist global invalid
		let result = CDVPluginResult(
			status: CDVCommandStatus_ERROR,
			messageAs: "Benutzer ist abgemeldet."
		)

		self.commandDelegate.send(result, callbackId: command.callbackId)
		return
	}

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

		guard let config = command.arguments.first as? [String: Any] else {
			let result = CDVPluginResult(status: CDVCommandStatus_ERROR, messageAs: "Ungültige Konfigurationsparameter")
			self.commandDelegate.send(result, callbackId: command.callbackId)
			return
		}
		
		// Parameter auslesen (LDAP oder Fallback OIDC)
		let ldapInfoUrlStr = config["ldapinfourl"] as? String
		let userClaimKey = config["userclaim"] as? String ?? "sub" // Fallback auf "sub", falls nicht angegeben
		let issuerStr = config["issuer"] as? String

		// 1. Token intern holen
		authService.performAuthenticatedRequest { [weak self] accessToken, idToken, error in
			guard let self = self else { return }

			if let error = error {
				self.sendCordovaError(error.localizedDescription, callbackId: command.callbackId)
				return
			}

			guard let accessToken = accessToken else {
				self.sendCordovaError("Kein Access Token vorhanden", callbackId: command.callbackId)
				return
			}

			// --- NEUER LDAP FLOW ---
			if let ldapStr = ldapInfoUrlStr, !ldapStr.isEmpty {
				
				// URL zusammenbauen wie im Java Code
				var finalLdapUrlStr = ldapStr
				if !finalLdapUrlStr.hasSuffix("ldap/lookup") {
					finalLdapUrlStr += finalLdapUrlStr.hasSuffix("/") ? "ldap/lookup" : "/ldap/lookup"
				}
				
				guard let ldapUrl = URL(string: finalLdapUrlStr) else {
					self.sendCordovaError("Ungültige LDAP URL", callbackId: command.callbackId)
					return
				}
				
				guard let idToken = idToken else {
					self.sendCordovaError("Für den LDAP Request wird ein ID Token benötigt", callbackId: command.callbackId)
					return
				}
				
				// Claim aus dem ID Token extrahieren
				let filterValue = self.extractClaim(from: idToken, claimKey: "persnr") ?? ""
				
				// Request mit Retry-Logik starten
				self.performLDAPRequest(url: ldapUrl, accessToken: accessToken, filter: filterValue, retriesLeft: 3) { result in
					switch result {
					case .success(let parsedData):
						if let jsonData = try? JSONSerialization.data(withJSONObject: parsedData),
						let jsonString = String(data: jsonData, encoding: .utf8) {
							let pluginResult = CDVPluginResult(status: CDVCommandStatus_OK, messageAs: jsonString)
							self.commandDelegate.send(pluginResult, callbackId: command.callbackId)
						} else {
							self.sendCordovaError("Fehler beim Serialisieren der LDAP Daten", callbackId: command.callbackId)
						}
					case .failure(let error):
						self.sendCordovaError(error.localizedDescription, callbackId: command.callbackId)
					}
				}
				
			} 
			// --- ALTER OIDC FLOW (Fallback) ---
			else if let issuerStr = issuerStr, let issuer = URL(string: issuerStr) {
				
				let userInfoURL = issuer.appendingPathComponent("userinfo")
				var request = URLRequest(url: userInfoURL)
				request.httpMethod = "GET"
				request.setValue("Bearer \(accessToken)", forHTTPHeaderField: "Authorization")
				request.setValue("application/json", forHTTPHeaderField: "Accept")

				let task = URLSession.shared.dataTask(with: request) { data, response, error in
					if let error = error {
						self.sendCordovaError(error.localizedDescription, callbackId: command.callbackId)
						return
					}

					guard let httpResponse = response as? HTTPURLResponse, let data = data else {
						self.sendCordovaError("Keine gültige Antwort", callbackId: command.callbackId)
						return
					}

					if !(200...299).contains(httpResponse.statusCode) {
						let body = String(data: data, encoding: .utf8) ?? ""
						self.sendCordovaError("HTTP \(httpResponse.statusCode): \(body)", callbackId: command.callbackId)
						return
					}

					let jsonString = String(data: data, encoding: .utf8) ?? "{}"
					let result = CDVPluginResult(status: CDVCommandStatus_OK, messageAs: jsonString)
					self.commandDelegate.send(result, callbackId: command.callbackId)
				}
				task.resume()
				
			} else {
				self.sendCordovaError("Weder LDAP-URL noch Issuer-URL gefunden", callbackId: command.callbackId)
			}
		}
	}

	// MARK: - Helper Methods

	/// Hilfsfunktion um Cordova Fehler zu senden und Boilerplate zu reduzieren
	private func sendCordovaError(_ message: String, callbackId: String) {
		let result = CDVPluginResult(status: CDVCommandStatus_ERROR, messageAs: message)
		self.commandDelegate.send(result, callbackId: callbackId)
	}

	/// Führt den LDAP POST-Request mit Retry-Logik (3 Versuche, 3 Sekunden Pause) aus
	private func performLDAPRequest(url: URL, accessToken: String, filter: String, retriesLeft: Int, completion: @escaping (Result<[String: Any], Error>) -> Void) {
		
		let attributes = [
			"samaccountname", "company", "mail", "facsimileTelephoneNumber", "telephoneNumber",
			"mobile", "streetAddress", "postalCode", "l", "displayName", "sn", "givenName",
			"department", "extensionAttribute2", "extensionattribute1", "extensionAttribute4",
			"jpegPhoto", "thumbnailPhoto", "office"
		]
		
		let jsonParams: [String: Any] = [
			"filter": filter,
			"attributes": attributes
		]
		
		var request = URLRequest(url: url)
		request.httpMethod = "POST"
		request.setValue("Bearer \(accessToken)", forHTTPHeaderField: "Authorization")
		request.setValue("application/json; charset=utf-8", forHTTPHeaderField: "Content-Type")
		request.setValue("application/json", forHTTPHeaderField: "Accept")
		request.httpBody = try? JSONSerialization.data(withJSONObject: jsonParams)
		
		let task = URLSession.shared.dataTask(with: request) { [weak self] data, response, error in
			guard let self = self else { return }
			
			let statusCode = (response as? HTTPURLResponse)?.statusCode ?? -1
			let isSuccess = (200...299).contains(statusCode)
			
			if isSuccess, let data = data {
				if let json = try? JSONSerialization.jsonObject(with: data) as? [String: Any],
				let parsedData = self.parseInfosFromLdap(json) {
					completion(.success(parsedData))
					return
				}
			}
			
			// Retry Logic
			if retriesLeft > 1 {
				DispatchQueue.global().asyncAfter(deadline: .now() + 3.0) {
					self.performLDAPRequest(url: url, accessToken: accessToken, filter: filter, retriesLeft: retriesLeft - 1, completion: completion)
				}
			} else {
				let errorMsg = error?.localizedDescription ?? "Fehler beim Abrufen der UserInfo-Daten von LDAP"
				completion(.failure(NSError(domain: "LDAPService", code: -1, userInfo: [NSLocalizedDescriptionKey: errorMsg])))
			}
		}
		task.resume()
	}

	/// Parst die LDAP Antwort in ein flaches Dictionary
	private func parseInfosFromLdap(_ response: [String: Any]) -> [String: Any]? {
		guard let results = response["results"] as? [[String: Any]],
			let firstResult = results.first,
			let attributes = firstResult["attributes"] as? [[String: Any]] else {
			return nil
		}
		
		var resultDict: [String: Any] = [:]
		
		if let userId = firstResult["id"] {
			resultDict["user"] = userId
		}
		
		for attribute in attributes {
			if let name = attribute["name"] as? String, let value = attribute["value"] {
				resultDict[name.lowercased()] = value
			}
		}
		
		return resultDict
	}

	/// Extrahiert einen bestimmten Claim (z.B. Mail, SAMAccountName) aus dem JWT (ID Token)
	private func extractClaim(from jwt: String, claimKey: String) -> String? {
		let segments = jwt.components(separatedBy: ".")
		guard segments.count > 1 else { return nil }
		
		// Base64Url Decoding fixen (Padding auffüllen)
		var base64String = segments[1]
			.replacingOccurrences(of: "-", with: "+")
			.replacingOccurrences(of: "_", with: "/")
		
		let length = Double(base64String.lengthOfBytes(using: .utf8))
		let requiredLength = 4 * ceil(length / 4.0)
		let paddingLength = requiredLength - length
		if paddingLength > 0 {
			let padding = String(repeating: "=", count: Int(paddingLength))
			base64String += padding
		}
		
		guard let payloadData = Data(base64Encoded: base64String, options: .ignoreUnknownCharacters),
			let payload = try? JSONSerialization.jsonObject(with: payloadData, options: []) as? [String: Any] else {
			return nil
		}
		
		return payload[claimKey] as? String
	}
	
    // SEHR WICHTIG: Fängt die Callback-URL ab, wenn die AppAuth-Webview zurück zur App leitet
    override func handleOpenURL(_ notification: Notification) {
		print("AUTHCALLBACK")
        guard let url = notification.object as? URL else { return }
        _ = authService.resumeExternalUserAgentFlow(with: url)
    }
}
