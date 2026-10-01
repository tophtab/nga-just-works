package sp.phone.common;

public interface ApiConstants {

    /**
     * 板块图标。经 Coil 直接加载，不走解码链，因此 {@code NgaImageHost.normalizeLegacyHosts} 碰不到它，
     * 必须写对域名。
     *
     * <p>路径族在 {@code img4} 上（同表情），不是 {@code /attachments/}，故不能用附件主机——
     * 见 {@code NgaImageHost} 类注释。2026-08-06 实测 http/https 均 200。
     */
    String URL_BOARD_ICON = "https://img4.nga.cn/ngabbs/nga_classic/f/app/%s.png";

    /**
     * 合集板块（stid）图标，同样经 Coil 直接加载。
     *
     * <p>2026-08-06 实测 200（取 {@code assets/board_list.json} 里的真实 stid 复测五个，
     * 返回体 2.4–3.1KB 的 PNG，各不相同）。
     *
     * <p>⚠️ 验证这条时别拿 1、2、100 之类的小数字当 stid——真实 stid 是 8 位数，
     * 小数字一律 404（那是「无此合集」，不是「路径已废」），足以把人误导成域名换了也没用。
     */
    String URL_BOARD_ICON_STID = "https://img4.nga.cn/proxy/cache_attach/ficon/%sv.png";

    int NGA_NOTIFICATION_TYPE_TOPIC_REPLY = 1;

    int NGA_NOTIFICATION_TYPE_REPLY_REPLY = 2;

    int NGA_NOTIFICATION_TYPE_TOPIC_COMMENT = 3;

    int NGA_NOTIFICATION_TYPE_REPLY_COMMENT = 4;

    int NGA_NOTIFICATION_TYPE_TOPIC_AT = 7;

    int NGA_NOTIFICATION_TYPE_REPLY_AT = 8;

    int NGA_NOTIFICATION_TYPE_NEW_MESSAGE = 10;

    int NGA_NOTIFICATION_TYPE_MESSAGE_REPLY = 11;

    int MASK_FONT_RED = 1;

    int MASK_FONT_BLUE = 2;

    int MASK_FONT_GREEN = 4;

    int MASK_FONT_ORANGE = 8;

    int MASK_FONT_SILVER = 16;

    int MASK_FONT_BOLD = 32;

    int MASK_FONT_ITALIC = 64;

    int MASK_FONT_UNDERLINE = 128;

    // 主题被锁定 2^10
    int MASK_TYPE_LOCK = 1024;

    // 主题中有附件 2^13
    int MASK_TYPE_ATTACHMENT = 8192;

    // 合集 2^15
    int MASK_TYPE_ASSEMBLE = 32768;


}
