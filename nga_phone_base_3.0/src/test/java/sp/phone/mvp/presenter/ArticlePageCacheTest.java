package sp.phone.mvp.presenter;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import com.alibaba.fastjson.JSON;

import org.junit.Test;

import sp.phone.http.bean.ThreadData;
import sp.phone.mvp.model.entity.ThreadPageInfo;
import sp.phone.param.ArticleListParam;

public class ArticlePageCacheTest {

    private static final int TID = 420001;
    private static final String SUBJECT = "离线缓存回归帖";

    @Test
    public void fullThreadWithoutLaunchMetadataIsEligibleBeforeChildPageLoads() {
        ArticleListParam pager = fullThread(0);

        assertNull(pager.topicInfo);
        assertTrue(ArticlePageCache.isCacheableContext(pager));
    }

    @Test
    public void replyAuthorSearchAndCacheContextsAreNeverEligibleOrPrepared() {
        ArticleListParam reply = fullThread(3);
        reply.pid = 900001;
        ArticleListParam author = fullThread(3);
        author.authorId = 23;
        ArticleListParam anonymousAuthor = fullThread(3);
        anonymousAuthor.authorId = -1;
        ArticleListParam search = fullThread(3);
        search.searchPost = 1;
        ArticleListParam cache = fullThread(3);
        cache.loadCache = true;

        for (ArticleListParam param : new ArticleListParam[]{reply, author, anonymousAuthor, search, cache}) {
            // Supplying a valid description must not make a partial page cacheable.
            param.topicInfo = JSON.toJSONString(threadInfo(TID, SUBJECT));
            assertFalse(ArticlePageCache.isCacheableContext(param));
            assertNull(ArticlePageCache.prepare(param, loadedPage()));
        }
    }

    @Test
    public void invalidOrMissingThreadContextCannotCache() {
        assertFalse(ArticlePageCache.isCacheableContext(null));
        assertNull(ArticlePageCache.prepare(null, loadedPage()));
        for (int tid : new int[]{0, -1}) {
            ArticleListParam param = fullThread(1);
            param.tid = tid;
            assertFalse(ArticlePageCache.isCacheableContext(param));
            assertNull(ArticlePageCache.prepare(param, loadedPage()));
        }
    }

    @Test
    public void missingLaunchMetadataRoundTripsAsExistingCacheDescription() {
        ArticleListParam param = fullThread(7);
        ThreadData page = loadedPage();
        ThreadPageInfo loadedInfo = page.getThreadInfo();
        loadedInfo.setFid(-7);
        loadedInfo.setAuthor("offline-author");
        loadedInfo.setAuthorId(23);
        loadedInfo.setLastPoster("offline-replier");
        loadedInfo.setReplies(145);
        loadedInfo.setPostDate(1700000000);
        String rawData = page.getRawData();

        ArticleListParam saved = ArticlePageCache.prepare(param, page);

        assertNotNull(saved);
        // TopicListModel reads the persisted description with this same bean codec.
        ThreadPageInfo restored = JSON.parseObject(saved.topicInfo, ThreadPageInfo.class);
        assertEquals(TID, restored.getTid());
        assertEquals(SUBJECT, restored.getSubject());
        assertEquals(-7, restored.getFid());
        assertEquals("offline-author", restored.getAuthor());
        assertEquals(23, restored.getAuthorId());
        assertEquals("offline-replier", restored.getLastPoster());
        assertEquals(145, restored.getReplies());
        assertEquals(1700000000, restored.getPostDate());
        assertEquals(7, saved.page);
        assertNull(param.topicInfo);
        assertSame(rawData, page.getRawData());
    }

    @Test
    public void suppliedDescriptionIsKeptVerbatimWhenLoadedMetadataDiffers() {
        ArticleListParam param = fullThread(2);
        param.topicInfo = "{ \"tid\": " + TID
                + ", \"subject\": \"topic-list title\", \"author\": \"list-author\", \"extra\": true }";
        String supplied = param.topicInfo;

        ArticleListParam saved = ArticlePageCache.prepare(param, loadedPage());

        assertNotNull(saved);
        assertEquals(supplied, saved.topicInfo);
        assertEquals(supplied, param.topicInfo);
        ThreadPageInfo restored = JSON.parseObject(saved.topicInfo, ThreadPageInfo.class);
        assertEquals("topic-list title", restored.getSubject());
        assertEquals("list-author", restored.getAuthor());
    }

    @Test
    public void existingDescriptionWorksWhenResponseOmitsThreadMetadata() {
        ArticleListParam param = fullThread(2);
        param.topicInfo = JSON.toJSONString(threadInfo(TID, SUBJECT));
        ThreadData page = loadedPage();
        page.setThreadInfo(null);

        ArticleListParam saved = ArticlePageCache.prepare(param, page);

        assertNotNull(saved);
        assertEquals(param.topicInfo, saved.topicInfo);
    }

    @Test
    public void blankLaunchMetadataFallsBackToLoadedThread() {
        for (String description : new String[]{null, "", " \t\n", "\u3000", "\u00a0"}) {
            ArticleListParam param = fullThread(1);
            param.topicInfo = description;

            ArticleListParam saved = ArticlePageCache.prepare(param, loadedPage());

            assertNotNull(saved);
            assertEquals(SUBJECT,
                    JSON.parseObject(saved.topicInfo, ThreadPageInfo.class).getSubject());
            assertEquals(description, param.topicInfo);
        }
    }

    @Test
    public void snapshotRetainsSelectedChildPageWithoutMutatingPagerOrLoadedData() {
        ArticleListParam pager = fullThread(0);
        pager.content = "unchanged navigation content";
        ArticleListParam selectedPage = (ArticleListParam) pager.clone();
        selectedPage.page = 7;
        ThreadData page = loadedPage();
        page.getThreadInfo().setPage(1);

        ArticleListParam saved = ArticlePageCache.prepare(selectedPage, page);

        assertNotNull(saved);
        assertNotSame(selectedPage, saved);
        assertEquals(7, saved.page);
        assertEquals(TID, saved.tid);
        assertEquals(pager.title, saved.title);
        assertEquals(pager.content, saved.content);
        assertEquals(0, pager.page);
        assertEquals(7, selectedPage.page);
        assertNull(pager.topicInfo);
        assertNull(selectedPage.topicInfo);
        assertEquals(1, page.getThreadInfo().getPage());

        // The asynchronous write must keep the original target and description.
        selectedPage.page = 9;
        selectedPage.tid = TID + 1;
        selectedPage.title = "later navigation title";
        selectedPage.topicInfo = "later navigation metadata";
        page.getThreadInfo().setSubject("later response title");
        assertEquals(7, saved.page);
        assertEquals(TID, saved.tid);
        assertEquals(SUBJECT, saved.title);
        assertEquals(SUBJECT,
                JSON.parseObject(saved.topicInfo, ThreadPageInfo.class).getSubject());
    }

    @Test
    public void preparingAnotherPageDoesNotOverwritePreviousSnapshot() {
        ArticleListParam firstPage = fullThread(2);
        ArticleListParam secondPage = fullThread(7);
        ThreadData secondData = loadedPage();
        secondData.getThreadInfo().setSubject("updated thread title");

        ArticleListParam firstSave = ArticlePageCache.prepare(firstPage, loadedPage());
        ArticleListParam secondSave = ArticlePageCache.prepare(secondPage, secondData);

        assertNotNull(firstSave);
        assertNotNull(secondSave);
        assertEquals(2, firstSave.page);
        assertEquals(7, secondSave.page);
        assertEquals(SUBJECT,
                JSON.parseObject(firstSave.topicInfo, ThreadPageInfo.class).getSubject());
        assertEquals("updated thread title",
                JSON.parseObject(secondSave.topicInfo, ThreadPageInfo.class).getSubject());
        assertNull(firstPage.topicInfo);
        assertNull(secondPage.topicInfo);
    }

    @Test
    public void missingLoadedPageOrRawDataCannotCache() {
        ArticleListParam param = fullThread(1);
        param.topicInfo = JSON.toJSONString(threadInfo(TID, SUBJECT));
        assertNull(ArticlePageCache.prepare(param, null));
        for (String raw : new String[]{null, "", " \t\n", "\u3000", "\u00a0"}) {
            ThreadData page = loadedPage();
            page.setRawData(raw);
            assertNull(ArticlePageCache.prepare(param, page));
        }
    }

    @Test
    public void unusableLoadedDescriptionsCannotBecomeCacheEntries() {
        ThreadPageInfo[] descriptions = {
                null,
                threadInfo(0, SUBJECT),
                threadInfo(TID + 1, SUBJECT),
                threadInfo(TID, null),
                threadInfo(TID, ""),
                threadInfo(TID, " \t\n"),
                threadInfo(TID, "\u3000"),
                threadInfo(TID, "\u00a0")
        };
        for (ThreadPageInfo description : descriptions) {
            ThreadData page = loadedPage();
            page.setThreadInfo(description);
            assertNull(ArticlePageCache.prepare(fullThread(1), page));
        }
    }

    @Test
    public void mismatchedLoadedThreadIsRejectedEvenWithSuppliedDescription() {
        ArticleListParam param = fullThread(1);
        param.topicInfo = JSON.toJSONString(threadInfo(TID, SUBJECT));
        ThreadData page = loadedPage();
        page.setThreadInfo(threadInfo(TID + 1, SUBJECT));

        assertNull(ArticlePageCache.prepare(param, page));
    }

    @Test
    public void invalidSuppliedDescriptionsAreRejectedRatherThanOverwritten() {
        String[] descriptions = {
                "not-json", "{", "null", "[]", "{}",
                JSON.toJSONString(threadInfo(TID + 1, SUBJECT)),
                JSON.toJSONString(threadInfo(TID, null)),
                JSON.toJSONString(threadInfo(TID, " \t\n")),
                JSON.toJSONString(threadInfo(TID, "\u3000")),
                JSON.toJSONString(threadInfo(TID, "\u00a0"))
        };
        for (String description : descriptions) {
            ArticleListParam param = fullThread(1);
            param.topicInfo = description;
            assertNull(ArticlePageCache.prepare(param, loadedPage()));
            assertEquals(description, param.topicInfo);
        }
    }

    @Test
    public void unselectedOrInvalidPageCannotProduceCacheRequest() {
        for (int page : new int[]{0, -1}) {
            ArticleListParam param = fullThread(page);
            assertTrue(ArticlePageCache.isCacheableContext(param));
            assertNull(ArticlePageCache.prepare(param, loadedPage()));
        }
    }

    private static ArticleListParam fullThread(int page) {
        ArticleListParam param = new ArticleListParam();
        param.tid = TID;
        param.title = SUBJECT;
        param.page = page;
        return param;
    }

    private static ThreadPageInfo threadInfo(int tid, String subject) {
        ThreadPageInfo info = new ThreadPageInfo();
        info.setTid(tid);
        info.setSubject(subject);
        return info;
    }

    private static ThreadData loadedPage() {
        ThreadData page = new ThreadData();
        page.setThreadInfo(threadInfo(TID, SUBJECT));
        page.setRawData("{\"data\":{\"__T\":{\"tid\":" + TID
                + ",\"subject\":\"" + SUBJECT + "\"},\"__ROWS\":146}}");
        return page;
    }
}
