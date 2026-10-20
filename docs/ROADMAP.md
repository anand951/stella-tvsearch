# Roadmap

## 0. Research ✅ (October 2026)
- Tested Google search, JustWatch, Plex and streaming deep links on a SHIELD TV. See [FINDINGS.md](FINDINGS.md).
- Picked the Streaming Availability API and validated it with test searches (Predator, Dangal).

## 1. Command-line prototype ✅ (October 2026, see prototype/)
- `stella search "predator"`: the merged and ranked list from Plex plus the Streaming Availability API.
- Proves matching (TMDB IDs), ranking and caching.
- Uses about 10 API calls.

## 2. Close the two launch gaps ✅ (October 2026)
- Netflix: ✅ solved with the partner-style launch (see FINDINGS).
- Plex: ✅ solved: an app-route link opens the item page, then one OK press plays (see FINDINGS). Still to check: TV episodes.

## 3. TV app (personal)
- Android TV search screen in the same modern style as the custom launcher, with the Stella mascot as the focus indicator.
- Settings: country, my services, API key, library servers.
- Search tile on the custom launcher.

## 4. Decide on open source
- If yes: pick a license, scrub the history of anything personal, write a CONTRIBUTING guide, and publish.

## 5. Optional hosted tier
- Caching server for availability data, accounts, "leaving soon" alerts, watchlist sync (Trakt), multiple TVs and profiles.
