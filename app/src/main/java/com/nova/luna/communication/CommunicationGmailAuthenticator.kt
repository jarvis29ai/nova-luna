package com.nova.luna.communication

import android.accounts.Account
import android.accounts.AccountManager
import android.content.Context
import android.os.Bundle

class CommunicationGmailAuthenticator(private val context: Context) {

    private val accountManager = AccountManager.get(context)
    private val scope = "oauth2:https://www.googleapis.com/auth/gmail.readonly"

    fun listAccounts(): List<Account> {
        return accountManager.getAccountsByType("com.google").toList()
    }

    fun getAccountByName(name: String): Account? {
        return accountManager.getAccountsByType("com.google").find { it.name == name }
    }

    fun getGoogleAccount(): Account? {
        // For simplicity in this local-first model, we'll try to find a previously selected account
        // or just return the first one if there is only one.
        val accounts = accountManager.getAccountsByType("com.google")
        return accounts.firstOrNull()
    }

    /**
     * Gets the auth token. This might trigger a user consent dialog if not already granted.
     * Note: In a real app, this would be asynchronous and might need to handle activity result.
     * We return a result that can indicate if an Intent is required.
     */
    fun getAuthToken(account: Account): String? {
        return try {
            val future = accountManager.getAuthToken(account, scope, null, false, null, null)
            val result: Bundle = future.result
            
            // If the result contains an Intent, it means user interaction is required
            if (result.containsKey(AccountManager.KEY_INTENT)) {
                // In a real activity-based app, we'd launch this intent.
                // For this model, we report it as blocked/needs interaction.
                null
            } else {
                result.getString(AccountManager.KEY_AUTHTOKEN)
            }
        } catch (e: Exception) {
            null
        }
    }

    fun invalidateToken(token: String) {
        accountManager.invalidateAuthToken("com.google", token)
    }
}
