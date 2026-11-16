package com.babelmanager.app

import com.babelmanager.app.data.autofill.AppUri
import com.babelmanager.app.data.autofill.EntryMatcher
import com.babelmanager.app.data.vault.Entry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class EntryMatcherTest {
    private fun e(title: String, url: String = "") = Entry(id = title, title = title, url = url)

    @Test fun hostIsExtractedFromUrls() {
        assertEquals("exemplo.com", EntryMatcher.hostOf("https://www.exemplo.com/login?x=1"))
        assertEquals("exemplo.com", EntryMatcher.hostOf("Exemplo.com:8080/a"))
        assertEquals("exemplo.com", EntryMatcher.hostOf("https://user@exemplo.com/"))
    }

    @Test fun baseDomainHandlesCountrySuffixes() {
        assertEquals("exemplo.com", EntryMatcher.baseDomain("login.exemplo.com"))
        assertEquals("exemplo.com.br", EntryMatcher.baseDomain("conta.login.exemplo.com.br"))
        assertEquals("exemplo.com", EntryMatcher.baseDomain("exemplo.com"))
    }

    @Test fun webDomainMatchesSubdomainsButNotLookalikes() {
        val bank = e("Banco", "https://banco.exemplo.com")
        assertTrue(EntryMatcher.matches(bank, null, "login.exemplo.com"))
        assertFalse(EntryMatcher.matches(bank, null, "exemplo.com.evil.net"))
        assertFalse(EntryMatcher.matches(bank, null, "outro.com"))
        assertFalse(EntryMatcher.matches(e("Sem endereço"), null, "exemplo.com"))
    }

    @Test fun appMatchesByPackageInUrlOrTitleToken() {
        assertTrue(EntryMatcher.matches(e("X", "androidapp://com.spotify.music"), "com.spotify.music", null))
        assertTrue(EntryMatcher.matches(e("Spotify"), "com.spotify.music", null))
        assertFalse(EntryMatcher.matches(e("Netflix"), "com.spotify.music", null))
        assertFalse(EntryMatcher.matches(e("Android"), "com.android.chrome", null))
    }

    @Test fun appUriRoundTripsAndOnlyMatchesItsOwnApp() {
        assertEquals("androidapp://com.spotify.music", AppUri.of("com.spotify.music"))
        assertEquals("com.spotify.music", AppUri.packageOf(" androidapp://com.spotify.music "))
        assertEquals(null, AppUri.packageOf("https://spotify.com"))
        assertEquals(null, AppUri.packageOf("androidapp://"))
        assertEquals("br.com.lojasrenner", AppUri.packageOf("android://1cmZvk4Jj_r0b==@br.com.lojasrenner/"))
        assertEquals(null, AppUri.packageOf("android://sem-pacote"))
        val app = e("Spotify", AppUri.of("com.spotify.music"))
        assertTrue(EntryMatcher.matches(app, "com.spotify.music", null))
        assertFalse(EntryMatcher.matches(app, "com.spotify.lite", null))
        // app híbrido (WebView) que também informa domínio: o pacote ainda vale
        assertTrue(EntryMatcher.matches(app, "com.spotify.music", "open.spotify.com"))
        // um pacote com cara de domínio não pode casar com site
        assertFalse(EntryMatcher.matches(e("X", AppUri.of("exemplo.com")), null, "exemplo.com"))
    }

    @Test fun splitPutsSuggestedFirstAndSortsByTitle() {
        val list = listOf(e("zeta"), e("Beta", "https://exemplo.com"), e("alfa"), e("Alpha", "exemplo.com"))
        val (suggested, others) = EntryMatcher.split(list, null, "exemplo.com")
        assertEquals(listOf("Alpha", "Beta"), suggested.map { it.title })
        assertEquals(listOf("alfa", "zeta"), others.map { it.title })
    }
}
