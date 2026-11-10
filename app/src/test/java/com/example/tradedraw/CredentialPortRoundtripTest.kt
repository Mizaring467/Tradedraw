package com.example.tradedraw

import android.content.SharedPreferences
import com.example.tradedraw.core.SharedPreferencesCredentialStore
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

/**
 * T-1.3: Roundtrip de credenciales a través de [CredentialProvider] y [CredentialSink].
 * Ejecuta en JVM puro con un doble de prueba de SharedPreferences en memoria.
 */
class CredentialPortRoundtripTest {

    private class InMemorySharedPreferences : SharedPreferences {
        private val data = mutableMapOf<String, Any?>()

        override fun getAll(): MutableMap<String, *> = HashMap(data)
        override fun getString(key: String?, defValue: String?): String? = (data[key] as? String) ?: defValue
        override fun getStringSet(key: String?, defValues: MutableSet<String>?): MutableSet<String>? =
            @Suppress("UNCHECKED_CAST") (data[key] as? MutableSet<String>) ?: defValues
        override fun getInt(key: String?, defValue: Int): Int = (data[key] as? Int) ?: defValue
        override fun getLong(key: String?, defValue: Long): Long = (data[key] as? Long) ?: defValue
        override fun getFloat(key: String?, defValue: Float): Float = (data[key] as? Float) ?: defValue
        override fun getBoolean(key: String?, defValue: Boolean): Boolean = (data[key] as? Boolean) ?: defValue
        override fun contains(key: String?): Boolean = data.containsKey(key)
        override fun registerOnSharedPreferenceChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener?) {}
        override fun unregisterOnSharedPreferenceChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener?) {}

        override fun edit(): SharedPreferences.Editor = Editor(this)

        class Editor(private val prefs: InMemorySharedPreferences) : SharedPreferences.Editor {
            private val pending = mutableMapOf<String, Any?>()
            private var clearFlag = false

            override fun putString(key: String?, value: String?): SharedPreferences.Editor {
                if (key != null) pending[key] = value
                return this
            }
            override fun putStringSet(key: String?, values: MutableSet<String>?): SharedPreferences.Editor {
                if (key != null) pending[key] = values
                return this
            }
            override fun putInt(key: String?, value: Int): SharedPreferences.Editor {
                if (key != null) pending[key] = value
                return this
            }
            override fun putLong(key: String?, value: Long): SharedPreferences.Editor {
                if (key != null) pending[key] = value
                return this
            }
            override fun putFloat(key: String?, value: Float): SharedPreferences.Editor {
                if (key != null) pending[key] = value
                return this
            }
            override fun putBoolean(key: String?, value: Boolean): SharedPreferences.Editor {
                if (key != null) pending[key] = value
                return this
            }
            override fun remove(key: String?): SharedPreferences.Editor {
                if (key != null) pending[key] = this
                return this
            }
            override fun clear(): SharedPreferences.Editor {
                clearFlag = true
                return this
            }
            override fun commit(): Boolean {
                apply()
                return true
            }
            override fun apply() {
                if (clearFlag) prefs.data.clear()
                for ((k, v) in pending) {
                    if (v === this) prefs.data.remove(k)
                    else prefs.data[k] = v
                }
            }
        }
    }

    private lateinit var store: SharedPreferencesCredentialStore
    private lateinit var fakePrefs: InMemorySharedPreferences

    @Before
    fun setUp() {
        fakePrefs = InMemorySharedPreferences()
        store = SharedPreferencesCredentialStore(fakePrefs)
    }

    @Test
    fun roundtripPreservaValoresYClavesHistoricas() {
        store.setAuthToken("test_token_abc")
        store.setDeviceId("device_xyz_123")
        store.setCookieHeader("session=abc; path=/")
        store.setEndpointUrl("wss://ws.binomo.com/test")
        store.setActiveAsset("CRY/IDX")

        assertEquals("test_token_abc", store.getAuthToken())
        assertEquals("device_xyz_123", store.getDeviceId())
        assertEquals("session=abc; path=/", store.getCookieHeader())
        assertEquals("wss://ws.binomo.com/test", store.getEndpointUrl())
        assertEquals("CRY/IDX", store.getActiveAsset())

        // Verificar que las claves en las preferencias son idénticas al contrato histórico
        assertEquals("test_token_abc", fakePrefs.getString("ws_auth_token", null))
        assertEquals("device_xyz_123", fakePrefs.getString("ws_device_id", null))
        assertEquals("session=abc; path=/", fakePrefs.getString("ws_cookie_header", null))
        assertEquals("wss://ws.binomo.com/test", fakePrefs.getString("ws_endpoint_url", null))
        assertEquals("CRY/IDX", fakePrefs.getString("ws_active_asset", null))
    }
}
