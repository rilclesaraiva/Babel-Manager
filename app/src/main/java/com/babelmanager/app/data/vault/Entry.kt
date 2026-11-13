package com.babelmanager.app.data.vault

import org.json.JSONArray
import org.json.JSONObject

data class Entry(
    val id: String,
    val title: String,
    val username: String = "",
    val password: String = "",
    val url: String = "",
    val note: String = "",
    val createdAt: Long = 0L,
    val updatedAt: Long = 0L,
)

/** Conteúdo (antes de cifrar) do cofre em JSON. */
object VaultCodec {
    fun encode(entries: List<Entry>): ByteArray {
        val array = JSONArray()
        for (e in entries) {
            array.put(
                JSONObject()
                    .put("id", e.id)
                    .put("title", e.title)
                    .put("username", e.username)
                    .put("password", e.password)
                    .put("url", e.url)
                    .put("note", e.note)
                    .put("createdAt", e.createdAt)
                    .put("updatedAt", e.updatedAt),
            )
        }
        return JSONObject().put("entries", array).toString().toByteArray(Charsets.UTF_8)
    }

    fun decode(bytes: ByteArray): List<Entry> {
        val array = JSONObject(String(bytes, Charsets.UTF_8)).getJSONArray("entries")
        return List(array.length()) { i ->
            val o = array.getJSONObject(i)
            Entry(
                id = o.getString("id"),
                title = o.optString("title"),
                username = o.optString("username"),
                password = o.optString("password"),
                url = o.optString("url"),
                note = o.optString("note"),
                createdAt = o.optLong("createdAt"),
                updatedAt = o.optLong("updatedAt"),
            )
        }
    }
}
