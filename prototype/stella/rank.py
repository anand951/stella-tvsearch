"""Merges results from every source into one list and ranks the ways to watch each title."""
import re

# Lower is better.
OWNED, MY_SERVICE, FREE, ADDON, RENT, BUY, OTHER_SUBSCRIPTION = range(7)

TIER_LABELS = {
    OWNED: "in your library",
    MY_SERVICE: "on your services",
    FREE: "free",
    ADDON: "add-on channel",
    RENT: "rent",
    BUY: "buy",
    OTHER_SUBSCRIPTION: "not in your plan",
}


def tier(option, my_services):
    t = option["type"]
    if t == "owned":
        return OWNED
    if t == "subscription":
        return MY_SERVICE if option["service_id"] in my_services else OTHER_SUBSCRIPTION
    if t == "free":
        return FREE
    if t == "addon":
        return ADDON
    return RENT if t == "rent" else BUY


def _norm(title):
    return re.sub(r"[^a-z0-9]", "", title.lower())


def merge(*sources):
    """Combines titles from all sources. Same TMDB id (or IMDb id, then title + year) = same title."""
    merged, index = [], {}
    for titles in sources:
        for t in titles:
            keys = [k for k in (t.get("tmdb") and "tmdb:" + t["tmdb"],
                                t.get("imdb") and "imdb:" + t["imdb"],
                                f"name:{_norm(t['title'])}:{t.get('year')}") if k]
            existing = next((index[k] for k in keys if k in index), None)
            if existing is None:
                existing = {**t, "options": []}
                merged.append(existing)
            existing["options"].extend(t["options"])
            for k in keys:
                index[k] = existing
    return merged


def rank(titles, query, my_services):
    """Orders each title's options best-first, then orders titles by relevance and best option."""
    q = _norm(query)
    for t in titles:
        seen, options = set(), []
        for o in sorted(t["options"], key=lambda o: (tier(o, my_services), o.get("amount", 0))):
            # One entry per service and tier: the API lists each quality (SD/HD/4K) separately.
            key = (o["service_id"], o.get("addon_name"), tier(o, my_services))
            if key not in seen:
                seen.add(key)
                options.append({**o, "tier": tier(o, my_services)})
        t["options"] = options
        t["best_tier"] = options[0]["tier"] if options else 99

    def relevance(t):
        name = _norm(t["title"])
        return 0 if name == q else 1 if name.startswith(q) else 2 if q in name else 3

    return sorted(titles, key=lambda t: (relevance(t), t["best_tier"], -(t.get("year") or 0)))
