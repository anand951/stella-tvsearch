# Set it up on your own TV

This guide takes you from nothing to the Search app running on your Android TV. It was built and tested on an NVIDIA SHIELD TV. Other Android TV devices running Android 8.0 or later should work, but haven't been tested.

There's no app store version yet. You build the app on your computer with your own keys, then install it on your TV over the network. Plan on about an hour the first time.

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

On a SHIELD TV:

1. **Settings → Device Preferences → About**. Scroll to **Build** and press OK on it seven times, until it says you're a developer.
2. Go back to **Device Preferences → Developer options** and turn on **Network debugging**. Note the address it shows (the TV's IP).

Other Android TVs have the same steps, sometimes under different names (USB debugging, ADB debugging).

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

The app appears in the TV's app list as **Search**. Move it to your favourites row to keep it handy.

The first time it opens, it downloads its title list from TMDB (about a minute). It refreshes the list weekly by itself.

## Which apps it can open

Pressing OK opens the title directly in these apps, when they're installed: **Plex, Netflix, Prime Video, Disney+, Hulu, HBO Max, Paramount+**, and **YouTube** for free movies. Other services still show where a title is streaming, but can't open it yet. [FINDINGS.md](FINDINGS.md) explains how each app's link was worked out, if you want to add one.

## Keep your build to yourself

Your keys are built into the app file. That's fine on your own TV, but don't share your `app-release.apk` with anyone: they'd be using your keys and your free lookups.

## Changing settings later

Edit `~/.config/stella-tv/config.json` (or a key file), then repeat step 6. Settings are read when the app is built, not on the TV.

## If something doesn't work

| Symptom | Likely cause |
|---|---|
| No Plex results | The TV's IP isn't on Plex's "allowed without auth" list, or `plex.url` uses `localhost` instead of the server's network address |
| No "where to watch" for anything | The Streaming Availability key is missing or wrong, or the month's free lookups are used up |
| Search only works after pressing Search | The TMDB token is missing, or the title list is still downloading |
| `adb: device unauthorized` | Accept the prompt on the TV. Android forgets the approval after about a week without use |
