# Architecture

ByteBrief is a single-activity Android app with a clear split between **collecting** remote content and
**understanding** it locally.

```
            ┌────────────────────────── Compose UI shell (Kotlin) ──────────────────────────┐
            │ theme · glass kit · components · screens          observe LiveData, call VMs   │
            └───────────────────────────────────────┬────────────────────────────────────────┘
                                                    │  Java ViewModels (state + logic)
        ┌───────────────────┬───────────────────────┼────────────────────┬────────────────────┐
        ▼                   ▼                       ▼                    ▼                    ▼
  ContentRepository   FeedRepository          AiService            BriefingService       ModelManager
  (read, search,      (feeds, OPML,        (cache by content    (deterministic pick,   (download state,
   rank, actions)      followed repos)      hash + model)        model writes text)     load/unload)
        │                   │                       │                    │                    │
        ▼                   ▼                       ▼                    ▼                    ▼
   Room database ◄──── Ingestor ◄──── Sources   LocalAiEngine ──► AiPipeline ──► llama.cpp (JNI)
   (FTS4, indexes)     (idempotent)   HN · Trending · RSS · Releases     │
        ▲                                                                ▼
        └──────── WorkManager: periodic sync · daily briefing · model download · article cache
```

## Layers

### Collection (no AI involved)

* `net/HttpFetcher` is the only network client for sources. It enforces an http(s) allow-list, a response size
  cap, refuses https→http redirects, and maps failures to typed `SourceException`s (rate-limited, retryable, …).
* `source/*` adapters turn each remote format into `ContentItem`s:
  * `hn/HackerNewsSource` — official Firebase API, item details fetched in parallel, already-known old stories are
    not requested again.
  * `github/TrendingParser` + `TrendingSource` — the **only** code that knows GitHub's trending markup. An empty
    result is an error. Enrichment (topics, license, README) goes through `GithubApi` and stops quietly when the
    unauthenticated quota runs low.
  * `github/ReleaseSource` — releases of followed repositories, conditional requests with ETags, drafts skipped,
    pre-releases optional.
  * `rss/FeedParser`, `OpmlCodec`, `FeedSource` — namespace-aware pull parser (RSS 2.0/1.0, Atom), no DTD or entity
    resolution, tolerant of BOMs and truncation, HTML feed auto-discovery, conditional refresh.
* `repo/Ingestor` writes items idempotently. An item is identified by `(source_type, source_id)`; re-ingesting keeps
  user state (read, bookmark, feedback), never replaces richer stored text with a poorer one, unions topics and
  metadata, and resets AI status only when the text hash changes.
* `repo/SyncManager` runs one pass over all enabled sources. Each source, feed and repository fails independently.

### Domain and storage

* `domain/UrlCanonicalizer` canonicalizes display URLs and computes `dedupKey` (scheme, `www.`, tracking
  parameters, fragments, trailing slashes and parameter order are ignored; GitHub repository URLs collapse to
  `github.com/owner/repo`). Releases keep their own key so they are not merged with the repository.
* One row per source item preserves source-specific data; rows sharing a `dedup_key` are *grouped* in queries and
  UI, never merged. Fuzzy title matching is deliberately not used.
* Room schema v1 is exported to `app/schemas`. Full-text search uses FTS4 because Room supports it on every
  Android SQLite build (the prefix operator goes inside the quotes: `"term*"`). There is no destructive migration
  fallback: a missing migration fails loudly rather than wiping user data.

### Local AI

* `llama/` wraps llama.cpp in a small JNI layer: handle-based (no global state), UTF-8 `byte[]` in and out (JNI
  "modified UTF-8" mishandles emoji), cooperative cancellation, optional GBNF grammar.
* `ai/LlamaAiEngine` implements `LocalAiEngine`. It reports **READY only after the model has loaded and completed a
  real one-token decode**. All native calls run on one dedicated thread.
* `ai/AiPipeline` is runtime-independent (it talks to a `TextGenerator` interface, which is how it is unit-tested
  against a scripted generator). It handles prompt construction, context budgeting and truncation, a map-reduce
  pass for text longer than the context window, grammar-constrained generation, validation and a bounded
  two-attempt repair.
* `ai/Gbnf` + `ai/Grammars` build bounded JSON grammars. Bounding string lengths matters: it makes it impossible
  for a rambling model to be cut off mid-string by the token limit.
* `ai/PromptSanitizer`, `PromptBuilder`, `OutputParsers` implement the injection defences described below.
* `ai/AiService` caches validated output per item, kind, **content hash** and model. Cached output is served even
  when no model is loaded. `BriefingService` picks items deterministically with the ranker; the model only writes
  a sentence for each, and the order is never the model's.
* Failures are values: `AiResult` has `SUCCESS, CANCELLED, MODEL_UNAVAILABLE, UNSUPPORTED, MALFORMED_OUTPUT,
  OUT_OF_MEMORY, INSUFFICIENT_CONTENT, NOT_CACHED, ERROR`. Nothing is ever substituted for a failed generation.

### Ranking and personalization

`ranking/Ranker` is a documented weighted sum of named signals; every non-zero signal is returned as a reason the
UI can show. `Personalizer` learns topic and source weights from your own actions, can be disabled, decays old
weights, and can be reset (learned only, or everything).

### Background work

`work/Scheduler` registers WorkManager jobs from settings: periodic sync (network constraint, exponential backoff,
bounded retries), a self-rescheduling daily briefing whose next run is computed on the **wall clock** (so DST
changes do not shift it), resumable model download as a `dataSync` foreground job, and article caching.

## Threading

* UI: Compose on the main thread; ViewModels expose `LiveData`.
* I/O: a small executor in `AppContainer`; HN item fetches use a short-lived pool inside the source.
* Inference: one `bytebrief-ai` thread. Operations queue; cancellation is cooperative.

## Security model

| Threat | Mitigation |
|---|---|
| Malicious URL schemes in feeds, OPML, HN links | `UrlCanonicalizer` accepts http(s) only; the fetcher re-checks; feeds are upgraded to https |
| Cleartext or downgraded transport | Network security config denies cleartext (debug allows `localhost` for tests); redirects https→http refused |
| Huge or hostile responses | Size caps per request type; XML parsed with no DTD/entities; images size- and type-checked |
| Prompt injection in articles, READMEs, comments, titles | Sanitizer removes chat-control tokens and invisible characters (NFKC first); random per-request document fences; "document is data" system rule; grammar-constrained output; links stripped; evidence must appear in the source |
| Tampered or truncated model | Pinned SHA-256 and exact size; verification marker; atomic rename |
| Secret exposure | GitHub token encrypted with a Keystore AES-GCM key, never logged or backed up |
| Code in READMEs | Rendered as inert text only; never executed or linkified |

## Why Kotlin at all

`com.kyant.backdrop` exposes its effect as a Compose `Modifier` (`drawBackdrop`) over Compose graphics layers.
That API has no Java entry point, and the prompt requires the real library rather than imitation transparency. So
the UI layer is Compose, written in Kotlin. The Java side exposes ordinary `ViewModel`/`LiveData` state, and a
single `@JvmStatic` bridge (`ByteBriefUi.setContent`) connects the Java activity to Compose. The trade-off is a
second language in the build; the benefit is that Java owns every non-UI concern.

A glass surface must never sit inside the layer it samples (it recurses natively). The kit therefore records only
the screen's content as the backdrop, draws bars and the tab bar as siblings above it, and renders controls inside
scrolling content with a cheap frosted style.
