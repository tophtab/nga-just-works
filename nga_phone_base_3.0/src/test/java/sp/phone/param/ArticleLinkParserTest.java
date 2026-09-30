package sp.phone.param;

import org.junit.Test;
import static org.junit.Assert.*;

public class ArticleLinkParserTest {
    @Test public void actualViaLinkAndReplyIdentity() {
        ArticleListParam topic = ArticleLinkParser.parse("nga://openType=2?page=1&tid=47649154&");
        assertNotNull(topic);
        assertEquals(47649154, topic.tid);
        assertEquals(1, topic.page);
        ArticleListParam reply = ArticleLinkParser.parse("nga://openType=5?pid=2147483647&tid=23&page=4");
        assertNotNull(reply);
        assertEquals(Integer.MAX_VALUE, reply.pid);
        assertEquals(23, reply.tid);
        assertEquals(4, reply.page);
        assertEquals(1, ArticleLinkParser.parse("nga://openType=5?pid=8").page);
    }

    @Test public void rejectsMalformedOrUnsupportedIdentity() {
        String[] links = {null, "", "nga://openType=1?tid=1", "nga://openType=3?tid=1",
                "nga://openType=2?pid=2", "nga://openType=5?tid=2", "nga://openType=2?tid=0",
                "nga://openType=2?tid=-1", "nga://openType=2?tid=2147483648",
                "nga://openType=2?tid=1&tid=2", "nga://openType=2?tid=1&page=0",
                "nga://openType=2?tid=1&page=x", "nga://openType=2?tid=1&pid=-2",
                "nga://openType=2?tid=", "nga://openType=2?tid=%31%ZZ",
                "nga://user@openType=2?tid=1", "nga://openType=2:80?tid=1",
                "nga://openType=2/read.php?tid=1", "nga://openType=2/?tid=1",
                "nga://openType%3D2?tid=1", "nga://openType=2?tid=1#extra",
                "nga:openType=2?tid=1", "other://openType=2?tid=1"};
        for (String link : links) assertNull(link, ArticleLinkParser.parse(link));
    }

    @Test public void knownFieldsOnlyAndIdenticalDuplicates() {
        ArticleListParam param = ArticleLinkParser.parse(
                "nga://openType=2?tid=3&tid=3&authorid=88&searchpost=1&url=https://other.test");
        assertNotNull(param);
        assertEquals(3, param.tid);
        assertEquals(0, param.authorId);
        assertEquals(0, param.searchPost);
        assertEquals(0, param.targetPid);
        assertEquals(-1, param.targetFloor);
        assertEquals(3, ArticleLinkParser.parse("nga://openType=2?%74id=%33").tid);
    }

    @Test public void webLinksKeepExistingQueryFields() {
        for (String host : new String[]{"nga.178.com", "bbs.ngacn.cc", "bbs.nga.cn", "ngabbs.com"}) {
            for (String scheme : new String[]{"http", "https"}) {
                ArticleListParam param = ArticleLinkParser.parse(scheme + "://" + host
                        + "/read.php?tid=10&pid=20&authorid=30&page=4&searchpost=1#reply");
                assertNotNull(param);
                assertEquals(10, param.tid);
                assertEquals(20, param.pid);
                assertEquals(30, param.authorId);
                assertEquals(4, param.page);
                assertEquals(1, param.searchPost);
            }
        }
    }

    @Test public void externalOrDisguisedWebOriginsCannotBecomeAuthenticatedReads() {
        for (String link : new String[]{"https://other.test/read.php?tid=1",
                "https://bbs.nga.cn.other.test/read.php?tid=1", "https://bbs.nga.cn@other.test/read.php?tid=1",
                "https://user@bbs.nga.cn/read.php?tid=1", "https://bbs.nga.cn:444/read.php?tid=1",
                "https://bbs.nga.cn/thread.php?tid=1", "https://bbs.nga.cn/%72ead.php?tid=1",
                "https://bbs.nga.cn/read.php?authorid=1", "https://bbs.nga.cn/read.php?tid=1&authorid=x"}) {
            assertNull(link, ArticleLinkParser.parse(link));
        }
    }
}
