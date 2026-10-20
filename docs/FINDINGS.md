# Findings from testing on a real TV

Test device: **NVIDIA SHIELD TV** (Android TV 11) running a custom launcher, with the stock Google TV launcher and Google Assistant disabled. Tested October 2026.

## Summary

| What | Works? | Notes |
|---|---|---|
| Google universal search (Assistant / "katniss") | ❌ | Finds titles, but selecting a result fails without the stock launcher |
| JustWatch TV app search | ✅ by hand | Good results, but no deep links into the app and no knowledge of your own library |
| Plex server search API | ✅ | `/search?query=` returns full metadata, including file paths and TMDB-style GUIDs |
| Deep link into the Plex app for one library item | ❌ | Every format tried failed (see below) |
| Streaming Availability API search | ✅ | Accurate, includes prices and direct links |
| Disney+ direct link | ✅ | Plays the video after the profile picker |
| Hulu direct link | ✅ | Opens the right title page after the profile picker |
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

### Deep-linking into the Plex Android TV app (fails)

The current Plex app (React Native based) was tested with:

| Link | Result |
|---|---|
| `plex://server://<machineId>/com.plexapp.plugins.library/library/metadata/<ratingKey>` | Ignored (opens Plex home) |
| `plex://preplay/?metadataKey=/library/metadata/<id>&metadataType=1&server=<machineId>` | Ignored |
| `plex://watch/video?uri=server://<machineId>/com.plexapp.plugins.library/library/metadata/<id>` | Starts playback, then **"Playback Error: Item not known when attempting to build decision"** |
| `https://watch.plex.tv/movie/<slug>` | Opens the Discover page, but with **no Play button** for the library copy |

**Ideas still to try:**
1. The **Plex Companion / remote-control API**: tell the Plex client on the TV to `playMedia` (how the phone app's "cast to TV" works).
2. Play the file in our own player, straight from the server (direct play). Simple, but loses Plex resume/watched sync unless we report progress back through the API.
3. Jellyfin and Kodi have documented, open APIs and are likely easier.

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
