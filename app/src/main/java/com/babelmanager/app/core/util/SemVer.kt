package com.babelmanager.app.core.util

data class SemVer(val major: Int, val minor: Int, val patch: Int) : Comparable<SemVer> {

    override fun compareTo(other: SemVer): Int =
        compareValuesBy(this, other, SemVer::major, SemVer::minor, SemVer::patch)

    companion object {
        private val pattern = Regex("""^v?(\d+)\.(\d+)\.(\d+)""")

        /** Aceita "1.2.3" e "v1.2.3". Pré-lançamento e metadados depois do patch são ignorados. */
        fun parse(text: String): SemVer? {
            val m = pattern.find(text.trim()) ?: return null
            val (major, minor, patch) = m.destructured
            return SemVer(
                major.toIntOrNull() ?: return null,
                minor.toIntOrNull() ?: return null,
                patch.toIntOrNull() ?: return null,
            )
        }
    }
}
