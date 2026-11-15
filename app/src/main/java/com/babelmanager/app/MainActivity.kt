package com.babelmanager.app

import android.os.Bundle
import android.view.WindowManager
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.lifecycleScope
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.babelmanager.app.core.ui.BabelTheme
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

// FragmentActivity (e não só ComponentActivity) porque o BiometricPrompt exige.
class MainActivity : FragmentActivity() {

    private val container: AppContainer
        get() = (application as BabelApp).container

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // Bloqueia capturas de tela e a miniatura em "recentes" (ajuste do usuário, ligado por padrão).
        lifecycleScope.launch {
            container.settings.settings
                .map { it.blockScreenshots }
                .distinctUntilChanged()
                .collect { block ->
                    if (block) window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
                    else window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
                }
        }

        setContent {
            val appearance by container.settings.settings.collectAsStateWithLifecycle()
            BabelTheme(appearance.themeMode, appearance.dynamicColor, appearance.siteIcons) { AppRoot(container) }
        }
    }

    override fun onStart() {
        super.onStart()
        container.autoLock.onStart()
    }

    override fun onStop() {
        super.onStop()
        // Girar a tela recria a Activity e não deve contar como sair do app.
        if (!isChangingConfigurations) container.autoLock.onStop()
    }
}
