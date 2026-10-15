# Dependencies

All versions are pinned. They were the latest stable releases when chosen and are compatible with each other as
built and tested here. Updating is a deliberate change that needs the test suites re-run.

| Component | Version | Why |
|---|---|---|
| Gradle (wrapper) | 9.8.0 | bundles Kotlin 2.4.10, matching the glass library's toolchain |
| Android Gradle Plugin | 9.3.2 | required by the glass library's artifacts |
| compileSdk / targetSdk / minSdk | 37 / 36 / 33 | newest SDK installed for compiling (a dependency requires it); Android 16 target; Android 13 floor |
| Kotlin / Compose compiler | 2.4.10 | UI shell only |
| Compose (JetBrains) | 1.12.0 | the version the glass library is built against |
| io.github.kyant0:backdrop, shapes | 2.0.1, 1.2.1 | the required Liquid Glass library (Apache-2.0). `shapes` must be declared explicitly |
| androidx.room | 2.8.5 | persistence, FTS4 |
| androidx.work | 2.12.0 | background work |
| androidx.lifecycle | 2.11.0 | ViewModel, LiveData |
| OkHttp | 5.5.0 | networking |
| jsoup | 1.23.2 | HTML parsing (GitHub Trending, article text) |
| llama.cpp | tag `b11642` (git submodule) | on-device inference; MIT |
| NDK / CMake | 29.0.13113456 / 3.31.6 | native build |
| JUnit / MockWebServer / kxml2 / org.json | 4.13.2 / 5.5.0 / 2.3.0 / 20260814 | tests |

## Choices worth knowing

* **Why llama.cpp and not LiteRT-LM.** Chosen in the project's requirements interview. llama.cpp loads GGUF models from many publishers, has
  ARM-optimized kernels and a mature grammar sampler that gives strict JSON from small models. The JNI layer is
  about 400 lines.
* **Why FTS4 and not FTS5.** Room has first-class support for FTS4 on every Android SQLite build; FTS5 would need
  hand-written tables, triggers and migrations.
* **Why no cloud fallback.** There is none. If the model is unavailable the app says so and everything that does
  not need inference keeps working.
* **Liquid Glass minimum API.** The effect uses AGSL runtime shaders, which need API 33 — the same as this app's
  `minSdk`.

Lint's dependency-freshness hints are disabled on purpose (see the `lint {}` block in `app/build.gradle`).
