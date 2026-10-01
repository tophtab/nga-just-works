package gov.anzong.androidnga.activity.compose.board

import sp.phone.common.ApiConstants
import java.net.URI
import java.util.Locale

/** BOARD.CATEGORIES icons have a different path family from post attachments. */
internal object BoardIconUrlResolver {
    private const val ORDINARY_PATH = "/ngabbs/nga_classic/f/app/"
    private const val COLLECTION_PATH = "/proxy/cache_attach/ficon/"
    val defaultPrefix: String = ApiConstants.URL_BOARD_ICON.substringBefore("%s")

    fun normalize(value: Any?): String? {
        val text = (value as? String)?.trim()?.takeIf { it.isNotEmpty() } ?: return null
        if (text.any { it.isWhitespace() || it == '\\' }) return null
        val uri = try { URI(text) } catch (_: Exception) { return null }
        val host = uri.host?.lowercase(Locale.ROOT) ?: return null
        val scheme = uri.scheme?.lowercase(Locale.ROOT) ?: return null
        if (uri.rawUserInfo != null || uri.rawQuery != null || uri.rawFragment != null) return null
        if (host.length > 253 || !host.contains('.') || host.split('.').any {
                it.isEmpty() || it.length > 63 || !it.matches(Regex("[a-z0-9](?:[a-z0-9-]*[a-z0-9])?"))
            }) return null
        if (host.split('.').all { it.all(Char::isDigit) } ||
            host.substringAfterLast('.') in setOf("localhost", "local", "null", "undefined") ||
            host.matches(Regex("img[0-9]*\\.(nga\\.178\\.com|ngacn\\.cc)"))) return null
        if (uri.rawPath.trimEnd('/') + "/" != ORDINARY_PATH) return null
        when (scheme) {
            "https" -> if (uri.port != -1 && uri.port != 443) return null
            "http" -> if (host != "img4.nga.cn" || (uri.port != -1 && uri.port != 80)) return null
            else -> return null
        }
        return "https://$host$ORDINARY_PATH"
    }

    fun resolve(prefix: String, fid: Int, stid: Int): String = when {
        stid != 0 -> if (prefix == defaultPrefix) {
            String.format(Locale.ROOT, ApiConstants.URL_BOARD_ICON_STID, stid)
        } else {
            prefix.removeSuffix(ORDINARY_PATH) + COLLECTION_PATH + stid + "v.png"
        }
        fid != 0 -> prefix + fid + ".png"
        else -> ""
    }
}
