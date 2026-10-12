package app.yuxino.mimi.android

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import app.yuxino.mimi.android.provider.ServiceProvider
import java.io.File
import org.json.JSONObject

/** Explicit, offline Dev acceptance input; normal encrypted save, no credential diagnostics. */
internal class PrivateProviderSaveChecks(private val context: Context) {
    fun run() {
        val input = File(context.cacheDir, "acceptance-provider.json")
        try {
            check(input.isFile && input.length() in 1..16_384)
            check(JSONObject(input.readText(Charsets.UTF_8)).getString("provider") == "volcano")
            val key = MasterKey.Builder(context).setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build()
            val preferences = EncryptedSharedPreferences.create(context, "mimi_secure", key,
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM)
            val before = preferences.all
            val otherAccounts = ServiceProvider.entries.filter { it != ServiceProvider.VOLCANO }
                .associateWith { SettingsStore.configuration(context, it) }
            TemporaryProviderAcceptance(context).persistOnly()
            val allowed = setOf("api_key_volcano", "base_url_volcano", "model_volcano")
            val after = preferences.all
            check(before.filterKeys { it !in allowed } == after.filterKeys { it !in allowed })
            check(otherAccounts.all { (provider, configuration) ->
                val saved = SettingsStore.configuration(context, provider)
                saved.credentials == configuration.credentials && saved.endpoint == configuration.endpoint &&
                    saved.model == configuration.model
            })
            check(SettingsStore.isConfigured(context, ServiceProvider.VOLCANO))
            check(SettingsStore.flushPendingWritesForTests(context))
        } catch (_: Exception) {
            // Private parser/save exceptions can contain fields; expose one label only.
            throw IllegalStateException("private_provider_save_failed")
        } finally { check(!input.exists() || input.delete()) }
    }
}
