import Foundation
import AppAuth
import UIKit
import Combine

final class AuthService: NSObject, ObservableObject {

    static let shared = AuthService()

    private var authState: OIDAuthState?
    private var currentAuthorizationFlow: OIDExternalUserAgentSession?

    private let tokenStore = KeychainTokenStore()

    @Published private(set) var isAuthenticated: Bool = false

    override init() {
        super.init()
        self.authState = tokenStore.load()
        self.isAuthenticated = authState?.isAuthorized ?? false
    }

    // MARK: - Login mit dynamischer Konfiguration
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

            let request = OIDAuthorizationRequest(
                configuration: configuration,
                clientId: clientId,
                scopes: scopes,
                redirectURL: redirectURI,
                responseType: OIDResponseTypeCode,
                additionalParameters: prompt ? ["prompt":"login"] : nil
            )

            self.currentAuthorizationFlow = OIDAuthState.authState(byPresenting: request, presenting: vc) { authState, error in
                if let state = authState {
                    self.setAuthState(state)
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
        guard let state = authState else {
            let error = NSError(domain: "AuthService", code: -1, userInfo: [NSLocalizedDescriptionKey: "Nicht authentifiziert"])
            callback(nil, nil, error)
            return
        }

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
            completion(true)
            return
        }

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