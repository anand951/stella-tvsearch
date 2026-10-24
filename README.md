# Stella TV

**One search box for everything you can watch**: your own media library (Plex, later Jellyfin and Kodi) and the streaming services you pay for, in one list, with one press to start watching.

> Working name. Stella is the labradoodle mascot from the custom TV home screen this idea grew out of.

Status: **research and design**. No app code yet. This repo holds what was learned while testing on a real TV, and the plan.

## The problem

You want to watch *Predator*. Is it in your own library? On Netflix? Hulu? Rent-only? Today you open four apps to find out.

- **Google TV search** answers this, but only with Google's launcher, ads and tracking. On a de-Googled Android TV with a custom launcher, its results can't be opened.
- **JustWatch** shows where something streams, but knows nothing about your own library. Its TV app can't be opened from another app with a search filled in.
- **Plex Discover** shows streaming availability next to your library, but only inside Plex.

## The idea

Type a title once and get one result list:

| Predator (1987) | **Your Plex**, free · Prime Video, rent $3.99 |
|---|---|
| Predator: Badlands (2025) | **Your Plex**, free · **Hulu**, included · Apple TV, rent $5.99 |
| Prey (2022) | **Hulu**, included |

Select a result and it opens straight into the right app, on the right title. Results are ranked: things you already own first, then your subscriptions, then free services, then rentals.

Found something for later? **Add to watchlist** saves it on the TV itself (no account, no Plex needed). Open Search with nothing typed, or press **Watchlist** beside the search box, to see your saved titles with up-to-date info on where each one is playing.

## Documents

| Doc | What's in it |
|---|---|
| [docs/FINDINGS.md](docs/FINDINGS.md) | What was tested on an NVIDIA SHIELD TV, and what worked |
| [docs/DATA-SOURCES.md](docs/DATA-SOURCES.md) | APIs for streaming availability, local libraries and watch history |
| [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md) | How the app and the optional caching server fit together |
| [docs/ROADMAP.md](docs/ROADMAP.md) | Prototype → personal use → possible open-source release |

## Rebuilding after a crash

The full step-by-step guide (PC tools, keys, Plex, Shield, NAS) is in the private `shield-home` repo: `RECOVERY.md`.

## Secrets

API keys never go in this repo. Locally, the Streaming Availability key lives at `~/.config/streaming-api/key` (mode 600). See [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md#secrets).

## License

None yet. That's deliberate while the repo is private and the open-source decision is open. Until a license is added, all rights are reserved.
