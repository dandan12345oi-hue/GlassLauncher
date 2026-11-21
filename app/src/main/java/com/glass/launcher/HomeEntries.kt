package com.glass.launcher

import android.util.Base64

/**
 * Serialização compacta de pastas da tela inicial.
 * Itens normais continuam usando a chave package/activity existente.
 */
object HomeEntries {
    private const val PREFIX = "@folder:"

    fun isFolder(value: String): Boolean = value.startsWith(PREFIX)

    fun folder(name: String, appKeys: List<String>): String {
        val payload = (name.ifBlank { "Pasta" } + "\n" + appKeys.distinct().joinToString("\n"))
        return PREFIX + Base64.encodeToString(
            payload.toByteArray(Charsets.UTF_8),
            Base64.URL_SAFE or Base64.NO_WRAP
        )
    }

    fun name(value: String): String = decode(value).first

    fun apps(value: String): List<String> = decode(value).second

    private fun decode(value: String): Pair<String, List<String>> {
        if (!isFolder(value)) return "" to listOf(value)
        return try {
            val payload = String(
                Base64.decode(value.removePrefix(PREFIX), Base64.URL_SAFE or Base64.NO_WRAP),
                Charsets.UTF_8
            )
            val parts = payload.split("\n")
            (parts.firstOrNull()?.ifBlank { "Pasta" } ?: "Pasta") to
                parts.drop(1).filter { it.contains("/") }.distinct()
        } catch (_: Exception) {
            "Pasta" to emptyList()
        }
    }
}
