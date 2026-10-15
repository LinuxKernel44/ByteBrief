# Testing

Nothing in this project is reported as working unless a test or a recorded manual run shows it. This page lists
what each suite proves and how to run it.

## JVM unit tests

```bash
./gradlew :app:testDebugUnitTest --no-build-cache
```

| Suite | Proves |
|---|---|
| `domain/*` | URL allow-list and canonicalization, dedup keys, hashing, safe FTS query building, topics, HTML→text |
| `net/HttpFetcherTest` | scheme rejection, size caps, rate-limit mapping, redirects, retryability |
| `source/hn`, `source/github`, `source/rss` | each source against a mock server and a **real GitHub Trending page fixture**; feed formats, dates, XXE and entity-expansion safety, OPML round trips |
| `repo/IngestorMergeTest` | user state and richer text survive re-ingestion; AI status invalidated only when text changes |
| `ranking/*` | every ranking signal, diversity, determinism, "total stars are ignored", personalization learning/reset |
| `ai/AiPipelineTest` etc. | prompt fencing and sanitizing, validation, bounded retry, truncation and chunking, cancellation, OOM, grounding, briefing id filtering — against a scripted generator that exists only in tests |
| `ai/ModelDownloaderTest` | `Range`/`If-Range` resume, ignored ranges, tampered or oversized data, low disk, dropped connections |
| `work/SchedulerTest` | next briefing time across DST changes |
| `ui/MarkdownLiteTest` (Kotlin) | README/release-note rendering rules |

## Instrumented tests (emulator or device, API 33+)

```bash
# Room, sync, services, workers, UI smoke tests (no model needed)
./gradlew :app:connectedDebugAndroidTest

# Real-model tests: push a model first and pass its path
adb push Qwen3-4B-Instruct-2507-Q4_0.gguf /data/local/tmp/model.gguf
adb shell sha256sum /data/local/tmp/model.gguf      # must equal the value in docs/MODELS.md
./gradlew :llama:connectedDebugAndroidTest :app:connectedDebugAndroidTest \
  -Pandroid.testInstrumentationRunnerArguments.modelPath=/data/local/tmp/model.gguf
```

Without `modelPath` the real-model tests are reported as **skipped**, never as passed.

| Suite | Proves |
|---|---|
| `data/DatabaseTest` | idempotent ingest, grouping, FTS (prefix, filters, update/delete), retention, cascades |
| `repo/SyncManagerTest` | all four sources end to end against a local server and a real database; failure isolation; per-feed backoff; ETags; rate limits |
| `ai/AiServiceTest`, `BriefingServiceTest` | caching by content hash and model, staleness, cancel, deterministic briefing selection |
| `security/TokenStoreTest` | Keystore encryption round trip, tamper rejection |
| `work/BriefingWorkerTest` | the daily job posts the notification and queues the next run |
| `ui/UiSmokeTest` | the real glass UI launches, every tab and secondary screen opens, search works, solid mode works |
| `ai/LlamaAiEngineTest` (model) | READY only after a real decode; summaries, takeaways, explanations, grounded Q&A, honest "not in the text", prompt-injection resistance, cancellation, briefing ids |
| `llama/LlamaNativeTest` (model) | the JNI layer: generation, cancellation, context overflow, UTF-8 streaming, grammar constraint |

## Manual verification record

Performed on an Android 16 (API 36) x86_64 emulator with the signed, minified release APK: first-run flow,
sync from live services, model download with pause/resume and SHA-256 verification, model load, in-app benchmark,
summary generation, a simulated tablet-width display (two-pane), launcher icon. The release build was uninstalled
and reinstalled between runs because debug and release keys differ.

## Known gaps

* No physical-device or arm64 execution. No TalkBack run. No long-duration battery or thermal measurement.
* WorkManager background constraints (Doze, battery saver, OEM background limits) are exercised through
  `TestListenableWorkerBuilder`, not through real idle periods.
