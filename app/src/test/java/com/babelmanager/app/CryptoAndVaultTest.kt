package com.babelmanager.app

import com.babelmanager.app.data.crypto.InvalidVaultException
import com.babelmanager.app.data.crypto.VaultCrypto
import com.babelmanager.app.data.crypto.VaultFile
import com.babelmanager.app.data.crypto.WrongPasswordException
import com.babelmanager.app.data.vault.Entry
import com.babelmanager.app.data.vault.VaultRepository
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class CryptoAndVaultTest {
    @get:Rule
    val tmp = TemporaryFolder()

    private val fastIterations = 1_000

    private fun repo(file: File) = VaultRepository(file, kdfIterations = fastIterations)

    @Test
    fun sealAndOpenRoundTrip() {
        val salt = VaultCrypto.newSalt()
        val key = VaultCrypto.deriveKey("correct horse".toCharArray(), salt, fastIterations)
        val sealed = VaultCrypto.seal(key, "hello".toByteArray(), salt, fastIterations)
        val parsed = VaultFile.parse(sealed.toBytes())
        assertEquals("hello", String(VaultCrypto.open(key, parsed)))
    }

    @Test
    fun wrongKeyIsRejected() {
        val salt = VaultCrypto.newSalt()
        val key = VaultCrypto.deriveKey("right".toCharArray(), salt, fastIterations)
        val other = VaultCrypto.deriveKey("wrong".toCharArray(), salt, fastIterations)
        val sealed = VaultCrypto.seal(key, "secret".toByteArray(), salt, fastIterations)
        try {
            VaultCrypto.open(other, sealed)
            fail("expected WrongPasswordException")
        } catch (e: WrongPasswordException) {
        }
    }

    @Test
    fun tamperedCiphertextIsRejected() {
        val salt = VaultCrypto.newSalt()
        val key = VaultCrypto.deriveKey("pw".toCharArray(), salt, fastIterations)
        val sealed = VaultCrypto.seal(key, "secret".toByteArray(), salt, fastIterations)
        sealed.ciphertext[0] = (sealed.ciphertext[0].toInt() xor 1).toByte()
        try {
            VaultCrypto.open(key, sealed)
            fail("expected WrongPasswordException")
        } catch (e: WrongPasswordException) {
        }
    }

    @Test
    fun changedIterationCountInHeaderBreaksDecryption() {
        val salt = VaultCrypto.newSalt()
        val key = VaultCrypto.deriveKey("pw".toCharArray(), salt, fastIterations)
        val sealed = VaultCrypto.seal(key, "secret".toByteArray(), salt, fastIterations)
        val forged = VaultFile(fastIterations + 1, sealed.salt, sealed.iv, sealed.ciphertext)
        try {
            VaultCrypto.open(key, forged)
            fail("expected WrongPasswordException")
        } catch (e: WrongPasswordException) {
        }
    }

    @Test
    fun garbageIsNotAVault() {
        try {
            VaultFile.parse("not json".toByteArray())
            fail("expected InvalidVaultException")
        } catch (e: InvalidVaultException) {
        }
        try {
            VaultFile.parse("""{"format":"other"}""".toByteArray())
            fail("expected InvalidVaultException")
        } catch (e: InvalidVaultException) {
        }
    }

    @Test
    fun vaultPersistsAndUnlocksInANewInstance() = runBlocking {
        val file = File(tmp.root, "vault.babel")
        val first = repo(file)
        assertEquals(VaultRepository.State.Missing, first.state.value)
        first.create("a long master password".toCharArray())
        first.upsert(Entry(id = "1", title = "Example", username = "me", password = "p@ss", url = "example.com"))
        assertEquals(1, first.entries.value.size)

        val second = repo(file)
        assertEquals(VaultRepository.State.Locked, second.state.value)
        second.unlock("a long master password".toCharArray())
        assertEquals("p@ss", second.entries.value.single().password)
        assertTrue(second.entries.value.single().createdAt > 0)
    }

    @Test
    fun fileOnDiskDoesNotContainPlaintext() = runBlocking {
        val file = File(tmp.root, "vault.babel")
        val vault = repo(file)
        vault.create("a long master password".toCharArray())
        vault.upsert(Entry(id = "1", title = "UniqueTitleXyz", password = "UniquePasswordXyz"))
        val raw = String(file.readBytes())
        assertFalse(raw.contains("UniqueTitleXyz"))
        assertFalse(raw.contains("UniquePasswordXyz"))
    }

    @Test
    fun wrongMasterPasswordDoesNotUnlock() = runBlocking {
        val file = File(tmp.root, "vault.babel")
        repo(file).create("a long master password".toCharArray())
        val vault = repo(file)
        try {
            vault.unlock("nope".toCharArray())
            fail("expected WrongPasswordException")
        } catch (e: WrongPasswordException) {
        }
        assertEquals(VaultRepository.State.Locked, vault.state.value)
        assertTrue(vault.entries.value.isEmpty())
    }

    @Test
    fun lockClearsMemory() = runBlocking {
        val vault = repo(File(tmp.root, "vault.babel"))
        vault.create("a long master password".toCharArray())
        vault.upsert(Entry(id = "1", title = "x", password = "y"))
        vault.lock()
        assertEquals(VaultRepository.State.Locked, vault.state.value)
        assertTrue(vault.entries.value.isEmpty())
        assertNull(vault.exportKey())
    }

    @Test
    fun unlockWithKeyWorks() = runBlocking {
        val file = File(tmp.root, "vault.babel")
        val vault = repo(file)
        vault.create("a long master password".toCharArray())
        vault.upsert(Entry(id = "1", title = "x", password = "y"))
        val key = vault.exportKey()
        assertNotNull(key)
        val other = repo(file)
        other.unlockWithKey(key!!)
        assertEquals(1, other.entries.value.size)
    }

    @Test
    fun changePasswordReencrypts() = runBlocking {
        val file = File(tmp.root, "vault.babel")
        val vault = repo(file)
        vault.create("old master password".toCharArray())
        vault.upsert(Entry(id = "1", title = "x", password = "y"))
        vault.changePassword("old master password".toCharArray(), "new master password".toCharArray())

        val reopened = repo(file)
        try {
            reopened.unlock("old master password".toCharArray())
            fail("old password must stop working")
        } catch (e: WrongPasswordException) {
        }
        reopened.unlock("new master password".toCharArray())
        assertEquals(1, reopened.entries.value.size)
    }

    @Test
    fun changePasswordRequiresCurrentPassword() = runBlocking {
        val vault = repo(File(tmp.root, "vault.babel"))
        vault.create("old master password".toCharArray())
        try {
            vault.changePassword("wrong".toCharArray(), "new master password".toCharArray())
            fail("expected WrongPasswordException")
        } catch (e: WrongPasswordException) {
        }
    }

    @Test
    fun restoreReplacesVaultWithBackup() = runBlocking {
        val sourceFile = File(tmp.root, "source.babel")
        val source = repo(sourceFile)
        source.create("backup master password".toCharArray())
        source.upsert(Entry(id = "b", title = "from backup", password = "pw"))
        val backupBytes = source.encryptedBytes()!!

        val targetFile = File(tmp.root, "target.babel")
        val target = repo(targetFile)
        target.create("different password!".toCharArray())
        target.upsert(Entry(id = "t", title = "local only", password = "pw"))

        target.restore(backupBytes, "backup master password".toCharArray())
        assertEquals(listOf("from backup"), target.entries.value.map { it.title })
        assertArrayEquals(backupBytes, targetFile.readBytes())
    }

    @Test
    fun importSkipsDuplicates() = runBlocking {
        val vault = repo(File(tmp.root, "vault.babel"))
        vault.create("a long master password".toCharArray())
        vault.upsert(Entry(id = "1", title = "A", username = "u", password = "p", url = "a.com"))
        val added = vault.importEntries(
            listOf(
                Entry(id = "2", title = "A again", username = "u", password = "p", url = "a.com"),
                Entry(id = "3", title = "B", username = "u2", password = "p2", url = "b.com"),
            ),
        )
        assertEquals(1, added)
        assertEquals(2, vault.entries.value.size)
    }

    @Test
    fun deleteRemovesEntry() = runBlocking {
        val vault = repo(File(tmp.root, "vault.babel"))
        vault.create("a long master password".toCharArray())
        vault.upsert(Entry(id = "1", title = "A"))
        vault.delete("1")
        assertTrue(vault.entries.value.isEmpty())
    }
}
