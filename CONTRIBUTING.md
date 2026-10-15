# Contributing

* Source, comments, identifiers, UI text and commit messages are in **English**.
* Application logic is **Java**. Kotlin is for the Compose UI shell only (`app/src/main/kotlin`); keep it thin and
  put state and logic in a Java ViewModel or service.
* A change is done when `./gradlew :app:testDebugUnitTest :app:lintRelease` passes and, for anything touching
  storage, sync, AI or UI, the relevant instrumented tests pass (see `docs/TESTING.md`).
* Do not add features that send user content to a remote service, add analytics, or invent model output on failure.
* Never fabricate test results. If something could not be verified, say so in the pull request.
* Treat all remote content as untrusted. New sources must go through `HttpFetcher` and `UrlCanonicalizer`.
