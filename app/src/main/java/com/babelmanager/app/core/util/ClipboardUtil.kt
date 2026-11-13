package com.babelmanager.app.core.util

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.os.PersistableBundle

object ClipboardUtil {
    private const val EXTRA_IS_SENSITIVE = "android.content.extra.IS_SENSITIVE"
    private val handler = Handler(Looper.getMainLooper())
    private var pending: Runnable? = null

    /** Copia marcando o conteúdo como sensível e limpa depois de [clearAfterSeconds] (0 = não limpa). */
    fun copySensitive(context: Context, label: String, text: String, clearAfterSeconds: Int) {
        val manager = context.getSystemService(ClipboardManager::class.java) ?: return
        val clip = ClipData.newPlainText(label, text)
        clip.description.extras = PersistableBundle().apply { putBoolean(EXTRA_IS_SENSITIVE, true) }
        manager.setPrimaryClip(clip)

        pending?.let(handler::removeCallbacks)
        pending = null
        if (clearAfterSeconds > 0) {
            val task = Runnable {
                try {
                    manager.clearPrimaryClip()
                } catch (e: Exception) {
                    // Sem foco o Android pode recusar; nada a fazer.
                }
            }
            pending = task
            handler.postDelayed(task, clearAfterSeconds * 1000L)
        }
    }
}
