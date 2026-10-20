# Architecture (draft)

## Components

```
 ┌────────────── TV (Android TV app) ──────────────┐
 │  Search screen  ──►  Result list  ──►  Launcher │──► Netflix / Hulu / Disney+ … (deep link)
 │        │                                        │──► Plex / Jellyfin / Kodi (play item)
 └────────┼────────────────────────────────────────┘
          │ query (debounced)
          ▼
 ┌──────────── Search service ────────────┐
 │  merge + rank + cache                  │
 │   ├─ Local library adapters (Plex, …)  │  ◄── runs on the TV in personal mode
 │   └─ Availability adapter              │  ◄── direct to the API (personal) or via our server (hosted)
 └────────────────────────────────────────┘
```

### Two modes, same code

1. **Personal mode (first):** everything runs on the TV. The user brings their own API key. Library adapters talk to the local server on the home network. No server of ours.
2. **Hosted mode (only if commercial):** availability lookups go through a small caching server we run. One lookup of "Predator" serves every user, which keeps API costs flat as users grow. Library lookups still stay on the user's network, so we never see their library.

Design the availability adapter behind an interface from day one, so switching modes is configuration, not a rewrite.

## Search flow (TV app)

1. **As you type** (350 ms pause): search the **local title list** (word prefixes, by popularity) and **Plex** (`/search`) in parallel. Merge by TMDB id. No streaming API calls.
2. **When a poster is highlighted** (and the next one): look up where it streams, `GET /v4/shows/{tmdbId}`, cached for a day. Its badge and "watch on" buttons then fill in.
3. **On Search / Enter with a full word (4+ letters):** also run the streaming title search, so titles outside the local list can still be found.
4. **Weekly:** rebuild the local list from TMDB in the background (see FINDINGS, "Search-as-you-type").

Title order is fixed when results appear: relevance, then your library, then popularity. Availability arriving later doesn't reorder posters.

## Ranking

1. Owned (in a local library), then
2. Included in a service the user subscribes to (from settings), then
3. Free (Tubi, Pluto, Roku Channel, Prime "free" and so on), then
4. Add-on channels, then rent, then buy, sorted by price.

Collapse duplicates by TMDB ID.

## Launching

- **Streaming:** `Intent.ACTION_VIEW` with the provider's `videoLink` (or `link`) and an explicit package. Keep a per-service override table, because links behave differently. Netflix needs an explicit component, flags `0x10808000` and extra `source=30` (see FINDINGS).
- **Local:** open the item in the library's own app, so quality and audio passthrough stay native. Plex: `plex://libraries/<machineId>/movie/<slug>/<ratingKey>` (one OK press to play). Jellyfin and Kodi: their remote APIs (to be tested). No built-in player.

## API budget

- Search after the user pauses typing (about 600 ms) or presses Search, not on every key.
- Cache results by `(normalised title, country)` for about 24 hours, and title details by ID.
- The personal free tier (1,000 calls a month) covers this comfortably: typing costs nothing. Each highlighted title costs one call, once a day.
- TMDB (local list, posters) is free for personal use. **Commercial use of TMDB needs their approval.** Note this for the hosted tier.

## Secrets

- Never commit keys. `.gitignore` covers `*.key`, `.env*`, `local.properties` and `secrets/`.
- Developer machine: the Streaming Availability key is in `~/.config/streaming-api/key` (mode 600).
- Personal TV build: the key is entered in the app's settings screen (stored in app-private storage), or injected at build time from the file above, never from the repo.
- Plex: prefer the server's "allowed without auth" list for the TV's IP over storing a token.

## Open questions

- Plex: the TV-episode route and whether the slug matters.
- Can the app run as an ordinary app that also works as a launcher? Newer Google TV and Fire TV devices restrict replacement launchers.
