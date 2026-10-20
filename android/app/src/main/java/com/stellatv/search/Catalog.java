package com.stellatv.search;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/** Search results from every source, merged into one list and ranked. Mirrors prototype/stella/rank.py. */
final class Catalog {

    // Where an option ranks. Lower is better.
    static final int OWNED = 0, MY_SERVICE = 1, FREE = 2, ADDON = 3, RENT = 4, BUY = 5, OTHER_SUBSCRIPTION = 6;

    static final class Option {
        String service, serviceName, addonId, addonName, type, price, quality, link, videoLink, plexLink;
        int color;   // the service's brand colour, for a logo tile when its app isn't installed
        double amount;
        int tier;

        /** Short label for a button: "Plex", "Netflix", "Rent · $3.99 · Prime Video". */
        String label() {
            String name = addonName != null ? addonName + " (" + serviceName + ")" : serviceName;
            switch (tier) {
                case RENT: return "Rent " + (price != null ? price : "") + " · " + name;
                case BUY: return "Buy " + (price != null ? price : "") + " · " + name;
                case FREE: return name + " · Free";
                case OTHER_SUBSCRIPTION: return name + " · not in your plan";
                default: return name;
            }
        }
    }

    static final class Title {
        String title, kind, tmdb, imdb, overview, poster, backdrop;
        int year, runtimeMin;
        float popularity;
        boolean availabilityLoaded;   // streaming options looked up (titles from the local list start without)
        List<String> genres = new ArrayList<>();
        List<Option> options = new ArrayList<>();
        int bestTier = 99;

        Option bestLaunchable() {
            for (Option o : options) if (Opener.canOpen(o)) return o;
            return null;
        }
    }

    static int tier(Option o, Set<String> myServices) {
        switch (o.type) {
            case "owned": return OWNED;
            case "subscription": return myServices.contains(o.service) ? MY_SERVICE : OTHER_SUBSCRIPTION;
            case "free": return FREE;
            // e.g. Hulu inside Disney+: if you already pay for Hulu, it's included.
            case "addon": return o.addonId != null && myServices.contains(o.addonId) ? MY_SERVICE : ADDON;
            case "rent": return RENT;
            default: return BUY;
        }
    }

    static String norm(String s) {
        return s == null ? "" : s.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]", "");
    }

    /** Same TMDB id, then IMDb id, then title + year = same title. Library details win over streaming ones. */
    static List<Title> merge(List<List<Title>> sources) {
        List<Title> merged = new ArrayList<>();
        Map<String, Title> index = new HashMap<>();
        for (List<Title> titles : sources) {
            for (Title t : titles) {
                List<String> keys = new ArrayList<>();
                if (t.tmdb != null) keys.add("tmdb:" + t.tmdb);
                if (t.imdb != null) keys.add("imdb:" + t.imdb);
                keys.add("name:" + norm(t.title) + ":" + t.year);
                Title existing = null;
                for (String k : keys) if (index.containsKey(k)) { existing = index.get(k); break; }
                if (existing == null) {
                    merged.add(t);
                    existing = t;
                } else {
                    existing.options.addAll(t.options);
                    existing.popularity = Math.max(existing.popularity, t.popularity);
                    existing.availabilityLoaded |= t.availabilityLoaded;
                    if (existing.poster == null) existing.poster = t.poster;
                    if (existing.backdrop == null) existing.backdrop = t.backdrop;
                    if (existing.overview == null) existing.overview = t.overview;
                    if (existing.genres.isEmpty()) existing.genres = t.genres;
                }
                for (String k : keys) index.put(k, existing);
            }
        }
        return merged;
    }

    /**
     * Orders each title's options best-first (one per service and tier), then titles by relevance,
     * your library first, then popularity. Streaming options arrive later for local-list titles, so
     * they don't affect title order (posters shouldn't jump around while you look at them).
     */
    static List<Title> rank(List<Title> titles, String query, Set<String> myServices) {
        for (Title t : titles) sortOptions(t, myServices);
        String q = norm(query);

        // The API also matches foreign and alternative titles, which look random here. Keep titles
        // whose name contains the query, unless that leaves nothing (e.g. a typo).
        List<Title> out = new ArrayList<>();
        for (Title t : titles) if (relevance(t, q) < 3) out.add(t);
        if (out.isEmpty()) out.addAll(titles);
        Collections.sort(out, Comparator.<Title>comparingInt(t -> relevance(t, q))
                .thenComparingInt(t -> t.bestTier == OWNED ? 0 : 1)
                .thenComparingDouble(t -> -t.popularity)
                .thenComparingInt(t -> -t.year));
        return out;
    }

    /** Ranks a title's ways to watch, best first, keeping one per service and tier. */
    static void sortOptions(Title t, Set<String> myServices) {
        for (Option o : t.options) o.tier = tier(o, myServices);
        t.options.sort(Comparator.<Option>comparingInt(o -> o.tier).thenComparingDouble(o -> o.amount)
                .thenComparingInt(o -> o.addonId == null ? 0 : 1));   // the service's own app before an add-on
        Set<String> seen = new HashSet<>();
        List<Option> unique = new ArrayList<>();
        for (Option o : t.options) {
            // The API lists each quality (SD/HD/4K) separately; keep the best one. An included add-on
            // (Hulu in Disney+) counts as the service it adds, so it isn't shown twice.
            String what = o.tier == MY_SERVICE && o.addonId != null ? o.addonId : o.addonName == null ? o.service : o.service + "|" + o.addonName;
            if (seen.add(what + "|" + o.tier)) unique.add(o);
        }
        t.options = unique;
        t.bestTier = unique.isEmpty() ? 99 : unique.get(0).tier;
    }

    private static int relevance(Title t, String q) {
        String n = norm(t.title);
        // "The Hunger Games" starts with "hun" as far as people are concerned.
        String bare = t.title == null ? n : norm(t.title.replaceFirst("(?i)^(the|a|an)\\s+", ""));
        return n.equals(q) || bare.equals(q) ? 0 : n.startsWith(q) || bare.startsWith(q) ? 1 : n.contains(q) ? 2 : 3;
    }

    static Set<String> csv(String s) {
        Set<String> out = new HashSet<>();
        for (String p : s.split(",")) if (!p.trim().isEmpty()) out.add(p.trim());
        return out;
    }

    private Catalog() { }
}
