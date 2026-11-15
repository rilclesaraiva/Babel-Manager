package com.babelmanager.app.data.apps

import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.drawable.Drawable
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class AppInfo(val packageName: String, val label: String)

/**
 * Apps instalados que têm ícone no lançador (visibilidade declarada em `<queries>` no manifesto,
 * sem QUERY_ALL_PACKAGES). A lista é lida e usada só no aparelho.
 */
class InstalledApps(private val pm: PackageManager, private val ownPackage: String) {

    suspend fun launchable(): List<AppInfo> = withContext(Dispatchers.IO) {
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        pm.queryIntentActivities(intent, 0)
            .mapNotNull { info ->
                val pkg = info.activityInfo?.packageName
                if (pkg == null || pkg == ownPackage) null else AppInfo(pkg, info.loadLabel(pm).toString())
            }
            .distinctBy { it.packageName }
            .sortedBy { it.label.lowercase() }
    }

    /** Nome do app, ou null se não está instalado (ou não é visível). */
    fun label(packageName: String): String? = try {
        pm.getApplicationLabel(pm.getApplicationInfo(packageName, 0)).toString()
    } catch (e: PackageManager.NameNotFoundException) {
        null
    }

    fun icon(packageName: String): Drawable? = try {
        pm.getApplicationIcon(packageName)
    } catch (e: PackageManager.NameNotFoundException) {
        null
    }

    fun launchIntent(packageName: String): Intent? = pm.getLaunchIntentForPackage(packageName)
}
