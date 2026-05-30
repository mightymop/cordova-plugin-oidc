import Foundation
import AppAuth
import UIKit
import Combine

final class AuthService: NSObject, ObservableObject {

    static let shared = AuthService()

    private var authState: OIDAuthState?
    private var currentAuthorizationFlow: OIDExternalUserAgentSession?

    private let tokenStore = KeychainTokenStore()
    public let globalStore = GlobalAuthStore()

    @Published private(set) var isAuthenticated: Bool = false

    override init() {
        super.init()
        self.authState = tokenStore.load()
        self.isAuthenticated = authState?.isAuthorized ?? false
    }

    // MARK: - VALIDATION

    func isSessionValid() -> Bool {

        guard let login = globalStore.getLogin() else {
            return false
        }

        if let logout = globalStore.getLogout(),
           logout > login {
            return false
        }

        return true
    }

    // MARK: - LOGIN

    func login(presenting vc: UIViewController,
               issuer: URL,
               clientId: String,
               scopes: [String],
               redirectURI: URL,
               prompt: Bool,
               completion: @escaping (Bool) -> Void) {

        print("Login func")
        OIDAuthorizationService.discoverConfiguration(forIssuer: issuer) { configuration, error in
            guard let configuration = configuration else {
                print("Discovery failed:", error?.localizedDescription ?? "Unknown")
                completion(false)
                return
            }

            let forcePrompt = !self.isSessionValid() || prompt

            let request = OIDAuthorizationRequest(
                configuration: configuration,
                clientId: clientId,
                scopes: scopes,
                redirectURL: redirectURI,
                responseType: OIDResponseTypeCode,
                additionalParameters: forcePrompt ? ["prompt":"login"] : nil
            )

            self.currentAuthorizationFlow = OIDAuthState.authState(byPresenting: request, presenting: vc) { authState, error in
                if let state = authState {
                    self.setAuthState(state)
		    // 🔥 GLOBAL LOGIN UPDATE
                    self.globalStore.setLogin(Date())
                    completion(true)
                } else {
                    print("Auth error:", error?.localizedDescription ?? "Unknown")
                    completion(false)
                }
            }
        }
    }

    // MARK: - Callback Handling
    func resumeExternalUserAgentFlow(with url: URL) -> Bool {
        if let flow = currentAuthorizationFlow, flow.resumeExternalUserAgentFlow(with: url) {
            currentAuthorizationFlow = nil
            return true
        }
        return false
    }

    // MARK: - Token Access
     func performAuthenticatedRequest(_ callback: @escaping (String?, String?, Error?) -> Void) {
    // 1. Sicherheit: Ist das State-Objekt vorhanden UND autorisiert?
		guard let state = self.authState, state.isAuthorized else {
			clearState()
			let error = NSError(domain: "AuthService", 
								code: -999, 
								userInfo: [NSLocalizedDescriptionKey: "Session invalid (global logout)"])
			callback(nil, nil, error)
			return
		}

		// 2. Jetzt ist 'state' sicher ausgepackt und wir führen die Aktion aus
		state.performAction { accessToken, idToken, error in
			callback(accessToken, idToken, error)
		}
	}

    func getIdToken() -> String? {
        return authState?.lastTokenResponse?.idToken
    }

    // MARK: - Logout (ADFS wichtig!)
    func logout(presenting vc: UIViewController, idToken: String?, postLogoutRedirectURI: URL, completion: @escaping (Bool) -> Void) {
        print("Logout func")

        guard let configuration = authState?.lastAuthorizationResponse.request.configuration,
            configuration.discoveryDocument?.endSessionEndpoint != nil else {
            
            // Wenn kein EndSessionEndpoint da ist, löschen wir zumindest lokal
            self.clearState()
	    // fallback
            globalStore.setLogout(Date())
            completion(true)
            return
        }


        //  OIDC END SESSION REQUEST
        let request = OIDEndSessionRequest(
            configuration: configuration,
            idTokenHint: idToken ?? "",
            postLogoutRedirectURL: postLogoutRedirectURI,
            additionalParameters: nil
        )

        guard let externalUserAgent = OIDExternalUserAgentIOS(presenting: vc) else {
            print("Failed to create external user agent for logout")
            self.clearState()
            completion(false)
            return
        }

        self.currentAuthorizationFlow = OIDAuthorizationService.present(
			request,
			externalUserAgent: externalUserAgent
		) { response, error in

			if let error = error as NSError? {

				// User hat Logout abgebrochen
				if error.domain == OIDGeneralErrorDomain,
				   error.code == OIDErrorCode.userCanceledAuthorizationFlow.rawValue {

					print("Logout abgebrochen")
					completion(false)
					return
				}

				print("Logout error:", error.localizedDescription)
				completion(false)
				return
			}

			if response != nil {

				print("Logout erfolgreich")
 				// 1. GLOBAL LOGOUT MARKER (sofort)
		        self.globalStore.setLogout(Date())
				self.clearState()
				completion(true)

			} else {

				print("Unknown logout state")
				completion(false)
			}
		}
    }

    // MARK: - Persistierung
    private func setAuthState(_ state: OIDAuthState) {
        authState = state
        try? tokenStore.save(state)
        state.stateChangeDelegate = self
        self.isAuthenticated = state.isAuthorized
    }

    private func clearState() {
        self.authState = nil
        self.isAuthenticated = false
        self.tokenStore.clear()
    }
}

extension AuthService: OIDAuthStateChangeDelegate {
    func didChange(_ state: OIDAuthState) {
        try? tokenStore.save(state)
        DispatchQueue.main.async {
            self.isAuthenticated = state.isAuthorized
        }
    }
}
