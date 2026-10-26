# AGENTS.md

Instructions for AI coding assistants (Claude Code, Codex, Gemini CLI, Cursor, Copilot and others) working in this repository. People should start with [README.md](README.md) and [docs/SETUP.md](docs/SETUP.md); this file repeats the setup in the form an assistant needs, with the rules that matter.

## First: which job are you doing?

| You were asked to | Do this |
|---|---|
| Install the Search app on someone's TV | Follow [Installing for a person](#installing-for-a-person) below, on their real TV. The only files you create or change are the person's own settings outside the repo (`~/.config/...`) and `android/local.properties`. Do not edit the code, docs or build files, and do not open issues or pull requests unless the person asks. |
| Change the app or the docs | Skip to [Changing the code](#changing-the-code). |

## Installing for a person

[docs/SETUP.md](docs/SETUP.md) is the procedure; this section is how to run it with someone who may not be technical.

### Rules that are not negotiable

- **Never ask for a key, token or password in the chat.** Not the Streaming Availability key, not the TMDB token, not a Plex token. Give the person the `read -rsp` command from SETUP.md step 1 to run in their own terminal, so the secret goes straight to a file. If they paste one into the chat anyway, tell them to regenerate it on the provider's site, and don't use the pasted one.
- **Never print, log or commit a secret.** Check that a key file exists and isn't empty (`test -s`), not what's in it. `.gitignore` already covers `local.properties`, `*.key`, `.env*` and `secrets/`; never use `git add -f` on any of them.
- **Never share the built APK** or put it anywhere but the person's TV. The keys are inside it.
- **One step at a time.** Anything the person does on the TV or a website is one step per message. Say exactly what to press, then wait for them to say it's done before the next step.
- **Don't change the TV beyond what setup needs.** Turning on developer options and debugging is all. Don't disable, uninstall or force-stop apps, change the default launcher or change settings. For debloating, point them to [CleanYourTV](https://github.com/Gathin23/cleanyourtv); it's a separate project with its own safety rules.
- **Ask before taking over the TV** if someone might be watching it: installing restarts nothing, but the first launch opens the app on screen.
- **Close network debugging at the end** (see [Finish](#finish)). Port 5555 is unencrypted, and anyone on the network can try it while it's open.

### 1. Find out what you're working with

Ask, or check, before you start:

- **The computer:** Linux, macOS, or Windows. On Windows, use WSL (Ubuntu) and run everything there; the commands in SETUP.md assume a Unix shell.
- **The TV:** brand and model, and whether it's Android TV, Google TV or Fire TV.

| TV | What to do |
|---|---|
| NVIDIA SHIELD TV | Tested. Follow SETUP.md as written |
| Google TV or Android TV built in (Sony, TCL, Hisense, Philips and others), Chromecast with Google TV, Google TV Streamer | Should work, untested. Debugging is turned on differently (below). Continue, and tell the person it hasn't been tested on their model |
| Fire TV (Stick, Cube, Fire TV-branded sets) | **Stop.** Not supported: its streaming apps are different builds, so "open in the app" won't work, and its home screen doesn't show sideloaded apps in the usual place. Say so plainly |
| Samsung (Tizen), LG (webOS), Roku, Apple TV | **Stop.** Not Android; this app can't run there |

- **What they have:** which streaming services they pay for (for `my_services`), and whether they use Plex. Plex is optional.

### 2. Keys

SETUP.md step 1. The person signs up on the two websites themselves and saves each key with the `read -rsp` command. Then confirm with `test -s ~/.config/streaming-api/key && test -s ~/.config/tmdb/token && echo ok`.

### 3. Settings

SETUP.md step 2. You may write `~/.config/stella-tv/config.json` for them from their answers. Service ids: `netflix`, `prime`, `disney`, `hulu`, `hbo`, `paramount`, `apple`, `peacock`. Country is a two-letter code. Leave out `plex` if they don't use it. This file holds no secrets.

### 4. Plex access (only with Plex)

SETUP.md step 3. The person changes the Plex setting. You can help find the server's address. Check it answers with `curl -s -o /dev/null -w '%{http_code}' http://<server>:32400/identity`: `200` means it's reachable.

### 5. Debugging on the TV

- **SHIELD:** SETUP.md step 4 (Network debugging), then `adb connect <tv ip>:5555`.
- **Google TV and most built-in Android TVs:** Settings → System → About → press OK on **Android TV OS build** seven times. Then Settings → System → Developer options → **USB debugging** on. If there's a **Network debugging** or **ADB debugging** switch, turn it on too, and try `adb connect <tv ip>:5555`.
- **If port 5555 stays closed** (common on newer Google TV): Developer options → **Wireless debugging** → on → **Pair device with pairing code**. The person reads you the pairing port and code (a pairing code is single-use, so it's fine in the chat), then `adb pair <tv ip>:<pairing port>`, then `adb connect <tv ip>:<port shown on the Wireless debugging screen>`. Wireless debugging switches itself off when Wi-Fi drops.
- **Tell the person before** the "Allow debugging?" prompt appears on the TV, and ask them to choose **Always allow**.
- **Finding the TV's IP:** it's on the TV under Settings → Network, or the same screen as the debugging switch. Don't scan the person's network without asking.
- **macOS:** if `adb connect` says "No route to host" while the TV is clearly on, macOS's Local Network privacy is blocking the terminal. Have the person allow it in System Settings → Privacy & Security → Local Network.

Confirm with `adb devices`: the TV must say `device`, not `unauthorized` or `offline`.

Check the Android version before building: `adb shell getprop ro.build.version.sdk` must be **26 or higher** (Android 8.0). Below that, stop and say so. Don't lower `minSdk`.

### 6. Build tools, build, install

SETUP.md steps 5 and 6. Check for Java 17 first (`java -version`) and an existing Android SDK (`$ANDROID_HOME`, `~/Android/Sdk`, `~/Library/Android/sdk`) before downloading anything.

- After `./gradlew assembleRelease`, confirm `android/app/build/outputs/apk/release/app-release.apk` exists.
- To check the keys made it in without printing them: `grep -c '""' android/app/build/generated/source/buildConfig/release/com/stellatv/search/BuildConfig.java` counts empty settings. `PLEX_URL` empty is fine without Plex; an empty `STREAMING_API_KEY` or `TMDB_TOKEN` means a key file wasn't found.
- If `adb install` hangs (Android 14 can wait for an on-screen confirmation), push the APK and install from the shell instead: `adb push app-release.apk /data/local/tmp/s.apk && adb shell pm install -r /data/local/tmp/s.apk`.

### 7. Check it works

Open it: `adb shell monkey -p com.stellatv.search 1`. Ask the person to search for a well-known title and say what they see. A screenshot helps: `adb exec-out screencap -p > shot.png` (streaming apps often show black in screenshots; that's normal).

The first launch downloads the title list (about a minute). Until then search works only after pressing Search.

### Finish

1. Ask the person to turn **USB debugging**, **Network debugging** and **Wireless debugging** off in Developer options. Debugging is needed again only to update the app.
2. Check the port is closed: `adb disconnect` then `nc -z -w 3 <tv ip> 5555; echo $?`. Non-zero means closed. On macOS a "No route to host" from `nc` proves nothing; only "Connection refused" does.
3. Tell them where their settings live (`~/.config/stella-tv/config.json`) and that changing a setting means building and installing again.

### When to stop

Stop and explain, rather than retrying in a loop, when: the TV is Fire TV or not Android; the Android version is below 8.0; every install fails with `INSTALL_FAILED_VERIFICATION_FAILURE` (some China-market sets refuse all sideloads); or the build fails twice with the same error. Show the error and ask how they want to go on.

## Changing the code

| Path | What it is |
|---|---|
| `android/` | The TV app, package `com.stellatv.search`. Plain Java, no AndroidX, `minSdk` 26, JDK 17, SDK platform 34. [android/README.md](android/README.md) maps the classes |
| `prototype/` | The command-line prototype in Python (standard library only). `rank.py` and `Catalog.java` share the same ranking rules; change both |
| `docs/` | Setup, findings from testing on a real TV, data sources, architecture, roadmap |

- **Personal settings never go in the repo.** Keys, Plex addresses, IPs and service lists are read at build time from the developer's `~/.config` (see `android/app/build.gradle`). Keep it that way.
- **No IP addresses, names, account emails, device serials or screenshots of someone's own library** in commits. Use `<tv ip>` or `192.168.1.20` in docs.
- **No telemetry, ads or analytics.** The app talks only to the Streaming Availability API, TMDB, and the person's own Plex server.
- **Opening titles in other apps** follows per-app rules in `Opener.java`, each found by testing on a real TV ([docs/FINDINGS.md](docs/FINDINGS.md)). A new app's rule needs the same: say which TV and app version you tested on.
- **Remote first.** Every screen is used with a D-pad: check Up, Down, Left, Right and Back from every element you add.
- **Build check:** `cd android && ./gradlew assembleRelease` must succeed with no personal config present.
- **No prebuilt APKs** in the repo. People build from source with their own keys.
