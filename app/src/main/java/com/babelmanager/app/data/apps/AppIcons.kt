package com.babelmanager.app.data.apps

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.LruCache
import androidx.core.graphics.drawable.toBitmap
import com.babelmanager.app.data.autofill.AppUri
import java.io.File
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

    /** Do app instalado; guardado em `filesDir/app-icons`, para continuar aparecendo depois de desinstalar o app. */
    private suspend fun loadApp(context: Context, url: String): Bitmap? {
        val pkg = AppUri.packageOf(url) ?: return null
        cache.get(pkg)?.let { return it }
        return withContext(Dispatchers.IO) {
            val file = File(File(context.filesDir, "app-icons").apply { mkdirs() }, "$pkg.png")
            val drawable = InstalledApps(context.packageManager, context.packageName).icon(pkg)
            val fresh = drawable?.let { runCatching { it.toBitmap(SIZE_PX, SIZE_PX) }.getOrNull() }
            if (fresh != null) {
                runCatching { file.outputStream().use { fresh.compress(Bitmap.CompressFormat.PNG, 100, it) } }
            }
            (fresh ?: if (file.exists()) BitmapFactory.decodeFile(file.path) else null)?.also { cache.put(pkg, it) }
        }
    }
}
