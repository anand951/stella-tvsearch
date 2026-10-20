"""Local Plex library search."""
import json
import re
import urllib.parse
import urllib.request


class PlexClient:
    def __init__(self, url, token=None):
        self.url = url.rstrip("/")
        self.token = token
        self._machine_id = None

    def _get(self, path, **params):
        if self.token:
            params["X-Plex-Token"] = self.token
        q = ("?" + urllib.parse.urlencode(params)) if params else ""
        req = urllib.request.Request(self.url + path + q, headers={"Accept": "application/json"})
        with urllib.request.urlopen(req, timeout=10) as r:
            return json.load(r)["MediaContainer"]

    @property
    def machine_id(self):
        if self._machine_id is None:
            self._machine_id = self._get("/identity")["machineIdentifier"]
        return self._machine_id

    def search(self, query):
        """Returns matching movies and shows in the library, with their TMDB ids for matching."""
        found = self._get("/search", query=query, includeGuids=1, limit=20)
        titles = []
        for m in found.get("Metadata", []):
            if m.get("type") not in ("movie", "show"):
                continue
            kind = "movie" if m["type"] == "movie" else "series"
            guids = {g["id"].split("://")[0]: g["id"].split("://")[1] for g in m.get("Guid", [])}
            media = (m.get("Media") or [{}])[0]
            titles.append({
                "title": m["title"],
                "year": m.get("year"),
                "kind": kind,
                "tmdb": f"{'movie' if kind == 'movie' else 'tv'}/{guids['tmdb']}" if "tmdb" in guids else None,
                "imdb": guids.get("imdb"),
                "options": [{
                    "source": "plex",
                    "service_id": "plex",
                    "service_name": "Plex (your library)",
                    "type": "owned",
                    "amount": 0,
                    "quality": media.get("videoResolution"),
                    "rating_key": m["ratingKey"],
                    "plex_link": self.app_link(kind, m["title"], m["ratingKey"]),
                }],
            })
        return titles

    def app_link(self, kind, title, rating_key):
        """Opens the item's page in the Plex Android TV app (Watch is focused; one OK press plays)."""
        slug = re.sub(r"[^a-z0-9]+", "-", title.lower()).strip("-") or "item"
        section = "movie" if kind == "movie" else "show"
        return f"plex://libraries/{self.machine_id}/{section}/{slug}/{rating_key}"
