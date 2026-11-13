package com.babelmanager.app.features.vault

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.babelmanager.app.data.vault.Entry
import com.babelmanager.app.data.vault.VaultRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class VaultViewModel(private val vault: VaultRepository) : ViewModel() {

    val query = MutableStateFlow("")

    val allEntries: StateFlow<List<Entry>> = vault.entries

    /** Itens filtrados pela busca (título, usuário e site), em ordem alfabética. */
    val entries: StateFlow<List<Entry>> = combine(vault.entries, query) { list, q ->
        val term = q.trim().lowercase()
        list.asSequence()
            .filter {
                term.isEmpty() ||
                    it.title.lowercase().contains(term) ||
                    it.username.lowercase().contains(term) ||
                    it.url.lowercase().contains(term)
            }
            .sortedBy { it.title.lowercase() }
            .toList()
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun setQuery(value: String) {
        query.value = value
    }

    fun save(entry: Entry) {
        viewModelScope.launch { vault.upsert(entry) }
    }

    fun delete(id: String) {
        viewModelScope.launch { vault.delete(id) }
    }

    fun lock() = vault.lock()
}
