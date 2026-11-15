package com.babelmanager.app

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.IntentSender
import android.graphics.drawable.Icon
import android.service.autofill.Dataset
import android.service.autofill.FillResponse
import android.service.autofill.InlinePresentation
import android.view.View
import android.view.autofill.AutofillValue
import android.view.inputmethod.InlineSuggestionsRequest
import android.widget.RemoteViews
import androidx.autofill.inline.UiVersions
import androidx.autofill.inline.v1.InlineSuggestionUi
import com.babelmanager.app.data.autofill.ParsedForm
import com.babelmanager.app.data.vault.Entry
import java.util.concurrent.atomic.AtomicInteger

/**
 * Monta o que o sistema mostra: lista suspensa e sugestões embutidas no teclado. Nunca inclui a
 * senha em nenhum texto exibido; ela só vai como valor a preencher, quando a pessoa escolhe.
 */
internal object AutofillResponses {
    private val codes = AtomicInteger(0)

    fun view(context: Context, title: String, subtitle: String?): RemoteViews =
        RemoteViews(context.packageName, R.layout.autofill_item).apply {
            setTextViewText(R.id.autofill_text, title)
            if (subtitle.isNullOrEmpty()) setViewVisibility(R.id.autofill_sub, View.GONE)
            else setTextViewText(R.id.autofill_sub, subtitle)
        }

    private fun inline(
        context: Context,
        request: InlineSuggestionsRequest?,
        index: Int,
        title: String,
        subtitle: String?,
    ): InlinePresentation? {
        val specs = request?.inlinePresentationSpecs
        if (specs.isNullOrEmpty()) return null
        val spec = specs[minOf(index, specs.size - 1)]
        if (!UiVersions.getVersions(spec.style).contains(UiVersions.INLINE_UI_VERSION_1)) return null
        val attribution = PendingIntent.getActivity(
            context,
            0,
            Intent(context, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE,
        )
        val content = InlineSuggestionUi.newContentBuilder(attribution)
            .setTitle(title)
            .apply { if (!subtitle.isNullOrEmpty()) setSubtitle(subtitle) }
            .setStartIcon(Icon.createWithResource(context, R.drawable.ic_autofill_key))
            .build()
        return InlinePresentation(content.slice, spec, false)
    }

    /** Abre a [AutofillActivity] (desbloqueio e/ou escolha da entrada). */
    private fun authSender(context: Context, form: ParsedForm, pickAny: Boolean): IntentSender {
        val intent = Intent(context, AutofillActivity::class.java)
            .putExtra(AutofillActivity.EXTRA_USERNAME_ID, form.usernameId)
            .putParcelableArrayListExtra(AutofillActivity.EXTRA_PASSWORD_IDS, ArrayList(form.passwordIds))
            .putExtra(AutofillActivity.EXTRA_PACKAGE, form.packageName)
            .putExtra(AutofillActivity.EXTRA_DOMAIN, form.webDomain)
            .putExtra(AutofillActivity.EXTRA_PICK_ANY, pickAny)
        // O sistema acrescenta dados ao Intent: o PendingIntent precisa ser mutável.
        return PendingIntent.getActivity(
            context,
            codes.incrementAndGet(),
            intent,
            PendingIntent.FLAG_CANCEL_CURRENT or PendingIntent.FLAG_MUTABLE,
        ).intentSender
    }

    /**
     * Cofre trancado (ou sem sugestão): um único item que abre o app. É um item com autenticação **do
     * dataset** (e não da resposta inteira): assim o Android preenche direto o que o app devolver, sem
     * mostrar a lista de novo para a pessoa tocar outra vez.
     */
    fun authResponse(context: Context, form: ParsedForm, request: InlineSuggestionsRequest?, pickAny: Boolean = false): FillResponse =
        FillResponse.Builder()
            .addDataset(authDataset(context, form, request, 0, context.getString(if (pickAny) R.string.autofill_other else R.string.autofill_presentation), pickAny))
            .build()

    /** Dataset com os valores da entrada; null se a entrada não tem nada para esta tela. */
    fun entryDataset(
        context: Context,
        entry: Entry,
        form: ParsedForm,
        request: InlineSuggestionsRequest?,
        index: Int,
    ): Dataset? {
        val builder = Dataset.Builder(view(context, entry.title, entry.username))
        var any = false
        val usernameId = form.usernameId
        if (usernameId != null && entry.username.isNotEmpty()) {
            builder.setValue(usernameId, AutofillValue.forText(entry.username))
            any = true
        }
        if (entry.password.isNotEmpty()) {
            for (id in form.passwordIds) {
                builder.setValue(id, AutofillValue.forText(entry.password))
                any = true
            }
        }
        if (!any) return null
        inline(context, request, index, entry.title, entry.username)?.let(builder::setInlinePresentation)
        return builder.build()
    }

    /** Item que abre o app: `pickAny` mostra a lista completa; senão o app escolhe sozinho se só houver uma sugestão. */
    fun authDataset(context: Context, form: ParsedForm, request: InlineSuggestionsRequest?, index: Int, title: String, pickAny: Boolean): Dataset {
        val builder = Dataset.Builder(view(context, title, null))
        for (id in form.allIds) builder.setValue(id, null)
        builder.setAuthentication(authSender(context, form, pickAny))
        inline(context, request, index, title, null)?.let(builder::setInlinePresentation)
        return builder.build()
    }
}
