# Set it up on your own TV

This guide takes you from nothing to the Search app running on your Android TV.

There's no app store version yet. You build the app on your computer with your own keys, then install it on your TV over the network. Plan on about an hour the first time.

**Rather have an AI assistant walk you through it?** Download the repo (`git clone https://github.com/anand951/stella-tvsearch`), open that folder in Claude Code, Codex or Gemini CLI, and say: *"Help me install this on my TV. My TV is a <brand and model>."* It reads [AGENTS.md](../AGENTS.md), which tells it to go one step at a time and never to ask for your keys in the chat.

## Will it work on my TV?

| TV | Works? |
|---|---|
| NVIDIA SHIELD TV | Yes, tested |
| TVs with Google TV or Android TV built in (Sony, TCL, Hisense, Philips and others), Chromecast with Google TV, Google TV Streamer | Should work, not tested yet. Needs Android 8.0 or later |
| Amazon Fire TV | Not yet. Its streaming apps are different builds, so opening a title in them would need its own rules |
| Samsung, LG, Roku, Apple TV | No. They don't run Android apps |

If you try it on a TV that isn't listed as tested, please open an issue saying how it went.

You don't need to change your TV's home screen to use this. It's an ordinary app, and works alongside the stock home screen or a replacement such as [CleanYourTV](https://github.com/Gathin23/cleanyourtv).

## What you need

| Thing | Why | Cost |
|---|---|---|
| An Android TV (SHIELD TV recommended) | Runs the app | |
| A computer (Linux, macOS or Windows with WSL) on the same network | Builds and installs the app | |
| A [Streaming Availability API](https://developers.movieofthenight.com) key | Where each title streams, and the prices | Free for 1,000 lookups a month, which is plenty for one household |
| A [TMDB](https://www.themoviedb.org) account and its API Read Access Token | The list of titles you search as you type, and the posters | Free for personal use |
| A Plex Media Server (optional) | Shows your own library first | |

Without Plex, the app still searches every streaming service. Without the TMDB token, search only finds whole words after you press Search, and there are no free-on-YouTube results.

## 1. Get your keys

Never paste these into a chat, an issue or a commit. They go in files on your computer, which only you can read.

1. **Streaming Availability:** sign up at <https://developers.movieofthenight.com>, pick the free plan and copy your API key. Save it:
   ```
   mkdir -p ~/.config/streaming-api
   read -rsp "API key: " k && printf '%s\n' "$k" > ~/.config/streaming-api/key && chmod 600 ~/.config/streaming-api/key
   ```
2. **TMDB:** create an account, then go to **Settings → API**, request an API key (choose "personal use") and copy the **API Read Access Token** (the long one, not the short "API Key"). Save it:
   ```
   mkdir -p ~/.config/tmdb
   read -rsp "TMDB token: " k && printf '%s\n' "$k" > ~/.config/tmdb/token && chmod 600 ~/.config/tmdb/token
   ```

## 2. Tell it about your services

Copy the example settings and edit them:

```
mkdir -p ~/.config/stella-tv
cp prototype/config.example.json ~/.config/stella-tv/config.json
```

| Setting | What to put |
|---|---|
| `country` | Two-letter country code, such as `us`, `ca` or `gb`. Availability differs by country |
| `my_services` | The services you pay for: `netflix`, `prime`, `disney`, `hulu`, `hbo`, `paramount`, `apple`, `peacock` and so on. These rank first, as "No extra cost" |
| `plex.url` | Your Plex server's address on your home network, such as `http://192.168.1.10:32400`. Delete the whole `plex` line if you don't use Plex |
| `tv` | Not used by the TV app. Only the command-line prototype needs it |

## 3. Let the TV reach Plex (skip without Plex)

The app doesn't store a Plex token. Instead, Plex lets your TV in without signing in:

1. Give your TV a fixed address on your network (in your router, reserve its IP).
2. In Plex on the web: **Settings → Network → Show Advanced → "List of IP addresses and networks that are allowed without auth"**. Add your TV's IP, such as `192.168.1.20/255.255.255.255`, then **Save**.

## 4. Turn on network debugging on the TV

**On a SHIELD TV:**

1. **Settings → Device Preferences → About**. Scroll to **Build** and press OK on it seven times, until it says you're a developer.
2. Go back to **Device Preferences → Developer options** and turn on **Network debugging**. Note the address it shows (the TV's IP).

**On Google TV and most TVs with Android TV built in:**

1. **Settings → System → About**. Press OK on **Android TV OS build** seven times.
2. Go back to **System → Developer options** and turn on **USB debugging**. If there's also a **Network debugging** or **ADB debugging** switch, turn that on too.
3. Find the TV's IP under **Settings → Network & Internet**, on the network you're connected to.

Some newer Google TV models don't accept a network connection that way. If `adb connect` in step 6 fails, use **Developer options → Wireless debugging** instead: turn it on, choose **Pair device with pairing code**, then on your computer run `adb pair <tv-ip>:<pairing port>` and type the code. Then `adb connect <tv-ip>:<port>` with the port shown on the Wireless debugging screen.

## 5. Install the build tools on your computer

You need Java 17 and the Android SDK command-line tools. Android Studio includes both, or install just the tools:

1. Java 17: from your package manager (`sudo apt install openjdk-17-jdk`) or [Adoptium](https://adoptium.net).
2. Android command-line tools: download them from <https://developer.android.com/studio#command-line-tools-only>, unzip into `~/android-sdk/cmdline-tools/latest`, then:
   ```
   ~/android-sdk/cmdline-tools/latest/bin/sdkmanager --sdk_root=$HOME/android-sdk "platform-tools" "platforms;android-34" "build-tools;34.0.0"
   echo "sdk.dir=$HOME/android-sdk" > android/local.properties
   ```
   `local.properties` is ignored by git, so your path never ends up in a commit.

## 6. Build and install

```
cd android
./gradlew assembleRelease
~/android-sdk/platform-tools/adb connect <tv-ip>:5555
~/android-sdk/platform-tools/adb install -r app/build/outputs/apk/release/app-release.apk
```

The first `adb connect` shows a prompt on the TV. Choose **Always allow from this computer**, then run `adb connect` again. If Gradle can't find Java, put `JAVA_HOME=/path/to/jdk17` in front of `./gradlew`.

On Windows, do all of this inside WSL (Ubuntu). On a Mac, if `adb connect` says "No route to host", allow your terminal under **System Settings → Privacy & Security → Local Network**.

The app appears in the TV's app list as **Search**. Move it to your favourites row to keep it handy.

The first time it opens, it downloads its title list from TMDB (about a minute). It refreshes the list weekly by itself.

## 7. Turn debugging off

Network debugging leaves an unencrypted door open on your home network. Once the app is installed, go back to **Developer options** and turn **Network debugging**, **USB debugging** and **Wireless debugging** off. Turn them on again only when you want to update the app.

## Which apps it can open

Pressing OK opens the title directly in these apps, when they're installed: **Plex, Netflix, Prime Video, Disney+, Hulu, HBO Max, Paramount+**, and **YouTube** for free movies. Other services still show where a title is streaming, but can't open it yet. [FINDINGS.md](FINDINGS.md) explains how each app's link was worked out, if you want to add one.

## Keep your build to yourself

Your keys are built into the app file. That's fine on your own TV, but don't share your `app-release.apk` with anyone: they'd be using your keys and your free lookups.

## Changing settings later

Edit `~/.config/stella-tv/config.json` (or a key file), then repeat step 6. Settings are read when the app is built, not on the TV.

## If something doesn't work

Start with **About** (the ⓘ at the top right of the app). It shows whether each source is working, and what to fix if one isn't.

| Symptom | Likely cause |
|---|---|
| No Plex results | The TV's IP isn't on Plex's "allowed without auth" list, or `plex.url` uses `localhost` instead of the server's network address |
| No "where to watch" for anything | The Streaming Availability key is missing or wrong, or the month's free lookups are used up |
| Search only works after pressing Search | The TMDB token is missing, or the title list is still downloading |
| `adb: device unauthorized` | Accept the prompt on the TV. Android forgets the approval after about a week without use |
