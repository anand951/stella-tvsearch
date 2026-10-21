package com.stellatv.search;

import android.content.Context;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Collections;
import java.util.Locale;

/**
 * Movies that are free with ads on YouTube. The Streaming Availability API doesn't cover YouTube,
 * but TMDB's watch providers (JustWatch data) do. TMDB doesn't say which video it is, so the
 * option opens a YouTube search for the movie; the official free copy is usually the top result.
 */
final class YouTubeSource {
    private static final String TMDB = "https://api.themoviedb.org/3";
    private static final long CACHE_MS = 24 * 3600 * 1000L;
    private static final int YOUTUBE = 192, YOUTUBE_FREE = 235;   // TMDB provider ids

    private final File cacheDir;
    private final String token, region;

    YouTubeSource(Context c, String tmdbToken, String country) {
        this.cacheDir = new File(c.getCacheDir(), "providers");
        this.token = tmdbToken;
        this.region = country.toUpperCase(Locale.ROOT);
    }

    boolean enabled() {
        return token != null && !token.isEmpty();
    }

    /** The "free on YouTube" option for a movie, or null. Call off the main thread. */
    Catalog.Option freeOption(Catalog.Title t) throws Exception {
        if (t.tmdb == null || !t.tmdb.matches("movie/\\d+")) return null;   // movies only for now
        File f = new File(cacheDir, t.tmdb.replace('/', '-') + ".json");
        String body;
        if (f.exists() && System.currentTimeMillis() - f.lastModified() < CACHE_MS) {
            body = new String(Files.readAllBytes(f.toPath()), StandardCharsets.UTF_8);
        } else {
            body = Http.getString(TMDB + "/" + t.tmdb + "/watch/providers",
                    Collections.singletonMap("Authorization", "Bearer " + token));
            cacheDir.mkdirs();
            Files.write(f.toPath(), body.getBytes(StandardCharsets.UTF_8));
        }
        JSONObject here = new JSONObject(body).optJSONObject("results");
        here = here == null ? null : here.optJSONObject(region);
        if (here == null || !(lists(here.optJSONArray("free")) || lists(here.optJSONArray("ads")))) return null;

        Catalog.Option o = new Catalog.Option();
        o.service = "youtube";
        o.serviceName = "YouTube";
        o.type = "free";
        o.color = 0xFFFF0000;
        String q = t.title + (t.year > 0 ? " " + t.year : "") + " full movie";
        o.link = "https://www.youtube.com/results?search_query=" + URLEncoder.encode(q, "UTF-8");
        return o;
    }

    private static boolean lists(JSONArray providers) {
        for (int i = 0; providers != null && i < providers.length(); i++) {
            int id = providers.optJSONObject(i).optInt("provider_id");
            if (id == YOUTUBE || id == YOUTUBE_FREE) return true;
        }
        return false;
    }
}
