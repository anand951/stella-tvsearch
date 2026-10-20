package com.stellatv.search;

import android.content.Context;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Streaming availability from the Streaming Availability API. Results are cached on disk for a day. */
final class StreamingSource {
    private static final String API = "https://api.movieofthenight.com/v4";
    private static final long CACHE_MS = 24 * 3600 * 1000L;

    private final File cacheDir;
    private final String apiKey, country;

    StreamingSource(Context c, String apiKey, String country) {
        this.cacheDir = new File(c.getCacheDir(), "streaming");
        this.apiKey = apiKey;
        this.country = country;
    }

    boolean enabled() {
        return apiKey != null && !apiKey.isEmpty();
    }

    /** Title search. The API matches whole words only, so this is the fallback for full words. */
    List<Catalog.Title> search(String query) throws Exception {
        String body = cached("search|" + country + "|" + query.trim().toLowerCase(), API + "/shows/search/title?country="
                + country + "&series_granularity=show&title=" + URLEncoder.encode(query.trim(), "UTF-8"));
        JSONArray shows = new JSONArray(body);
        List<Catalog.Title> out = new ArrayList<>();
        for (int i = 0; i < shows.length(); i++) out.add(title(shows.getJSONObject(i)));
        return out;
    }

    /** Where to watch one title, by TMDB id ("movie/106"). Returns null if the API doesn't know it. */
    Catalog.Title lookup(String tmdb) throws Exception {
        if (tmdb == null || !tmdb.matches("(movie|tv)/\\d+")) return null;
        String body;
        try {
            body = cached("show|" + country + "|" + tmdb, API + "/shows/" + tmdb + "?country=" + country + "&series_granularity=show");
        } catch (Exception e) {
            if (String.valueOf(e.getMessage()).contains("404")) return null;
            throw e;
        }
        return title(new JSONObject(body));
    }

    /** One API call per new key per day; repeats come from disk. */
    private String cached(String key, String url) throws Exception {
        File f = new File(cacheDir, sha(key) + ".json");
        if (f.exists() && System.currentTimeMillis() - f.lastModified() < CACHE_MS) {
            return new String(Files.readAllBytes(f.toPath()), StandardCharsets.UTF_8);
        }
        String body = Http.getString(url, Collections.singletonMap("X-API-Key", apiKey));
        cacheDir.mkdirs();
        Files.write(f.toPath(), body.getBytes(StandardCharsets.UTF_8));
        return body;
    }

    private Catalog.Title title(JSONObject s) {
        Catalog.Title t = new Catalog.Title();
        t.title = s.optString("title");
        t.year = s.optInt("releaseYear", s.optInt("firstAirYear", 0));
        t.kind = "movie".equals(s.optString("showType")) ? "movie" : "series";
        t.tmdb = s.optString("tmdbId", null);
        t.imdb = s.optString("imdbId", null);
        t.overview = s.optString("overview", null);
        t.runtimeMin = s.optInt("runtime", 0);
        t.availabilityLoaded = true;
        JSONArray genres = s.optJSONArray("genres");
        for (int i = 0; genres != null && i < genres.length() && i < 3; i++) t.genres.add(genres.optJSONObject(i).optString("name"));
        JSONObject images = s.optJSONObject("imageSet");
        if (images != null) {
            JSONObject poster = images.optJSONObject("verticalPoster");
            JSONObject backdrop = images.optJSONObject("horizontalBackdrop");
            if (poster != null) t.poster = poster.optString("w240", null);
            if (backdrop != null) t.backdrop = backdrop.optString("w1080", null);
        }
        JSONObject byCountry = s.optJSONObject("streamingOptions");
        JSONArray opts = byCountry == null ? null : byCountry.optJSONArray(country);
        for (int i = 0; opts != null && i < opts.length(); i++) {
            JSONObject o = opts.optJSONObject(i);
            Catalog.Option op = new Catalog.Option();
            op.service = o.optJSONObject("service").optString("id");
            op.serviceName = o.optJSONObject("service").optString("name");
            JSONObject addon = o.optJSONObject("addon");
            op.addonName = addon == null ? null : addon.optString("name");
            op.type = o.optString("type");
            JSONObject price = o.optJSONObject("price");
            op.price = price == null ? null : price.optString("formatted", null);
            op.amount = price == null ? 0 : price.optDouble("amount", 0);
            op.quality = o.optString("quality", null);
            op.link = o.optString("link", null);
            op.videoLink = o.optString("videoLink", null);
            t.options.add(op);
        }
        return t;
    }

    private static String sha(String s) throws Exception {
        byte[] d = MessageDigest.getInstance("SHA-256").digest(s.getBytes(StandardCharsets.UTF_8));
        StringBuilder b = new StringBuilder();
        for (int i = 0; i < 12; i++) b.append(String.format("%02x", d[i]));
        return b.toString();
    }
}
