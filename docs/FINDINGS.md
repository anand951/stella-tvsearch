# Findings from testing on a real TV

Test device: **NVIDIA SHIELD TV** (Android TV 11) running a custom launcher, with the stock Google TV launcher and Google Assistant disabled. Tested October 2026.

## Summary

| What | Works? | Notes |
|---|---|---|
| Google universal search (Assistant / "katniss") | ❌ | Finds titles, but selecting a result fails without the stock launcher |
| JustWatch TV app search | ✅ by hand | Good results, but no deep links into the app and no knowledge of your own library |
| Plex server search API | ✅ | `/search?query=` returns full metadata, including file paths and TMDB-style GUIDs |
| Deep link into the Plex app for one library item | ✅ | `plex://libraries/<machineId>/movie/<slug>/<ratingKey>` opens the item page with **Watch** focused; one OK press plays at full quality |
| Streaming Availability API search | ✅ | Accurate, includes prices and direct links |
| Disney+ direct link | ✅ | Plays the video after the profile picker |
| Hulu direct link | ✅ | Opens the right title page after the profile picker |
| HBO Max direct link (`videoLink`) | ✅ | Plays immediately, no profile picker |
| Paramount+ link (`link`) | ✅ | Profile picker → plays the right movie |
| Prime Video link (`videoLink`, `&action=play`) | ✅ | Profile picker → plays |
| Netflix direct link (`/watch/<id>`) | ✅ | **Plays**, but only when launched the "partner way" (see below). A plain VIEW intent stops at the profile picker |

## Google universal search

- Re-enabling the Google app (`com.google.android.katniss`) gives a working keyboard search screen (`SearchTabActivity`).
- Selecting a result shows "Google Assistant is having a problem processing your request". Logcat: `item instanceof DisplayEntityItem has no tapInput`.
- Enabling `com.google.android.videos` didn't help. Result actions seem to depend on the stock launcher, which a custom-launcher setup has to keep disabled.

**Conclusion:** you can't rely on Google's search with a custom launcher. That's the gap this project fills.

## JustWatch

- The Android TV app (`com.justwatch.justwatch`) installs from the Play Store. Login is by QR code or a code at justwatch.com/tv.
- Search, title pages and "N more ways to watch" all work by remote.
- **No deep links:** the app only registers `MAIN` and Cast intents, so other apps can't open it with a search filled in.
- Watch out: the "more ways to watch" screen auto-launches the first provider after a countdown.
- It doesn't know about personal libraries (Plex and so on).

## Plex

### Server API (works)

- `GET http://<server>:32400/search?query=predator` (JSON with `Accept: application/json`) returns movies with `ratingKey`, `title`, `year`, `summary`, `thumb`/`art`, `duration`, the media file path, and `guid` (`plex://movie/...`), which maps to TMDB/IMDb.
- Auth: either an `X-Plex-Token`, or the server's **"List of IP addresses and networks that are allowed without auth"** setting for the TV's IP. The second keeps tokens off the device.

### Deep-linking into the Plex Android TV app (works, one press)

**Working link** (Plex for Android TV 2026.20, tested on the TV):

```
am start -a android.intent.action.VIEW \
  -d "plex://libraries/<machineId>/movie/<slug>/<ratingKey>" com.plexapp.android
```

- `<machineId>` is the server's `machineIdentifier` (from `GET /identity`). `<ratingKey>` comes from `/search`.
- It opens the library item page (resolution, audio track and subtitle shown) with **Watch** focused. One OK press plays it in Plex's own player, so quality, audio passthrough, resume and watched status all behave normally.
- `<slug>` was the lowercase title (`predator`). It's not yet checked whether the slug matters.
- **TV shows:** `plex://libraries/<machineId>/show/<slug>/<showRatingKey>` opens the show page (seasons row, **Watch** focused). Tested with an 11-season show.
- No autoplay parameter was found in the app.
- Other app routes found in the bundle, not yet tested:
  - `libraries/:serverId/show/:slug/:showKey/s/:seasonId/:seasonKey?/e/:episodeId/:episodeKey`: likely the episode page. Not needed yet, because the show page's Watch button resumes the show.
  - `libraries/:serverId/collection/:key`
  - `libraries/:serverId/playlist/:keyName`
- How it was found: the app is React Native. `strings` on `assets/index.android.bundle` in the APK shows the route table. The app accepts the prefixes `plex://`, `https://watch.plex.tv` and `https://watch.plextv.dev`.

**Links that failed earlier:**

| Link | Result |
|---|---|
| `plex://server://<machineId>/com.plexapp.plugins.library/library/metadata/<ratingKey>` | Ignored (opens Plex home) |
| `plex://preplay/?metadataKey=/library/metadata/<id>&metadataType=1&server=<machineId>` | Ignored |
| `plex://watch/video?uri=server://<machineId>/com.plexapp.plugins.library/library/metadata/<id>` | Starts, then "Playback Error: Item not known when attempting to build decision" |
| `https://watch.plex.tv/movie/<slug>` | Discover page with no Play button for the library copy |

**Remote control (Companion) is not local any more.** The current app opens no Companion HTTP port (32500) and doesn't answer GDM discovery. "Cast to TV" goes through plex.tv PubSub (cloud, needs the account token) and the Vizbee SDK. We don't need it now that the link above works.

**Our own player was rejected:** playing the file directly in our own app would lose Plex's audio passthrough and quality handling. Always hand playback to the Plex app.

## Streaming Availability API (movieofthenight)

- `GET https://api.movieofthenight.com/v4/shows/search/title?title=Predator&country=us&show_type=movie` with an `X-API-Key` header.
- It returned 8 relevant titles, each with `streamingOptions.us[]`: service, type (`subscription` / `rent` / `buy` / `free` / `addon`), price, quality, audio and subtitle languages, `link` (always present, a web URL) and `videoLink` (direct to the video, when available).
- Hindi works: *Dangal* (2016) returned `originalTitle: "दंगल"`, Netflix subscription and a `netflix.com/watch/...` link.
- The US service list includes Netflix, Prime Video (plus about 140 channel add-ons, including Eros Now), Disney+, Hulu, HBO Max, Apple TV, Peacock, Paramount+, Tubi, Pluto TV, The Roku Channel and Zee5.
- **Not covered:** Sling TV, which is mostly live TV.
- Free tier: 1,000 requests a month. The paid tier is about $50 a month for about 25,000 (check current pricing).

## Deep links into streaming apps (Android TV)

Each app registered web-URL intent filters for its own domain:

| App | Package | Link tested | Result |
|---|---|---|---|
| Disney+ | `com.disney.disneyplus` | `https://www.disneyplus.com/video/<id>` | ✅ Profile picker → **plays** |
| Hulu | `com.hulu.livingroomplus` | `https://www.hulu.com/watch/<id>` | ✅ Profile picker → title page (no Play button when the title isn't in the plan) |
| HBO Max | `com.wbd.stream` | `https://play.hbomax.com/video/watch/<id>` (the API's `videoLink`; `play.max.com` also registered) | ✅ **Plays** straight away, no profile picker |
| Paramount+ | `com.cbs.ott` | `https://www.paramountplus.com/movies/video/<id>/` (the API's `link`; no `videoLink` given) | ✅ Profile picker → **plays** the requested movie |
| Prime Video | `com.amazon.amazonvideo.livingroom` | `https://app.primevideo.com/detail?gti=<id>&action=play` (the API's `videoLink`) | ✅ Profile picker → **plays** |
| Netflix | `com.netflix.ninja` | `http://www.netflix.com/watch/<id>` with explicit component, flags and `source` extra (below) | ✅ **Plays** (profile picker skipped on a repeat launch) |

### Netflix needs the "partner" launch

What failed:
- `am start -a VIEW -d https://www.netflix.com/watch/<id> com.netflix.ninja`: profile picker, then the home screen.
- Same with `/title/<id>`: profile picker, then no playback.

What works (cold start, tested twice):

```
am force-stop com.netflix.ninja
am start -n com.netflix.ninja/.MainActivity -a android.intent.action.VIEW \
  -d "http://www.netflix.com/watch/<id>" -f 0x10808000 -e source 30
```

- `-f 0x10808000` = `NEW_TASK | CLEAR_TOP | RESET_TASK_IF_NEEDED`-style flags.
- `-e source 30` is the extra used by partner launchers. Netflix appears to ignore deep links without it.
- On the second launch it went straight into playback with no profile picker.
- Android equivalent: `Intent(ACTION_VIEW, uri).setClassName("com.netflix.ninja", "com.netflix.ninja.MainActivity").addFlags(0x10808000).putExtra("source", "30")`.
- Other registered schemes, not needed so far: `nflx://www.netflix.com/...`, `/deeplink.*`, `/browse`.

Notes:
- Launch with an explicit package (`am start -a VIEW -d <url> <package>`) so the link doesn't open a browser.
- **Cold start matters:** when the app was already running, some links just brought it to the front. Our app should handle that (for example, a Netflix-specific flow).
- Profile pickers can't be skipped. That's acceptable at one button press.
- Netflix and Disney+ block screenshots, so automated tests can't see their screens. Use `dumpsys media_session` to detect playback.

## Search-as-you-type (October 2026)

Typing on a TV remote means short, partial words ("hun", "pred"). Neither online search handles them:

| Source | "hun" | "hunte" | "hunted" / "hunge" |
|---|---|---|---|
| Streaming Availability `/shows/search/title` | Junk (whole-word, fuzzy: "She Walks in Darkness", "Murder, She Wrote") | "Hunt", "Hunter" titles | ✅ *The Hunted* |
| TMDB `/search/multi` | 10,000 hits led by exact word "Hun" ("Attila the Hun") | — | ✅ *The Hunger Games* |

The streaming search also matches **foreign and alternative titles** ("La preda", "The Hunted"), which look random when only the English title is shown.

**What works:** a **local title list** on the TV, searched by word prefix and ordered by popularity:
- Built from TMDB `/discover` (English titles, posters, years):
  - movies by popularity and by vote count (500 pages each; TMDB's maximum page is 500)
  - TV shows by popularity (500 pages)
  - Hindi films (`with_original_language=hi`, 200 pages)
- ~1,700 requests, about a minute, ~25,000 titles, ~2 MB.
- TMDB's daily ID export files (`files.tmdb.org/p/exports/`, 28 MB of movies) were rejected: they only carry the **original-language** title (e.g. "プライド").
- **Where to watch** comes from `GET /v4/shows/{movie|tv}/{tmdbId}?country=us` (one call per title, cached for a day), fetched only when a poster is highlighted.
- A device that sleeps mid-download loses its network. Per-page retries plus "rebuild next time if over 5% failed" handle this.

## YouTube free movies (2026-10-20)

- The Streaming Availability API does not cover YouTube. TMDB's watch providers (JustWatch data) do:
  provider 235 "YouTube Free" (and 192 "YouTube") under `free`/`ads` for the region. About 10,000 US movies.
- TMDB doesn't give the video id. The YouTube TV app (`com.google.android.youtube.tv`) accepts
  `https://www.youtube.com/results?search_query=...` as a VIEW intent and opens its search screen.
- Tested with "The Terminator 1984 full movie": the official free copy (tagged Free, 1:47:25) was the
  first result and already selected, so OK plays it. Movies only for now.
