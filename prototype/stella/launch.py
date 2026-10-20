"""Opens a title in the right app on an Android TV, over ADB. See docs/FINDINGS.md for why each rule exists."""
import shlex
import subprocess
import urllib.parse

PACKAGES = {
    "netflix": "com.netflix.ninja",
    "hbo": "com.wbd.stream",
    "paramount": "com.cbs.ott",
    "disney": "com.disney.disneyplus",
    "hulu": "com.hulu.livingroomplus",
    "plex": "com.plexapp.android",
    "prime": "com.amazon.amazonvideo.livingroom",
}


def intent_args(option):
    """Returns the `am start` arguments for an option, or None if we don't know how to open it."""
    service = option["service_id"]
    if service == "plex":
        if not _safe_url(option.get("plex_link"), ("plex",)):
            return None
        return ["-a", "android.intent.action.VIEW", "-d", option["plex_link"], PACKAGES["plex"]]
    url = option.get("video_link") or option.get("link")
    if not _safe_url(url, ("http", "https")) or service not in PACKAGES:
        return None
    if service == "netflix":
        # Netflix ignores plain deep links; it needs the partner-style launch.
        url = url.replace("https://", "http://")
        return ["-n", "com.netflix.ninja/.MainActivity", "-a", "android.intent.action.VIEW",
                "-d", url, "-f", "0x10808000", "-e", "source", "30"]
    return ["-a", "android.intent.action.VIEW", "-d", url, PACKAGES[service]]


def launch(tv, option, adb="adb"):
    args = intent_args(option)
    if args is None:
        raise ValueError(f"Don't know how to open {option['service_name']} on the TV yet")
    package = PACKAGES[option["service_id"]]
    base = [adb, "-s", tv, "shell"]
    subprocess.run([adb, "connect", tv], capture_output=True)
    subprocess.run(base + ["input", "keyevent", "KEYCODE_WAKEUP"], check=True)
    # Cold start: some apps only bring themselves to the front if already running.
    subprocess.run(base + ["am", "force-stop", package], check=True)
    # `adb shell` runs arguments through the device shell, so quote every one of them.
    subprocess.run(base + ["am", "start"] + [shlex.quote(a) for a in args], check=True, capture_output=True)


def _safe_url(url, schemes):
    """Links come from an external API: accept only expected schemes and no control characters."""
    if not url or any(ord(c) < 32 or ord(c) == 127 for c in url):
        return False
    return urllib.parse.urlsplit(url).scheme in schemes
