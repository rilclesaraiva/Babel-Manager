package com.babelmanager.app

import com.babelmanager.app.data.vault.Entry
import android.os.CancellationSignal
import android.service.autofill.AutofillService
import android.service.autofill.FillCallback
import android.service.autofill.FillRequest
import android.service.autofill.FillResponse
import android.service.autofill.SaveCallback
import android.service.autofill.SaveRequest
import com.babelmanager.app.data.autofill.AutofillDiagnostics
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
        val diagnostics = (application as BabelApp).container.settings.current.autofillDiagnostics
        fun reply(outcome: String, response: FillResponse?) {
            if (diagnostics) AutofillDiagnostics.record(structure, form, outcome)
            callback.onSuccess(response)
        }
        if (form == null || form.packageName == packageName) {
            reply("nada (sem formulario ou o proprio app)", null)
            return
        }

        val container = (application as BabelApp).container
        container.autoLock.checkExpired() // não oferecer dados se o app já deveria estar trancado
        val inline = request.inlineSuggestionsRequest

        // Já tem senha escrita: não há o que sugerir.
        if (form.passwordHasText) {
            reply("nada (senha ja tem texto)", null)
            return
        }
        // Já tem texto no campo de usuário (ex.: e-mail que a página preencheu): só sugere o que começa com ele.
        val typed = form.usernameText.trim()

        if (container.vault.state.value != VaultRepository.State.Unlocked) {
            val locked = lockedResponse(container, form, inline, typed)
            reply(if (locked == null) "nada (trancado, texto nao combina)" else "trancado: ofereceu itens", locked)
            return
        }

        val (suggested, _) = EntryMatcher.split(container.vault.entries.value, form.packageName, form.webDomain)
        val datasets = suggested.filter { startsLikeTyped(it, typed) }.mapIndexedNotNull { i, entry -> AutofillResponses.entryDataset(this, entry, form, inline, i) }
            .take(MAX_SUGGESTIONS)

        val response = FillResponse.Builder()
        datasets.forEach(response::addDataset)
        var count = datasets.size
        if (typed.isEmpty()) {
            response.addDataset(
                AutofillResponses.authDataset(this, form, inline, count, getString(R.string.autofill_other), pickAny = true),
            )
            count++
        }
        count += addActions(response, form, inline, count, withManage = typed.isEmpty())
        if (count == 0) {
            reply("nada (aberto, texto nao combina)", null)
            return
        }
        val check = if (diagnostics) suggested.firstOrNull { it.username.isNotEmpty() }?.let { "; " + AutofillResponses.filterSelfCheck(it) }.orEmpty() else ""
        reply("aberto: ofereceu ${datasets.size} entrada(s)$check", response.build())
    }

    /**
     * Ações no fim da lista, como no gerenciador do Google: "Sugerir senha forte" (só com campo de senha; gera, salva e
     * preenche) e "Gerenciar senhas" (abre o app). Devolve quantos itens foram acrescentados.
     */
    private fun addActions(response: FillResponse.Builder, form: ParsedForm, inline: InlineSuggestionsRequest?, startIndex: Int, withManage: Boolean): Int {
        var n = 0
        if (form.passwordIds.isNotEmpty()) {
            response.addDataset(
                AutofillResponses.authDataset(this, form, inline, startIndex + n, getString(R.string.autofill_suggest_strong), pickAny = false, mode = AutofillActivity.MODE_SUGGEST),
            )
            n++
        }
        if (withManage) {
            response.addDataset(
                AutofillResponses.authDataset(this, form, inline, startIndex + n, getString(R.string.autofill_manage), pickAny = false, mode = AutofillActivity.MODE_MANAGE),
            )
            n++
        }
        return n
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
                var count = shown.size
                if (typed.isEmpty()) {
                    response.addDataset(
                        AutofillResponses.authDataset(this, form, inline, count, getString(R.string.autofill_other), pickAny = true),
                    )
                    count++
                }
                addActions(response, form, inline, count, withManage = typed.isEmpty())
                return response.build()
            }
        }
        val response = FillResponse.Builder()
        var count = 0
        if (typed.isEmpty()) {
            response.addDataset(AutofillResponses.authDataset(this, form, inline, 0, getString(R.string.autofill_presentation), pickAny = false))
            count++
        }
        count += addActions(response, form, inline, count, withManage = typed.isEmpty())
        return if (count == 0) null else response.build()
    }

    override fun onSaveRequest(request: SaveRequest, callback: SaveCallback) {
        callback.onSuccess()
    }

    private companion object {
        const val MAX_SUGGESTIONS = 4
    }
}
