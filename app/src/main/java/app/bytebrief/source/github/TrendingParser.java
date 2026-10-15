package app.bytebrief.source.github;

import androidx.annotation.Nullable;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Adapter for the HTML of https://github.com/trending. GitHub offers no documented API for this
 * list, so all markup knowledge lives here. Anything that cannot be read is left empty or zero
 * rather than guessed, and entries without a valid owner/repo are dropped.
 */
public final class TrendingParser {
    /** One row of the trending page. Numbers are 0 when the page did not provide them. */
    public static final class Repo {
        public final String owner;
        public final String name;
        @Nullable
        public final String description;
        @Nullable
        public final String language;
        public final long stars;
        public final long forks;
        /** Stars gained in the period shown by the page, or -1 when not shown. */
        public final long starsInPeriod;

        Repo(String owner, String name, @Nullable String description, @Nullable String language, long stars,
             long forks, long starsInPeriod) {
            this.owner = owner;
            this.name = name;
            this.description = description;
            this.language = language;
            this.stars = stars;
            this.forks = forks;
            this.starsInPeriod = starsInPeriod;
        }
    }

    private static final Pattern REPO_PATH = Pattern.compile("^/([A-Za-z0-9_.-]{1,100})/([A-Za-z0-9_.-]{1,100})$");
    private static final Pattern PERIOD_STARS = Pattern.compile("([\\d,.]+)\\s*([kK])?\\s+stars?\\s+(today|this week|this month)");

    private TrendingParser() {}

    public static List<Repo> parse(String html) {
        List<Repo> repos = new ArrayList<>();
        Document doc = Jsoup.parse(html);
        for (Element row : doc.select("article.Box-row")) {
            Element link = row.selectFirst("h2 a[href]");
            if (link == null) continue;
            Matcher m = REPO_PATH.matcher(link.attr("href").trim());
            if (!m.matches()) continue;

            Element desc = row.selectFirst("p");
            Element lang = row.selectFirst("[itemprop=programmingLanguage]");
            Element stars = row.selectFirst("a[href$=/stargazers]");
            Element forks = row.selectFirst("a[href$=/forks]");

            long period = -1;
            Matcher pm = PERIOD_STARS.matcher(row.text());
            if (pm.find()) period = parseCount(pm.group(1), pm.group(2));

            repos.add(new Repo(m.group(1), m.group(2),
                    desc == null || desc.text().trim().isEmpty() ? null : desc.text().trim(),
                    lang == null || lang.text().trim().isEmpty() ? null : lang.text().trim(),
                    stars == null ? 0 : parseCount(stars.text(), null),
                    forks == null ? 0 : parseCount(forks.text(), null),
                    period));
        }
        return repos;
    }

    static long parseCount(String text, @Nullable String suffix) {
        String digits = text.replaceAll("[^0-9.,]", "").replace(",", "");
        if (digits.isEmpty()) return 0;
        try {
            double v = Double.parseDouble(digits);
            if (suffix != null && suffix.equalsIgnoreCase("k")) v *= 1000;
            return Math.round(v);
        } catch (NumberFormatException e) {
            return 0;
        }
    }
}
