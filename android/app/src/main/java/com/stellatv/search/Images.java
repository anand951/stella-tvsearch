package com.stellatv.search;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Handler;
import android.os.Looper;
import android.util.LruCache;
import android.widget.ImageView;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Loads poster and backdrop images off the main thread, with a small memory cache. */
final class Images {
    private static final LruCache<String, Bitmap> cache = new LruCache<String, Bitmap>(48 * 1024 * 1024) {
        @Override protected int sizeOf(String k, Bitmap b) { return b.getByteCount(); }
    };
    private static final ExecutorService pool = Executors.newFixedThreadPool(4);
    private static final Handler main = new Handler(Looper.getMainLooper());

    interface Done { void loaded(Bitmap b); }

    static void load(String url, Done done) {
        if (url == null) return;
        Bitmap hit = cache.get(url);
        if (hit != null) { done.loaded(hit); return; }
        pool.execute(() -> {
            try {
                byte[] data = Http.get(url, null);
                Bitmap b = BitmapFactory.decodeByteArray(data, 0, data.length);
                if (b == null) return;
                cache.put(url, b);
                main.post(() -> done.loaded(b));
            } catch (Exception ignored) { }
        });
    }

    /** Sets the image unless the view has since been reused for another URL. */
    static void into(ImageView v, String url) {
        v.setTag(url);
        load(url, b -> { if (url.equals(v.getTag())) v.setImageBitmap(b); });
    }

    private Images() { }
}
