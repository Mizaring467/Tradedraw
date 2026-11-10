package com.example.tradedraw.core

import android.content.Context
import android.content.SharedPreferences

/**
 * Puertos P0 para particionar el bus de credenciales `TradeDraw_WSConfig`.
 */
interface CredentialProvider {
    fun getAuthToken(): String
    fun getDeviceId(): String
    fun getCookieHeader(): String
    fun getEndpointUrl(): String
    fun getActiveAsset(): String
}

interface CredentialSink {
    fun setAuthToken(token: String)
    fun setDeviceId(deviceId: String)
    fun setCookieHeader(cookieHeader: String)
    fun setEndpointUrl(url: String)
    fun setActiveAsset(asset: String)
}

/**
 * Implementación canónica sobre SharedPreferences `TradeDraw_WSConfig`.
 * Mantiene intactas las claves y el archivo histórico.
 */
class SharedPreferencesCredentialStore(
    private val prefs: SharedPreferences
) : CredentialProvider, CredentialSink {

    companion object {
        fun fromContext(context: Context): SharedPreferencesCredentialStore =
            SharedPreferencesCredentialStore(
                context.getSharedPreferences("TradeDraw_WSConfig", Context.MODE_PRIVATE)
            )
    }

    override fun getAuthToken(): String = prefs.getString("ws_auth_token", "") ?: ""
    override fun setAuthToken(token: String) {
        prefs.edit().putString("ws_auth_token", token.trim()).apply()
    }

    override fun getDeviceId(): String = prefs.getString("ws_device_id", "") ?: ""
    override fun setDeviceId(deviceId: String) {
        prefs.edit().putString("ws_device_id", deviceId.trim()).apply()
    }

    override fun getCookieHeader(): String = prefs.getString("ws_cookie_header", "") ?: ""
    override fun setCookieHeader(cookieHeader: String) {
        prefs.edit().putString("ws_cookie_header", cookieHeader.trim()).apply()
    }

    override fun getEndpointUrl(): String = prefs.getString("ws_endpoint_url", "") ?: ""
    override fun setEndpointUrl(url: String) {
        prefs.edit().putString("ws_endpoint_url", url.trim()).apply()
    }

    override fun getActiveAsset(): String = prefs.getString("ws_active_asset", "") ?: ""
    override fun setActiveAsset(asset: String) {
        prefs.edit().putString("ws_active_asset", asset.trim()).apply()
    }
}
