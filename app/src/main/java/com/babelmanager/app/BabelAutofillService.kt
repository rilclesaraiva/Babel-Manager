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
            callback.onSuccess(AutofillResponses.authResponse(this, form, inline))
            return
        }

        val (suggested, _) = EntryMatcher.split(container.vault.entries.value, form.packageName, form.webDomain)
        val datasets = suggested.mapIndexedNotNull { i, entry -> AutofillResponses.entryDataset(this, entry, form, inline, i) }
            .take(MAX_SUGGESTIONS)
        if (datasets.isEmpty()) {
            callback.onSuccess(AutofillResponses.authResponse(this, form, inline))
            return
        }

        val response = FillResponse.Builder()
        datasets.forEach(response::addDataset)
        response.addDataset(AutofillResponses.otherDataset(this, form, inline, datasets.size))
        callback.onSuccess(response.build())
    }

    override fun onSaveRequest(request: SaveRequest, callback: SaveCallback) {
        callback.onSuccess()
    }

    private companion object {
        const val MAX_SUGGESTIONS = 4
    }
}
