package com.babelmanager.app.features.checkup

import com.babelmanager.app.core.security.PasswordStrength
import com.babelmanager.app.core.security.StrengthLevel
import com.babelmanager.app.data.vault.Entry

data class HealthReport(
    val checked: Int,
    /** Cada grupo reúne itens que usam exatamente a mesma senha. */
    val reusedGroups: List<List<Entry>>,
    val weak: List<Entry>,
) {
    val reusedCount: Int get() = reusedGroups.sumOf { it.size }
    val hasIssues: Boolean get() = reusedGroups.isNotEmpty() || weak.isNotEmpty()
}

/** Análise local (repetidas e fracas): nada sai do aparelho. A checagem de vazamentos é à parte (`PwnedPasswords`). */
object PasswordHealth {
    fun analyze(entries: List<Entry>): HealthReport {
        val withPassword = entries.filter { it.password.isNotEmpty() }
        val reused = withPassword
            .groupBy { it.password }
            .values
            .filter { it.size > 1 }
            .map { group -> group.sortedBy { it.title.lowercase() } }
            .sortedBy { it.first().title.lowercase() }
        val weak = withPassword
            .filter { PasswordStrength.estimate(it.password).level == StrengthLevel.Weak }
            .sortedBy { it.title.lowercase() }
        return HealthReport(withPassword.size, reused, weak)
    }
}
