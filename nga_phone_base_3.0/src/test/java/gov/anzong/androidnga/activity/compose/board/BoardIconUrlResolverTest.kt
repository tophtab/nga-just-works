package gov.anzong.androidnga.activity.compose.board

import org.junit.Assert.*
import org.junit.Test

class BoardIconUrlResolverTest {
    private val path = "/ngabbs/nga_classic/f/app/"

    @Test fun acceptsKnownDirectoryAndNormalizesOriginAndTrailingSlashes() {
        listOf("https://img4.nga.cn", "http://img4.nga.cn", "https://IMG4.NGA.CN:443").forEach {
            assertEquals(BoardIconUrlResolver.defaultPrefix, BoardIconUrlResolver.normalize(" $it$path/// "))
        }
        assertEquals("https://icons.cdn.test$path", BoardIconUrlResolver.normalize("https://icons.cdn.test$path"))
    }

    @Test fun buildsOrdinaryNegativeAndCollectionIdsWithCollectionPriority() {
        val prefix = "https://icons.cdn.test$path"
        assertEquals(prefix + "-7.png", BoardIconUrlResolver.resolve(prefix, -7, 0))
        assertEquals("https://icons.cdn.test/proxy/cache_attach/ficon/12345678v.png",
            BoardIconUrlResolver.resolve(prefix, 42, 12345678))
        assertEquals("", BoardIconUrlResolver.resolve(prefix, 0, 0))
        assertEquals("https://img4.nga.cn/proxy/cache_attach/ficon/123v.png",
            BoardIconUrlResolver.resolve(BoardIconUrlResolver.defaultPrefix, 0, 123))
    }

    @Test fun rejectsUntrustedTypesOriginsAndUnknownDirectories() {
        val invalid = listOf(null, "", 4, listOf("x"), mapOf("host" to "x"),
            "https://localhost$path", "https://127.0.0.1$path", "https://[::1]$path",
            "https://null$path", "https://undefined$path", "https://cdn.localhost$path",
            "https://img9.nga.178.com$path", "https://img.ngacn.cc$path",
            "https://img4.nga.cn:444$path", "http://other.test$path",
            "https://user:pass@img4.nga.cn$path", "https://img4.nga.cn$path?q=1",
            "https://img4.nga.cn$path#x", "//img4.nga.cn$path", "ftp://img4.nga.cn$path",
            "https://img4.nga.cn/attachments/", "https://img4.nga.cn/new/icons/",
            "https://img4.nga.cn/ngabbs/nga_classic/f/%61pp/", "https://img4.nga.cn\\$path",
            "https://img4.nga.cn/a b", "https://bad_host.test$path")
        invalid.forEach { assertNull("Unexpected accepted input: $it", BoardIconUrlResolver.normalize(it)) }
    }
}
