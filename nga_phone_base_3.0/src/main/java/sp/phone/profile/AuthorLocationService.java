package sp.phone.profile;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;

import androidx.annotation.NonNull;
import androidx.lifecycle.DefaultLifecycleObserver;
import androidx.lifecycle.LifecycleOwner;
import androidx.lifecycle.MutableLiveData;

import com.justwen.androidnga.base.network.retrofit.RetrofitHelper;
import com.justwent.androidnga.bu.UserManager;

import java.io.File;
import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;

import gov.anzong.androidnga.common.PreferenceKey;
import gov.anzong.androidnga.common.util.ForumUtils;
import sp.phone.common.User;
import sp.phone.http.bean.ThreadData;

/** App-scoped cache/transport with view-scoped, lifecycle-aware delivery. */
public final class AuthorLocationService {

    private static AuthorLocationService instance;

    private final Handler main = new Handler(Looper.getMainLooper());
    private final ExecutorService disk = Executors.newSingleThreadExecutor();
    private final AuthorLocationRepository repository;
    private final List<Runnable> awaitingSession = new ArrayList<>();
    private final SharedPreferences preferences;
    // SharedPreferences keeps listeners weakly; keep this app-only listener alive.
    private final SharedPreferences.OnSharedPreferenceChangeListener preferenceListener;
    private boolean sessionPending;
    private long sessionSignal;

    private AuthorLocationService(Context context) {
        Context app = context.getApplicationContext();
        preferences = app.getSharedPreferences(PreferenceKey.PERFERENCE, Context.MODE_PRIVATE);
        // Initializes the established browser compatibility UA, without issuing a request.
        RetrofitHelper.getInstance();
        AuthorLocationStore store = new AuthorLocationStore(
                new File(app.getCacheDir(), "author-locations-v1.json"));
        repository = new AuthorLocationRepository(new ProfileLocationTransport(System::currentTimeMillis),
                System::currentTimeMillis, SystemClock::elapsedRealtime, this::captureSession, main::post,
                (action, delayMillis) -> {
                    main.postDelayed(action, delayMillis);
                    return () -> main.removeCallbacks(action);
                },
                entries -> disk.execute(() -> store.write(entries)));
        preferenceListener = (prefs, key) -> {
            if (PreferenceKey.KEY_NGA_DOMAIN.equals(key) || PreferenceKey.USER_AGENT.equals(key)) {
                invalidateAccountSignal();
            }
        };
        preferences.registerOnSharedPreferenceChangeListener(preferenceListener);
        disk.execute(() -> {
            List<AuthorLocationCache.Entry> entries = store.read();
            main.post(() -> repository.restore(entries));
        });
    }

    private static AuthorLocationService get(Context context) {
        if (instance == null) {
            instance = new AuthorLocationService(context);
        }
        return instance;
    }

    public static Page bind(Context context, LifecycleOwner owner,
                            Consumer<AuthorLocationRepository.Snapshot> display) {
        AuthorLocationService service = get(context);
        Page page = new Page(service);
        page.updates.observe(owner, delivery -> {
            if (!page.closed && delivery.generation == page.generation) {
                display.accept(delivery.snapshot);
            }
        });
        owner.getLifecycle().addObserver(page);
        service.observeAccount(owner);
        return page;
    }

    private void observeAccount(LifecycleOwner owner) {
        // Each observer's first value is a replay, not an account mutation. It still gets a
        // deferred consistency check, so opening a new page never restarts other pages' work.
        boolean[] initialIndex = {true};
        boolean[] initialList = {true};
        UserManager.INSTANCE.getActiveIndexLiveData().observe(owner, index -> {
            if (initialIndex[0]) {
                initialIndex[0] = false;
                main.post(repository::synchronizeSession);
            } else {
                invalidateAccountSignal();
            }
        });
        UserManager.INSTANCE.getUserListLiveData().observe(owner, users -> {
            if (initialList[0]) {
                initialList[0] = false;
                main.post(repository::synchronizeSession);
            } else {
                invalidateAccountSignal();
            }
        });
    }

    private void invalidateAccountSignal() {
        // UserManager emits the index before assigning the selected account. Stop old work
        // immediately, then read the settled values on the next main-loop turn.
        repository.invalidateSession();
        sessionSignal++;
        if (sessionPending) {
            return;
        }
        sessionPending = true;
        main.post(() -> {
            sessionPending = false;
            repository.synchronizeSession();
            List<Runnable> ready = new ArrayList<>(awaitingSession);
            awaitingSession.clear();
            for (Runnable action : ready) {
                action.run();
            }
        });
    }

    private ProfileSession captureSession() {
        if (sessionPending) {
            return null;
        }
        List<User> users = UserManager.INSTANCE.getUserList();
        int index = UserManager.INSTANCE.getActiveIndex();
        String uid = null;
        String cid = null;
        if (!users.isEmpty()) {
            if (index < 0 || index >= users.size() || users.get(index) == null) {
                return null;
            }
            // removeUser can leave getActiveUser stale even after its callbacks. The settled
            // list/index select the viewing account, and copies also catch same-UID re-login.
            User user = users.get(index);
            uid = user.getUserId();
            cid = user.getCid();
        }
        String ua = preferences.getString(PreferenceKey.USER_AGENT,
                RetrofitHelper.getInstance().getUserAgent());
        try {
            return ProfileSession.create(ForumUtils.getAvailableDomain(), uid, cid, ua);
        } catch (RuntimeException ignored) {
            // Invalid legacy domain preferences disable this supplemental operation only.
            return null;
        }
    }

    private void whenSessionSettled(Runnable action) {
        main.post(() -> {
            if (sessionPending) {
                awaitingSession.add(action);
            } else {
                action.run();
            }
        });
    }

    private static final class Delivery {
        final long generation;
        final AuthorLocationRepository.Snapshot snapshot;

        Delivery(long generation, AuthorLocationRepository.Snapshot snapshot) {
            this.generation = generation;
            this.snapshot = snapshot;
        }
    }

    /** The app repository cannot keep a view alive through LiveData's lifecycle observers. */
    private static final class DeliverySink implements Consumer<AuthorLocationRepository.Snapshot> {
        private final WeakReference<MutableLiveData<Delivery>> updates;
        private final long generation;

        DeliverySink(MutableLiveData<Delivery> updates, long generation) {
            this.updates = new WeakReference<>(updates);
            this.generation = generation;
        }

        @Override
        public void accept(AuthorLocationRepository.Snapshot snapshot) {
            MutableLiveData<Delivery> target = updates.get();
            if (target != null) {
                target.setValue(new Delivery(generation, snapshot));
            }
        }
    }

    public static final class Page implements DefaultLifecycleObserver, AutoCloseable {
        private final AuthorLocationService service;
        private final MutableLiveData<Delivery> updates = new MutableLiveData<>();
        private AuthorLocationRepository.Subscription subscription;
        private ThreadData lastDeliveredData;
        private long generation;
        private boolean closed;

        private Page(AuthorLocationService service) {
            this.service = service;
        }

        /** Accepts fresh page data; READY replays only restore the current metadata. */
        public void deliver(ThreadData data, boolean online) {
            if (closed) {
                return;
            }
            if (data != null && data == lastDeliveredData) {
                // Keep the original subscription's online/cache-only intent and pending work.
                Delivery previous = updates.getValue();
                if (previous != null && previous.generation == generation) {
                    updates.setValue(previous);
                }
                return;
            }
            lastDeliveredData = data;
            long version = ++generation;
            long signal = service.sessionSignal;
            if (subscription != null) {
                subscription.close();
                subscription = null;
            }
            updates.setValue(new Delivery(version, AuthorLocationRepository.Snapshot.empty()));
            Set<Integer> authors = ArticleAuthorIds.fromPage(data);
            service.whenSessionSettled(() -> {
                if (!closed && generation == version && service.sessionSignal == signal) {
                    subscription = service.repository.subscribe(authors, online,
                            new DeliverySink(updates, version));
                }
            });
        }

        @Override
        public void onStart(@NonNull LifecycleOwner owner) {
            service.repository.synchronizeSession();
            // Re-evaluate expiry after a long stop without turning a revisit into network work.
            Delivery previous = updates.getValue();
            if (!closed && previous != null) {
                updates.setValue(previous);
            }
        }

        @Override
        public void onDestroy(@NonNull LifecycleOwner owner) {
            close();
        }

        @Override
        public void close() {
            closed = true;
            lastDeliveredData = null;
            generation++;
            if (subscription != null) {
                subscription.close();
                subscription = null;
            }
        }
    }
}
