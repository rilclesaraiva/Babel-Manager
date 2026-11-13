package com.babelmanager.app

import com.babelmanager.app.data.update.ReleaseInfo
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class UpdateTest {
    @Test
    fun parsesLatestReleaseResponse() {
        val json = """
            {"tag_name":"v1.2.0","html_url":"https://github.com/o/r/releases/tag/v1.2.0",
             "body":"### Added\n- Something",
             "assets":[
               {"name":"notes.txt","browser_download_url":"https://x/notes.txt"},
               {"name":"babelmanager_v1.2.0.apk","browser_download_url":"https://x/babelmanager_v1.2.0.apk"}
             ]}
        """.trimIndent()
        val release = ReleaseInfo.fromJson(json)
        assertNotNull(release)
        assertEquals("v1.2.0", release!!.version)
        assertEquals("https://x/babelmanager_v1.2.0.apk", release.downloadUrl)
        assertEquals("### Added\n- Something", release.notes)
    }

    @Test
    fun releaseWithoutApkFallsBackToPage() {
        val release = ReleaseInfo.fromJson("""{"tag_name":"v1.0.0","html_url":"https://p","assets":[]}""")!!
        assertNull(release.downloadUrl)
        assertEquals("https://p", release.pageUrl)
    }

    @Test
    fun invalidJsonIsIgnored() {
        assertNull(ReleaseInfo.fromJson("<html>rate limited</html>"))
    }
}
