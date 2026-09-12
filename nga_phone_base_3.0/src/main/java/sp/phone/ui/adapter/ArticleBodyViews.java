package sp.phone.ui.adapter;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Supplier;

import sp.phone.http.bean.ThreadData;
import sp.phone.http.bean.ThreadRowInfo;
import sp.phone.mvp.model.thread.ArticlePagingInfo;
import sp.phone.mvp.model.thread.ArticleRequestKey;
import sp.phone.mvp.model.thread.ArticleRowPresentation;

/** Owns only this adapter's body views; response replacement does not imply body replacement. */
final class ArticleBodyViews<V> {
    private final Supplier<V> create;
    private final Consumer<V> release;
    private List<Slot<V>> slots = Collections.emptyList();
    private ThreadData response;
    private ArticleRequestKey pageKey;
    private int resolvedTid;

    ArticleBodyViews(Supplier<V> create, Consumer<V> release) {
        this.create = create;
        this.release = release;
    }

    void setData(ThreadData data) {
        if (data != null && data == response) {
            return;
        }
        ArticlePagingInfo paging = data == null ? null : data.getPagingInfo();
        ArticleRequestKey nextKey = paging == null ? null : new ArticleRequestKey(
                paging.query, paging.generation, paging.source, paging.pageSize,
                paging.owner, paging.effectivePage);
        boolean samePage = nextKey != null && nextKey.equals(pageKey)
                && paging.resolvedTid == resolvedTid;
        Map<String, Slot<V>> previous = new HashMap<>();
        for (Slot<V> slot : slots) {
            if (samePage && slot.identity != null) {
                previous.put(slot.identity, slot);
            } else {
                release(slot);
            }
        }

        List<ThreadRowInfo> rows = data == null || data.getRowList() == null
                ? Collections.emptyList() : data.getRowList();
        List<String> identities = new ArrayList<>(rows.size());
        Set<String> seen = new HashSet<>();
        Set<String> duplicates = new HashSet<>();
        for (ThreadRowInfo row : rows) {
            String identity = identity(row);
            identities.add(identity);
            if (identity != null && !seen.add(identity)) {
                duplicates.add(identity);
            }
        }
        List<Slot<V>> next = new ArrayList<>(rows.size());
        for (String identity : identities) {
            // Ambiguous/missing identities still get independent slots, never another row's view.
            if (duplicates.contains(identity)) identity = null;
            Slot<V> retained = identity == null ? null : previous.remove(identity);
            next.add(retained == null ? new Slot<>(identity) : retained);
        }
        for (Slot<V> slot : previous.values()) release(slot);
        slots = next;
        response = data;
        pageKey = nextKey;
        resolvedTid = paging == null ? 0 : paging.resolvedTid;
    }

    V getOrCreate(int position) {
        Slot<V> slot = slots.get(position);
        if (slot.view == null) slot.view = create.get();
        return slot.view;
    }

    void clear() {
        for (Slot<V> slot : slots) release(slot);
        slots = Collections.emptyList();
        response = null;
        pageKey = null;
        resolvedTid = 0;
    }

    private void release(Slot<V> slot) {
        if (slot.view != null) {
            release.accept(slot.view);
            slot.view = null;
        }
    }

    private static String identity(ThreadRowInfo row) {
        if (row == null || row.getTid() <= 0 || row.getFormattedHtmlData() == null
                || row.getFormattedHtmlData().isEmpty()) return null;
        if (row.getPid() > 0) return row.getTid() + ":pid:" + row.getPid();
        // The topic's original post has PID zero. An unknown reply must not inherit its view.
        if (row.getPid() == 0 && ArticleRowPresentation.isPost(row)
                && ArticleRowPresentation.hasFloor(row) && row.getLou() == 0) {
            return row.getTid() + ":topic";
        }
        return null;
    }

    private static final class Slot<V> {
        final String identity;
        V view;

        Slot(String identity) {
            this.identity = identity;
        }
    }
}
