# Changelog

## Unreleased

* New app icon: a glass AI chip with a star, as adaptive, monochrome (themed) and status-bar icons.

## 0.1.1 — settings fix

* Fixed Settings and Sources: switches and chips saved their value but kept showing the old state, so they looked unresponsive.
* "Forget what it learned" and "Delete saved summaries" now confirm what happened; deleting summaries asks first.
* Buttons that were truncated or could overflow now wrap; dialog buttons no longer show the screen through them.
* Onboarding: Back returns to the previous step, content stays above the keyboard, custom topics appear as chips.
* "Updated now ago" now reads "Updated just now".

## 0.1.0 — first release

* Sources: Hacker News, GitHub Trending, GitHub Releases, RSS/Atom with OPML.
* On-device AI through llama.cpp: summary, key takeaways, three-level explanations, grounded Q&A, daily briefing.
* Offline storage and full-text search; deduplication across sources; transparent local ranking.
* Dark Liquid Glass UI with opaque fallback; two-pane layout on wide screens.
* Resumable, hash-verified model downloads (Wi-Fi first); WorkManager sync and a scheduled briefing notification.
* Signed, minified release APKs for arm64-v8a and x86_64.

Known limitations are listed in the README ("What is verified, and what is not").
