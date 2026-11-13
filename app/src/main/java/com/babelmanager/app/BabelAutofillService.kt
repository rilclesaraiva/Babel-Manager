package com.babelmanager.app

import android.app.PendingIntent
import android.content.Intent
import android.os.CancellationSignal
import android.service.autofill.AutofillService
import android.service.autofill.FillCallback
import android.service.autofill.FillRequest
import android.service.autofill.FillResponse
import android.service.autofill.SaveCallback
import android.service.autofill.SaveRequest
import android.widget.RemoteViews
import com.babelmanager.app.data.autofill.FieldParser
import java.util.concurrent.atomic.AtomicInteger

/**
 * Serviço de preenchimento automático. Nunca mostra dados do cofre na lista do sistema:
 * responde só com "Preencher com o Babel Manager", que abre a tela do app (desbloqueio e escolha da
 * entrada). Salvar senhas novas ainda não é suportado.
 */
class BabelAutofillService : AutofillService() {

    override fun onFillRequest(request: FillRequest, cancellationSignal: CancellationSignal, callback: FillCallback) {
        val structure = request.fillContexts.lastOrNull()?.structure
        val form = structure?.let(FieldParser::parse)
        if (form == null || form.packageName == packageName) {
            callback.onSuccess(null)
            return
        }

        val intent = Intent(this, AutofillActivity::class.java)
            .putExtra(AutofillActivity.EXTRA_USERNAME_ID, form.usernameId)
            .putParcelableArrayListExtra(AutofillActivity.EXTRA_PASSWORD_IDS, ArrayList(form.passwordIds))
            .putExtra(AutofillActivity.EXTRA_PACKAGE, form.packageName)
            .putExtra(AutofillActivity.EXTRA_DOMAIN, form.webDomain)
        // O sistema acrescenta dados ao Intent: o PendingIntent precisa ser mutável.
        val sender = PendingIntent.getActivity(
            this,
            nextCode.incrementAndGet(),
            intent,
            PendingIntent.FLAG_CANCEL_CURRENT or PendingIntent.FLAG_MUTABLE,
        ).intentSender

        val presentation = RemoteViews(packageName, R.layout.autofill_item)
            .apply { setTextViewText(R.id.autofill_text, getString(R.string.autofill_presentation)) }
        val ids = (listOfNotNull(form.usernameId) + form.passwordIds).toTypedArray()
        callback.onSuccess(FillResponse.Builder().setAuthentication(ids, sender, presentation).build())
    }

    override fun onSaveRequest(request: SaveRequest, callback: SaveCallback) {
        callback.onSuccess()
    }

    private companion object {
        val nextCode = AtomicInteger(0)
    }
}
