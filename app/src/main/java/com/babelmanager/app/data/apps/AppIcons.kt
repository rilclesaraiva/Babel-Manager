package com.babelmanager.app.data.apps

import android.content.Context
import android.graphics.Bitmap
import android.util.LruCache
import androidx.core.graphics.drawable.toBitmap
import com.babelmanager.app.data.autofill.AppUri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Ícone do app para entradas ligadas a um app (`androidapp://<pacote>`). Lido do próprio aparelho
 * (PackageManager): nenhuma chamada de rede. Guardado em memória para a lista rolar sem recarregar.
 */
object AppIcons {
    private const val SIZE_PX = 144
    private val cache = LruCache<String, Bitmap>(64)

    /** Ícone da entrada: do app instalado (`androidapp://`) ou, se [sites] estiver ligado, o favicon do site. */
    suspend fun load(context: Context, url: String, sites: Boolean): Bitmap? {
        if (AppUri.packageOf(url) == null) return if (sites) loadSite(context, url) else null
        return loadApp(context, url)
    }

    private suspend fun loadSite(context: Context, url: String): Bitmap? {
        val icons = SiteIcons(context.applicationContext)
        val host = icons.hostFor(url) ?: return null
        cache.get("site:$host")?.let { return it }
        return withContext(Dispatchers.IO) {
            icons.fetch(host)?.also { cache.put("site:$host", it) }
        }
    }

    private suspend fun loadApp(context: Context, url: String): Bitmap? {
        val pkg = AppUri.packageOf(url) ?: return null
        cache.get(pkg)?.let { return it }
        return withContext(Dispatchers.IO) {
            val drawable = InstalledApps(context.packageManager, context.packageName).icon(pkg) ?: return@withContext null
            runCatching { drawable.toBitmap(SIZE_PX, SIZE_PX) }.getOrNull()?.also { cache.put(pkg, it) }
        }
    }
}
