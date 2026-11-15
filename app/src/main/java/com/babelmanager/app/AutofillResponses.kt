package com.babelmanager.app

import java.util.regex.Pattern
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
    private fun authSender(context: Context, form: ParsedForm, pickAny: Boolean, entryId: String? = null): IntentSender {
        val intent = Intent(context, AutofillActivity::class.java)
            .putExtra(AutofillActivity.EXTRA_USERNAME_ID, form.usernameId)
            .putParcelableArrayListExtra(AutofillActivity.EXTRA_PASSWORD_IDS, ArrayList(form.passwordIds))
            .putExtra(AutofillActivity.EXTRA_PACKAGE, form.packageName)
            .putExtra(AutofillActivity.EXTRA_DOMAIN, form.webDomain)
            .putExtra(AutofillActivity.EXTRA_PICK_ANY, pickAny)
            .putExtra(AutofillActivity.EXTRA_ENTRY_ID, entryId)
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
        // Filtro explícito (em vez da regra padrão do sistema): no campo de usuário a sugestão fica enquanto o
        // texto digitado for o começo do usuário/título; na senha, só com o campo vazio.
        @Suppress("DEPRECATION")
        if (usernameId != null && entry.username.isNotEmpty()) {
            builder.setValue(usernameId, AutofillValue.forText(entry.username), startsLike(listOf(entry.username, entry.title)))
            any = true
        }
        if (entry.password.isNotEmpty()) {
            for (id in form.passwordIds) {
                @Suppress("DEPRECATION")
                builder.setValue(id, AutofillValue.forText(entry.password), EMPTY_ONLY)
                any = true
            }
        }
        if (!any) return null
        inline(context, request, index, entry.title, entry.username)?.let(builder::setInlinePresentation)
        return builder.build()
    }

    /**
     * Item que abre o app: `pickAny` mostra a lista completa; senão o app escolhe sozinho se só houver uma sugestão.
     *
     * No Android 13+ o item some quando a pessoa já digitou algo: no campo de usuário só fica se o texto
     * digitado for o começo do usuário/título de [matchTexts]; nos demais (e sem [matchTexts]) só com o campo vazio.
     * Antes do Android 13 não há como filtrar item sem valor, então ele fica sempre à mostra.
     */
    fun authDataset(
        context: Context,
        form: ParsedForm,
        request: InlineSuggestionsRequest?,
        index: Int,
        title: String,
        pickAny: Boolean,
        subtitle: String? = null,
        entryId: String? = null,
        matchTexts: List<String> = emptyList(),
    ): Dataset {
        val builder = Dataset.Builder(view(context, title, subtitle))
        // setValue(id, null, filtro) existe desde o Android 8 e vale em qualquer versão: o sistema esconde o item
        // quando o texto do campo não casa com o filtro (o formato novo com Field só existe no Android 13+).
        for (id in form.allIds) {
            val pattern = if (id == form.usernameId) startsLike(matchTexts) else EMPTY_ONLY
            @Suppress("DEPRECATION")
            builder.setValue(id, null, pattern)
        }
        builder.setAuthentication(authSender(context, form, pickAny, entryId))
        inline(context, request, index, title, subtitle)?.let(builder::setInlinePresentation)
        return builder.build()
    }

    private val EMPTY_ONLY: Pattern = Pattern.compile("")

    /**
     * Casa com o texto vazio e com qualquer começo (sem diferenciar maiúsculas) de um dos [texts].
     * Lista plana de prefixos, sem grupos aninhados: simples para qualquer motor de regex.
     */
    private fun startsLike(texts: List<String>): Pattern {
        val prefixes = LinkedHashSet<String>()
        for (text in texts.filter { it.isNotBlank() }) {
            val t = text.take(MAX_MATCH_CHARS)
            for (n in 1..t.length) prefixes += t.substring(0, n)
        }
        if (prefixes.isEmpty()) return EMPTY_ONLY
        return Pattern.compile("(?:|" + prefixes.joinToString("|") { Pattern.quote(it) } + ")", Pattern.CASE_INSENSITIVE)
    }

    private const val MAX_MATCH_CHARS = 64
}
