package com.babelmanager.app

import com.babelmanager.app.data.vault.Entry
import android.os.CancellationSignal
import android.service.autofill.AutofillService
import android.service.autofill.FillCallback
import android.service.autofill.FillRequest
import android.service.autofill.FillResponse
import android.service.autofill.SaveCallback
import android.service.autofill.SaveRequest
import com.babelmanager.app.data.autofill.EntryMatcher
import com.babelmanager.app.data.autofill.FieldParser
import com.babelmanager.app.data.autofill.ParsedForm
import android.view.inputmethod.InlineSuggestionsRequest
import com.babelmanager.app.data.vault.VaultRepository

/**
 * Serviço de preenchimento automático.
 * - Cofre trancado: só "Preencher com o Babel Manager", que abre o app para desbloquear.
 * - Cofre aberto: oferece direto as entradas que combinam com o site/app (título e usuário; a senha
 *   nunca aparece na lista), mais "Outras entradas…" para escolher qualquer uma.
 * Salvar senhas novas ainda não é suportado.
 */
class BabelAutofillService : AutofillService() {

    override fun onFillRequest(request: FillRequest, cancellationSignal: CancellationSignal, callback: FillCallback) {
        val structure = request.fillContexts.lastOrNull()?.structure
        val form = structure?.let(FieldParser::parse)
        if (form == null || form.packageName == packageName) {
            callback.onSuccess(null)
            return
        }

        val container = (application as BabelApp).container
        container.autoLock.checkExpired() // não oferecer dados se o app já deveria estar trancado
        val inline = request.inlineSuggestionsRequest

        // Já tem senha escrita: não há o que sugerir.
        if (form.passwordHasText) {
            callback.onSuccess(null)
            return
        }
        // Já tem texto no campo de usuário (ex.: e-mail que a página preencheu): só sugere o que começa com ele.
        val typed = form.usernameText.trim()

        if (container.vault.state.value != VaultRepository.State.Unlocked) {
            callback.onSuccess(lockedResponse(container, form, inline, typed))
            return
        }

        val (suggested, _) = EntryMatcher.split(container.vault.entries.value, form.packageName, form.webDomain)
        val datasets = suggested.filter { startsLikeTyped(it, typed) }.mapIndexedNotNull { i, entry -> AutofillResponses.entryDataset(this, entry, form, inline, i) }
            .take(MAX_SUGGESTIONS)
        if (datasets.isEmpty()) {
            callback.onSuccess(if (typed.isEmpty()) AutofillResponses.authResponse(this, form, inline, pickAny = true) else null)
            return
        }

        val response = FillResponse.Builder()
        datasets.forEach(response::addDataset)
        if (typed.isEmpty()) {
            response.addDataset(
                AutofillResponses.authDataset(this, form, inline, datasets.size, getString(R.string.autofill_other), pickAny = true),
            )
        }
        callback.onSuccess(response.build())
    }

    private fun startsLikeTyped(entry: Entry, typed: String): Boolean =
        typed.isEmpty() || entry.username.startsWith(typed, ignoreCase = true) || entry.title.startsWith(typed, ignoreCase = true)

    /**
     * Cofre trancado. Com "sugestões com o cofre trancado" (e biometria) ligadas, lista as entradas que
     * combinam (do índice, sem senha); tocar numa delas abre só o painel do sistema (digital ou PIN).
     * Sem isso, ou sem sugestão, fica o item "Preencher com o Babel Manager" que abre o app.
     */
    private fun lockedResponse(container: AppContainer, form: ParsedForm, inline: InlineSuggestionsRequest?, typed: String): FillResponse? {
        val s = container.settings.current
        if (s.lockedSuggestions && s.biometricEnabled) {
            val (suggested, _) = EntryMatcher.split(container.suggestionIndex.load(), form.packageName, form.webDomain)
            val matching = suggested.filter { startsLikeTyped(it, typed) }
            if (matching.isNotEmpty()) {
                val response = FillResponse.Builder()
                val shown = matching.take(MAX_SUGGESTIONS)
                shown.forEachIndexed { i, entry ->
                    response.addDataset(
                        AutofillResponses.authDataset(this, form, inline, i, entry.title, pickAny = false, subtitle = entry.username, entryId = entry.id, matchTexts = listOf(entry.username, entry.title)),
                    )
                }
                if (typed.isEmpty()) {
                    response.addDataset(
                        AutofillResponses.authDataset(this, form, inline, shown.size, getString(R.string.autofill_other), pickAny = true),
                    )
                }
                return response.build()
            }
        }
        return if (typed.isEmpty()) AutofillResponses.authResponse(this, form, inline) else null
    }

    override fun onSaveRequest(request: SaveRequest, callback: SaveCallback) {
        callback.onSuccess()
    }

    private companion object {
        const val MAX_SUGGESTIONS = 4
    }
}
