package sp.phone.view.editor;

import org.junit.Test;
import java.util.Collections;
import java.util.List;
import static org.junit.Assert.*;

public class InlineMediaLoadQueueTest {
    private List<InlineMediaSource.Token> tokens() {
        return InlineMediaSource.parse("[s:ac:赞同][s:ac:赞同][img]./x.png[/img]", "https://img.nga.cn/attachments");
    }

    @Test public void repeatedMediaHaveIndependentTicketsAndAtMostTwoLoads() {
        InlineMediaLoadQueue queue = new InlineMediaLoadQueue(2);
        List<InlineMediaSource.Token> tokens = tokens();
        queue.reset(tokens);
        InlineMediaLoadQueue.Ticket first = queue.next(), second = queue.next();
        assertSame(tokens.get(0), first.token);
        assertSame(tokens.get(1), second.token);
        assertNotSame(first, second);
        assertNull(queue.next());
        queue.complete(second); // Image failure releases its slot just like success.
        InlineMediaLoadQueue.Ticket third = queue.next();
        assertSame(tokens.get(2), third.token);
        assertNull(queue.next());
        queue.complete(first);
        queue.complete(third);
        assertNull(queue.next());
    }

    @Test public void editOrViewRetirementDropsQueueAndStaleCompletions() {
        InlineMediaLoadQueue queue = new InlineMediaLoadQueue(2);
        queue.reset(tokens());
        InlineMediaLoadQueue.Ticket retired = queue.next();
        queue.reset(tokens()); // New input while the old decode is running.
        InlineMediaLoadQueue.Ticket current = queue.next();
        assertNotNull(queue.next());
        queue.complete(retired);
        queue.complete(retired); // A duplicate late callback must not grant another slot.
        assertNull(queue.next());
        queue.complete(current);
        assertNotNull(queue.next());
        queue.reset(Collections.emptyList()); // Pause/close retires all queued work.
        queue.complete(current);
        assertNull(queue.next());
    }
}
