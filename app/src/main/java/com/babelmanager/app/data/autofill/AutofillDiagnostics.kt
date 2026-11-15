package com.babelmanager.app.data.autofill

import android.app.assist.AssistStructure
import android.text.InputType
import android.view.View
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Diagnóstico do preenchimento (Ajustes, desligado por padrão). Guarda **só na memória** o que o Android
 * informou em cada pedido: tipo, dicas e papel de cada campo, e se havia texto. Nunca o texto em si, nem senha,
 * nem nada do cofre. Some quando o app fecha.
 */
object AutofillDiagnostics {
    private const val MAX_RECORDS = 20
    private const val MAX_NODES = 40
    private const val MAX_DEPTH = 40

    private val _log = MutableStateFlow<List<String>>(emptyList())
    val log: StateFlow<List<String>> = _log.asStateFlow()

    fun clear() {
        _log.value = emptyList()
    }

    fun record(structure: AssistStructure?, form: ParsedForm?, outcome: String) {
        val lines = ArrayList<String>()
        val time = java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.US).format(java.util.Date())
        lines += "[$time] ${structure?.activityComponent?.packageName ?: "?"} -> $outcome"
        if (structure != null) {
            val domains = LinkedHashSet<String>()
            val nodes = ArrayList<String>()
            for (i in 0 until structure.windowNodeCount) {
                walk(structure.getWindowNodeAt(i).rootViewNode, form, domains, nodes, 0)
            }
            lines += "  dominio: ${domains.joinToString().ifEmpty { "(nenhum)" }}"
            lines += "  campos com id: ${nodes.size}" + if (nodes.size >= MAX_NODES) "+" else ""
            lines += nodes
        }
        lines += "  formulario: " + if (form == null) "nao reconhecido" else
            "usuario=${form.usernameId != null} senhas=${form.passwordIds.size} textoUsuario=${form.usernameText.isNotEmpty()} textoSenha=${form.passwordHasText}"
        _log.value = (listOf(lines.joinToString("\n")) + _log.value).take(MAX_RECORDS)
    }

    private fun walk(node: AssistStructure.ViewNode, form: ParsedForm?, domains: MutableSet<String>, out: MutableList<String>, depth: Int) {
        if (depth > MAX_DEPTH || out.size >= MAX_NODES) return
        node.webDomain?.takeIf { it.isNotBlank() }?.let(domains::add)
        val id = node.autofillId
        val type = node.autofillType
        val html = node.htmlInfo
        if (id != null && (type != View.AUTOFILL_TYPE_NONE || html?.tag.equals("input", true))) {
            val attrs = html?.attributes?.associate { it.first.lowercase() to it.second.lowercase() }.orEmpty()
            val value = node.autofillValue
            val role = when {
                form?.usernameId == id -> "USUARIO"
                form?.passwordIds?.contains(id) == true -> "SENHA"
                else -> "ignorado"
            }
            val parts = listOfNotNull(
                role,
                "tipo=" + when (type) { View.AUTOFILL_TYPE_TEXT -> "texto"; View.AUTOFILL_TYPE_NONE -> "nenhum"; else -> type.toString() },
                "visivel=" + (node.visibility == View.VISIBLE),
                "vazio=" + !(value != null && value.isText && value.textValue.isNotEmpty()),
                node.autofillHints?.takeIf { it.isNotEmpty() }?.let { "dicas=" + it.joinToString("|") },
                html?.tag?.let { "tag=$it" },
                attrs["type"]?.let { "type=$it" },
                attrs["autocomplete"]?.let { "autocomplete=$it" },
                attrs["name"]?.let { "name=$it" },
                attrs["id"]?.let { "htmlId=$it" },
                node.idEntry?.let { "idEntry=$it" },
                "inputType=" + (node.inputType and InputType.TYPE_MASK_CLASS) + "/" + (node.inputType and InputType.TYPE_MASK_VARIATION),
            )
            out += "  - " + parts.joinToString(" ")
        }
        for (i in 0 until node.childCount) walk(node.getChildAt(i), form, domains, out, depth + 1)
    }
}
