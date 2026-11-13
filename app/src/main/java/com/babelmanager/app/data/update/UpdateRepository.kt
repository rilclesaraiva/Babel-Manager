package com.babelmanager.app.data.update

import android.content.SharedPreferences
import com.babelmanager.app.core.util.SemVer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONException
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

data class ReleaseInfo(
    val version: String,
    val notes: String,
    val pageUrl: String,
    val downloadUrl: String?,
) {
    companion object {
        /** Lê a resposta de `GET /repos/OWNER/REPO/releases/latest`. */
        fun fromJson(json: String): ReleaseInfo? = try {
            val o = JSONObject(json)
            val assets = o.optJSONArray("assets")
            var apk: String? = null
            if (assets != null) {
                for (i in 0 until assets.length()) {
                    val a = assets.getJSONObject(i)
                    if (a.optString("name").endsWith(".apk")) {
                        apk = a.optString("browser_download_url").ifEmpty { null }
                        break
                    }
                }
            }
            ReleaseInfo(
                version = o.getString("tag_name"),
                notes = o.optString("body"),
                pageUrl = o.optString("html_url"),
                downloadUrl = apk,
            )
        } catch (e: JSONException) {
            null
        }
    }
}

enum class PostponeKind { None, Snoozed, Skipped }

data class Postpone(val kind: PostponeKind = PostponeKind.None, val untilMillis: Long = 0L, val version: String? = null)

enum class CheckOutcome { Updated, NotModified, NoRelease, Failed, Disabled }

class UpdateStore(private val prefs: SharedPreferences) {
    var etag: String?
        get() = prefs.getString("etag", null)
        set(value) = prefs.edit().putString("etag", value).apply()

    var cachedJson: String?
        get() = prefs.getString("cached_release", null)
        set(value) = prefs.edit().putString("cached_release", value).apply()

    var snoozeUntil: Long
        get() = prefs.getLong("snooze_until", 0L)
        set(value) = prefs.edit().putLong("snooze_until", value).apply()

    var skippedVersion: String?
        get() = prefs.getString("skipped_version", null)
        set(value) = prefs.edit().putString("skipped_version", value).apply()
}

/**
 * Consulta a última Release pública do repositório no GitHub. Usa ETag: resposta 304
 * ("não mudou") não gasta o limite da API. Só funciona com Releases públicas.
 */
class UpdateRepository(
    private val repo: String,
    private val installedVersion: String,
    private val store: UpdateStore,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    /** Desligado enquanto o repositório não estiver configurado (OWNER/REPO é o valor padrão). */
    val enabled: Boolean
        get() = repo != PLACEHOLDER && Regex("^[A-Za-z0-9_.-]+/[A-Za-z0-9_.-]+$").matches(repo)

    /** Última Release guardada, se for mais nova que a instalada. */
    fun cachedNewer(): ReleaseInfo? {
        val release = store.cachedJson?.let(ReleaseInfo::fromJson) ?: return null
        val remote = SemVer.parse(release.version) ?: return null
        val installed = SemVer.parse(installedVersion) ?: return null
        return release.takeIf { remote > installed }
    }

    /** O que o aviso deve mostrar agora, respeitando adiar e pular. */
    fun visibleUpdate(): ReleaseInfo? {
        val release = cachedNewer() ?: return null
        if (store.snoozeUntil > clock()) return null
        if (store.skippedVersion == release.version) return null
        return release
    }

    fun postpone(): Postpone {
        val skipped = store.skippedVersion
        if (skipped != null) return Postpone(PostponeKind.Skipped, version = skipped)
        val until = store.snoozeUntil
        if (until > clock()) return Postpone(PostponeKind.Snoozed, untilMillis = until)
        return Postpone()
    }

    fun snooze(days: Int) {
        store.snoozeUntil = clock() + days * DAY_MILLIS
    }

    fun skip(version: String) {
        store.skippedVersion = version
    }

    fun clearPostpone() {
        store.snoozeUntil = 0L
        store.skippedVersion = null
    }

    suspend fun check(): CheckOutcome = withContext(Dispatchers.IO) {
        if (!enabled) return@withContext CheckOutcome.Disabled
        var connection: HttpURLConnection? = null
        try {
            connection = (URL("https://api.github.com/repos/$repo/releases/latest").openConnection() as HttpURLConnection).apply {
                connectTimeout = TIMEOUT_MS
                readTimeout = TIMEOUT_MS
                setRequestProperty("Accept", "application/vnd.github+json")
                setRequestProperty("User-Agent", "BabelManager/$installedVersion")
                store.etag?.let { setRequestProperty("If-None-Match", it) }
            }
            when (connection.responseCode) {
                HttpURLConnection.HTTP_NOT_MODIFIED -> CheckOutcome.NotModified
                HttpURLConnection.HTTP_OK -> {
                    val body = connection.inputStream.bufferedReader().use { it.readText() }
                    // Resposta nova sempre substitui a guardada.
                    store.cachedJson = body
                    store.etag = connection.getHeaderField("ETag")
                    CheckOutcome.Updated
                }
                HttpURLConnection.HTTP_NOT_FOUND -> {
                    store.cachedJson = null
                    store.etag = null
                    CheckOutcome.NoRelease
                }
                else -> CheckOutcome.Failed
            }
        } catch (e: IOException) {
            CheckOutcome.Failed
        } finally {
            connection?.disconnect()
        }
    }

    companion object {
        const val PLACEHOLDER = "OWNER/REPO"
        private const val TIMEOUT_MS = 10_000
        private const val DAY_MILLIS = 24L * 60 * 60 * 1000
    }
}
