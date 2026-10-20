# Android TV search app

The TV version of the prototype: one search screen across your Plex library and your streaming services, with Stella (the labradoodle mascot) as the focus indicator.

- Type with the TV keyboard. Results update as you type, from a local list of ~25,000 popular titles plus your Plex library. "hun" finds *The Hunger Games*.
- Where each title streams is looked up when you highlight it (one API call per title per day).
- **OK** on a poster opens the best way to watch it: your library first, then your services.
- **Down** shows every way to watch it, including free, add-on, rent and buy.
- **Back** returns to the search bar.

## Build (personal)

Settings are read **at build time** from the developer's machine, never from the repo:

- `~/.config/stella-tv/config.json`: `country`, `my_services`, `plex.url` (see `prototype/config.example.json`)
- The API key file it names (default `~/.config/streaming-api/key`)
- `~/.config/tmdb/token`: the TMDB API Read Access Token, for the local title list. Without it, search falls back to the streaming title search (whole words only).

```
cd android
JAVA_HOME=<jdk17> ./gradlew assembleRelease
adb install -r app/build/outputs/apk/release/app-release.apk
```

The key ends up inside your personal APK. That's fine for your own TV, but don't share that APK. A shareable build would ask for the key in a settings screen, or use the hosted proxy (see docs/ARCHITECTURE.md).

Plex: add the TV's IP to the server's "allowed without auth" list. No token is stored.

## Code

| File | Job |
|---|---|
| `SearchActivity` | Screen, keys, Stella placement |
| `TitleIndex` | The local title list: weekly TMDB download, word-prefix search |
| `PlexSource`, `StreamingSource` | Library search; streaming title search and per-title lookup (cached on disk for 24 hours) |
| `Catalog` | Merge and ranking, same rules as `prototype/stella/rank.py` |
| `Opener` | Per-app launch rules from docs/FINDINGS.md |
| `MascotView` | Stella |
