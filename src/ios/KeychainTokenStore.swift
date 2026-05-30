//
//  KeychainTokenStore.swift
//  ios-oidc-app-a
//
//  Created by Christoph Huschenhöfer on 23.02.26.
//

import Foundation
import Security
import AppAuth

final class GlobalAuthStore {

    private let suiteName = "group.plugin.cordova.oidc"

    private let loginKey = "globalLoginDate"
    private let logoutKey = "globalLogoutDate"

    private var defaults: UserDefaults? {
        UserDefaults(suiteName: suiteName)
    }

    // MARK: Login
    func setLogin(_ date: Date) {
        defaults?.set(date, forKey: loginKey)
    }

    func getLogin() -> Date? {
        defaults?.object(forKey: loginKey) as? Date
    }

    // MARK: Logout
    func setLogout(_ date: Date) {
        defaults?.set(date, forKey: logoutKey)
    }

    func getLogout() -> Date? {
        defaults?.object(forKey: logoutKey) as? Date
    }
}

final class KeychainTokenStore {

    private let service = "plugin.cordova.oidc"
    private let account = "oidc_state"

    func save(_ state: OIDAuthState) throws {

        let data = try NSKeyedArchiver.archivedData(
            withRootObject: state,
            requiringSecureCoding: true
        )

        let query: [String: Any] = [
            kSecClass as String: kSecClassGenericPassword,
            kSecAttrService as String: service,
            kSecAttrAccount as String: account,
            kSecAttrAccessible as String:
                kSecAttrAccessibleAfterFirstUnlockThisDeviceOnly,
            kSecValueData as String: data
        ]

        SecItemDelete(query as CFDictionary)
        let status = SecItemAdd(query as CFDictionary, nil)

        guard status == errSecSuccess else {
            throw NSError(domain: NSOSStatusErrorDomain, code: Int(status))
        }
    }

    func load() -> OIDAuthState? {

        let query: [String: Any] = [
            kSecClass as String: kSecClassGenericPassword,
            kSecAttrService as String: service,
            kSecAttrAccount as String: account,
            kSecReturnData as String: true
        ]

        var item: CFTypeRef?
        let status = SecItemCopyMatching(query as CFDictionary, &item)

        guard status == errSecSuccess,
              let data = item as? Data else {
            return nil
        }

        return try? NSKeyedUnarchiver.unarchivedObject(
            ofClass: OIDAuthState.self,
            from: data
        )
    }

    func clear() {
        let query: [String: Any] = [
            kSecClass as String: kSecClassGenericPassword,
            kSecAttrService as String: service,
            kSecAttrAccount as String: account
        ]
        SecItemDelete(query as CFDictionary)
    }
}
