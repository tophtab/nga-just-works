package sp.phone.view;

import android.content.Context;
import android.content.res.Resources;
import android.content.res.XmlResourceParser;

import androidx.fragment.app.Fragment;

import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserException;

import java.io.IOException;
import java.util.List;
import java.util.Random;

import gov.anzong.androidnga.R;

/** Reads only bundled settings metadata; retains neither a Context nor a Fragment. */
final class BundledLoadingTips {

    private static final String ANDROID_NAMESPACE = "http://schemas.android.com/apk/res/android";
    private static final LoadingTipSelector SELECTOR = new LoadingTipSelector(new Random());
    private static List<Integer> eligibleTips;

    private BundledLoadingTips() {
    }

    static synchronized int next(Context context) {
        if (eligibleTips == null) {
            eligibleTips = LoadingTipCatalog.eligibleTips(hasAiSettings(context));
        }
        return SELECTOR.next(eligibleTips);
    }

    private static boolean hasAiSettings(Context context) {
        try (XmlResourceParser settings = context.getResources().getXml(R.xml.settings)) {
            for (int event = settings.getEventType(); event != XmlPullParser.END_DOCUMENT;
                 event = settings.next()) {
                if (event == XmlPullParser.START_TAG && LoadingTipCatalog.isAiSettingsEntry(
                        settings.getAttributeValue(ANDROID_NAMESPACE, "key"),
                        settings.getAttributeValue(ANDROID_NAMESPACE, "fragment"),
                        name -> isBundledFragment(context, name))) {
                    return true;
                }
            }
        } catch (IOException | XmlPullParserException | Resources.NotFoundException ignored) {
            // A missing optional settings destination must not advertise an unavailable feature.
        }
        return false;
    }

    private static boolean isBundledFragment(Context context, String name) {
        try {
            Class<?> destination = Class.forName(name, false, context.getClassLoader());
            return Fragment.class.isAssignableFrom(destination);
        } catch (ClassNotFoundException | LinkageError ignored) {
            return false;
        }
    }
}
