package sp.phone.view.editor;

import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.drawable.Drawable;
import android.text.style.ImageSpan;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/** Anchors the image bottom to the actual text baseline, independently of line spacing. */
final class InlineEmoticonSpan extends ImageSpan {
    InlineEmoticonSpan(Drawable drawable) {
        super(drawable, ALIGN_BASELINE);
    }

    @Override public int getSize(@NonNull Paint paint, CharSequence text, int start, int end,
            @Nullable Paint.FontMetricsInt metrics) {
        Drawable drawable = getDrawable();
        if (metrics != null) {
            // Keep room for text descenders, including lines containing only an emoticon.
            paint.getFontMetricsInt(metrics);
            metrics.ascent = Math.min(metrics.ascent, -drawable.getBounds().height());
            metrics.top = Math.min(metrics.top, metrics.ascent);
        }
        return drawable.getBounds().width();
    }

    @Override public void draw(@NonNull Canvas canvas, CharSequence text, int start, int end,
            float x, int top, int y, int bottom, @NonNull Paint paint) {
        Drawable drawable = getDrawable();
        int checkpoint = canvas.save();
        canvas.translate(x, y - drawable.getBounds().bottom);
        drawable.draw(canvas);
        canvas.restoreToCount(checkpoint);
    }
}
