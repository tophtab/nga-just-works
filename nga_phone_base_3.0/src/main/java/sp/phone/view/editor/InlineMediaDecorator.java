package sp.phone.view.editor;

import android.graphics.Bitmap;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.text.Editable;
import android.text.Spanned;
import android.text.TextWatcher;
import android.text.style.ImageSpan;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.bumptech.glide.Glide;
import com.bumptech.glide.RequestManager;
import com.bumptech.glide.load.engine.DiskCacheStrategy;
import com.bumptech.glide.request.target.CustomTarget;
import com.bumptech.glide.request.transition.Transition;

import java.util.ArrayList;
import java.util.List;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.HashSet;
import java.util.Collections;

import gov.anzong.androidnga.common.util.NgaImageHost;
import sp.phone.common.PhoneConfiguration;

/** One view owns all targets. Only spans change when a bitmap arrives; never source/selection. */
public final class InlineMediaDecorator implements TextWatcher {
    private static final int MAX_IN_FLIGHT = 2;
    private static final int IMAGE_SIZE = 384;
    private static final int EMOTICON_SIZE = 96;
    private final InlineMediaEditText editor;
    private final RequestManager requests;
    private final InlineMediaSource.Revision revision = new InlineMediaSource.Revision();
    private final List<MediaTarget> targets = new ArrayList<>();
    private final InlineMediaLoadQueue queue = new InlineMediaLoadQueue(MAX_IN_FLIGHT);
    private final Map<String, Uri> localImages = new HashMap<>();
    private final Set<String> failedResources = new HashSet<>();
    private final Runnable rebuild = this::rebuild;
    private final Runnable pump = this::pump;
    private boolean closed;
    private boolean suspended;
    private long pendingRevision;

    public InlineMediaDecorator(InlineMediaEditText editor) {
        this.editor = editor;
        requests = Glide.with(editor);
        editor.addTextChangedListener(this);
        schedule();
    }

    @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) { }
    @Override public void onTextChanged(CharSequence s, int start, int before, int count) {
        revision.changed(); // Reject delivery immediately, before the posted rebuild runs.
    }
    @Override public void afterTextChanged(Editable s) { schedule(); }

    private void schedule() {
        editor.removeCallbacks(rebuild);
        if (!closed && !suspended) editor.post(rebuild);
    }

    private void rebuild() {
        if (closed || suspended) return;
        // Keep completed spans whose exact source survived editing; cancel obsolete/pending targets.
        Editable text = editor.getText();
        for (MediaTarget target : new ArrayList<>(targets)) {
            int start = target.span == null ? -1 : text.getSpanStart(target.span);
            int end = target.span == null ? -1 : text.getSpanEnd(target.span);
            if (start < 0 || end <= start
                    || !target.token.source.contentEquals(text.subSequence(start, end))
                    || (target.token.image && !PhoneConfiguration.getInstance().isImageLoadEnabled())) {
                retire(target);
            }
        }
        List<InlineMediaSource.Token> pending = new ArrayList<>();
        pendingRevision = revision.current();
        for (InlineMediaSource.Token token : InlineMediaSource.parse(text.toString(), NgaImageHost.attachmentsPrefix())) {
            if (token.image && !PhoneConfiguration.getInstance().isImageLoadEnabled()) continue;
            if (failedResources.contains(token.resource)) continue;
            if (text.getSpans(token.start, token.end, ImageSpan.class).length == 0) pending.add(token);
        }
        queue.reset(pending);
        pump();
    }

    private void pump() {
        if (closed || suspended || pendingRevision != revision.current()) return;
        InlineMediaLoadQueue.Ticket ticket;
        while ((ticket = queue.next()) != null) {
            InlineMediaSource.Token token = ticket.token;
            long generation = revision.current();
            if (!revision.accepts(generation, token, editor.getText())
                    || (token.image && !PhoneConfiguration.getInstance().isImageLoadEnabled())) {
                queue.complete(ticket);
                continue;
            }
            int size = token.image ? IMAGE_SIZE : EMOTICON_SIZE;
            MediaTarget target = new MediaTarget(ticket, generation, size);
            targets.add(target);
            Object resource = localImages.containsKey(token.source) ? localImages.get(token.source) : token.resource;
            requests.asBitmap().load(resource).override(size, size).fitCenter()
                    .diskCacheStrategy(DiskCacheStrategy.AUTOMATIC).into(target);
        }
    }

    private void retire(MediaTarget target) {
        targets.remove(target);
        queue.complete(target.ticket);
        if (target.span != null) editor.getText().removeSpan(target.span);
        requests.clear(target);
    }

    public void suspend() {
        suspended = true;
        revision.changed();
        clear();
    }

    public void resume() {
        suspended = false;
        schedule();
    }

    private void clear() {
        editor.removeCallbacks(rebuild);
        editor.removeCallbacks(pump);
        queue.reset(Collections.emptyList());
        for (MediaTarget target : new ArrayList<>(targets)) retire(target);
    }

    public void close() {
        closed = true;
        revision.close();
        editor.removeTextChangedListener(this);
        clear();
        localImages.clear();
        failedResources.clear();
    }

    public void registerLocalImage(String source, Uri uri) { localImages.put(source, uri); }

    private final class MediaTarget extends CustomTarget<Bitmap> {
        final InlineMediaSource.Token token;
        final InlineMediaLoadQueue.Ticket ticket;
        final long generation;
        ImageSpan span;

        MediaTarget(InlineMediaLoadQueue.Ticket ticket, long generation, int size) {
            super(size, size);
            this.ticket = ticket;
            this.token = ticket.token;
            this.generation = generation;
        }

        private void complete() {
            queue.complete(ticket);
            // Glide forbids starting/clearing requests synchronously inside its callbacks.
            editor.removeCallbacks(pump);
            if (!closed && !suspended) editor.post(pump);
        }

        @Override public void onResourceReady(@NonNull Bitmap bitmap, @Nullable Transition<? super Bitmap> transition) {
            if (targets.contains(this) && revision.accepts(generation, token, editor.getText())
                    && (!token.image || PhoneConfiguration.getInstance().isImageLoadEnabled())) {
                BitmapDrawable drawable = new BitmapDrawable(editor.getResources(), bitmap);
                int maxWidth = Math.max(1, editor.getWidth() - editor.getPaddingLeft() - editor.getPaddingRight());
                float scale = Math.min(1f, (float) maxWidth / bitmap.getWidth());
                drawable.setBounds(0, 0, Math.max(1, (int) (bitmap.getWidth() * scale)),
                        Math.max(1, (int) (bitmap.getHeight() * scale)));
                span = new ImageSpan(drawable, ImageSpan.ALIGN_BASELINE);
                editor.getText().setSpan(span, token.start, token.end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                editor.requestLayout();
                editor.invalidate();
            }
            complete();
        }

        @Override public void onLoadFailed(@Nullable Drawable errorDrawable) {
            if (targets.contains(this) && revision.accepts(generation, token, editor.getText())) {
                failedResources.add(token.resource);
            }
            complete();
        }

        @Override public void onLoadCleared(@Nullable Drawable placeholder) {
            if (span != null) editor.getText().removeSpan(span);
        }
    }
}
