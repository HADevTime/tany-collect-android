package ma.tany.collect.core.storage

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.first
import ma.tany.core.network.SessionStore
import ma.tany.core.network.StoredSession
import org.json.JSONObject
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

private val Context.secureSessionStore: DataStore<Preferences> by preferencesDataStore(name = "tany_secure_session")

/**
 * Session persistence encrypted with an AES-256-GCM key that lives in the Android Keystore (non-exportable).
 * Only the ciphertext is written to disk (DataStore); the token never appears in plain SharedPreferences, logs or
 * backups (backups are disabled — data_extraction_rules.xml). An undecryptable value (key invalidated, restore on
 * another device) is wiped and treated as signed out.
 */
class KeystoreSessionStore(private val context: Context) : SessionStore {
    override suspend fun read(): StoredSession? {
        val encoded = context.secureSessionStore.data.first()[KEY_PAYLOAD] ?: return null
        return try {
            val json = JSONObject(decrypt(encoded))
            StoredSession(token = json.getString("token"), userId = json.getString("userId"))
        } catch (e: Exception) {
            clear()
            null
        }
    }

    override suspend fun write(session: StoredSession) {
        val json = JSONObject().put("token", session.token).put("userId", session.userId).toString()
        val payload = encrypt(json)
        context.secureSessionStore.edit { it[KEY_PAYLOAD] = payload }
    }

    override suspend fun clear() {
        context.secureSessionStore.edit { it.remove(KEY_PAYLOAD) }
    }

    private fun encrypt(plain: String): String {
        val cipher = Cipher.getInstance(TRANSFORMATION).apply { init(Cipher.ENCRYPT_MODE, key()) }
        val sealed = cipher.iv + cipher.doFinal(plain.toByteArray(Charsets.UTF_8))
        return Base64.encodeToString(sealed, Base64.NO_WRAP)
    }

    private fun decrypt(encoded: String): String {
        val sealed = Base64.decode(encoded, Base64.NO_WRAP)
        val iv = sealed.copyOfRange(0, IV_BYTES)
        val cipher = Cipher.getInstance(TRANSFORMATION).apply { init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(TAG_BITS, iv)) }
        return String(cipher.doFinal(sealed, IV_BYTES, sealed.size - IV_BYTES), Charsets.UTF_8)
    }

    private fun key(): SecretKey {
        val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        (keyStore.getEntry(KEY_ALIAS, null) as? KeyStore.SecretKeyEntry)?.let { return it.secretKey }
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE).apply {
            init(
                KeyGenParameterSpec.Builder(KEY_ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                    .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                    .setKeySize(256)
                    .build(),
            )
        }.generateKey()
    }

    private companion object {
        const val ANDROID_KEYSTORE = "AndroidKeyStore"
        const val KEY_ALIAS = "tany_session_key_v1"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val IV_BYTES = 12
        const val TAG_BITS = 128
        val KEY_PAYLOAD = stringPreferencesKey("session_v1")
    }
}
