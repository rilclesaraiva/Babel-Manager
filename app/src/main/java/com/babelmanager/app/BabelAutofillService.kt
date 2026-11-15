package com.babelmanager.app

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

        if (container.vault.state.value != VaultRepository.State.Unlocked) {
            callback.onSuccess(lockedResponse(container, form, inline))
            return
        }

        val (suggested, _) = EntryMatcher.split(container.vault.entries.value, form.packageName, form.webDomain)
        val datasets = suggested.mapIndexedNotNull { i, entry -> AutofillResponses.entryDataset(this, entry, form, inline, i) }
            .take(MAX_SUGGESTIONS)
        if (datasets.isEmpty()) {
            callback.onSuccess(AutofillResponses.authResponse(this, form, inline, pickAny = true))
            return
        }

        val response = FillResponse.Builder()
        datasets.forEach(response::addDataset)
        response.addDataset(
            AutofillResponses.authDataset(this, form, inline, datasets.size, getString(R.string.autofill_other), pickAny = true),
        )
        callback.onSuccess(response.build())
    }

    /**
     * Cofre trancado. Com "sugestões com o cofre trancado" (e biometria) ligadas, lista as entradas que
     * combinam (do índice, sem senha); tocar numa delas abre só o painel do sistema (digital ou PIN).
     * Sem isso, ou sem sugestão, fica o item "Preencher com o Babel Manager" que abre o app.
     */
    private fun lockedResponse(container: AppContainer, form: ParsedForm, inline: InlineSuggestionsRequest?): FillResponse {
        val s = container.settings.current
        if (s.lockedSuggestions && s.biometricEnabled) {
            val (suggested, _) = EntryMatcher.split(container.suggestionIndex.load(), form.packageName, form.webDomain)
            if (suggested.isNotEmpty()) {
                val response = FillResponse.Builder()
                val shown = suggested.take(MAX_SUGGESTIONS)
                shown.forEachIndexed { i, entry ->
                    response.addDataset(
                        AutofillResponses.authDataset(this, form, inline, i, entry.title, pickAny = false, subtitle = entry.username, entryId = entry.id),
                    )
                }
                response.addDataset(
                    AutofillResponses.authDataset(this, form, inline, shown.size, getString(R.string.autofill_other), pickAny = true),
                )
                return response.build()
            }
        }
        return AutofillResponses.authResponse(this, form, inline)
    }

    override fun onSaveRequest(request: SaveRequest, callback: SaveCallback) {
        callback.onSuccess()
    }

    private companion object {
        const val MAX_SUGGESTIONS = 4
    }
}
