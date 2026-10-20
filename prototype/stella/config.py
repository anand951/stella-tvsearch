"""Personal settings. These live outside the repo, in ~/.config/stella-tv/."""
import json
import os
from pathlib import Path

CONFIG_DIR = Path(os.environ.get("STELLA_CONFIG_DIR", Path.home() / ".config" / "stella-tv"))
CACHE_DIR = Path(os.environ.get("STELLA_CACHE_DIR", Path.home() / ".cache" / "stella-tv"))

DEFAULTS = {
    "country": "us",
    # Streaming Availability service ids you subscribe to, e.g. ["netflix", "hbo", "hulu"].
    "my_services": [],
    # Optional local Plex server: {"url": "http://127.0.0.1:32400", "token_file": null}
    "plex": None,
    # Where the API key is stored (one line, chmod 600).
    "api_key_file": str(Path.home() / ".config" / "streaming-api" / "key"),
    # ADB address of the TV, for "stella play". Optional.
    "tv": None,
}


def load():
    cfg = dict(DEFAULTS)
    path = CONFIG_DIR / "config.json"
    if path.exists():
        cfg.update(json.loads(path.read_text()))
    return cfg


def read_secret(path):
    """Reads a key or token from a file, never from the config itself."""
    if not path:
        return None
    p = Path(os.path.expanduser(path))
    return p.read_text().strip() if p.exists() else None
