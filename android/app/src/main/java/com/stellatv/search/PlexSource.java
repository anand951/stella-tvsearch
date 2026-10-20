package com.stellatv.search;

import org.json.JSONArray;
import org.json.JSONObject;

import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Local Plex library search. Relies on the server's "allowed without auth" list for this TV,
 * so no token is stored on the device.
 */
final class PlexSource {
    private static final Map<String, String> JSON = Collections.singletonMap("Accept", "application/json");
    private final String url;
    private String machineId;

    PlexSource(String url) {
        this.url = url == null ? "" : url.replaceAll("/+$", "");
    }

    boolean enabled() {
        return !url.isEmpty();
    }

    List<Catalog.Title> search(String query) throws Exception {
        if (machineId == null) {
            machineId = new JSONObject(Http.getString(url + "/identity", JSON))
                    .getJSONObject("MediaContainer").getString("machineIdentifier");
        }
        JSONObject mc = new JSONObject(Http.getString(url + "/search?includeGuids=1&limit=20&query="
                + URLEncoder.encode(query.trim(), "UTF-8"), JSON)).getJSONObject("MediaContainer");
        JSONArray items = mc.optJSONArray("Metadata");
        List<Catalog.Title> out = new ArrayList<>();
        for (int i = 0; items != null && i < items.length(); i++) {
            JSONObject m = items.getJSONObject(i);
            String type = m.optString("type");
            if (!type.equals("movie") && !type.equals("show")) continue;
            out.add(title(m, type.equals("movie") ? "movie" : "series"));
        }
        return out;
    }

    private Catalog.Title title(JSONObject m, String kind) {
        Catalog.Title t = new Catalog.Title();
        t.title = m.optString("title");
        t.year = m.optInt("year", 0);
        t.kind = kind;
        t.overview = m.optString("summary", null);
        t.runtimeMin = (int) (m.optLong("duration", 0) / 60000);
        JSONArray guids = m.optJSONArray("Guid");
        for (int i = 0; guids != null && i < guids.length(); i++) {
            String id = guids.optJSONObject(i).optString("id");
            if (id.startsWith("tmdb://")) t.tmdb = (kind.equals("movie") ? "movie/" : "tv/") + id.substring(7);
            if (id.startsWith("imdb://")) t.imdb = id.substring(7);
        }
        JSONArray genres = m.optJSONArray("Genre");
        for (int i = 0; genres != null && i < genres.length() && i < 3; i++) t.genres.add(genres.optJSONObject(i).optString("tag"));
        t.poster = image(m.optString("thumb", null), 240, 360);
        t.backdrop = image(m.optString("art", null), 1280, 720);

        Catalog.Option o = new Catalog.Option();
        o.service = "plex";
        o.serviceName = "Plex";
        o.type = "owned";
        JSONArray media = m.optJSONArray("Media");
        if (media != null && media.length() > 0) o.quality = media.optJSONObject(0).optString("videoResolution", null);
        String slug = t.title.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", "-").replaceAll("^-|-$", "");
        // Opens the item page in the Plex app with Watch focused (see docs/FINDINGS.md).
        o.plexLink = "plex://libraries/" + machineId + "/" + (kind.equals("movie") ? "movie" : "show") + "/"
                + (slug.isEmpty() ? "item" : slug) + "/" + m.optString("ratingKey");
        t.options.add(o);
        return t;
    }

    private String image(String path, int w, int h) {
        if (path == null || path.isEmpty()) return null;
        try {
            return url + "/photo/:/transcode?width=" + w + "&height=" + h + "&minSize=1&url=" + URLEncoder.encode(path, "UTF-8");
        } catch (Exception e) {
            return null;
        }
    }
}
