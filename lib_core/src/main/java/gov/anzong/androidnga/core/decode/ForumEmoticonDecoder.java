package gov.anzong.androidnga.core.decode;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import gov.anzong.androidnga.common.util.EmoticonUtils;

/** Renders the same built-in assets that the composer inserts. */
public class ForumEmoticonDecoder implements IForumDecoder {

    private static final Pattern EMOTICON = Pattern.compile("\\[s:([^\\[\\]:]*):([^\\[\\]]*)]");

    @Override
    public String decode(String content) {
        Matcher matcher = EMOTICON.matcher(content);
        StringBuffer result = new StringBuffer();
        while (matcher.find()) {
            String category = matcher.group(1);
            String path = EmoticonUtils.resolveAssetPath(category, matcher.group(2));
            if (path == null) {
                continue;
            }
            String attributes = EmoticonUtils.usesConfiguredWidth(path)
                    ? " class='emoticon invertFilter'" : "";
            String html = "<img" + attributes + " src='file:///android_asset/" + path + "'>";
            matcher.appendReplacement(result, Matcher.quoteReplacement(html));
        }
        matcher.appendTail(result);
        return result.toString();
    }
}
