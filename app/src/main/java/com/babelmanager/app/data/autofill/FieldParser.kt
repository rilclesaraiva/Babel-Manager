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
    /** Texto que já está no campo de usuário (ex.: e-mail preenchido pela página); vazio se nada. */
    val usernameText: String = "",
    /** Algum campo de senha já tem texto. */
    val passwordHasText: Boolean = false,
) {
    val allIds: List<AutofillId> get() = listOfNotNull(usernameId) + passwordIds
}

/** Lê a estrutura da tela e acha usuário e senha. Heurística simples: dicas explícitas primeiro. */
object FieldParser {

    private class Field(
        val id: AutofillId,
        val isPassword: Boolean,
        val isUsername: Boolean,
        val isText: Boolean,
        val text: String,
    )

    private val USER_WORDS = listOf("user", "usuario", "usuário", "login", "email", "e-mail", "mail", "cpf", "account", "conta", "identifier")

    fun parse(structure: AssistStructure): ParsedForm? {
        val fields = ArrayList<Field>()
        val domains = ArrayList<String>()
        for (i in 0 until structure.windowNodeCount) {
            walk(structure.getWindowNodeAt(i).rootViewNode, fields, domains, 0)
        }
        val firstPassword = fields.indexOfFirst { it.isPassword }
        val usernameField = if (firstPassword >= 0) {
            val before = fields.subList(0, firstPassword)
            before.lastOrNull { it.isUsername } ?: before.lastOrNull { it.isText }
        } else {
            fields.firstOrNull { it.isUsername }
        }
        val username = usernameField?.id
        val passwordFields = fields.filter { it.isPassword }.take(MAX_PASSWORD_FIELDS)
        val passwords = passwordFields.map { it.id }
        if (username == null && passwords.isEmpty()) return null
        return ParsedForm(
            usernameId = username,
            passwordIds = passwords,
            packageName = structure.activityComponent?.packageName,
            webDomain = domains.firstOrNull(),
            usernameText = usernameField?.text.orEmpty(),
            passwordHasText = passwordFields.any { it.text.isNotEmpty() },
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
        val value = node.autofillValue
        // Só o valor de autofill (o `text` do nó pode ser o rótulo/placeholder do campo vazio, não o que foi digitado).
        val current = (if (value != null && value.isText) value.textValue.toString() else "").trim()
        val inputClass = node.inputType and InputType.TYPE_MASK_CLASS
        val variation = node.inputType and InputType.TYPE_MASK_VARIATION
        val html = node.htmlInfo
        val attrs = html?.attributes?.associate { it.first.lowercase() to it.second.lowercase() }.orEmpty()
        val htmlType = attrs["type"]
        val isHtmlInput = html?.tag.equals("input", ignoreCase = true)
        val autocomplete = attrs["autocomplete"].orEmpty()

        val password = View.AUTOFILL_HINT_PASSWORD.lowercase() in hints ||
            htmlType == "password" ||
            "password" in autocomplete ||
            (inputClass == InputType.TYPE_CLASS_TEXT && variation in PASSWORD_VARIATIONS) ||
            (inputClass == InputType.TYPE_CLASS_NUMBER && variation == InputType.TYPE_NUMBER_VARIATION_PASSWORD)
        if (password) return Field(id, isPassword = true, isUsername = false, isText = false, text = if (current.isEmpty()) "" else "*") // só importa se há texto; a senha não é guardada

        // Em páginas web o <input> muitas vezes vem sem `type` (padrão = texto) e sem inputType.
        val text = inputClass == InputType.TYPE_CLASS_TEXT ||
            htmlType in TEXT_TYPES ||
            (isHtmlInput && htmlType == null)
        if (!text) return null
        val label = listOfNotNull(
            node.idEntry, node.hint, htmlType,
            attrs["name"], attrs["id"], attrs["placeholder"], attrs["aria-label"], node.contentDescription?.toString(),
        ).joinToString(" ").lowercase()
        // Busca e afins nunca são login (a não ser que a página diga explicitamente que é usuário).
        val explicitUser = View.AUTOFILL_HINT_USERNAME.lowercase() in hints || "username" in autocomplete
        if (!explicitUser && SEARCH_WORDS.any { it in label }) return null
        val username = View.AUTOFILL_HINT_USERNAME.lowercase() in hints ||
            View.AUTOFILL_HINT_EMAIL_ADDRESS.lowercase() in hints ||
            "username" in autocomplete || "email" in autocomplete ||
            htmlType == "email" ||
            variation == InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS ||
            variation == InputType.TYPE_TEXT_VARIATION_WEB_EMAIL_ADDRESS ||
            USER_WORDS.any { it in label }
        return Field(id, isPassword = false, isUsername = username, isText = true, text = current)
    }

    private val SEARCH_WORDS = listOf("search", "pesquis", "busca", "buscar")
    private val TEXT_TYPES = setOf("text", "email", "tel", "url", "number")

    private val PASSWORD_VARIATIONS = setOf(
        InputType.TYPE_TEXT_VARIATION_PASSWORD,
        InputType.TYPE_TEXT_VARIATION_WEB_PASSWORD,
        InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD,
    )
    private const val MAX_PASSWORD_FIELDS = 3
    private const val MAX_DEPTH = 40
}
