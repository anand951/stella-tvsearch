"""Tiny JSON file cache, so repeat searches don't spend API calls."""
import hashlib
import json
import time

from .config import CACHE_DIR


def _path(namespace, key):
    digest = hashlib.sha256(key.encode()).hexdigest()[:24]
    return CACHE_DIR / namespace / f"{digest}.json"


def get(namespace, key, max_age):
    p = _path(namespace, key)
    if p.exists() and time.time() - p.stat().st_mtime < max_age:
        return json.loads(p.read_text())
    return None


def put(namespace, key, value):
    p = _path(namespace, key)
    p.parent.mkdir(parents=True, exist_ok=True)
    p.write_text(json.dumps(value))
