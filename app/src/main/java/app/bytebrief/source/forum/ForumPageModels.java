package app.bytebrief.source.forum;

import androidx.annotation.Nullable;

import java.util.ArrayList;
import java.util.List;

/** What the XenForo parser extracts from a page. Plain data, no behaviour. */
public final class ForumPageModels {
    private ForumPageModels() {}

    public static final class ThreadSummary {
        public long threadId;
        public String title = "";
        public String url = "";
        @Nullable public String author;
        @Nullable public String prefix;
        public long startedAt;
        public long lastPostAt;
        public int replies;
        public long views;
        public boolean sticky;
    }

    public static final class ThreadList {
        @Nullable public String title;
        public final List<ThreadSummary> threads = new ArrayList<>();
        @Nullable public String nextUrl;
    }

    public static final class Post {
        public long id;
        @Nullable public String author;
        public long postedAt;
        public String text = "";
        public final List<String> attachments = new ArrayList<>();
    }

    public static final class ThreadPage {
        @Nullable public String title;
        public final List<Post> posts = new ArrayList<>();
        @Nullable public String nextUrl;
    }

    public static final class Node {
        public String title = "";
        public String url = "";
        public boolean category;
        @Nullable public String description;
    }

    public static final class NodeList {
        @Nullable public String title;
        public final List<Node> nodes = new ArrayList<>();
        /** True when the page lists threads, so this node can be followed. */
        public boolean hasThreads;
    }
}
