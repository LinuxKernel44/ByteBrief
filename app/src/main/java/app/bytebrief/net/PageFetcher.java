package app.bytebrief.net;

/** Loads a web page the way a browser does, including any scripts a site needs to run before it shows content. */
public interface PageFetcher {
    /** A loaded page. {@link #html} is the rendered document, not the raw response. */
    final class Page {
        public final String html;
        public final String finalUrl;

        public Page(String html, String finalUrl) {
            this.html = html;
            this.finalUrl = finalUrl;
        }
    }

    /**
     * Blocks until the page is ready. Never call from the main thread.
     *
     * @throws SourceException of kind {@link SourceException.Kind#VERIFICATION_REQUIRED} when the site is
     *                         asking the user to prove they are human
     */
    Page load(String url) throws SourceException;
}
