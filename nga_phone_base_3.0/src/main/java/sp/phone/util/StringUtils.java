package sp.phone.util;

import android.annotation.SuppressLint;
import android.content.res.AssetManager;

import androidx.annotation.Nullable;

import java.io.IOException;
import java.io.InputStream;
import java.text.DateFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import gov.anzong.androidnga.Utils;
import gov.anzong.androidnga.base.util.ContextUtils;
import gov.anzong.androidnga.common.util.EmoticonUtils;
import gov.anzong.androidnga.common.util.NLog;
import gov.anzong.androidnga.common.util.NgaImageHost;
import sp.phone.http.bean.StringFindResult;
import sp.phone.theme.ThemeManager;

@SuppressLint("SimpleDateFormat")
public class StringUtils {
    public final static String key = "asdfasdf";
    private static final String lesserNukeStyle = "<div style='border:1px solid #B63F32;margin:10px 10px 10px 10px;padding:10px' > <span style='color:#EE8A9E'>用户因此贴被暂时禁言，此效果不会累加</span><br/>";
    private static final String styleAlignRight = "<div style='text-align:right' >";
    private static final String styleAlignLeft = "<div style='text-align:left' >";
    private static final String styleAlignCenter = "<div style='text-align:center' >";
    private static final String styleColor = "<span style='color:$1' >";
    private static final String ignoreCaseTag = "(?i)";
    private static final String endDiv = "</div>";

    /**
     * 验证是否是邮箱
     */
    public static boolean isEmail(String email) {
        if (isEmpty(email))
            return false;
        String pattern1 = "^([a-z0-9A-Z]+[-_|\\.]?)+[a-z0-9A-Z_]@([a-z0-9A-Z]+(-[a-z0-9A-Z]+)?\\.)+[a-zA-Z]{2,}$";
        Pattern pattern = Pattern.compile(pattern1);
        Matcher mat = pattern.matcher(email);
        if (!mat.find()) {
            return false;
        } else {
            return true;
        }
    }

    /**
     * 判断是否是 "" 或者 null
     */
    public static boolean isEmpty(String str) {
        if (str != null && !"".equals(str)) {
            return false;
        } else {
            return true;
        }
    }

    /* 给候总客户端乱码加适配 */
    public static String unescape(String src) {
        if (isEmpty(src))
            return "";
        StringBuffer tmp = new StringBuffer();
        tmp.ensureCapacity(src.length());
        int lastPos = 0, pos = 0;
        char ch;
        String patternStr = "[A-Fa-f0-9]{4}";
        while (lastPos < src.length()) {
            pos = src.indexOf("%", lastPos);
            if (pos == lastPos) {
                if (pos > src.length() - 3) {
                    tmp.append(src.substring(pos, src.length()));
                    lastPos = pos + 3;
                } else {
                    if (src.charAt(pos + 1) == 'u') {
                        try {
                            if (Pattern.matches(patternStr,
                                    src.substring(pos + 2, pos + 6))) {
                                ch = (char) Integer.parseInt(
                                        src.substring(pos + 2, pos + 6), 16);
                                tmp.append(ch);
                                lastPos = pos + 6;
                            } else {
                                tmp.append(src.substring(pos, pos + 3));
                                lastPos = pos + 3;
                            }
                        } catch (Exception e) {
                            tmp.append(src.substring(pos, pos + 3));
                            lastPos = pos + 3;
                        }

                    } else {
                        try {
                            ch = (char) Integer.parseInt(
                                    src.substring(pos + 1, pos + 3), 16);
                            tmp.append(ch);
                            lastPos = pos + 3;
                        } catch (Exception e) {
                            tmp.append(src.substring(pos, pos + 3));
                            lastPos = pos + 3;
                        }
                    }
                }
            } else {
                if (pos == -1) {
                    tmp.append(src.substring(lastPos));
                    lastPos = src.length();
                } else {
                    tmp.append(src.substring(lastPos, pos));
                    lastPos = pos;
                }
            }
        }
        return tmp.toString();
    }

    /**
     * yy-M-dd hh:mm
     */
    public static Long sDateToLong(String sDate) {
        DateFormat df = new SimpleDateFormat("yy-M-dd hh:mm");
        Date date = null;
        try {
            date = df.parse(sDate);
        } catch (ParseException e) {
            e.printStackTrace();
        }
        return date.getTime();
    }

    public static boolean isNumer(String str) {
        Pattern pattern = Pattern.compile("[0-9]*");
        return pattern.matcher(str).matches();
    }

    public static Long parseLong(String str) {
        if (str == null) {
            return null;
        } else {
            if (str.equals("")) {
                return 0l;
            } else {
                return Long.parseLong(str);
            }
        }
    }

    public static Long sDateToLong(String sDate, String dateFormat) {
        DateFormat df = new SimpleDateFormat(dateFormat);
        Date date = new Date();
        try {
            date = df.parse(sDate);
        } catch (ParseException e) {
            e.printStackTrace();
        }
        return date.getTime();
    }

    public static String encodeUrl(final String s, final String charset) {

        /*
         * try { return java.net.URLEncoder.encode(s,charset); // this not work
         * in android 4.4 if a english char is followed //by a Chinese character
         *
         * } catch (UnsupportedEncodingException e) {
         *
         * return ""; }
         */
        String ret = UriEncoderWithCharset.encode(s, null, charset);
        // NLog.i("111111", s+"----->"+ret);
        return ret;
    }

    public static String parseHTML(String s) {
        // 转换字体
        if (s.indexOf("[quote]") != -1) {
            s = s.replace("[quote]", "");
            s = s.replace("[/quote]", "</font><font color='#1d2a63' size='10'>");

            s = s.replace("[b]", "<font color='red' size='1'>");
            s = s.replace("[/b]", "</font>");
            s = s.replace("<br/><br/>", "<br/>");
            s = s.replace("<br/><br/>", "<br/>");

            s = s.replace("[/pid]", "<font color='blue' size='2'>");
            s = s + "</font>";
        } else {
            s = "<font color='#1d2a63' size='10'>" + s;
            s = s + "</font>";
        }
        // 转换 表情

        s = s.replaceAll("(\\[s:\\d\\])", "<img src='$1'>");
        return s;
    }

    public static String decodealbum(String s, String quotediv) {
        int startpos = s.indexOf("[album="), endpos = s.indexOf("[/album]") + 8;
        String sup = "", sdown = "", salbum = "", stemp = "", stitle = "";
        while (startpos < endpos && startpos >= 0) {
            sup = s.substring(0, startpos);
            if (endpos >= 0)
                sdown = s.substring(endpos, s.length());
            salbum = s.substring(startpos, endpos);
            stitle = salbum.replaceAll("(?i)" + "\\[album=(.*?)\\](.*?)\\[/album\\]", "$1");
            stemp = salbum.replaceAll("(?i)" + "\\[album=(.*?)\\](.*?)\\[/album\\]", "$2");
            if (stemp.startsWith("<br/>")) {
                stemp = "[img]" + stemp.substring(5) + "[/img]";
            }
            stitle = "相册列表:" + stitle + "<br/>";
            stemp = stemp.replaceAll("<br/>", "[/img]<br/><br/>[img]");
            stemp = "<br/>" + quotediv + stitle + "<br/>" + stemp + "</div>";
            s = sup + stemp + sdown;
            startpos = s.indexOf("[album=");
            endpos = s.indexOf("[/album]") + 8;
        }
        return s;
    }

    public static String decodeForumTag(String ret, boolean showImage,
                                        int imageQuality, @Nullable List<String> imageUrls) {
        if (StringUtils.isEmpty(ret))
            return "";
        // 这是与 ForumImageDecoder 并行的另一条老解码路径，需独立做一次旧图床归一化，
        // 否则走这条路的历史帖子仍然写死着已死的域名。
        ret = NgaImageHost.normalizeLegacyHosts(ret);
        // s = StringUtils.unEscapeHtml(s);
        String quoteStyle = "<div style='background:#E8E8E8;padding:5px;border:1px solid #888' >";
        if (ThemeManager.getInstance().isNightMode())
            quoteStyle = "<div style='background:#000000;padding:5px;border:1px solid #888' >";

        final String styleLeft = "<div style='float:left' >";
        final String styleRight = "<div style='float:right' >";
        ret = decodealbum(ret, quoteStyle);
        ret = ret.replaceAll(ignoreCaseTag + "&amp;", "&");
        ret = ret.replaceAll(ignoreCaseTag + "\\[l\\]", styleLeft);
        ret = ret.replaceAll(ignoreCaseTag + "\\[/l\\]", endDiv);
        // ret = ret.replaceAll("\\[L\\]", styleLeft);
        // ret = ret.replaceAll("\\[/L\\]", endDiv);

        ret = ret.replaceAll(ignoreCaseTag + "\\[r\\]", styleRight);
        ret = ret.replaceAll(ignoreCaseTag + "\\[/r\\]", endDiv);
        // ret = ret.replaceAll("\\[R\\]", styleRight);
        // ret = ret.replaceAll("\\[/R\\]", endDiv);

        ret = ret.replaceAll(ignoreCaseTag + "\\[align=right\\]", styleAlignRight);
        ret = ret.replaceAll(ignoreCaseTag + "\\[align=left\\]", styleAlignLeft);
        ret = ret.replaceAll(ignoreCaseTag + "\\[align=center\\]", styleAlignCenter);
        ret = ret.replaceAll(ignoreCaseTag + "\\[/align\\]", endDiv);

        ret = ret.replaceAll(
                ignoreCaseTag
                        + "\\[b\\]Reply to \\[pid=(.+?),(.+?),(.+?)\\]Reply\\[/pid\\] (.+?)\\[/b\\]",
                "[quote]Reply to [b]<a href='" + Utils.getNGAHost() + "read.php?searchpost=1&pid=$1' style='font-weight: bold;'>[Reply]</a> $4[/b][/quote]");

        ret = ret.replaceAll(
                ignoreCaseTag + "\\[pid=(.+?),(.+?),(.+?)\\]Reply\\[/pid\\]",
                "<a href='" + Utils.getNGAHost() + "read.php?searchpost=1&pid=$1' style='font-weight: bold;'>[Reply]</a>");

        // 某些帖子会导致这个方法卡住, 暂时不清楚原因, 和这个方法的作用.... by elrond
        /*ret = ret.replaceAll(
                ignoreCaseTag + "={3,}((^=){0,}(.*?){0,}(^=){0,})={3,}",
                "<h4 style='font-weight: bold;border-bottom: 1px solid #AAA;clear: both;margin-bottom: 0px;'>$1</h4>");*/

        ret = ret.replaceAll(ignoreCaseTag + "\\[quote\\]", quoteStyle);
        ret = ret.replaceAll(ignoreCaseTag + "\\[/quote\\]", endDiv);

        ret = ret.replaceAll(ignoreCaseTag + "\\[code\\]", quoteStyle + "Code:");
        ret = ret.replaceAll(ignoreCaseTag + "\\[code(.+?)\\]", quoteStyle);
        ret = ret.replaceAll(ignoreCaseTag + "\\[/code\\]", endDiv);
        // reply
        // ret = ret.replaceAll(
        // ignoreCaseTag +"\\[pid=\\d+\\]Reply\\[/pid\\]", "Reply");
        // ret = ret.replaceAll(
        // ignoreCaseTag +"\\[pid=\\d+,\\d+,\\d\\]Reply\\[/pid\\]", "Reply");

        // topic
        ret = ret.replaceAll(ignoreCaseTag + "\\[tid=\\d+\\]Topic\\[/pid\\]",
                "Topic");
        ret = ret.replaceAll(ignoreCaseTag + "\\[tid=?(\\d{0,50})\\]Topic\\[/tid\\]",
                "<a href='" + Utils.getNGAHost() + "read.php?tid=$1' style='font-weight: bold;'>[Topic]</a>");
        // reply
        // s =
        // s.replaceAll("\\[b\\]Reply to \\[pid=\\d+\\]Reply\\[/pid\\] (Post by .+ \\(\\d{4,4}-\\d\\d-\\d\\d \\d\\d:\\d\\d\\))\\[/b\\]"
        // , "Reply to Reply <b>$1</b>");
        // 转换 tag
        // [b]
        ret = ret.replaceAll(ignoreCaseTag + "\\[b\\]", "<b>");
        ret = ret.replaceAll(ignoreCaseTag + "\\[/b\\]", "</b>"/* "</font>" */);

        // item
        ret = ret.replaceAll(ignoreCaseTag + "\\[item\\]", "<b>");
        ret = ret.replaceAll(ignoreCaseTag + "\\[/item\\]", "</b>");

        ret = ret.replaceAll(ignoreCaseTag + "\\[u\\]", "<u>");
        ret = ret.replaceAll(ignoreCaseTag + "\\[/u\\]", "</u>");

        ret = ret.replaceAll(ignoreCaseTag + "\\[s:(\\d+)\\]",
                "<img src='file:///android_asset/a$1.gif'>");
        ret = buildEmoticonImage(ret);
        ret = ret.replace(ignoreCaseTag + "<br/><br/>", "<br/>");
        // [url][/url]
        ret = ret.replaceAll(
                ignoreCaseTag + "\\[url\\]/([^\\[|\\]]+)\\[/url\\]",
                "<a href=\"" + Utils.getNGAHost() + "$1\">" + Utils.getNGAHost() + "$1</a>");
        ret = ret.replaceAll(
                ignoreCaseTag + "\\[url\\]([^\\[|\\]]+)\\[/url\\]",
                "<a href=\"$1\">$1</a>");
        ret = ret.replaceAll(ignoreCaseTag
                        + "\\[url=/([^\\[|\\]]+)\\]\\s*(.+?)\\s*\\[/url\\]",
                "<a href=\"" + Utils.getNGAHost() + "$1\">$2</a>");
        ret = ret.replaceAll(ignoreCaseTag
                        + "\\[url=([^\\[|\\]]+)\\]\\s*(.+?)\\s*\\[/url\\]",
                "<a href=\"$1\">$2</a>");
        ret = ret.replaceAll(ignoreCaseTag
                + "\\[uid=?(\\d{0,50})\\](.+?)\\[\\/uid\\]", "$2");
        ret = ret.replaceAll(
                ignoreCaseTag + "Post by\\s{0,}([^\\[\\s]{1,})\\s{0,}\\(",
                "Post by <a href='" + Utils.getNGAHost() + "nuke.php?func=ucp&username=$1' style='font-weight: bold;'>[$1]</a> (");
        ret = ret.replaceAll(
                ignoreCaseTag + "\\[@(.{2,20}?)\\]",
                "<a href='" + Utils.getNGAHost() + "nuke.php?func=ucp&username=$1' style='font-weight: bold;'>[@$1]</a>");
        ret = ret.replaceAll(ignoreCaseTag
                + "\\[uid=-?(\\d{0,50})\\](.+?)\\[\\/uid\\]", "$2");
        ret = ret.replaceAll(ignoreCaseTag
                        + "\\[hip\\](.+?)\\[\\/hip\\]",
                "$1");
        ret = ret.replaceAll(ignoreCaseTag + "\\[tid=?(\\d{0,50})\\](.+?)\\[/tid\\]",
                "<a href='" + Utils.getNGAHost() + "read.php?tid=$1' style='font-weight: bold;'>[$2]</a>");
        ret = ret.replaceAll(
                ignoreCaseTag
                        + "\\[pid=(.+?)\\]\\[/pid\\]",
                "<a href='" + Utils.getNGAHost() + "read.php?pid=$1' style='font-weight: bold;'>[Reply]</a>");
        ret = ret.replaceAll(
                ignoreCaseTag
                        + "\\[pid=(.+?)\\](.+?)\\[/pid\\]",
                "<a href='" + Utils.getNGAHost() + "read.php?pid=$1' style='font-weight: bold;'>[$2]</a>");
        // flash
        ret = ret.replaceAll(
                ignoreCaseTag + "\\[flash\\](http[^\\[|\\]]+)\\[/flash\\]",
                "<a href=\"$1\"><img src='file:///android_asset/flash.png' style= 'max-width:100%;' ></a>");
        // color

        // ret = ret.replaceAll("\\[color=([^\\[|\\]]+)\\]\\s*(.+?)\\s*\\[/color\\]"
        // ,"<b style=\"color:$1\">$2</b>");
        ret = ret.replaceAll(ignoreCaseTag + "\\[color=([^\\[|\\]]+)\\]",
                styleColor);
        ret = ret.replaceAll(ignoreCaseTag + "\\[/color\\]", "</span>");

        // lessernuke
        ret = ret.replaceAll("\\[lessernuke\\]", lesserNukeStyle);
        ret = ret.replaceAll("\\[/lessernuke\\]", endDiv);

        ret = ret.replaceAll(
                "\\[table\\]",
                "<div><table cellspacing='0px' style='border:1px solid #aaa;width:99.9%;'><tbody>");
        ret = ret.replaceAll("\\[/table\\]", "</tbody></table></div>");
        ret = ret.replaceAll("\\[tr\\]", "<tr>");
        ret = ret.replaceAll("\\[/tr\\]", "<tr>");
        ret = ret.replaceAll(ignoreCaseTag
                        + "\\[td(\\d+)\\]",
                "<td style='width:$1%;border-left:1px solid #aaa;border-bottom:1px solid #aaa'>");
        ret = ret.replaceAll(ignoreCaseTag
                        + "\\[td\\scolspan(\\d+)\\swidth(\\d+)\\]",
                "<td colspan='$1' style='width:$2%;border-left:1px solid #aaa;border-bottom:1px solid #aaa'>");
        ret = ret.replaceAll(ignoreCaseTag
                        + "\\[td\\swidth(\\d+)\\scolspan(\\d+)\\]",
                "<td colspan='$2' style='width:$1%;border-left:1px solid #aaa;border-bottom:1px solid #aaa'>");

        ret = ret.replaceAll(ignoreCaseTag
                        + "\\[td\\swidth(\\d+)\\srowspan(\\d+)\\]",
                "<td rowspan='$2' style='width:$1%;border-left:1px solid #aaa;border-bottom:1px solid #aaa'>");
        ret = ret.replaceAll(ignoreCaseTag
                        + "\\[td\\srowspan(\\d+)\\swidth(\\d+)\\]",
                "<td rowspan='$1' style='width:$2%;border-left:1px solid #aaa;border-bottom:1px solid #aaa'>");

        ret = ret.replaceAll(ignoreCaseTag
                        + "\\[td\\scolspan(\\d+)\\srowspan(\\d+)\\swidth(\\d+)\\]",
                "<td colspan='$1' rowspan='$2' style='width:$3%;border-left:1px solid #aaa;border-bottom:1px solid #aaa'>");
        ret = ret.replaceAll(ignoreCaseTag
                        + "\\[td\\scolspan(\\d+)\\swidth(\\d+)\\srowspan(\\d+)\\]",
                "<td colspan='$1' rowspan='$3' style='width:$2%;border-left:1px solid #aaa;border-bottom:1px solid #aaa'>");
        ret = ret.replaceAll(ignoreCaseTag
                        + "\\[td\\srowspan(\\d+)\\scolspan(\\d+)\\swidth(\\d+)\\]",
                "<td rowspan='$1' colspan='$2' style='width:$3%;border-left:1px solid #aaa;border-bottom:1px solid #aaa'>");
        ret = ret.replaceAll(ignoreCaseTag
                        + "\\[td\\srowspan(\\d+)\\swidth(\\d+)\\scolspan(\\d+)\\]",
                "<td rowspan='$1' colspan='$3' style='width:$2%;border-left:1px solid #aaa;border-bottom:1px solid #aaa'>");
        ret = ret.replaceAll(ignoreCaseTag
                        + "\\[td\\swidth(\\d+)\\scolspan(\\d+)\\srowspan(\\d+)\\]",
                "<td rowspan='$3' colspan='$2' style='width:$1%;border-left:1px solid #aaa;border-bottom:1px solid #aaa'>");
        ret = ret.replaceAll(ignoreCaseTag
                        + "\\[td\\swidth(\\d+)\\srowspan(\\d+)\\scolspan(\\d+)\\]",
                "<td rowspan='$2' colspan='$3'  style='width:$1%;border-left:1px solid #aaa;border-bottom:1px solid #aaa'>");


        ret = ret.replaceAll(ignoreCaseTag
                        + "\\[td\\scolspan=(\\d+)\\]",
                "<td colspan='$1' style='border-left:1px solid #aaa;border-bottom:1px solid #aaa'>");
        ret = ret.replaceAll(ignoreCaseTag
                        + "\\[td\\srowspan=(\\d+)\\]",
                "<td rowspan='$1' style='border-left:1px solid #aaa;border-bottom:1px solid #aaa;'>");
        ret = ret.replaceAll("\\[td\\]", "<td style='border-left:1px solid #aaa;border-bottom:1px solid #aaa;'>");
        ret = ret.replaceAll("\\[/td\\]", "<td>");
        // [i][/i]
        ret = ret.replaceAll(ignoreCaseTag + "\\[i\\]",
                "<i style=\"font-style:italic\">");
        ret = ret.replaceAll(ignoreCaseTag + "\\[/i\\]", "</i>");
        // [del][/del]
        ret = ret.replaceAll(ignoreCaseTag + "\\[del\\]", "<del class=\"gray\">");
        ret = ret.replaceAll(ignoreCaseTag + "\\[/del\\]", "</del>");

        ret = ret.replaceAll(ignoreCaseTag + "\\[font=([^\\[|\\]]+)\\]",
                "<span style=\"font-family:$1\">");
        ret = ret.replaceAll(ignoreCaseTag + "\\[/font\\]", "</span>");

        // size
        ret = ret.replaceAll(ignoreCaseTag + "\\[size=(\\d+)%\\]",
                "<span style=\"font-size:$1%;line-height:$1%\">");
        ret = ret.replaceAll(ignoreCaseTag + "\\[/size\\]", "</span>");

        // [img]./ddd.jpg[/img]
        // if(showImage){
        ret = ret.replaceAll(ignoreCaseTag
                        + "\\[img\\]\\s*\\.(/[^\\[|\\]]+)\\s*\\[/img\\]",
                "<a href='" + NgaImageHost.attachmentsPrefix()
                        + "$1'><img src='"
                        + NgaImageHost.attachmentsPrefix()
                        + "$1' style= 'max-width:100%' ></a>");
        ret = ret.replaceAll(ignoreCaseTag
                        + "\\[img\\]\\s*(http[^\\[|\\]]+)\\s*\\[/img\\]",
                "<a href='$1'><img src='$1' style= 'max-width:100%' ></a>");

        ret = ret.replaceAll(ignoreCaseTag
                        + "\\[list\\](.+?)\\[/list\\]",
                "<ul>$1</ul>");
        ret = ret.replaceAll(ignoreCaseTag
                        + "\\[\\*\\](.+?)<br/>",
                "<li>$1</li>");

        try {
            ret = buildImage(ret, showImage, imageUrls);
            ret = buildAudioHtml(ret);
            ret = buildVideoHtml(ret);
            ret = convertGifImage(ret);
        } catch (Exception e) {
        }
        return ret;
    }

    private static String buildImage(String content, boolean showImage, List<String> imageUrls) {
        Pattern p = Pattern
                .compile("<img src='(http\\S+)' style= 'max-width:100%' >");
        Matcher m = p.matcher(content);
        while (m.find()) {
            String s0 = m.group();
            String s1 = m.group(1);
            String path = EmoticonUtils.getPathByURI(s1);
            if (path != null) {

                String newImgBlock = "<img src='"
                        + "file:///android_asset/" + path
                        + "' style= 'max-width:100%' >";
                content = content.replace(s0, newImgBlock);
            } else if (!showImage) {
                path = "ic_offline_image.png";
                String newImgBlock = "<img src='"
                        + "file:///android_asset/" + path
                        + "' style= 'max-width:100%' >";
                content = content.replace(s0, newImgBlock);
            } else {

                String newImgBlock = "<img src='"
                        + s1
                        + "' style= 'max-width:100%' >";
                content = content.replace(s0, newImgBlock);
                // 判断放宽为「是附件路径」而非「是某个特定主机」：主机名现在随设置变，
                // 钉死任一主机都会让图集在换域名后漏收图。
                if (s1.contains("attachments/") && imageUrls != null) {
                    imageUrls.add(s1);
                }
            }
        }
        return content;
    }

    private static String convertGifImage(String content) {
        Pattern pattern = Pattern.compile("(http\\S+).gif.(.*?).jpg");
        Matcher matcher = pattern.matcher(content);
        while (matcher.find()) {
            String s = matcher.group(0);
            content = content.replaceAll(s, s.substring(0, s.indexOf(".gif") + 4));
        }
        return content;
    }

    private static String buildEmoticonImage(String content) {
        for (int c = 0; c < EmoticonUtils.EMOTICON_LABEL.length; c++) {
            String category = EmoticonUtils.EMOTICON_LABEL[c][0];
            String size = "ng".equals(category) || "pg".equals(category)
                    ? " width=60 height=60" : "";
            for (String[] emoticon : EmoticonUtils.EMOTICON_URL[c]) {
                String code = "[s:" + category + ":" + emoticon[0] + "]";
                String path = EmoticonUtils.resolveAssetPath(category, emoticon[0]);
                String html = "<img src='file:///android_asset/" + path + "'" + size + ">";
                content = content.replaceAll(ignoreCaseTag + Pattern.quote(code),
                        Matcher.quoteReplacement(html));
            }
        }
        return content;
    }

    private static String buildAudioHtml(String content) {
        String regex = "\\[flash=audio](.*?)\\[/flash]";
        Pattern pattern = Pattern.compile(regex);
        Matcher matcher = pattern.matcher(content);
        while (matcher.find()) {
            String audioUrl = matcher.group();
            audioUrl = audioUrl.substring(14, audioUrl.indexOf("[/flash]") - 1);
            // <audio src="http://img.ngacn.cc/attachments/mon_201802/25/-7Q5-ak1cKe.mp3?duration=3&filename=nga_audio.mp3" controls="controls"></audio>
            audioUrl = "<audio src=\"" + NgaImageHost.attachmentsPrefix() + audioUrl + "&filename=nga_audio.mp3\" controls=\"controls\"></audio>";
            content = matcher.replaceFirst(audioUrl);
            matcher = pattern.matcher(content);
        }
        return content;
    }

    private static String buildVideoHtml(String content) {
        String regex = "\\[flash=video](.*?)\\[/flash]";
        Pattern pattern = Pattern.compile(regex);
        Matcher matcher = pattern.matcher(content);
        while (matcher.find()) {
            String url = matcher.group();
            url = url.substring("[flash=video]".length() + 1, url.indexOf("[/flash]"));
            url = "<video src=\"" + NgaImageHost.attachmentsPrefix() + url + "\" controls=\"controls\"></video>";
            content = matcher.replaceFirst(url);
            matcher = pattern.matcher(content);
        }
        return content;
    }

    public static String removeBrTag(String s) {
        s = s.replaceAll("<br/><br/>", "\n");
        s = s.replaceAll("<br/>", "\n");
        return s;
    }

    public static String unEscapeHtml(String s) {
        return StringHelper.unescapeHTML(s);
    }

    public static StringFindResult getStringBetween(String data, int begPosition, String startStr, String endStr) {
        StringFindResult ret = new StringFindResult();
        do {
            if (isEmpty(data) || begPosition < 0
                    || data.length() <= begPosition || isEmpty(startStr)
                    || isEmpty(startStr))
                break;

            int start = data.indexOf(startStr, begPosition);
            if (start == -1)
                break;

            start += startStr.length();
            int end = data.indexOf(endStr, start);
            if (end == -1)
                end = data.length();
            ret.result = data.substring(start, end);
            ret.position = end + endStr.length();

        } while (false);

        return ret;
    }

    public static String toBinaryArray(byte[] bytes) {
        StringBuilder builder = new StringBuilder(bytes.length * Byte.SIZE);
        for (int i = 0; i < Byte.SIZE * bytes.length; i++) {
            builder.append((bytes[i / Byte.SIZE] << i % Byte.SIZE & 0x80) == 0 ? '0' : '1');
        }
        return builder.toString();
    }

    public static int getUrlParameter(String url, String paraName) {
        if (StringUtils.isEmpty(url)) {
            return 0;
        }
        final String pattern = paraName + "=";
        int start = url.indexOf(pattern);
        if (start == -1)
            return 0;
        start += pattern.length();
        int end = url.indexOf("&", start);
        if (end == -1)
            end = url.length();
        String value = url.substring(start, end);
        int ret = 0;
        try {
            ret = Integer.parseInt(value);
        } catch (Exception e) {
            NLog.e("getUrlParameter", "invalid url:" + url);
        }

        return ret;
    }

    public static String timeStamp2Date1(String timeStamp) {
        return timeStamp2Date(timeStamp, "yyyy-MM-dd HH:mm:ss");
    }

    public static String timeStamp2Date2(String timeStamp) {
        return timeStamp2Date(timeStamp, "MM-dd HH:mm");
    }

    public static String timeStamp2Date(String timeStamp, String format) {
        Calendar calendar = Calendar.getInstance();
        calendar.setTimeInMillis(Long.parseLong(timeStamp) * 1000);
        return new SimpleDateFormat(format, Locale.getDefault()).format(calendar.getTime());
    }

    public static String getStringFromAssets(String path) {
        AssetManager assetManager = ContextUtils.getContext().getAssets();
        try (InputStream is = assetManager.open(path)) {
            int length = is.available();
            byte[] buffer = new byte[length];
            is.read(buffer);
            return new String(buffer, "utf-8");
        } catch (IOException e) {
            e.printStackTrace();
            return "";
        }
    }

}
