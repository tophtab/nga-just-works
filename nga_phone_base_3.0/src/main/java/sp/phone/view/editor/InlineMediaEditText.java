package sp.phone.view.editor;

import android.content.Context;
import android.text.Editable;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.style.ImageSpan;
import android.util.AttributeSet;

import androidx.appcompat.widget.AppCompatEditText;

import java.util.ArrayList;
import java.util.List;

/** Keeps the normal EditText/IME contract, with atomic replacement of displayed media. */
public final class InlineMediaEditText extends AppCompatEditText {
    private boolean adjustingSelection;

    public InlineMediaEditText(Context context, AttributeSet attrs) {
        super(context, attrs);
        setEditableFactory(new Editable.Factory() {
            @Override public Editable newEditable(CharSequence source) {
                return new MediaEditable(source);
            }
        });
    }

    static List<int[]> displayedRanges(Spanned text) {
        List<int[]> result = new ArrayList<>();
        for (ImageSpan span : text.getSpans(0, text.length(), ImageSpan.class)) {
            result.add(new int[]{text.getSpanStart(span), text.getSpanEnd(span)});
        }
        result.sort((a, b) -> Integer.compare(a[0], b[0]));
        return result;
    }

    @Override protected void onSelectionChanged(int start, int end) {
        if (!adjustingSelection && start >= 0 && end >= 0 && getText() != null) {
            int[] range = InlineMediaSource.expand(start, end, displayedRanges(getText()));
            if (range[0] != start || range[1] != end) {
                adjustingSelection = true;
                setSelection(range[0], range[1]);
                adjustingSelection = false;
                return;
            }
        }
        super.onSelectionChanged(start, end);
    }

    private static final class MediaEditable extends SpannableStringBuilder {
        MediaEditable(CharSequence source) { super(source); }

        @Override public SpannableStringBuilder replace(int start, int end,
                CharSequence source, int sourceStart, int sourceEnd) {
            int[] range = InlineMediaSource.expand(start, end, displayedRanges(this));
            return super.replace(range[0], range[1], source, sourceStart, sourceEnd);
        }
    }
}
