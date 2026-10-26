package com.vincentwill.calm.data

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import androidx.core.content.edit
import com.vincentwill.calm.util.SafeLog
import java.security.GeneralSecurityException
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * The API key, encrypted with an AES-GCM key that never leaves the Android Keystore. (This is the
 * recommended replacement for the deprecated EncryptedSharedPreferences.)
 */
class SecretStore(context: Context) {
    private val prefs = context.getSharedPreferences("calm_secrets", Context.MODE_PRIVATE)

    @Volatile
    private var cached: String? = null

    var apiKey: String?
        get() = cached ?: decrypt()?.also { cached = it }
        set(value) {
            if (value.isNullOrBlank()) {
                prefs.edit { remove(PREF_KEY) }
                cached = null
            } else {
                prefs.edit { putString(PREF_KEY, encrypt(value.trim())) }
                cached = value.trim()
            }
        }

    private fun encrypt(plain: String): String {
        val cipher = Cipher.getInstance(TRANSFORMATION).apply { init(Cipher.ENCRYPT_MODE, key()) }
        val iv = Base64.encodeToString(cipher.iv, Base64.NO_WRAP)
        val data = Base64.encodeToString(cipher.doFinal(plain.toByteArray()), Base64.NO_WRAP)
        return "$iv:$data"
    }

    private fun decrypt(): String? {
        val stored = prefs.getString(PREF_KEY, null) ?: return null
        return try {
            val (iv, data) = stored.split(':', limit = 2)
            val cipher = Cipher.getInstance(TRANSFORMATION).apply {
                init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, Base64.decode(iv, Base64.NO_WRAP)))
            }
            String(cipher.doFinal(Base64.decode(data, Base64.NO_WRAP)))
        } catch (e: GeneralSecurityException) {
            // E.g. the keystore key was wiped (restored backup). The key has to be entered again.
            SafeLog.w({ "stored API key unreadable" }, e)
            null
        } catch (e: IllegalArgumentException) {
            SafeLog.w({ "stored API key malformed" }, e)
            null
        }
    }

    private fun key(): SecretKey {
        val keyStore = KeyStore.getInstance(KEYSTORE).apply { load(null) }
        (keyStore.getKey(ALIAS, null) as? SecretKey)?.let { return it }
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, KEYSTORE).apply {
            init(
                KeyGenParameterSpec.Builder(ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                    .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                    .setKeySize(256)
                    .build(),
            )
        }.generateKey()
    }

    private companion object {
        const val KEYSTORE = "AndroidKeyStore"
        const val ALIAS = "calm_api_key"
        const val PREF_KEY = "anthropic_api_key"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
    }
}
