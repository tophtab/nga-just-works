package sp.phone.mvp.model.thread

import sp.phone.common.UserManagerImpl
import java.nio.charset.StandardCharsets
import java.security.MessageDigest

/** Request-only credentials. Neither this object nor its Cookie is a DTO/cache field. */
class ArticleAccount private constructor(@JvmField val owner: String, private val cookie: String) {
    internal fun cookieHeader() = cookie
    fun sameCredentials(other: ArticleAccount) = owner == other.owner && cookie == other.cookie
    override fun toString() = "ArticleAccount(redacted)"

    companion object {
        @JvmStatic fun from(selected: Boolean, uid: String?, cid: String?): ArticleAccount {
            if (!selected) return ArticleAccount("guest", "")
            val owner = owner(uid) ?: throw ArticleFailure(ArticleFailureKind.AUTH)
            if (cid.isNullOrEmpty() || cid.length > 4096 || cid.any {
                    it.code !in 0x21..0x7e || it == '"' || it == ',' || it == ';' || it == '\\'
                }) throw ArticleFailure(ArticleFailureKind.AUTH)
            return ArticleAccount(owner, "ngaPassportUid=$owner; ngaPassportCid=$cid")
        }
        @JvmStatic fun owner(uid: String?): String? = uid?.takeIf {
            it.matches(Regex("[1-9][0-9]{0,9}")) && it.toLongOrNull()?.let { n -> n <= Int.MAX_VALUE } == true
        }
    }
}

/** The only global-manager bridge for the scoped operation and owned cache. Never logs accounts. */
object ArticleAccounts {
    @JvmStatic fun capture(): ArticleAccount {
        val manager = UserManagerImpl.getInstance()
        val user = manager.activeUser
        return ArticleAccount.from(user != null || manager.userSize > 0, user?.userId, user?.cid)
    }
    @JvmStatic fun currentOwner(): String? {
        val manager = UserManagerImpl.getInstance()
        val user = manager.activeUser
        return if (user == null && manager.userSize == 0) "guest" else ArticleAccount.owner(user?.userId)
    }
    @JvmStatic fun fingerprint(): String {
        val manager = UserManagerImpl.getInstance()
        val user = manager.activeUser
        val input = "${manager.userSize > 0}\u0000${user?.userId.orEmpty()}\u0000${user?.cid.orEmpty()}"
        return MessageDigest.getInstance("SHA-256").digest(input.toByteArray(StandardCharsets.UTF_8))
            .joinToString("") { "%02x".format(it) }
    }
    @JvmStatic fun isCurrent(account: ArticleAccount): Boolean = try { account.sameCredentials(capture()) }
        catch (_: ArticleFailure) { false }
}

/** Immutable operation snapshot; deliberately has no generated toString/copy of credentials. */
class ArticleOperation(
    @JvmField val key: ArticleRequestKey,
    @JvmField val origin: String,
    @JvmField val account: ArticleAccount,
    @JvmField val userAgent: String,
    @JvmField val source: ArticleSource = key.source,
    @JvmField val page: Int = key.page,
) {
    fun forSource(source: ArticleSource) = ArticleOperation(key, origin, account, userAgent, source, page)
    fun forPage(page: Int) = ArticleOperation(key, origin, account, userAgent, source, page)
    override fun toString() = "ArticleOperation(${source.format})"
}
