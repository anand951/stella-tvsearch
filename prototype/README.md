# Command-line prototype

Roadmap step 1: one search across your Plex library and the Streaming Availability API, ranked, with a command that opens the chosen result on an Android TV.

Python 3.10+ standard library only. No install step.

```
./stella.sh search predator        # ranked list
./stella.sh search predator --all  # every option (each quality, price)
./stella.sh play 1                 # open result 1 on the TV (best ▶ option)
./stella.sh play 2 3               # open result 2 using its 3rd ▶ option
```

Example (abridged):

```
 1. Predator (1987)
    ▶ in your library   Plex (your library)  1080
      rent              Prime Video  3.99 USD HD

 2. Predator: Badlands (2025)
    ▶ in your library   Plex (your library)  4K
    ▶ on your services  Hulu  UHD
```

## Setup

1. Put the Streaming Availability API key in `~/.config/streaming-api/key` (one line, `chmod 600`).
2. Copy `config.example.json` to `~/.config/stella-tv/config.json` and fill it in:
   - `my_services`: service ids from the API (`netflix`, `hbo`, `paramount`, `hulu`, `disney`, `prime`, `apple`, `peacock` and so on).
   - `plex`: optional. Add `"token_file": "~/.config/stella-tv/plex-token"` if you use a token instead of the allow-list below.
   - `tv`: optional, the TV's `adb connect` address. Needed only for `play`.

Personal settings and the cache (`~/.cache/stella-tv/`) live outside the repo.

### Plex access without a token

In Plex: **Settings → Network → "List of IP addresses and networks that are allowed without auth"**. Add the TV's IP and the IP of the machine running this prototype, e.g. `192.168.1.20/255.255.255.255`.

Gotcha: when the prototype runs on the Plex server itself, `127.0.0.1` is **not** accepted even when it's listed. Use the server's LAN address in both the allow-list and `plex.url`.

## How it works

| Module | Job |
|---|---|
| `plex.py` | `/search?includeGuids=1`, which gives TMDB/IMDb ids; builds the Plex app link |
| `availability.py` | `/shows/search/title`, with each search cached for 24 hours (one API call per new search) |
| `rank.py` | Merges by TMDB id → IMDb id → title + year. Ranks options: library → your services → free → add-on → rent → buy → other subscriptions |
| `launch.py` | Per-service launch rules over ADB. See [../docs/FINDINGS.md](../docs/FINDINGS.md). Links from the API are scheme-checked and shell-quoted |
| `cli.py` | `search` and `play` |

## Known gaps

- Plex TV shows open on the show page (tested). There's no episode picking yet.
- Peacock, Tubi, Pluto TV and others have no launch rule yet, so they show without ▶.
- Disney+ add-on via Hulu opens in Hulu's link format; untested.
