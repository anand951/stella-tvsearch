package com.stellatv.search;

import android.content.Context;
import android.util.Log;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * A local list of the ~25,000 most popular movies and shows (English titles, from TMDB), kept in
 * the app's own storage and rebuilt weekly. Typing searches it instantly: every word you type
 * must start a word of the title, so "hun" finds The Hunger Games and The Hunted.
 */
final class TitleIndex {
    private static final String TAG = "StellaIndex";
    private static final String TMDB = "https://api.themoviedb.org/3";
    private static final String POSTERS = "https://image.tmdb.org/t/p/w342";
    private static final long MAX_AGE_MS = 7L * 24 * 3600 * 1000;
    private static final int FILE_VERSION = 2;   // 2: popularity includes vote count

    /** One TMDB list to page through: {path, pages}. TMDB serves at most 500 pages (10,000 titles). */
    private static final Object[][] LISTS = {
            {"/discover/movie?sort_by=popularity.desc", 500},
            {"/discover/movie?sort_by=vote_count.desc", 500},   // well-known classics
            {"/discover/tv?sort_by=popularity.desc", 500},
            {"/discover/movie?sort_by=popularity.desc&with_original_language=hi", 200}, // Hindi films
    };

    static final class Entry {
        final String tmdb, title, poster;   // tmdb = "movie/106" or "tv/1396"
        final int year;
        final float popularity;
        final String[] words;

        Entry(String tmdb, String title, int year, float popularity, String poster) {
            this.tmdb = tmdb;
            this.title = title;
            this.year = year;
            this.popularity = popularity;
            this.poster = poster;
            this.words = words(title);
        }
    }

    interface Progress { void update(int percent); }

    private final File file;
    private final String token;
    private volatile List<Entry> entries = Collections.emptyList();
    private boolean oldFormat;

    TitleIndex(Context c, String tmdbToken) {
        this.file = new File(c.getFilesDir(), "titles.tsv");
        this.token = tmdbToken;
    }

    boolean enabled() { return token != null && !token.isEmpty(); }
    boolean ready() { return !entries.isEmpty(); }
    int size() { return entries.size(); }
    long updated() { return file.lastModified(); }
    boolean stale() { return oldFormat || !file.exists() || System.currentTimeMillis() - file.lastModified() > MAX_AGE_MS; }

    /** Loads the saved list, if any. Call off the main thread. */
    void load() {
        if (!file.exists()) return;
        List<Entry> out = new ArrayList<>(30000);
        try (BufferedReader r = new BufferedReader(new InputStreamReader(new FileInputStream(file), StandardCharsets.UTF_8))) {
            String header = r.readLine();
            if (header == null || !header.equals("v" + FILE_VERSION)) { oldFormat = true; return; }
            for (String line; (line = r.readLine()) != null; ) {
                String[] f = line.split("\t", -1);
                if (f.length < 5) continue;
                out.add(new Entry(f[0], f[1], parseInt(f[2]), Float.parseFloat(f[3]), f[4].isEmpty() ? null : f[4]));
            }
            entries = out;
            Log.i(TAG, "Loaded " + out.size() + " titles");
        } catch (Exception e) {
            Log.w(TAG, "Could not read title list", e);
        }
    }

    /** Downloads the lists from TMDB and saves them. Takes about a minute. Call off the main thread. */
    void rebuild(Progress progress) throws Exception {
        List<String> urls = new ArrayList<>();
        for (Object[] list : LISTS) {
            for (int page = 1; page <= (int) list[1]; page++) {
                urls.add(TMDB + list[0] + "&include_adult=false&language=en-US&page=" + page);
            }
        }
        Map<String, Entry> byId = Collections.synchronizedMap(new LinkedHashMap<>());
        Map<String, String> auth = Collections.singletonMap("Authorization", "Bearer " + token);
        AtomicInteger done = new AtomicInteger(), failed = new AtomicInteger();
        ExecutorService pool = Executors.newFixedThreadPool(4);
        for (String url : urls) {
            pool.execute(() -> {
                try {
                    boolean tv = url.contains("/discover/tv");
                    JSONArray results = new JSONObject(getWithRetry(url, auth)).getJSONArray("results");
                    for (int i = 0; i < results.length(); i++) {
                        JSONObject m = results.getJSONObject(i);
                        String title = m.optString(tv ? "name" : "title").replace('\t', ' ').replace('\n', ' ').trim();
                        if (title.isEmpty()) continue;
                        String date = m.optString(tv ? "first_air_date" : "release_date", "");
                        String poster = m.isNull("poster_path") ? "" : m.optString("poster_path", "");
                        String id = (tv ? "tv/" : "movie/") + m.getLong("id");
                        byId.put(id, new Entry(id, title, date.length() >= 4 ? parseInt(date.substring(0, 4)) : 0,
                                score(m.optDouble("popularity", 0), m.optInt("vote_count", 0)), poster));
                    }
                } catch (Exception e) {
                    failed.incrementAndGet();
                }
                int n = done.incrementAndGet();
                if (progress != null && n % 20 == 0) progress.update(n * 100 / urls.size());
            });
        }
        pool.shutdown();
        pool.awaitTermination(10, TimeUnit.MINUTES);
        if (byId.size() < 1000) throw new Exception("TMDB returned too few titles (" + failed.get() + " pages failed)");

        List<Entry> out = new ArrayList<>(byId.values());
        out.sort(Comparator.comparingDouble((Entry e) -> -e.popularity));
        File tmp = new File(file.getPath() + ".tmp");
        try (Writer w = new OutputStreamWriter(new FileOutputStream(tmp), StandardCharsets.UTF_8)) {
            w.write("v" + FILE_VERSION + "\n");
            for (Entry e : out) {
                w.write(e.tmdb + "\t" + e.title + "\t" + e.year + "\t" + e.popularity + "\t" + (e.poster == null ? "" : e.poster) + "\n");
            }
        }
        if (!tmp.renameTo(file)) throw new Exception("Could not save title list");
        // Too many gaps (e.g. the TV slept mid-download): keep what we have, but try again next time.
        if (failed.get() > urls.size() / 20) file.setLastModified(0);
        oldFormat = false;
        entries = out;
        Log.i(TAG, "Built " + out.size() + " titles (" + failed.get() + " of " + urls.size() + " pages failed)");
    }

    /**
     * TMDB's popularity is what's trending this week, so on its own Hunter x Hunter (100, 2k votes)
     * beats The Hunger Games (24, 24k votes). Adding the vote count lets well-known titles win while
     * a new release that everyone's watching can still rise to the top.
     */
    private static float score(double popularity, int votes) {
        return (float) (popularity + votes / 100.0);
    }

    private static String getWithRetry(String url, Map<String, String> headers) throws Exception {
        for (int attempt = 1; ; attempt++) {
            try {
                return Http.getString(url, headers);
            } catch (Exception e) {
                if (attempt == 3) throw e;
                Thread.sleep(1000L * attempt);
            }
        }
    }

    /** Titles where each typed word starts a word of the title. Exact and leading matches first, then popularity. */
    List<Entry> search(String query, int limit) {
        String[] q = words(query);
        if (q.length == 0) return Collections.emptyList();
        String joined = String.join(" ", q);
        List<Entry> hits = new ArrayList<>();
        for (Entry e : entries) if (matches(e.words, q)) hits.add(e);
        hits.sort(Comparator.<Entry>comparingInt(e -> rank(e, joined)).thenComparingDouble(e -> -e.popularity));
        return hits.size() > limit ? new ArrayList<>(hits.subList(0, limit)) : hits;
    }

    static Catalog.Title toTitle(Entry e) {
        Catalog.Title t = new Catalog.Title();
        t.title = e.title;
        t.year = e.year;
        t.kind = e.tmdb.startsWith("movie/") ? "movie" : "series";
        t.tmdb = e.tmdb;
        t.popularity = e.popularity;
        if (e.poster != null) t.poster = POSTERS + e.poster;
        return t;
    }

    private static boolean matches(String[] title, String[] q) {
        outer:
        for (String w : q) {
            for (String t : title) if (t.startsWith(w)) continue outer;
            return false;
        }
        return true;
    }

    /** 0 = the title is exactly the query, 1 = it starts with it, 2 = the words appear further in. */
    private static int rank(Entry e, String joined) {
        String t = String.join(" ", e.words);
        return t.equals(joined) ? 0 : t.startsWith(joined) || t.startsWith("the " + joined) ? 1 : 2;
    }

    /** Lowercase words with accents removed: "Amélie" -> ["amelie"]. */
    static String[] words(String s) {
        String folded = Normalizer.normalize(s, Normalizer.Form.NFD).replaceAll("\\p{M}", "").toLowerCase(Locale.ROOT);
        List<String> out = new ArrayList<>();
        for (String w : folded.split("[^\\p{L}\\p{N}]+")) if (!w.isEmpty()) out.add(w);
        return out.toArray(new String[0]);
    }

    private static int parseInt(String s) {
        try { return Integer.parseInt(s.trim()); } catch (Exception e) { return 0; }
    }
}
