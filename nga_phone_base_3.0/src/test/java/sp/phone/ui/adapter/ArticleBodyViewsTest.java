package sp.phone.ui.adapter;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertThrows;

import org.junit.After;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import sp.phone.http.bean.ThreadData;
import sp.phone.http.bean.ThreadRowInfo;
import sp.phone.mvp.model.thread.ArticlePageBasis;
import sp.phone.mvp.model.thread.ArticlePagingInfo;
import sp.phone.mvp.model.thread.ArticleQuery;
import sp.phone.mvp.model.thread.ArticleRowKind;
import sp.phone.mvp.model.thread.ArticleRowPresentation;
import sp.phone.mvp.model.thread.ArticleSource;

/** Executes the production resource owner; it does not simulate WebView document loading. */
public class ArticleBodyViewsTest {
    private static final int TID = 123;
    private static final String HTML = "<html><body>Original body</body></html>";
    private static final String OWNER = "reader-account-a";
    private static final long GENERATION = 7;

    private final List<Resource> created = new ArrayList<>();
    private final ArticleBodyViews<Resource> views = new ArticleBodyViews<>(() -> {
        Resource resource = new Resource();
        created.add(resource);
        return resource;
    }, resource -> resource.releases++);

    @After
    public void releaseRemainingResourcesExactlyOnce() {
        views.clear();
        views.clear();
        for (Resource resource : created) {
            assertEquals("Every created resource must be released exactly once", 1, resource.releases);
        }
    }

    @Test
    public void freshEqualResponseRetainsExistingResources() {
        ThreadData original = page(row(101), row(102));
        ThreadData refreshed = page(row(101), row(102));
        assertNotSame(original, refreshed);
        assertNotSame(original.getRowList().get(0), refreshed.getRowList().get(0));
        assertNotSame(original.getPagingInfo(), refreshed.getPagingInfo());

        views.setData(original);
        Resource first = views.getOrCreate(0);
        Resource second = views.getOrCreate(1);
        views.setData(refreshed);

        assertSame(first, views.getOrCreate(0));
        assertSame(second, views.getOrCreate(1));
        assertEquals(2, created.size());
        assertReleases(0, first, second);
    }

    @Test
    public void changedHtmlKeepsSameRowResourceAvailableForRebinding() {
        views.setData(page(row(101)));
        Resource body = views.getOrCreate(0);
        ThreadRowInfo edited = row(101);
        edited.setFormattedHtmlData("<html><body>Edited body with a new image</body></html>");
        edited.addImageUrl("https://images.invalid/new-image.png");

        views.setData(page(edited));

        assertSame(body, views.getOrCreate(0));
        assertEquals(1, created.size());
        assertReleases(0, body);
    }

    @Test
    public void readyReplayPreservesUnidentifiedAndDuplicateSlots() {
        ThreadData data = page(row(0, 101, 1, HTML), row(102), row(102));
        views.setData(data);
        Resource unknown = views.getOrCreate(0);
        Resource duplicateOne = views.getOrCreate(1);
        Resource duplicateTwo = views.getOrCreate(2);

        views.setData(data);

        assertSame(unknown, views.getOrCreate(0));
        assertSame(duplicateOne, views.getOrCreate(1));
        assertSame(duplicateTwo, views.getOrCreate(2));
        assertNotSame(duplicateOne, duplicateTwo);
        assertEquals(3, created.size());
        assertReleases(0, unknown, duplicateOne, duplicateTwo);
    }

    @Test
    public void allocationIsLazyEvenForLargePagesAndUnboundRowsCanDisappear() {
        views.setData(page(rows(101, 40)));
        assertEquals(0, created.size());

        Resource visible = views.getOrCreate(35);
        assertSame(visible, views.getOrCreate(35));
        assertEquals(1, created.size());

        views.setData(page(row(136)));

        assertSame(visible, views.getOrCreate(0));
        assertEquals(1, created.size());
        assertReleases(0, visible);
    }

    @Test
    public void growthShrinkAndReorderRetainOnlySurvivingRows() {
        views.setData(page(row(101), row(102), row(103)));
        Resource first = views.getOrCreate(0);
        Resource removed = views.getOrCreate(1);
        Resource third = views.getOrCreate(2);

        views.setData(page(row(103), row(104), row(101), row(105)));

        assertSame(third, views.getOrCreate(0));
        Resource fourth = views.getOrCreate(1);
        assertSame(first, views.getOrCreate(2));
        Resource fifth = views.getOrCreate(3);
        assertNotSame(fourth, fifth);
        assertEquals(5, created.size());
        assertReleases(1, removed);
        assertReleases(0, first, third, fourth, fifth);

        views.setData(page(row(105), row(103)));

        assertSame(fifth, views.getOrCreate(0));
        assertSame(third, views.getOrCreate(1));
        assertEquals(5, created.size());
        assertReleases(1, first, removed, fourth);
        assertReleases(0, third, fifth);

        views.setData(page(row(101)));

        assertNotSame(first, views.getOrCreate(0));
        assertEquals(6, created.size());
        assertReleases(1, first, removed, third, fourth, fifth);
    }

    @Test
    public void fortyRowPageRetainsEveryResourceThroughFreshReversedResponse() {
        ArticlePagingInfo paging = paging(query(), ArticleSource.APP_API, TID, 1, 1,
                40, OWNER, GENERATION);
        views.setData(page(paging, rows(101, 40)));
        Resource[] original = new Resource[40];
        ThreadRowInfo[] reversed = new ThreadRowInfo[40];
        for (int position = 0; position < 40; position++) {
            original[position] = views.getOrCreate(position);
            reversed[position] = row(140 - position);
        }

        views.setData(page(paging(query(), ArticleSource.APP_API, TID, 1, 1,
                40, OWNER, GENERATION), reversed));

        for (int position = 0; position < 40; position++) {
            assertSame("Reversed row " + position, original[39 - position], views.getOrCreate(position));
        }
        assertEquals(40, created.size());
        assertReleases(0, original);
    }

    @Test
    public void differentPidAtSamePositionCannotInheritResource() {
        views.setData(page(row(101)));
        Resource previous = views.getOrCreate(0);

        views.setData(page(row(102)));

        assertReleases(1, previous);
        assertNotSame(previous, views.getOrCreate(0));
        assertEquals(2, created.size());
    }

    @Test
    public void samePidFromDifferentThreadCannotInheritResource() {
        views.setData(page(row(101)));
        Resource previous = views.getOrCreate(0);

        views.setData(page(row(TID + 1, 101, 1, HTML)));

        assertReleases(1, previous);
        assertNotSame(previous, views.getOrCreate(0));
        assertEquals(2, created.size());
    }

    @Test
    public void positivePidDoesNotRequireKnownFloorOrPostKindForReuse() {
        ThreadRowInfo first = row(101);
        first.setLou(-1);
        first.setPresentation(presentation(ArticleRowKind.UNKNOWN, false));
        views.setData(page(first));
        Resource body = views.getOrCreate(0);
        ThreadRowInfo refreshed = row(101);
        refreshed.setPresentation(presentation(ArticleRowKind.COMMENT, false));

        views.setData(page(refreshed));

        assertSame(body, views.getOrCreate(0));
        assertEquals(1, created.size());
        assertReleases(0, body);
    }

    @Test
    public void knownOriginalPostWithZeroPidRetainsResource() {
        views.setData(page(row(0)));
        Resource originalPost = views.getOrCreate(0);
        ThreadRowInfo refreshed = row(0);
        refreshed.setPresentation(presentation(ArticleRowKind.POST, true));

        views.setData(page(refreshed));

        assertSame(originalPost, views.getOrCreate(0));
        assertEquals(1, created.size());
        assertReleases(0, originalPost);
    }

    @Test
    public void unknownIdentitiesGetIndependentResourcesAndRetireOnFreshResponse() {
        ThreadRowInfo[] rows = rowsWithUnknownIdentities();
        views.setData(page(rows));
        Resource[] original = new Resource[rows.length];
        for (int position = 0; position < rows.length; position++) {
            original[position] = views.getOrCreate(position);
        }
        assertEquals(rows.length, created.size());

        views.setData(page(rowsWithUnknownIdentities()));

        assertSame(original[0], views.getOrCreate(0));
        assertReleases(0, original[0]);
        for (int position = 1; position < rows.length; position++) {
            assertReleases(1, original[position]);
            assertNotSame("Unknown row " + position, original[position], views.getOrCreate(position));
        }
        assertEquals(rows.length * 2 - 1, created.size());
    }

    @Test
    public void duplicateIdentityNeverSharesOrRetainsAmbiguousResources() {
        views.setData(page(row(101), row(102)));
        Resource unique = views.getOrCreate(0);
        Resource stable = views.getOrCreate(1);

        views.setData(page(row(101), row(101), row(102)));

        assertReleases(1, unique);
        Resource duplicateOne = views.getOrCreate(0);
        Resource duplicateTwo = views.getOrCreate(1);
        assertNotSame(unique, duplicateOne);
        assertNotSame(unique, duplicateTwo);
        assertNotSame(duplicateOne, duplicateTwo);
        assertSame(stable, views.getOrCreate(2));
        assertEquals(4, created.size());

        views.setData(page(row(101), row(101), row(102)));

        assertReleases(1, duplicateOne, duplicateTwo);
        Resource refreshedOne = views.getOrCreate(0);
        Resource refreshedTwo = views.getOrCreate(1);
        assertNotSame(refreshedOne, refreshedTwo);
        assertSame(stable, views.getOrCreate(2));
        assertEquals(6, created.size());

        views.setData(page(row(101), row(102)));

        assertReleases(1, refreshedOne, refreshedTwo);
        Resource uniqueAgain = views.getOrCreate(0);
        assertNotSame(unique, uniqueAgain);
        assertNotSame(refreshedOne, uniqueAgain);
        assertNotSame(refreshedTwo, uniqueAgain);
        assertSame(stable, views.getOrCreate(1));
        assertEquals(7, created.size());
        assertReleases(0, stable, uniqueAgain);
    }

    @Test
    public void losingHtmlReleasesBodyWhileOtherRowsKeepTheirResources() {
        views.setData(page(row(101), row(102)));
        Resource body = views.getOrCreate(0);
        Resource stable = views.getOrCreate(1);
        for (String unavailableHtml : new String[]{null, ""}) {
            views.setData(page(row(TID, 101, 1, unavailableHtml), row(102)));

            assertReleases(1, body);
            assertSame(stable, views.getOrCreate(1));
            int creationsBeforeRestoringHtml = created.size();

            views.setData(page(row(101), row(102)));

            Resource restored = views.getOrCreate(0);
            assertNotSame(body, restored);
            assertSame(stable, views.getOrCreate(1));
            assertEquals(creationsBeforeRestoringHtml + 1, created.size());
            assertReleases(0, restored, stable);
            body = restored;
        }
        assertEquals(4, created.size());
    }

    @Test
    public void effectivePageChangeRetiresResourcesEvenIfRequestedPageIsUnchanged() {
        assertContextReplacement(paging(), paging(query(), ArticleSource.READ_PHP, TID,
                1, 2, 20, OWNER, GENERATION));
    }

    @Test
    public void everyQueryCoordinateParticipatesInResourceScope() {
        for (ArticleQuery changed : new ArticleQuery[]{
                new ArticleQuery(TID + 1, 0, 0, 0),
                new ArticleQuery(TID, 101, 0, 0),
                new ArticleQuery(TID, 0, 77, 0),
                new ArticleQuery(TID, 0, 0, 1)
        }) {
            assertContextReplacement(paging(), paging(changed, ArticleSource.READ_PHP, TID,
                    1, 1, 20, OWNER, GENERATION));
        }
    }

    @Test
    public void sourceChangeRetiresResources() {
        assertContextReplacement(paging(), paging(query(), ArticleSource.APP_API, TID,
                1, 1, 20, OWNER, GENERATION));
    }

    @Test
    public void ownerReplacementAndLossRetireResources() {
        ArticlePagingInfo missingOwner = paging(query(), ArticleSource.READ_PHP, TID,
                1, 1, 20, null, GENERATION);
        assertContextReplacement(paging(), paging(query(), ArticleSource.READ_PHP, TID,
                1, 1, 20, "reader-account-b", GENERATION));
        assertContextReplacement(paging(), missingOwner);
        assertContextReplacement(missingOwner, paging());
    }

    @Test
    public void readerGenerationChangeRetiresResources() {
        assertContextReplacement(paging(), paging(query(), ArticleSource.READ_PHP, TID,
                1, 1, 20, OWNER, GENERATION + 1));
    }

    @Test
    public void pageSizeReplacementAndLossRetireResources() {
        ArticlePagingInfo missingSize = paging(query(), ArticleSource.READ_PHP, TID,
                1, 1, null, OWNER, GENERATION);
        assertContextReplacement(paging(), paging(query(), ArticleSource.READ_PHP, TID,
                1, 1, 40, OWNER, GENERATION));
        assertContextReplacement(paging(), missingSize);
        assertContextReplacement(missingSize, paging());
    }

    @Test
    public void resolvedThreadChangeRetiresResourcesWithOtherwiseEqualScope() {
        assertContextReplacement(paging(), paging(query(), ArticleSource.READ_PHP, TID + 1,
                1, 1, 20, OWNER, GENERATION));
    }

    @Test
    public void missingPagingContextCannotJustifyReuseAcrossFreshResponses() {
        assertContextReplacement(paging(), null);
        assertContextReplacement(null, null);
        assertContextReplacement(null, paging());
    }

    @Test
    public void changedCountsAndRequestMetadataKeepSameEffectivePageResources() {
        views.setData(page(row(101)));
        Resource body = views.getOrCreate(0);
        ArticlePagingInfo refreshed = new ArticlePagingInfo(query(), ArticleSource.READ_PHP,
                TID, 3, 1, 20, 101, 2001, ArticlePageBasis.REPORTED,
                false, false, 1, OWNER, GENERATION);

        views.setData(page(refreshed, row(101)));

        assertSame(body, views.getOrCreate(0));
        assertEquals(1, created.size());
        assertReleases(0, body);
    }

    @Test
    public void clearReleasesOnceAndSameResponseCanCreateNewResourcesAfterward() {
        ThreadData data = page(row(101), row(102), row(103));
        views.setData(data);
        Resource first = views.getOrCreate(0);
        Resource third = views.getOrCreate(2);

        views.clear();
        views.clear();

        assertReleases(1, first, third);
        assertThrows(IndexOutOfBoundsException.class, () -> views.getOrCreate(0));
        assertEquals(2, created.size());

        views.setData(data);

        Resource recreated = views.getOrCreate(0);
        assertNotSame(first, recreated);
        assertSame(recreated, views.getOrCreate(0));
        assertEquals(3, created.size());
        assertReleases(0, recreated);
    }

    @Test
    public void nullDeliveryAndEmptyOrMissingRowsReleaseOwnedResources() {
        views.setData(page(row(101), row(102)));
        Resource first = views.getOrCreate(0);

        views.setData(null);
        views.setData(null);

        assertReleases(1, first);
        assertThrows(IndexOutOfBoundsException.class, () -> views.getOrCreate(0));
        assertEquals(1, created.size());

        views.setData(page(row(101)));
        Resource second = views.getOrCreate(0);
        assertNotSame(first, second);
        views.setData(page());

        assertReleases(1, second);
        assertThrows(IndexOutOfBoundsException.class, () -> views.getOrCreate(0));

        views.setData(page(row(101)));
        Resource third = views.getOrCreate(0);
        ThreadData missingRows = new ThreadData();
        missingRows.setPagingInfo(paging());
        views.setData(missingRows);

        assertReleases(1, third);
        assertThrows(IndexOutOfBoundsException.class, () -> views.getOrCreate(0));
        assertEquals(3, created.size());
    }

    @Test
    public void invalidPositionsDoNotAllocateResources() {
        assertThrows(IndexOutOfBoundsException.class, () -> views.getOrCreate(0));
        views.setData(page(row(101)));

        assertThrows(IndexOutOfBoundsException.class, () -> views.getOrCreate(-1));
        assertThrows(IndexOutOfBoundsException.class, () -> views.getOrCreate(1));
        assertEquals(0, created.size());

        views.getOrCreate(0);
        assertEquals(1, created.size());
    }

    private void assertContextReplacement(ArticlePagingInfo previous, ArticlePagingInfo next) {
        views.clear();
        int initialCreations = created.size();
        views.setData(page(previous, row(101), row(102)));
        Resource first = views.getOrCreate(0);
        Resource second = views.getOrCreate(1);

        views.setData(page(next, row(101), row(102)));

        assertReleases(1, first, second);
        Resource replacementFirst = views.getOrCreate(0);
        Resource replacementSecond = views.getOrCreate(1);
        assertNotSame(first, replacementFirst);
        assertNotSame(second, replacementSecond);
        assertNotSame(replacementFirst, replacementSecond);
        assertEquals(initialCreations + 4, created.size());
        assertReleases(0, replacementFirst, replacementSecond);
    }

    private static void assertReleases(int expected, Resource... resources) {
        for (Resource resource : resources) {
            assertEquals(expected, resource.releases);
        }
    }

    private static ThreadData page(ThreadRowInfo... rows) {
        return page(paging(), rows);
    }

    private static ThreadData page(ArticlePagingInfo paging, ThreadRowInfo... rows) {
        ThreadData data = new ThreadData();
        data.setPagingInfo(paging);
        data.setRowList(Arrays.asList(rows));
        data.setRowNum(rows.length);
        return data;
    }

    private static ArticleQuery query() {
        return new ArticleQuery(TID, 0, 0, 0);
    }

    private static ArticlePagingInfo paging() {
        return paging(query(), ArticleSource.READ_PHP, TID, 1, 1, 20, OWNER, GENERATION);
    }

    private static ArticlePagingInfo paging(ArticleQuery query, ArticleSource source,
            int resolvedTid, int requestedPage, int effectivePage, Integer pageSize,
            String owner, long generation) {
        return new ArticlePagingInfo(query, source, resolvedTid, requestedPage, effectivePage,
                pageSize, 100, 2000, ArticlePageBasis.REQUESTED, true, false, null,
                owner, generation);
    }

    private static ThreadRowInfo row(int pid) {
        return row(TID, pid, pid, HTML);
    }

    private static ThreadRowInfo row(int tid, int pid, int floor, String html) {
        ThreadRowInfo row = new ThreadRowInfo();
        row.setTid(tid);
        row.setPid(pid);
        row.setLou(floor);
        row.setFormattedHtmlData(html);
        return row;
    }

    private static ThreadRowInfo[] rows(int firstPid, int count) {
        ThreadRowInfo[] result = new ThreadRowInfo[count];
        for (int position = 0; position < count; position++) {
            result[position] = row(firstPid + position);
        }
        return result;
    }

    private static ArticleRowPresentation presentation(ArticleRowKind kind, boolean floorKnown) {
        return new ArticleRowPresentation(kind, floorKnown, false, false, true, null);
    }

    private static ThreadRowInfo[] rowsWithUnknownIdentities() {
        ThreadRowInfo unknownFloor = row(0);
        unknownFloor.setPresentation(presentation(ArticleRowKind.POST, false));
        ThreadRowInfo commentAtZero = row(0);
        commentAtZero.setPresentation(presentation(ArticleRowKind.COMMENT, true));
        ThreadRowInfo unknownKindAtZero = row(0);
        unknownKindAtZero.setPresentation(presentation(ArticleRowKind.UNKNOWN, true));
        return new ThreadRowInfo[]{
                row(101),
                row(0, 201, 1, HTML),
                row(-1, 202, 2, HTML),
                row(TID, -1, 0, HTML),
                row(TID, 0, 1, HTML),
                unknownFloor,
                commentAtZero,
                unknownKindAtZero,
                null
        };
    }

    private static final class Resource {
        int releases;
    }
}
