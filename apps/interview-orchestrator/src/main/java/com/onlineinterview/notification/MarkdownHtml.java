package com.onlineinterview.notification;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Minimal, safe Markdown -> HTML converter for email bodies. Handles the subset the
 *  coaching agent emits: ## / ### headings, - / * bullets, **bold**, [text](url) links,
 *  and blank-line paragraphs. Escapes HTML first so candidate/AI text cannot inject markup. */
final class MarkdownHtml {
    private static final Pattern INLINE =
            Pattern.compile("\\*\\*([^*]+)\\*\\*|\\[([^\\]]+)\\]\\((https?://[^)\\s]+)\\)");

    private MarkdownHtml() {}

    static String render(String markdown) {
        if (markdown == null || markdown.isBlank()) return "";
        var out = new StringBuilder();
        String[] lines = markdown.replace("\r\n", "\n").split("\n");
        boolean inList = false;
        var paragraph = new StringBuilder();
        for (String raw : lines) {
            String line = raw.strip();
            boolean bullet = line.startsWith("- ") || line.startsWith("* ");
            boolean heading = line.startsWith("#");
            if ((heading || bullet || line.isEmpty()) && paragraph.length() > 0) {
                out.append("<p>").append(inline(paragraph.toString())).append("</p>");
                paragraph.setLength(0);
            }
            if (bullet) {
                if (!inList) { out.append("<ul>"); inList = true; }
                out.append("<li>").append(inline(line.substring(2).strip())).append("</li>");
                continue;
            }
            if (inList) { out.append("</ul>"); inList = false; }
            if (line.isEmpty()) continue;
            if (heading) {
                int level = 0;
                while (level < line.length() && line.charAt(level) == '#') level++;
                String text = line.substring(level).strip();
                String tag = level <= 2 ? "h3" : "h4";
                out.append('<').append(tag).append('>').append(inline(text))
                        .append("</").append(tag).append('>');
                continue;
            }
            if (paragraph.length() > 0) paragraph.append(' ');
            paragraph.append(line);
        }
        if (paragraph.length() > 0) {
            out.append("<p>").append(inline(paragraph.toString())).append("</p>");
        }
        if (inList) out.append("</ul>");
        return out.toString();
    }

    private static String inline(String text) {
        String escaped = escape(text);
        Matcher matcher = INLINE.matcher(escaped);
        var sb = new StringBuilder();
        while (matcher.find()) {
            String replacement;
            if (matcher.group(1) != null) {
                replacement = "<strong>" + matcher.group(1) + "</strong>";
            } else {
                replacement = "<a href=\"" + matcher.group(3) + "\">" + matcher.group(2) + "</a>";
            }
            matcher.appendReplacement(sb, Matcher.quoteReplacement(replacement));
        }
        matcher.appendTail(sb);
        return sb.toString();
    }

    static String escape(String text) {
        return text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }
}
