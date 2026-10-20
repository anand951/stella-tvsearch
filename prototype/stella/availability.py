"""Streaming availability from the Streaming Availability API (movieofthenight.com)."""
import json
import urllib.parse
import urllib.request

from . import cache

API = "https://api.movieofthenight.com/v4"
CACHE_SECONDS = 24 * 3600


class AvailabilityClient:
    def __init__(self, api_key, country):
        self.api_key = api_key
        self.country = country
        self.calls = 0

    def search(self, query):
        """Returns titles matching the query, each with its streaming options for our country."""
        key = f"{self.country}|{query.strip().lower()}"
        shows = cache.get("search", key, CACHE_SECONDS)
        if shows is None:
            params = urllib.parse.urlencode(
                {"title": query, "country": self.country, "series_granularity": "show"})
            req = urllib.request.Request(f"{API}/shows/search/title?{params}",
                                         headers={"X-API-Key": self.api_key})
            with urllib.request.urlopen(req, timeout=20) as r:
                shows = json.load(r)
            self.calls += 1
            cache.put("search", key, shows)
        return [self._title(s) for s in shows]

    def _title(self, show):
        options = []
        for o in show.get("streamingOptions", {}).get(self.country, []):
            addon = o.get("addon") or {}
            options.append({
                "source": "streaming",
                "service_id": o["service"]["id"],
                "service_name": o["service"]["name"],
                "addon_name": addon.get("name"),
                "type": o["type"],  # subscription, free, addon, rent, buy
                "price": (o.get("price") or {}).get("formatted"),
                "amount": float((o.get("price") or {}).get("amount") or 0),
                "quality": o.get("quality"),
                "link": o.get("link"),
                "video_link": o.get("videoLink"),
            })
        return {
            "title": show["title"],
            "year": show.get("releaseYear") or show.get("firstAirYear"),
            "kind": "movie" if show.get("showType") == "movie" else "series",
            "tmdb": show.get("tmdbId"),  # e.g. "movie/106"
            "imdb": show.get("imdbId"),
            "options": options,
        }
