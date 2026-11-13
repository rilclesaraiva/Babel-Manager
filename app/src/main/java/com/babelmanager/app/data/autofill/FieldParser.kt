package com.babelmanager.app.data.autofill

import android.app.assist.AssistStructure
import android.text.InputType
import android.view.View
import android.view.autofill.AutofillId

/** Campos de login encontrados na tela que pediu preenchimento. */
class ParsedForm(
    val usernameId: AutofillId?,
    val passwordIds: List<AutofillId>,
    val packageName: String?,
    val webDomain: String?,
)

/** Lê a estrutura da tela e acha usuário e senha. Heurística simples: dicas explícitas primeiro. */
object FieldParser {

    private class Field(val id: AutofillId, val isPassword: Boolean, val isUsername: Boolean, val isText: Boolean)

    private val USER_WORDS = listOf("user", "usuario", "usuário", "login", "email", "e-mail", "mail", "cpf", "account", "conta")

    fun parse(structure: AssistStructure): ParsedForm? {
        val fields = ArrayList<Field>()
        val domains = ArrayList<String>()
        for (i in 0 until structure.windowNodeCount) {
            walk(structure.getWindowNodeAt(i).rootViewNode, fields, domains, 0)
        }
        val firstPassword = fields.indexOfFirst { it.isPassword }
        val username = if (firstPassword >= 0) {
            val before = fields.subList(0, firstPassword)
            (before.lastOrNull { it.isUsername } ?: before.lastOrNull { it.isText })?.id
        } else {
            fields.firstOrNull { it.isUsername }?.id
        }
        val passwords = fields.filter { it.isPassword }.take(MAX_PASSWORD_FIELDS).map { it.id }
        if (username == null && passwords.isEmpty()) return null
        return ParsedForm(
            usernameId = username,
            passwordIds = passwords,
            packageName = structure.activityComponent?.packageName,
            webDomain = domains.firstOrNull(),
        )
    }

    private fun walk(node: AssistStructure.ViewNode, out: MutableList<Field>, domains: MutableList<String>, depth: Int) {
        if (depth > MAX_DEPTH) return
        node.webDomain?.takeIf { it.isNotBlank() }?.let(domains::add)
        val id = node.autofillId
        if (id != null && node.autofillType == View.AUTOFILL_TYPE_TEXT && node.visibility == View.VISIBLE) {
            classify(node, id)?.let(out::add)
        }
        for (i in 0 until node.childCount) walk(node.getChildAt(i), out, domains, depth + 1)
    }

    private fun classify(node: AssistStructure.ViewNode, id: AutofillId): Field? {
        val hints = node.autofillHints?.map { it.lowercase() }.orEmpty()
        val inputClass = node.inputType and InputType.TYPE_MASK_CLASS
        val variation = node.inputType and InputType.TYPE_MASK_VARIATION
        val htmlType = node.htmlInfo?.attributes?.firstOrNull { it.first.equals("type", ignoreCase = true) }?.second?.lowercase()

        val password = View.AUTOFILL_HINT_PASSWORD.lowercase() in hints ||
            htmlType == "password" ||
            (inputClass == InputType.TYPE_CLASS_TEXT && variation in PASSWORD_VARIATIONS) ||
            (inputClass == InputType.TYPE_CLASS_NUMBER && variation == InputType.TYPE_NUMBER_VARIATION_PASSWORD)
        if (password) return Field(id, isPassword = true, isUsername = false, isText = false)

        val text = inputClass == InputType.TYPE_CLASS_TEXT || htmlType == "text" || htmlType == "email" || htmlType == "tel"
        if (!text) return null
        val label = listOfNotNull(node.idEntry, node.hint, htmlType).joinToString(" ").lowercase()
        val username = View.AUTOFILL_HINT_USERNAME.lowercase() in hints ||
            View.AUTOFILL_HINT_EMAIL_ADDRESS.lowercase() in hints ||
            htmlType == "email" ||
            variation == InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS ||
            variation == InputType.TYPE_TEXT_VARIATION_WEB_EMAIL_ADDRESS ||
            USER_WORDS.any { it in label }
        return Field(id, isPassword = false, isUsername = username, isText = true)
    }

    private val PASSWORD_VARIATIONS = setOf(
        InputType.TYPE_TEXT_VARIATION_PASSWORD,
        InputType.TYPE_TEXT_VARIATION_WEB_PASSWORD,
        InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD,
    )
    private const val MAX_PASSWORD_FIELDS = 3
    private const val MAX_DEPTH = 40
}
