package app.bytebrief.source.forum;

/** Small hand-written pages that follow the XenForo 2 markup XDA Forums uses. */
final class ForumFixtures {
    private ForumFixtures() {}

    static final String BASE = "https://xdaforums.com/f/oneplus-15.12971/";

    private static String page(String template, String title, String body) {
        return "<html id=\"XF\" data-app=\"public\" data-template=\"" + template + "\"><head><title>" + title + " | XDA Forums</title></head>"
                + "<body><div class=\"p-body\"><div class=\"p-body-inner\">" + body + "</div></div></body></html>";
    }

    static String threadRow(long id, String slug, String prefix, String title, String author, long started, long last, String replies, String views, boolean sticky) {
        return "<div class=\"structItem structItem--thread js-threadListItem-" + id + "\" data-author=\"" + author + "\">"
                + "<div class=\"structItem-cell structItem-cell--main\">"
                + (sticky ? "<ul class=\"structItem-statuses\"><li><i class=\"structItem-status structItem-status--sticky\"></i></li></ul>" : "")
                + "<div class=\"structItem-title\">"
                + (prefix == null ? "" : "<a class=\"labelLink\" href=\"/f/x/?prefix_id=1\"><span class=\"label\">" + prefix + "</span></a>")
                + "<a href=\"/t/" + slug + "." + id + "/\" data-tp-primary=\"on\">" + title + "</a></div>"
                + "<div class=\"structItem-minor\"><ul class=\"structItem-parts\"><li><a class=\"username\">" + author + "</a></li>"
                + "<li class=\"structItem-startDate\"><a href=\"/t/" + slug + "." + id + "/\"><time class=\"u-dt\" data-time=\"" + started + "\">x</time></a></li></ul></div></div>"
                + "<div class=\"structItem-cell structItem-cell--meta\"><div><dl class=\"pairs\"><dt>Replies</dt><dd>" + replies + "</dd></dl></div>"
                + "<div><dl class=\"pairs\"><dt>Views</dt><dd>" + views + "</dd></dl></div></div>"
                + "<div class=\"structItem-cell structItem-cell--latest\"><a href=\"/t/" + slug + "." + id + "/latest\"><time class=\"structItem-latestDate u-dt\" data-time=\"" + last + "\">x</time></a></div>"
                + "</div>";
    }

    static String forumPage(String title, String rows, String nextHref) {
        String nav = nextHref == null ? "" : "<nav class=\"pageNavWrapper\"><div class=\"pageNav\"><a href=\"" + nextHref + "\" class=\"pageNav-jump pageNav-jump--next\">Next</a></div></nav>";
        return page("forum_view", title, "<h1 class=\"p-title-value\">" + title + "</h1><div class=\"structItemContainer\">" + rows + "</div>" + nav);
    }

    static String post(long id, String author, long time, String bodyHtml, String extra) {
        return "<article class=\"message message--post js-post\" data-author=\"" + author + "\" data-content=\"post-" + id + "\">"
                + "<div class=\"message-cell message-cell--main\"><header class=\"message-attribution\"><ul><li><a href=\"#\"><time class=\"u-dt\" data-time=\"" + time + "\">x</time></a></li></ul></header>"
                + "<div class=\"message-content\"><div class=\"message-userContent\"><article class=\"message-body js-selectToQuote\"><div class=\"bbWrapper\">" + bodyHtml + "</div></article></div>"
                + extra + "</div></div></article>";
    }

    static String threadPage(String title, String prefix, String posts, String nextHref) {
        String nav = nextHref == null ? "" : "<a href=\"" + nextHref + "\" class=\"pageNav-jump pageNav-jump--next\">Next</a>";
        return page("thread_view", title, "<h1 class=\"p-title-value\">" + (prefix == null ? "" : "<a class=\"labelLink\"><span class=\"label\">" + prefix + "</span></a>") + title + "</h1>" + posts + nav);
    }

    static String nodePage(String title, String nodes) {
        return page("category_view", title, "<h1 class=\"p-title-value\">" + title + "</h1>" + nodes);
    }

    static String node(String href, String title, String description) {
        return "<div class=\"node node--forum\"><div class=\"node-body\"><div class=\"node-main\"><h3 class=\"node-title\"><a href=\"" + href + "\">" + title
                + "</a></h3><div class=\"node-description\">" + description + "</div></div></div></div>";
    }

    static final String CHALLENGE = "<html><head><title>Just a moment...</title></head><body><h1>Verifying you are human. This may take a few seconds.</h1>"
            + "<p>This website uses a security service to protect against malicious bots.</p><script src=\"/cdn-cgi/challenge-platform/h/b/orchestrate\"></script>"
            + "<div id=\"cf-turnstile\"></div></body></html>";

    static final String ERROR_PAGE = "<html id=\"XF\" data-app=\"public\" data-template=\"error\"><head><title>Oops! | XDA Forums</title></head><body><div class=\"p-body\">Not found</div></body></html>";
}
