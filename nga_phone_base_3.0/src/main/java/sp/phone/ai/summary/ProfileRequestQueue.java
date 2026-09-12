package sp.phone.ai.summary;

import java.util.ArrayDeque;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.function.LongSupplier;

/** One profile collection call at a time, with a quiet interval after its response closes. */
final class ProfileRequestQueue {

    static final long REQUEST_INTERVAL_MILLIS = 500L;

    interface Scheduler {
        SummaryController.Cancelable schedule(Runnable action, long delayMillis);
    }

    private static final class Shared {
        static final ProfileRequestQueue INSTANCE = create();

        private static ProfileRequestQueue create() {
            ScheduledThreadPoolExecutor timer = new ScheduledThreadPoolExecutor(1, action -> {
                Thread thread = new Thread(action, "nga-profile-requests");
                thread.setDaemon(true);
                return thread;
            });
            timer.setRemoveOnCancelPolicy(true);
            return new ProfileRequestQueue(() -> TimeUnit.NANOSECONDS.toMillis(System.nanoTime()),
                    (action, delay) -> {
                        java.util.concurrent.Future<?> future = timer.schedule(action, delay, TimeUnit.MILLISECONDS);
                        return () -> future.cancel(false);
                    });
        }
    }

    static ProfileRequestQueue shared() {
        return Shared.INSTANCE;
    }

    final class Ticket implements SummaryController.Cancelable {
        private final Consumer<Ticket> start;

        private Ticket(Consumer<Ticket> start) {
            this.start = start;
        }

        /** Call only after the transport callback has finished reading/closing its response. */
        void complete() {
            release(this, true);
        }

        /** No transport was enqueued; preserve the preceding call's cooldown. */
        void skip() {
            release(this, false);
        }

        @Override
        public void cancel() {
            final Wakeup pending;
            synchronized (ProfileRequestQueue.this) {
                queue.remove(this);
                pending = queue.isEmpty() ? wakeup : null;
                if (pending != null) {
                    wakeup = null;
                }
            }
            if (pending != null) {
                pending.cancel();
            }
            // An active ticket stays owned until its callback closes/completes the call.
        }
    }

    private final class Wakeup implements Runnable {
        SummaryController.Cancelable cancellation = SummaryController.Cancelable.NONE;

        @Override
        public void run() {
            synchronized (ProfileRequestQueue.this) {
                if (wakeup != this) {
                    return;
                }
                wakeup = null;
            }
            dispatch();
        }

        void cancel() {
            final SummaryController.Cancelable handle;
            synchronized (ProfileRequestQueue.this) {
                handle = cancellation;
            }
            handle.cancel();
        }
    }

    private final LongSupplier elapsedClock;
    private final Scheduler scheduler;
    private final ArrayDeque<Ticket> queue = new ArrayDeque<>();
    private Ticket active;
    private Wakeup wakeup;
    private long completedAt;
    private boolean hasCompleted;

    ProfileRequestQueue(LongSupplier elapsedClock, Scheduler scheduler) {
        this.elapsedClock = elapsedClock;
        this.scheduler = scheduler;
    }

    Ticket enqueue(Consumer<Ticket> start) {
        Ticket ticket = new Ticket(start);
        synchronized (this) {
            queue.addLast(ticket);
        }
        dispatch();
        return ticket;
    }

    private void release(Ticket ticket, boolean completedCall) {
        synchronized (this) {
            if (active != ticket) {
                return;
            }
            active = null;
            if (completedCall) {
                completedAt = elapsedClock.getAsLong();
                hasCompleted = true;
            }
        }
        dispatch();
    }

    private void dispatch() {
        final Ticket ready;
        final Wakeup pending;
        final long delay;
        synchronized (this) {
            if (active != null || wakeup != null || queue.isEmpty()) {
                return;
            }
            delay = hasCompleted
                    ? REQUEST_INTERVAL_MILLIS - (elapsedClock.getAsLong() - completedAt) : 0L;
            if (delay > 0L) {
                pending = new Wakeup();
                wakeup = pending;
                ready = null;
            } else {
                ready = queue.removeFirst();
                active = ready;
                pending = null;
            }
        }
        // Neither transport dispatch nor a possibly synchronous scheduler holds the queue lock.
        if (ready != null) {
            ready.start.accept(ready);
        } else {
            SummaryController.Cancelable handle = scheduler.schedule(pending, delay);
            final boolean stale;
            synchronized (this) {
                stale = wakeup != pending;
                if (!stale) {
                    pending.cancellation = handle;
                }
            }
            if (stale) {
                handle.cancel();
            }
        }
    }
}
