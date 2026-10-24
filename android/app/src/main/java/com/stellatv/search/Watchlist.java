package com.stellatv.search;

import android.content.Context;
import android.util.Log;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;

/**
 * Titles saved for later, kept in a small file on this TV (no account or server needed). Only the
 * title's details are stored; where it streams is looked up fresh each time it's shown.
 */
final class Watchlist {
    private static final String TAG = "StellaWatchlist";

    static final class Saved {
        final Catalog.Title title;
        final long savedAt;
        Saved(Catalog.Title title, long savedAt) { this.title = title; this.savedAt = savedAt; }
    }

    private final File file;
    private final List<Saved> items = new ArrayList<>();   // newest first

    Watchlist(Context c) {
        file = new File(c.getFilesDir(), "watchlist.json");
        load();
    }

    int size() {
        return items.size();
    }

    boolean contains(Catalog.Title t) {
        return find(t) >= 0;
    }

    /** When a title was saved (ms), or 0 if it isn't on the list. */
    long savedAt(Catalog.Title t) {
        int i = find(t);
        return i < 0 ? 0 : items.get(i).savedAt;
    }

    void add(Catalog.Title t) {
        if (contains(t)) return;
        items.add(0, new Saved(t, System.currentTimeMillis()));
        save();
    }

    void remove(Catalog.Title t) {
        int i = find(t);
        if (i < 0) return;
        items.remove(i);
        save();
    }

    /** Fresh copies of the saved titles, newest first, ready to look up where they stream. */
    List<Catalog.Title> titles() {
        List<Catalog.Title> out = new ArrayList<>();
        for (Saved s : items) out.add(fromJson(toJson(s.title)));
        return out;
    }

    /** The TMDB id when there is one, else the name and year (titles only in Plex may lack an id). */
    static String key(Catalog.Title t) {
        return t.tmdb != null ? t.tmdb : "name:" + Catalog.norm(t.title) + ":" + t.year;
    }

    private int find(Catalog.Title t) {
        String k = key(t);
        for (int i = 0; i < items.size(); i++) if (key(items.get(i).title).equals(k)) return i;
        return -1;
    }

    private void load() {
        if (!file.exists()) return;
        try {
            JSONArray a = new JSONArray(new String(Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8));
            for (int i = 0; i < a.length(); i++) {
                JSONObject o = a.getJSONObject(i);
                items.add(new Saved(fromJson(o), o.optLong("savedAt")));
            }
        } catch (Exception e) {
            Log.w(TAG, "Could not read the watchlist", e);
        }
    }

    private void save() {
        try {
            JSONArray a = new JSONArray();
            for (Saved s : items) a.put(toJson(s.title).put("savedAt", s.savedAt));
            // Write a new file and swap it in, so a crash mid-write can't lose the list.
            File tmp = new File(file.getPath() + ".tmp");
            Files.write(tmp.toPath(), a.toString().getBytes(StandardCharsets.UTF_8));
            if (!tmp.renameTo(file)) throw new java.io.IOException("rename failed");
        } catch (Exception e) {
            Log.w(TAG, "Could not save the watchlist", e);
        }
    }

    private static JSONObject toJson(Catalog.Title t) {
        try {
            JSONObject o = new JSONObject();
            o.put("title", t.title).put("kind", t.kind).put("year", t.year).put("runtimeMin", t.runtimeMin);
            o.putOpt("tmdb", t.tmdb).putOpt("imdb", t.imdb).putOpt("overview", t.overview)
                    .putOpt("poster", t.poster).putOpt("backdrop", t.backdrop);
            o.put("genres", new JSONArray(t.genres));
            return o;
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private static Catalog.Title fromJson(JSONObject o) {
        Catalog.Title t = new Catalog.Title();
        t.title = o.optString("title");
        t.kind = o.optString("kind", "movie");
        t.year = o.optInt("year");
        t.runtimeMin = o.optInt("runtimeMin");
        t.tmdb = o.optString("tmdb", null);
        t.imdb = o.optString("imdb", null);
        t.overview = o.optString("overview", null);
        t.poster = o.optString("poster", null);
        t.backdrop = o.optString("backdrop", null);
        JSONArray g = o.optJSONArray("genres");
        for (int i = 0; g != null && i < g.length(); i++) t.genres.add(g.optString(i));
        t.checkPlex = true;   // whether it's in the library now is looked up, like where it streams
        return t;
    }
}
