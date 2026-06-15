package com.example.demo.post;

import java.util.regex.Pattern;

public final class PostContentText {
    private static final Pattern LINE_BREAK_TAG_PATTERN = Pattern.compile("<br(?:\\s[^>]*)?\\s*/?>", Pattern.CASE_INSENSITIVE);
    private static final Pattern HORIZONTAL_RULE_TAG_PATTERN = Pattern.compile("<hr(?:\\s[^>]*)?\\s*/?>", Pattern.CASE_INSENSITIVE);
    private static final Pattern BLOCK_TAG_PATTERN = Pattern.compile("</?(?:p|div|h[1-6])(?:\\s[^>]*)?>", Pattern.CASE_INSENSITIVE);
    private static final Pattern MARKDOWN_IMAGE_PATTERN = Pattern.compile("!\\[[^\\]]*]\\([^\\)]*\\)");
    private static final Pattern MARKDOWN_LINK_PATTERN = Pattern.compile("\\[([^\\]]+)]\\(([^\\)]*)\\)");

    private PostContentText() {
    }

    public static String createExcerpt(String content) {
        String normalized = toPlainText(content);
        return normalized.length() > 150 ? normalized.substring(0, 150) : normalized;
    }

    public static String toPlainText(String content) {
        String normalized = content == null ? "" : content;
        normalized = LINE_BREAK_TAG_PATTERN.matcher(normalized).replaceAll("\n");
        normalized = HORIZONTAL_RULE_TAG_PATTERN.matcher(normalized).replaceAll("\n");
        normalized = BLOCK_TAG_PATTERN.matcher(normalized).replaceAll(" ");
        normalized = MARKDOWN_IMAGE_PATTERN.matcher(normalized).replaceAll(" ");
        normalized = MARKDOWN_LINK_PATTERN.matcher(normalized).replaceAll("$1");
        return normalized.replaceAll("\\s+", " ").trim();
    }
}
