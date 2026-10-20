"""Command line: `stella search <title>` and `stella play <number>`."""
import argparse
import json
import shutil
import sys
import urllib.error

from . import config, launch, rank
from .availability import AvailabilityClient
from .config import CACHE_DIR
from .plex import PlexClient

LAST_RESULTS = CACHE_DIR / "last.json"
LAUNCHABLE_MARK = "▶"


def search(args, cfg):
    sources, notes = [], []
    if cfg["plex"]:
        plex = PlexClient(cfg["plex"]["url"], config.read_secret(cfg["plex"].get("token_file")))
        try:
            sources.append(plex.search(args.query))
        except urllib.error.HTTPError as e:
            notes.append(f"Plex: HTTP {e.code} (see prototype/README.md, 'Plex access')")
        except OSError as e:
            notes.append(f"Plex: {e}")
    api_key = config.read_secret(cfg["api_key_file"])
    if api_key:
        client = AvailabilityClient(api_key, cfg["country"])
        sources.append(client.search(args.query))
        notes.append(f"Streaming API calls used: {client.calls} (0 = served from cache)")
    else:
        notes.append("Streaming: no API key file, skipped")

    titles = rank.rank(rank.merge(*sources), args.query, set(cfg["my_services"]))[: args.limit]
    LAST_RESULTS.parent.mkdir(parents=True, exist_ok=True)
    LAST_RESULTS.write_text(json.dumps(titles))
    if args.json:
        print(json.dumps(titles, indent=2))
        return
    print_results(titles, args.all)
    for n in notes:
        print(f"  · {n}", file=sys.stderr)


def print_results(titles, show_all):
    width = min(shutil.get_terminal_size().columns, 110)
    for i, t in enumerate(titles, 1):
        kind = "" if t["kind"] == "movie" else "  [series]"
        print(f"\n{i:>2}. {t['title']} ({t.get('year') or '?'}){kind}")
        if not t["options"]:
            print("      not available to stream in this country")
        shown = t["options"] if show_all else _summarise(t["options"])
        for o in shown:
            mark = LAUNCHABLE_MARK if launch.intent_args(o) else " "
            name = o["service_name"] + (f" via {o['addon_name']}" if o.get("addon_name") else "")
            extra = " ".join(x for x in (o.get("price"), o.get("quality") and o["quality"].upper()) if x)
            line = f"    {mark} {rank.TIER_LABELS[o['tier']]:<17} {name}  {extra}"
            print(line[:width])
    print(f"\n  {LAUNCHABLE_MARK} = can open on the TV.  stella play <number> [option]")


def _summarise(options):
    """The best option, plus at most one per remaining tier, so the list stays short."""
    out, tiers = [], set()
    for o in options:
        if o["tier"] <= rank.MY_SERVICE or o["tier"] not in tiers:
            out.append(o)
            tiers.add(o["tier"])
    return out[:6]


def play(args, cfg):
    if not cfg["tv"]:
        sys.exit("Set \"tv\" (the TV's ADB address) in ~/.config/stella-tv/config.json")
    if not LAST_RESULTS.exists():
        sys.exit("Search first: stella search <title>")
    titles = json.loads(LAST_RESULTS.read_text())
    t = titles[args.number - 1]
    choices = [o for o in t["options"] if launch.intent_args(o)]
    if not choices:
        sys.exit(f"No way to open {t['title']} on the TV yet")
    o = choices[args.option - 1] if args.option else choices[0]
    print(f"Opening {t['title']} on {o['service_name']}…")
    launch.launch(cfg["tv"], o)


def main():
    p = argparse.ArgumentParser(prog="stella", description="One search across your library and streaming services.")
    sub = p.add_subparsers(dest="cmd", required=True)
    s = sub.add_parser("search", help="find where to watch a title")
    s.add_argument("query", nargs="+")
    s.add_argument("--all", action="store_true", help="show every option, not a summary")
    s.add_argument("--json", action="store_true")
    s.add_argument("--limit", type=int, default=8)
    pl = sub.add_parser("play", help="open a result from the last search on the TV")
    pl.add_argument("number", type=int)
    pl.add_argument("option", type=int, nargs="?", help="which ▶ option (default: the best)")
    args = p.parse_args()
    cfg = config.load()
    if args.cmd == "search":
        args.query = " ".join(args.query)
        search(args, cfg)
    else:
        play(args, cfg)
