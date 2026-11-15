package com.babelmanager.app.data.autofill

import com.babelmanager.app.data.vault.Entry

/**
 * Escolhe quais entradas combinam com a tela que pediu preenchimento.
 * Navegador: compara o domínio da página com o endereço salvo na entrada.
 * App: procura o nome do pacote no endereço (ex: `androidapp://com.exemplo.app`) ou no título.
 * É só uma sugestão: a pessoa sempre escolhe a entrada na tela do Babel Manager.
 */
/** Endereço de entrada que aponta para um app Android: `androidapp://com.exemplo.app`. */
object AppUri {
    const val PREFIX = "androidapp://"

    fun of(packageName: String): String = PREFIX + packageName

    fun packageOf(url: String): String? {
        val t = url.trim()
        if (!t.startsWith(PREFIX, ignoreCase = true)) return null
        return t.substring(PREFIX.length).trim().ifEmpty { null }
    }
}

object EntryMatcher {

    private val SECOND_LEVEL = setOf("com", "net", "org", "gov", "edu", "co", "ac")
    private val GENERIC_TOKENS = setOf("com", "org", "net", "android", "app", "apps", "mobile", "www", "google", "release", "prod", "production")

    fun hostOf(url: String): String {
        var s = url.trim().lowercase()
        s = s.substringAfter("://", s)
        s = s.substringBefore('/').substringBefore('?').substringBefore('#').substringAfterLast('@').substringBefore(':')
        return s.removePrefix("www.")
    }

    /** Domínio-base: `login.exemplo.com.br` → `exemplo.com.br`. Aproximação sem lista pública de sufixos. */
    fun baseDomain(host: String): String {
        val labels = host.split('.').filter { it.isNotEmpty() }
        if (labels.size <= 2) return labels.joinToString(".")
        val keep = if (labels.last().length == 2 && labels[labels.size - 2] in SECOND_LEVEL) 3 else 2
        return labels.takeLast(keep).joinToString(".")
    }

    fun matches(entry: Entry, packageName: String?, webDomain: String?): Boolean {
        val url = entry.url.trim()
        val entryApp = AppUri.packageOf(url)
        if (!webDomain.isNullOrBlank()) {
            if (entryApp != null) return false // entrada de app nunca combina com site
            val wanted = hostOf(webDomain)
            val have = hostOf(url)
            return have.isNotEmpty() && wanted.isNotEmpty() && baseDomain(have) == baseDomain(wanted)
        }
        if (packageName.isNullOrBlank()) return false
        val pkg = packageName.lowercase()
        if (entryApp != null) return entryApp.lowercase() == pkg
        if (url.lowercase().contains(pkg)) return true
        val tokens = pkg.split('.').filter { it.length >= 4 && it !in GENERIC_TOKENS }
        val haystack = (entry.title + " " + hostOf(url)).lowercase()
        return tokens.any { it in haystack }
    }

    /** Sugeridas primeiro (nesta ordem), depois as demais por título. */
    fun split(entries: List<Entry>, packageName: String?, webDomain: String?): Pair<List<Entry>, List<Entry>> {
        val (suggested, others) = entries.partition { matches(it, packageName, webDomain) }
        val byTitle = compareBy<Entry, String>(String.CASE_INSENSITIVE_ORDER) { it.title }
        return suggested.sortedWith(byTitle) to others.sortedWith(byTitle)
    }
}
