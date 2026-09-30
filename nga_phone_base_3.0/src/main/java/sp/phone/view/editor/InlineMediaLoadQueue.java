package sp.phone.view.editor;

import java.util.ArrayDeque;
import java.util.Collection;
import java.util.HashSet;
import java.util.Set;

/** A bounded, revision-owned queue. Retired completions cannot release a newer request's slot. */
final class InlineMediaLoadQueue {
    private final int limit;
    private final ArrayDeque<InlineMediaSource.Token> pending = new ArrayDeque<>();
    private final Set<Ticket> active = new HashSet<>();

    InlineMediaLoadQueue(int limit) { this.limit = limit; }

    static final class Ticket {
        final InlineMediaSource.Token token;
        Ticket(InlineMediaSource.Token token) { this.token = token; }
    }

    void reset(Collection<InlineMediaSource.Token> tokens) {
        active.clear();
        pending.clear();
        pending.addAll(tokens);
    }

    Ticket next() {
        if (active.size() >= limit || pending.isEmpty()) return null;
        Ticket ticket = new Ticket(pending.removeFirst());
        active.add(ticket);
        return ticket;
    }

    void complete(Ticket ticket) { active.remove(ticket); }
}
