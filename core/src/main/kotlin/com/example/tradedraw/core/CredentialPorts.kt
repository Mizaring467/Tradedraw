package com.example.tradedraw.core

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
