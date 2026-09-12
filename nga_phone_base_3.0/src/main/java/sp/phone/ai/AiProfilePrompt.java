package sp.phone.ai;

/** One profile-prompt choice. Custom text is kept verbatim even while a preset is selected. */
public final class AiProfilePrompt {
    public static final int MAX_CUSTOM_PROMPT_CHARS = 8192;

    public enum Style {
        FORUM_ROAST("forum_roast"), DETAILED("detailed"), CUSTOM("custom");

        private final String id;

        Style(String id) {
            this.id = id;
        }

        String getId() {
            return id;
        }

        static Style fromId(String id) {
            for (Style style : values()) {
                if (style.id.equals(id)) {
                    return style;
                }
            }
            throw new IllegalArgumentException("请选择有效的查成分提示词风格");
        }
    }

    public static final AiProfilePrompt DEFAULT = new AiProfilePrompt(Style.FORUM_ROAST, "");

    private static final String FORUM_ROAST_INSTRUCTIONS =
            "采用论坛锐评风格，按画像、标签、一句锐评三个短标签输出。"
            + "画像用 1 至 2 句点明样本支持的兴趣和发言习惯；标签给 2 至 3 个有依据的兴趣或表达标签，证据少可更少。"
            + "最后用一句辛辣锐评收尾，槽点须落在样本中的措辞或论证上。"
            + "口吻像看完帖子的论坛老哥：直白、利落、带梗，可以用反问、短比喻和反差制造笑点。"
            + "没有有据可查的槽点就直说，不硬凑笑话。"
            + "只输出简洁中文纯文本，尽量控制在 200 至 350 字，回复正文在1000字以内。"
            + "不用 BBCode、Markdown 表格或代码块，不加冗长开场。";

    private static final String DETAILED_INSTRUCTIONS =
            "采用中性、清晰的详细分析风格，客观归纳，不使用嘲讽或毒舌口吻。"
            + "只输出简洁中文纯文本，依次用兴趣关注、主要观点、发言风格、成分总结、标签五个短标签分段。"
            + "前三项各最多两条短观察，分别说明关注的话题、明确表达的观点和可观察的表达习惯。"
            + "成分总结用 1 至 2 句概括前述有据观察；标签给 3 至 5 个有依据的兴趣或表达标签，证据少可更少，不凑数。"
            + "回复正文在1000字以内。不用 BBCode、Markdown 表格或代码块，不加冗长开场。";

    private final Style style;
    private final String customText;

    public AiProfilePrompt(Style style, String customText) {
        if (style == null) {
            throw new IllegalArgumentException("请选择有效的查成分提示词风格");
        }
        if (customText == null) {
            throw new IllegalArgumentException("请输入自定义提示词");
        }
        if (customText.length() > MAX_CUSTOM_PROMPT_CHARS) {
            throw new IllegalArgumentException("自定义提示词不能超过 8192 个字符");
        }
        if (style == Style.CUSTOM && isBlank(customText)) {
            throw new IllegalArgumentException("请输入自定义提示词");
        }
        this.style = style;
        this.customText = customText;
    }

    public Style getStyle() {
        return style;
    }

    public String getCustomText() {
        return customText;
    }

    /** Style/format only; ProfileSummaryInput owns the shared evidence and data boundary. */
    public String getInstructions() {
        switch (style) {
            case FORUM_ROAST:
                return FORUM_ROAST_INSTRUCTIONS;
            case DETAILED:
                return DETAILED_INSTRUCTIONS;
            case CUSTOM:
                return customText;
            default:
                throw new IllegalStateException("Invalid profile prompt style");
        }
    }

    private static boolean isBlank(String text) {
        for (int offset = 0; offset < text.length();) {
            int codePoint = text.codePointAt(offset);
            if (!Character.isWhitespace(codePoint) && !Character.isSpaceChar(codePoint)) {
                return false;
            }
            offset += Character.charCount(codePoint);
        }
        return true;
    }

    @Override
    public boolean equals(Object other) {
        if (!(other instanceof AiProfilePrompt)) {
            return false;
        }
        AiProfilePrompt prompt = (AiProfilePrompt) other;
        return style == prompt.style && customText.equals(prompt.customText);
    }

    @Override
    public int hashCode() {
        return 31 * style.hashCode() + customText.hashCode();
    }

    @Override
    public String toString() {
        return "AiProfilePrompt{" + style + "}";
    }
}
