package app.bytebrief.testutil;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import app.bytebrief.domain.ItemFactory;
import app.bytebrief.net.HttpFetcher;

public final class Fixtures {
    /** 2026-10-15T12:00:00Z */
    public static final Clock CLOCK = Clock.fixed(Instant.parse("2026-10-15T12:00:00Z"), ZoneOffset.UTC);

    private Fixtures() {}

    public static HttpFetcher fetcher() {
        return new HttpFetcher(HttpFetcher.defaultClient(), CLOCK);
    }

    public static ItemFactory factory() {
        return new ItemFactory(CLOCK);
    }

    public static String resource(String path) {
        try (InputStream in = Fixtures.class.getClassLoader().getResourceAsStream(path)) {
            if (in == null) throw new IllegalArgumentException("Missing test resource " + path);
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buf = new byte[8192];
            for (int n; (n = in.read(buf)) > 0; ) out.write(buf, 0, n);
            return new String(out.toByteArray(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }
}
