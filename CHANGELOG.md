# Changelog

## Unreleased

* Buttons react to a tap: every control dims while pressed, "Follow this forum" turns into a green "Following" once
  done, and one-shot actions such as saving a token or clearing what was learned briefly confirm themselves.

## 0.2.1 — Faster refresh

* Refreshing sources now shows a progress bar with a done/total counter.
* Sources are refreshed in parallel (4 at a time by default). Settings > Sync lets you choose 2 to 8 at once, or turn
  parallel refresh off to go one by one.

## 0.2.0 — XDA Forums

* New source: XDA Forums. Follow a device forum by address or by browsing the forum tree (with a filter); its newest
  threads appear in For You, Browse, Search and Saved.
* Open a thread to read it in full (up to 12 pages, quotes shortened) and keep it offline; the AI features work on it.
* The forum's human-verification check is completed by a hidden system WebView; when it needs a tap, a bottom sheet
  with an eye button shows the page, and Sources shows "Action required".
* Optional alerts for new threads per forum; background refresh at most every 3 hours, on Wi-Fi by default.
* Database version 2 (new `forums` table, migrated without losing data). Public pages only, no sign-in.

## 0.1.2 — new icon

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
