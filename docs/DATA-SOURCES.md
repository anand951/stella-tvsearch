# Data sources

Before relying on any source, check these four things:
1. Does the free tier allow **commercial** use?
2. Cost per request or per user.
3. Does it give **deep links** into apps, not just "it's on Netflix"?
4. Which countries does it cover?
5. Are we allowed to **cache** results, and for how long? This decides whether a shared server is possible.

## Streaming availability

| Source | Link | Notes |
|---|---|---|
| **Streaming Availability API** (Movie of the Night) | https://www.movieofthenight.com/about/api · docs https://docs.movieofthenight.com · keys https://developers.movieofthenight.com | **Chosen for the prototype.** 66 countries; deep links; prices; leaving-soon dates; Hindi and Indian services. Also sold via RapidAPI. OpenAPI spec: https://github.com/movieofthenight/streaming-availability-api |
| Watchmode | https://api.watchmode.com | Free tier; sources with deep links; a good fallback or comparison |
| TMDB | https://developer.themoviedb.org/docs | Free posters and metadata. "Watch providers" (powered by JustWatch) has no deep links. Commercial use needs approval |
| JustWatch business data | https://www.justwatch.com/us/JustWatch-Streaming-API | Business licence only. No public API |
| Reelgood | https://reelgood.com | Sells B2B data |
| Gracenote | https://gracenote.com | Enterprise data used by TV makers. A reference point, not a starting option |

## Local libraries

| Source | Link | Notes |
|---|---|---|
| Plex | https://developer.plex.tv | Search works; app deep links don't (see FINDINGS). Investigate the Companion remote-control API |
| Jellyfin | https://api.jellyfin.org | Fully open. Likely the easiest "play this item" story |
| Kodi | https://kodi.wiki/view/JSON-RPC_API | JSON-RPC remote control, including `Player.Open` |

## Watch history and watchlists (possible premium features)

| Source | Link |
|---|---|
| Trakt | https://trakt.docs.apiary.io |
| Simkl | https://simkl.docs.apiary.io |

## Matching titles across sources

Use **TMDB IDs** (and IMDb IDs) as the shared key:
- The Streaming Availability API returns `tmdbId` and `imdbId`.
- Plex GUIDs (`plex://movie/...`) resolve to TMDB and IMDb in the item's `Guid` list (request with `includeGuids=1`).

Fall back to title + year only when no ID matches.
