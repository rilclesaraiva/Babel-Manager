package com.babelmanager.app.core.ui

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.changedToUp
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.LocalViewConfiguration
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay

/*
 * Campos de texto e teclado. Mesmas regras do BoomListener: campo em pílula, toque fora tira o foco e fecha o
 * teclado, teclado fechado tira o foco, e o teclado sai antes de abrir uma janela do sistema.
 */

/** Campo de uma linha: pílula. */
val FieldShape = CircleShape

/** Campo de várias linhas (a pílula ficaria estranha numa caixa alta): cantos bem arredondados. */
val MultiLineFieldShape = RoundedCornerShape(24.dp)

/** Área (na raiz) do campo que está com o foco agora; só um campo tem o foco por vez. */
private object FocusedField {
    private var owner: Any? = null
    var bounds: Rect? = null
        private set

    fun set(token: Any, rect: Rect?) {
        owner = token
        bounds = rect
    }

    fun clear(token: Any) {
        if (owner === token) {
            owner = null
            bounds = null
        }
    }
}

/** Vai no modifier de cada campo de texto: diz à raiz onde ele está quando tem o foco. */
@Composable
fun Modifier.trackFieldFocus(): Modifier {
    val token = remember { Any() }
    val bounds = remember { arrayOfNulls<Rect>(1) }
    var focused by remember { mutableStateOf(false) }
    DisposableEffect(token) { onDispose { FocusedField.clear(token) } }
    return this
        .onGloballyPositioned {
            bounds[0] = it.boundsInRoot()
            if (focused) FocusedField.set(token, bounds[0])
        }
        .onFocusChanged {
            focused = it.hasFocus
            if (it.hasFocus) FocusedField.set(token, bounds[0]) else FocusedField.clear(token)
        }
}

/**
 * Toque (sem deslizar) fora do campo focado tira o foco dele e fecha o teclado. Olha o toque antes dos filhos e não o
 * consome, então rolar a lista não tira o foco. Vai na raiz de cada janela (a raiz do app e dentro dos painéis).
 */
@Composable
fun Modifier.clearFocusOnTapOutside(): Modifier {
    val focus = LocalFocusManager.current
    val keyboard = LocalSoftwareKeyboardController.current
    val slop = LocalViewConfiguration.current.touchSlop
    val origin = remember { arrayOf(Offset.Zero) }
    return onGloballyPositioned { origin[0] = it.positionInRoot() }.pointerInput(Unit) {
        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
            val field = FocusedField.bounds ?: return@awaitEachGesture
            if (field.contains(down.position + origin[0])) return@awaitEachGesture
            while (true) {
                val event = awaitPointerEvent(PointerEventPass.Initial)
                if (event.changes.size > 1) return@awaitEachGesture
                val change = event.changes.firstOrNull { it.id == down.id } ?: return@awaitEachGesture
                if ((change.position - down.position).getDistance() > slop) return@awaitEachGesture
                if (change.changedToUp()) {
                    focus.clearFocus()
                    keyboard?.hide()
                    return@awaitEachGesture
                }
            }
        }
    }
}

/**
 * Teclado fechado (botão voltar, gesto): o campo perde o foco, em vez de ficar com o cursor piscando como se o teclado
 * ainda estivesse aberto. Basta chamar uma vez por janela.
 */
@Composable
fun ClearFocusWhenKeyboardCloses() {
    val focus = LocalFocusManager.current
    val density = LocalDensity.current
    val ime = WindowInsets.ime
    LaunchedEffect(Unit) {
        var wasVisible = false
        snapshotFlow { ime.getBottom(density) > 0 }.collect { visible ->
            if (wasVisible && !visible) focus.clearFocus()
            wasVisible = visible
        }
    }
}

/**
 * Tira o foco, fecha o teclado e espera ele sair (e a janela do app voltar a ter o foco) antes de abrir uma janela do
 * sistema (seletor de arquivo, biometria). Com o teclado ainda de pé atrás dela, os botões deixavam de sentir o toque.
 */
@Composable
fun rememberKeyboardDismisser(): suspend () -> Unit {
    val focus = LocalFocusManager.current
    val keyboard = LocalSoftwareKeyboardController.current
    val view = LocalView.current
    return remember(focus, keyboard, view) {
        suspend {
            focus.clearFocus(force = true)
            keyboard?.hide()
            delay(KEYBOARD_EXIT_MS)
            var waited = 0L
            while (!view.hasWindowFocus() && waited < FOCUS_WAIT_MAX_MS) {
                delay(FOCUS_POLL_MS)
                waited += FOCUS_POLL_MS
            }
        }
    }
}

private const val KEYBOARD_EXIT_MS = 150L
private const val FOCUS_POLL_MS = 16L
private const val FOCUS_WAIT_MAX_MS = 1000L
