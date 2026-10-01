package sp.phone.view.editor;

import gov.anzong.androidnga.common.util.EmoticonUtils;

/** Converts the article's CSS image dimensions to native display pixels. */
final class InlineEmoticonSize {
    final int width;
    final int height;

    InlineEmoticonSize(String assetPath, int configuredWidth, int sourceWidth, int sourceHeight,
            float density, int availableWidth) {
        int cssWidth = EmoticonUtils.usesConfiguredWidth(assetPath) ? configuredWidth : sourceWidth;
        width = Math.max(1, Math.min(availableWidth, Math.round(cssWidth * density)));
        height = Math.max(1, Math.round((float) sourceHeight * width / sourceWidth));
    }
}
