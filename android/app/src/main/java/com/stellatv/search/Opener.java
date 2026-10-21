package com.stellatv.search;

import android.content.Intent;
import android.net.Uri;

import java.util.HashMap;
import java.util.Map;

/** Opens a title in the right app. Each rule was tested on a SHIELD TV; see docs/FINDINGS.md. */
final class Opener {
    private static final Map<String, String> PACKAGES = new HashMap<>();
    static {
        PACKAGES.put("plex", "com.plexapp.android");
        PACKAGES.put("netflix", "com.netflix.ninja");
        PACKAGES.put("hbo", "com.wbd.stream");
        PACKAGES.put("paramount", "com.cbs.ott");
        PACKAGES.put("disney", "com.disney.disneyplus");
        PACKAGES.put("hulu", "com.hulu.livingroomplus");
        PACKAGES.put("prime", "com.amazon.amazonvideo.livingroom");
        PACKAGES.put("youtube", "com.google.android.youtube.tv");   // opens a search for the movie
    }

    static boolean canOpen(Catalog.Option o) {
        return intent(o) != null;
    }

    static Intent intent(Catalog.Option o) {
        String pkg = PACKAGES.get(o.service);
        if (pkg == null) return null;
        if (o.service.equals("plex")) {
            return safe(o.plexLink, "plex") ? view(o.plexLink, pkg) : null;
        }
        String url = o.videoLink != null ? o.videoLink : o.link;
        if (!safe(url, "http", "https")) return null;
        if (o.service.equals("netflix")) {
            // Netflix ignores plain deep links; it needs the partner-style launch.
            return new Intent(Intent.ACTION_VIEW, Uri.parse(url.replaceFirst("^https://", "http://")))
                    .setClassName(pkg, "com.netflix.ninja.MainActivity")
                    .addFlags(0x10808000)
                    .putExtra("source", "30");
        }
        return view(url, pkg);
    }

    private static Intent view(String url, String pkg) {
        // CLEAR_TASK restarts the app on the new link; some apps ignore links when already running.
        return new Intent(Intent.ACTION_VIEW, Uri.parse(url)).setPackage(pkg)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
    }

    /** Links come from an external API: accept only expected schemes and no control characters. */
    private static boolean safe(String url, String... schemes) {
        if (url == null) return false;
        for (char ch : url.toCharArray()) if (ch < 32 || ch == 127) return false;
        String scheme = Uri.parse(url).getScheme();
        for (String s : schemes) if (s.equals(scheme)) return true;
        return false;
    }

    private Opener() { }
}
