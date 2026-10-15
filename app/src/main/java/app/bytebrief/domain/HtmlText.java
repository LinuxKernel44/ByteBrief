package app.bytebrief.domain;

import androidx.annotation.Nullable;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;

/** Converts untrusted HTML into plain text. Nothing is ever executed or rendered as markup. */
public final class HtmlText {
    private HtmlText() {}

    /** Plain text with paragraph breaks preserved; null/blank input yields an empty string. */
    public static String toPlainText(@Nullable String html) {
        if (html == null || html.trim().isEmpty()) return "";
        Document doc = Jsoup.parse(html);
        doc.select("script, style, noscript, template, iframe, object, embed, svg, nav, footer").remove();
        doc.select("br").after("\\n");
        doc.select("p, div, li, h1, h2, h3, h4, h5, h6, tr, pre, blockquote").before("\\n\\n");
        String text = doc.text().replace("\\n", "\n");
        return text.replaceAll("[ \\t]*\\n[ \\t]*", "\n").replaceAll("\\n{3,}", "\n\n").trim();
    }

    /** Truncates at a word boundary, appending an ellipsis. */
    public static String truncate(String text, int maxChars) {
        if (text.length() <= maxChars) return text;
        int cut = text.lastIndexOf(' ', maxChars);
        if (cut < maxChars / 2) cut = maxChars;
        return text.substring(0, cut).trim() + "…";
    }
}
