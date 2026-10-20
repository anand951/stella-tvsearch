package com.stellatv.search;

import android.app.Activity;
import android.content.Context;
import android.graphics.Outline;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.util.TypedValue;
import android.view.View;
import android.view.ViewOutlineProvider;
import android.widget.TextView;

import java.util.HashMap;
import java.util.Map;

/** Shared helpers: everything is laid out in "design pixels" of a 1920x1080 screen (the mockup). */
final class Ui {
    static float scale = 1f;
    private static final Map<Integer, Typeface> faces = new HashMap<>();

    static void init(Activity a) {
        int w = Math.max(a.getResources().getDisplayMetrics().widthPixels,
                a.getResources().getDisplayMetrics().heightPixels);
        scale = w / 1920f;
    }

    static int px(float designPx) {
        return Math.round(designPx * scale);
    }

    static Typeface font(Context c, int weight) {
        Typeface t = faces.get(weight);
        if (t == null) {
            try {
                t = new Typeface.Builder(c.getAssets(), "figtree.ttf")
                        .setFontVariationSettings("'wght' " + weight).build();
            } catch (Exception e) {
                t = Typeface.create(Typeface.SANS_SERIF, weight >= 600 ? Typeface.BOLD : Typeface.NORMAL);
            }
            faces.put(weight, t);
        }
        return t;
    }

    static TextView text(Context c, float size, int weight, int color) {
        TextView t = new TextView(c);
        t.setTextSize(TypedValue.COMPLEX_UNIT_PX, px(size));
        t.setTypeface(font(c, weight));
        t.setTextColor(color);
        t.setIncludeFontPadding(false);
        t.setSingleLine(true);
        return t;
    }

    static GradientDrawable rounded(int fill, float radius, int strokeColor, float strokeWidth) {
        GradientDrawable g = new GradientDrawable();
        g.setColor(fill);
        g.setCornerRadius(px(radius));
        if (strokeWidth > 0) g.setStroke(Math.max(1, px(strokeWidth)), strokeColor);
        return g;
    }

    static void clipRound(View v, float radius) {
        final int r = px(radius);
        v.setOutlineProvider(new ViewOutlineProvider() {
            @Override public void getOutline(View view, Outline o) {
                o.setRoundRect(0, 0, view.getWidth(), view.getHeight(), r);
            }
        });
        v.setClipToOutline(true);
    }

    private Ui() { }
}
