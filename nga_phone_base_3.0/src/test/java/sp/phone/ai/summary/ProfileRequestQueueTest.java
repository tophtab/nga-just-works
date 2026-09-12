package sp.phone.ai.summary;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.PriorityQueue;
import java.util.function.LongSupplier;

public class ProfileRequestQueueTest {

    @Test
    public void aLongCallStillRequiresFiveHundredMillisecondsAfterCompletion() {
        FakeTime time = new FakeTime();
        ProfileRequestQueue queue = time.queue();
        List<ProfileRequestQueue.Ticket> started = new ArrayList<>();
        ProfileRequestQueue.Ticket first = queue.enqueue(started::add);
        queue.enqueue(started::add);
        time.advanceBy(10_000L);
        assertEquals(1, started.size());
        first.complete();
        time.advanceBy(499L);
        assertEquals(1, started.size());
        time.advanceBy(1L);
        assertEquals(2, started.size());
    }

    @Test
    public void canceledWaitingWorkDoesNotConsumeTheNextSlot() {
        FakeTime time = new FakeTime();
        ProfileRequestQueue queue = time.queue();
        List<ProfileRequestQueue.Ticket> started = new ArrayList<>();
        ProfileRequestQueue.Ticket first = queue.enqueue(started::add);
        ProfileRequestQueue.Ticket canceled = queue.enqueue(started::add);
        ProfileRequestQueue.Ticket next = queue.enqueue(started::add);
        canceled.cancel();
        first.complete();
        time.advanceBy(500L);
        assertEquals(2, started.size());
        assertEquals(next, started.get(1));
    }

    @Test
    public void staleWakeupsAndDuplicateCompletionsCannotShortenOrRestartTheCooldown() {
        FakeTime time = new FakeTime();
        ProfileRequestQueue queue = time.queue();
        List<ProfileRequestQueue.Ticket> started = new ArrayList<>();
        ProfileRequestQueue.Ticket first = queue.enqueue(started::add);
        first.complete();
        ProfileRequestQueue.Ticket canceled = queue.enqueue(started::add);
        canceled.cancel();
        assertTrue(time.alarms.get(0).canceled);
        ProfileRequestQueue.Ticket replacement = queue.enqueue(started::add);
        time.advanceBy(499L);
        time.alarms.get(0).action.run();
        first.complete();
        assertEquals(1, started.size());
        time.advanceBy(1L);
        assertEquals(2, started.size());
        assertEquals(replacement, started.get(1));
    }

    @Test
    public void activeCancellationDoesNotReleaseAnUnfinishedCall() {
        FakeTime time = new FakeTime();
        ProfileRequestQueue queue = time.queue();
        List<ProfileRequestQueue.Ticket> started = new ArrayList<>();
        ProfileRequestQueue.Ticket first = queue.enqueue(started::add);
        queue.enqueue(started::add);
        first.cancel();
        time.advanceBy(5_000L);
        assertEquals(1, started.size());
        first.complete();
        time.advanceBy(499L);
        assertEquals(1, started.size());
        time.advanceBy(1L);
        assertEquals(2, started.size());
    }

    /** Manual monotonic time shared by queue and source integration tests; no thread sleeps. */
    static final class FakeTime implements LongSupplier, ProfileRequestQueue.Scheduler {
        final List<Alarm> alarms = new ArrayList<>();
        private final PriorityQueue<Alarm> pending = new PriorityQueue<>(
                Comparator.comparingLong((Alarm alarm) -> alarm.at).thenComparingInt(alarm -> alarm.index));
        private long now;

        ProfileRequestQueue queue() {
            return new ProfileRequestQueue(this, this);
        }

        @Override
        public synchronized long getAsLong() {
            return now;
        }

        @Override
        public synchronized SummaryController.Cancelable schedule(Runnable action, long delayMillis) {
            Alarm alarm = new Alarm(action, now + delayMillis, alarms.size());
            alarms.add(alarm);
            pending.add(alarm);
            return () -> {
                synchronized (FakeTime.this) {
                    alarm.canceled = true;
                    pending.remove(alarm);
                }
            };
        }

        void advanceBy(long millis) {
            long target = getAsLong() + millis;
            while (true) {
                final Alarm alarm;
                synchronized (this) {
                    if (pending.isEmpty() || pending.peek().at > target) {
                        now = target;
                        return;
                    }
                    alarm = pending.remove();
                    now = Math.max(now, alarm.at);
                }
                if (!alarm.canceled) {
                    alarm.action.run();
                }
            }
        }
    }

    private static final class Alarm {
        final Runnable action;
        final long at;
        final int index;
        volatile boolean canceled;

        Alarm(Runnable action, long at, int index) {
            this.action = action;
            this.at = at;
            this.index = index;
        }
    }
}
