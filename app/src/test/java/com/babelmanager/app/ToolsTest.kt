package com.babelmanager.app

import com.babelmanager.app.core.security.MemorableOptions
import com.babelmanager.app.core.security.PasswordGenerator
import com.babelmanager.app.core.security.PasswordStrength
import com.babelmanager.app.core.security.RandomOptions
import com.babelmanager.app.core.security.Separator
import com.babelmanager.app.core.security.StrengthLevel
import com.babelmanager.app.core.security.WordList
import com.babelmanager.app.core.util.SemVer
import com.babelmanager.app.data.importer.ImportFormatException
import com.babelmanager.app.data.importer.PasswordExporter
import com.babelmanager.app.data.importer.PasswordImporter
import com.babelmanager.app.data.vault.Entry
import com.babelmanager.app.features.checkup.PasswordHealth
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class ToolsTest {

    // ── Gerador ──

    @Test
    fun randomPasswordHasRequestedLengthAndEveryChosenClass() {
        val generator = PasswordGenerator()
        repeat(200) {
            val pw = generator.random(RandomOptions(length = 12, upper = true, lower = true, digits = true, symbols = true))
            assertEquals(12, pw.length)
            assertTrue(pw.any { it.isUpperCase() })
            assertTrue(pw.any { it.isLowerCase() })
            assertTrue(pw.any { it.isDigit() })
            assertTrue(pw.any { !it.isLetterOrDigit() })
        }
    }

    @Test
    fun randomPasswordRespectsDisabledClasses() {
        val generator = PasswordGenerator()
        repeat(100) {
            val pw = generator.random(RandomOptions(length = 30, upper = false, lower = true, digits = false, symbols = false))
            assertTrue(pw.all { it in 'a'..'z' })
        }
    }

    @Test
    fun randomLengthIsClamped() {
        val generator = PasswordGenerator()
        assertEquals(PasswordGenerator.MIN_LENGTH, generator.random(RandomOptions(length = 1)).length)
        assertEquals(PasswordGenerator.MAX_LENGTH, generator.random(RandomOptions(length = 999)).length)
    }

    @Test
    fun memorablePasswordUsesWordsSeparatorsAndNumbers() {
        val generator = PasswordGenerator()
        val pw = generator.memorable(MemorableOptions(words = 5, capitalize = true, numbers = true, separator = Separator.Hyphen))
        val parts = pw.split("-")
        assertEquals(5, parts.size)
        parts.forEach {
            assertTrue(it.first().isUpperCase())
            assertTrue(it.last().isDigit())
        }
    }

    @Test
    fun wordListIsCleanAndBigEnough() {
        assertTrue(WordList.words.size >= 256)
        assertTrue(WordList.words.all { w -> w.length in 4..9 && w.all { it in 'a'..'z' } })
    }

    @Test
    fun entropyEstimatesGrowWithOptions() {
        val generator = PasswordGenerator()
        val short = generator.memorableEntropyBits(MemorableOptions(words = 3, numbers = false))
        val long = generator.memorableEntropyBits(MemorableOptions(words = 8, numbers = true))
        assertTrue(long > short)
    }

    // ── Força ──

    @Test
    fun strengthLevels() {
        assertEquals(StrengthLevel.Weak, PasswordStrength.estimate("").level)
        assertEquals(StrengthLevel.Weak, PasswordStrength.estimate("password").level)
        assertEquals(StrengthLevel.Weak, PasswordStrength.estimate("06815925242").level) // só dígitos
        assertEquals(StrengthLevel.Weak, PasswordStrength.estimate("aaaaaaaaaaaa").level)
        assertEquals(StrengthLevel.Weak, PasswordStrength.estimate("Ab1!").level) // curta
        assertTrue(PasswordStrength.estimate("nWm&Jn%WDM&4;xgN").level >= StrengthLevel.Strong)
    }

    // ── SemVer ──

    @Test
    fun semverParsingAndOrdering() {
        assertEquals(SemVer(1, 2, 3), SemVer.parse("v1.2.3"))
        assertEquals(SemVer(1, 2, 3), SemVer.parse("1.2.3-beta"))
        assertNull(SemVer.parse("latest"))
        assertTrue(SemVer.parse("1.10.0")!! > SemVer.parse("1.9.9")!!)
        assertTrue(SemVer.parse("2.0.0")!! > SemVer.parse("1.99.99")!!)
        assertTrue(SemVer.parse("0.0.1")!! > SemVer.parse("0.0.0")!!)
    }

    // ── Importação ──

    @Test
    fun importsChromeAndGoogleCsv() {
        val csv = "name,url,username,password,note\n" +
            "example.com,https://example.com/login,me@mail.com,\"pa,ss\"\"word\",\"line1\nline2\"\n" +
            "empty.com,https://empty.com,,,\n"
        val result = PasswordImporter.parse(csv) { "id" }
        assertEquals(1, result.entries.size)
        assertEquals(1, result.skipped)
        val e = result.entries.single()
        assertEquals("example.com", e.title)
        assertEquals("me@mail.com", e.username)
        assertEquals("pa,ss\"word", e.password)
        assertEquals("line1\nline2", e.note)
    }

    @Test
    fun importsBitwardenCsvAndHandlesBomAndCrLf() {
        val csv = "﻿folder,favorite,type,name,notes,fields,reprompt,login_uri,login_username,login_password,login_totp\r\n" +
            ",,login,My Bank,,,0,https://www.bank.com,user1,secret1,\r\n"
        val e = PasswordImporter.parse(csv).entries.single()
        assertEquals("My Bank", e.title)
        assertEquals("https://www.bank.com", e.url)
        assertEquals("user1", e.username)
        assertEquals("secret1", e.password)
    }

    @Test
    fun importsBitwardenJsonAndSkipsNonLogins() {
        val json = """
            {"items":[
              {"type":1,"name":"Site","notes":null,"login":{"username":"u","password":"p","uris":[{"uri":"https://site.com"}]}},
              {"type":2,"name":"A note","notes":"text"}
            ]}
        """.trimIndent()
        val result = PasswordImporter.parse(json)
        assertEquals(1, result.entries.size)
        assertEquals(1, result.skipped)
        assertEquals("https://site.com", result.entries.single().url)
        assertEquals("", result.entries.single().note)
    }

    @Test
    fun titleFallsBackToHostThenUsername() {
        val csv = "url,username,password\nhttps://www.example.org/path,bob,x\n"
        assertEquals("example.org", PasswordImporter.parse(csv).entries.single().title)
    }

    @Test
    fun unknownFormatIsRejected() {
        try {
            PasswordImporter.parse("just,some\ncolumns,here\n")
            fail("expected ImportFormatException")
        } catch (e: ImportFormatException) {
        }
        try {
            PasswordImporter.parse("   ")
            fail("expected ImportFormatException")
        } catch (e: ImportFormatException) {
        }
    }

    @Test
    fun exportThenImportRoundTrips() {
        val original = listOf(
            Entry("1", "A \"quoted\"", "u,1", "p\nw", "https://a.com", "n"),
            Entry("2", "B", "u2", "pw2", "", ""),
        )
        val back = PasswordImporter.parse(PasswordExporter.toCsv(original)).entries
        assertEquals(original.map { it.title to it.password }, back.map { it.title to it.password })
        assertEquals(original.map { it.username }, back.map { it.username })
    }

    // ── Check-up ──

    @Test
    fun healthFindsReusedAndWeak() {
        val entries = listOf(
            Entry("1", "Correios", password = "06815925242"),
            Entry("2", "Google", password = "06815925242"),
            Entry("3", "Strong", password = "nWm&Jn%WDM&4;xgN"),
            Entry("4", "NoPassword", password = ""),
        )
        val report = PasswordHealth.analyze(entries)
        assertEquals(3, report.checked)
        assertEquals(1, report.reusedGroups.size)
        assertEquals(2, report.reusedCount)
        assertEquals(listOf("Correios", "Google"), report.weak.map { it.title })
        assertTrue(report.hasIssues)
    }

    @Test
    fun healthIsCleanWhenAllUniqueAndStrong() {
        val report = PasswordHealth.analyze(
            listOf(
                Entry("1", "A", password = "nWm&Jn%WDM&4;xgN"),
                Entry("2", "B", password = "FzVSS<3r)%+[k4aU"),
            ),
        )
        assertNotNull(report)
        assertTrue(!report.hasIssues)
    }
}
