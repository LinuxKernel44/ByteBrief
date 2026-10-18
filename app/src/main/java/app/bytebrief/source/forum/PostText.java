package app.bytebrief.source.forum;

import org.jsoup.nodes.Element;
import org.jsoup.nodes.Node;
import org.jsoup.nodes.TextNode;

/**
 * Turns a post body into the small Markdown subset the reader draws (paragraphs, quotes, code,
 * bullets, bold). Scripts, styles, embeds and form elements are dropped; images become a short note,
 * and quoted posts are shortened so a long thread does not repeat itself.
 */
final class PostText {
    private static final int MAX_QUOTE_CHARS = 280;

    private PostText() {}

    static String render(Element body) {
        StringBuilder out = new StringBuilder();
        walk(body, out);
        return tidy(out);
    }

    private static String tidy(StringBuilder sb) {
        return sb.toString().replaceAll("[ \\t]+\\n", "\n").replaceAll("\\n{3,}", "\n\n").trim();
    }

    private static void walk(Node node, StringBuilder out) {
        if (node instanceof TextNode) {
            String t = ((TextNode) node).text().replace(' ', ' ').replaceAll("\\s+", " ");
            if (atLineStart(out)) t = t.stripLeading();
            out.append(t);
            return;
        }
        if (!(node instanceof Element)) return;
        Element e = (Element) node;
        String tag = e.normalName();
        switch (tag) {
            case "script": case "style": case "noscript": case "iframe": case "object": case "embed": case "svg":
            case "form": case "input": case "button": case "textarea": case "select":
                return;
            case "br":
                trimTrailingSpaces(out);
                out.append('\n');
                return;
            case "img": {
                String alt = e.attr("alt").trim();
                if (e.hasClass("smilie") || e.hasClass("mceSmilie")) {
                    out.append(alt);
                } else {
                    ensureBlank(out);
                    out.append("[image").append(alt.isEmpty() ? "" : ": " + alt).append(']');
                    ensureBlank(out);
                }
                return;
            }
            case "pre":
                fence(e.wholeText(), out);
                return;
            case "li":
                ensureNewline(out);
                out.append("- ");
                children(e, out);
                ensureNewline(out);
                return;
            case "b": case "strong":
                out.append("**");
                children(e, out);
                out.append("**");
                return;
            default:
                break;
        }
        if (e.hasClass("bbCodeBlock-expandLink") || e.hasClass("bbCodeBlock-shadow") || e.hasClass("bbCodeBlock-title")) return;
        if (e.hasClass("bbCodeBlock--quote") || tag.equals("blockquote")) {
            quote(e, out);
            return;
        }
        if (e.hasClass("bbCodeBlock--code")) {
            Element code = e.selectFirst("pre, code");
            fence(code != null ? code.wholeText() : e.text(), out);
            return;
        }
        if (e.hasClass("bbCodeBlock--unfurl") || e.hasClass("bbMediaWrapper")) {
            Element a = e.selectFirst("a[href]");
            if (a != null) {
                String href = a.absUrl("href");
                String label = a.text().trim();
                ensureBlank(out);
                out.append("Link: ").append(label.isEmpty() ? href : label);
                if (!href.isEmpty() && !href.equals(label)) out.append(" (").append(href).append(')');
                ensureBlank(out);
            }
            return;
        }
        if (tag.equals("a")) {
            String href = e.absUrl("href");
            String text = e.text().trim();
            children(e, out);
            boolean member = href.contains("/members/") || href.contains("/m/");
            if (href.startsWith("http") && !text.equals(href) && !text.isEmpty() && !text.startsWith("@") && !member) {
                out.append(" (").append(href).append(')');
            }
            return;
        }
        boolean block = isBlock(tag);
        if (block) ensureBlank(out);
        children(e, out);
        if (block) ensureBlank(out);
    }

    private static void quote(Element e, StringBuilder out) {
        String who = e.attr("data-quote").trim();
        Element content = e.selectFirst(".bbCodeBlock-expandContent, .bbCodeBlock-content");
        StringBuilder inner = new StringBuilder();
        walk(content != null ? content : e, inner);
        String text = tidy(inner);
        if (text.length() > MAX_QUOTE_CHARS) {
            int cut = text.lastIndexOf(' ', MAX_QUOTE_CHARS);
            text = text.substring(0, cut > MAX_QUOTE_CHARS / 2 ? cut : MAX_QUOTE_CHARS).trim() + "…";
        }
        ensureBlank(out);
        if (!who.isEmpty()) out.append("> ").append(who).append(" said:\n");
        for (String line : text.split("\n", -1)) out.append("> ").append(line).append('\n');
        ensureBlank(out);
    }

    private static void fence(String code, StringBuilder out) {
        ensureBlank(out);
        out.append("```\n").append(code.replaceAll("\\s+$", "")).append("\n```");
        ensureBlank(out);
    }

    private static void children(Element e, StringBuilder out) {
        for (Node c : e.childNodes()) walk(c, out);
    }

    private static boolean isBlock(String tag) {
        switch (tag) {
            case "p": case "div": case "ul": case "ol": case "h1": case "h2": case "h3": case "h4": case "h5": case "h6":
            case "table": case "tr": case "section": case "article": case "header": case "dl":
                return true;
            default:
                return false;
        }
    }

    private static boolean atLineStart(StringBuilder out) {
        return out.length() == 0 || out.charAt(out.length() - 1) == '\n';
    }

    private static void trimTrailingSpaces(StringBuilder out) {
        int n = out.length();
        while (n > 0 && out.charAt(n - 1) == ' ') n--;
        out.setLength(n);
    }

    private static void ensureNewline(StringBuilder out) {
        trimTrailingSpaces(out);
        if (out.length() > 0 && out.charAt(out.length() - 1) != '\n') out.append('\n');
    }

    /** Makes the output end with an empty line, unless nothing has been written yet. */
    private static void ensureBlank(StringBuilder out) {
        if (out.length() == 0) return;
        ensureNewline(out);
        int n = out.length();
        if (n < 2 || out.charAt(n - 2) != '\n') out.append('\n');
    }
}
