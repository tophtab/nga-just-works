package sp.phone.profile;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;

import sp.phone.http.bean.ThreadData;
import sp.phone.http.bean.ThreadRowInfo;

/** Copies all eligible delivered authors, including floors that have never had a ViewHolder. */
public final class ArticleAuthorIds {

    private ArticleAuthorIds() {
    }

    public static Set<Integer> fromPage(ThreadData page) {
        Set<Integer> authors = new LinkedHashSet<>();
        if (page != null && page.getRowList() != null) {
            for (ThreadRowInfo row : page.getRowList()) {
                if (row != null && !row.getISANONYMOUS() && row.getAuthorid() > 0) {
                    authors.add(row.getAuthorid());
                }
            }
        }
        return Collections.unmodifiableSet(authors);
    }
}
