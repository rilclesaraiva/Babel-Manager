package com.babelmanager.app

import android.content.Intent
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.babelmanager.app.core.security.PasswordGenerator
import com.babelmanager.app.core.security.RandomOptions
import com.babelmanager.app.core.ui.BabelTheme
import com.babelmanager.app.core.util.ClipboardUtil

/**
 * Painel rápido aberto pelo botão das configurações rápidas: gera uma senha forte e copia, ou abre o app.
 * Não toca no cofre (nem pede desbloqueio); a senha gerada só existe na memória até ser copiada.
 */
class QuickPanelActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        val container = (application as BabelApp).container
        setContent {
            val appearance by container.settings.settings.collectAsStateWithLifecycle()
            BabelTheme(appearance.themeMode, appearance.dynamicColor, appearance.siteIcons) {
                val context = LocalContext.current
                val generator = remember { PasswordGenerator() }
                var password by remember { mutableStateOf(generator.random(RandomOptions())) }
                var copied by remember { mutableStateOf(false) }
                Box(
                    Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.45f))
                        .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { finish() },
                    contentAlignment = Alignment.BottomCenter,
                ) {
                    Surface(
                        modifier = Modifier
                            .safeDrawingPadding()
                            .padding(16.dp)
                            .fillMaxWidth()
                            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {},
                        shape = RoundedCornerShape(28.dp),
                        color = MaterialTheme.colorScheme.surfaceContainerHigh,
                    ) {
                        Column(Modifier.padding(24.dp)) {
                            Text(stringResource(R.string.quick_panel_title), style = MaterialTheme.typography.titleLarge)
                            Text(
                                password,
                                modifier = Modifier.padding(vertical = 20.dp),
                                style = MaterialTheme.typography.titleMedium,
                                fontFamily = FontFamily.Monospace,
                            )
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                OutlinedButton(onClick = { password = generator.random(RandomOptions()); copied = false }) {
                                    Text(stringResource(R.string.autofill_use_strong_another))
                                }
                                Button(onClick = {
                                    ClipboardUtil.copySensitive(context, "generated", password, container.settings.current.clipboardClearSeconds)
                                    copied = true
                                }) {
                                    Text(stringResource(if (copied) R.string.copied else R.string.copy))
                                }
                            }
                            TextButton(
                                onClick = {
                                    startActivity(Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                                    finish()
                                },
                                modifier = Modifier.padding(top = 8.dp),
                            ) { Text(stringResource(R.string.quick_panel_open)) }
                        }
                    }
                }
            }
        }
    }
}
