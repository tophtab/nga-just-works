package sp.phone.view.editor;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class InlineEmoticonSizeTest {
    @Test public void acUsesReaderWidthInDensityPixelsAndPreservesAspectRatio() {
        assertSize(180, 155, new InlineEmoticonSize("ac/ac0.png", 60, 100, 86, 3f, 1080));
        assertSize(120, 103, new InlineEmoticonSize("ac/ac0.png", 60, 100, 86, 2f, 720));
        assertSize(240, 250, new InlineEmoticonSize("a2/a2.png", 80, 100, 104, 3f, 1080));
    }

    @Test public void nonAcFamiliesKeepNaturalReaderSizesInsteadOfASquareThumbnail() {
        assertSize(138, 180, new InlineEmoticonSize("ng/ng.png", 80, 46, 60, 3f, 1080));
        assertSize(300, 300, new InlineEmoticonSize("pg/pg.png", 80, 100, 100, 3f, 1080));
        assertSize(180, 180, new InlineEmoticonSize("pst/pst.png", 80, 60, 60, 3f, 1080));
        assertSize(180, 180, new InlineEmoticonSize("dt/dt.png", 80, 60, 60, 3f, 1080));
    }

    @Test public void narrowEditorClampsWidthWithoutDistortingTallEmoticons() {
        assertSize(90, 94, new InlineEmoticonSize("a2/a2.png", 80, 100, 104, 3f, 90));
        assertSize(90, 90, new InlineEmoticonSize("pg/pg.png", 80, 100, 100, 3f, 90));
    }

    private static void assertSize(int width, int height, InlineEmoticonSize actual) {
        assertEquals(width, actual.width);
        assertEquals(height, actual.height);
    }
}
