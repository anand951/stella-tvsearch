package com.stellatv.search;

import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.View;
import android.view.animation.DecelerateInterpolator;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * The "where to watch" shelf under a title, laid out like JustWatch: one group per way of paying
 * (Included, Free, Rent, Buy, Extra), each with a side tab, and each service shown by its own logo.
 * Tabs for groups that cost nothing are green; tabs for groups that cost money are pink.
 */
final class WhereToWatch {
    static final int TILE_W = 150, TILE_H = 84;
    private static final int RING = 8;
    private static final float FOCUS_SCALE = 1.08f;
    private static final int NO_COST = 0xFF5FD39B, COSTS = 0xFFFF8FA3;

    /** Apps whose TV banner can stand in for the service's logo. */
    private static final Map<String, String> APPS = new HashMap<>();
    static {
        APPS.put("plex", "com.plexapp.android");
        APPS.put("netflix", "com.netflix.ninja");
        APPS.put("hbo", "com.wbd.stream");
        APPS.put("paramount", "com.cbs.ott");
        APPS.put("disney", "com.disney.disneyplus");
        APPS.put("hulu", "com.hulu.livingroomplus");
        APPS.put("prime", "com.amazon.amazonvideo.livingroom");
        APPS.put("tubi", "com.tubitv");
        APPS.put("pluto", "tv.pluto.android");
        APPS.put("peacock", "com.peacocktv.peacockandroid");
        APPS.put("apple", "com.apple.atve.androidtv.appletv");
    }

    interface OnPick { void pick(Catalog.Option o); }

    private final Context c;
    private final PackageManager pm;
    private final Map<String, Drawable> banners = new HashMap<>();

    WhereToWatch(Context c) {
        this.c = c;
        this.pm = c.getPackageManager();
    }

    /** Fills {@code row} with the groups. Returns the tile Down from the poster should land on, or null. */
    View fill(LinearLayout row, Catalog.Title t, int upId, OnPick onPick) {
        row.removeAllViews();
        Map<String, List<Catalog.Option>> groups = new LinkedHashMap<>();
        for (String g : GROUPS) groups.put(g, new ArrayList<>());
        for (Catalog.Option o : t.options) {
            String g = group(o);
            List<Catalog.Option> list = groups.get(g);
            if (list.size() < 3) list.add(o);
        }
        View best = null;
        boolean firstGroup = true;
        for (Map.Entry<String, List<Catalog.Option>> e : groups.entrySet()) {
            if (e.getValue().isEmpty()) continue;
            LinearLayout lane = new LinearLayout(c);
            lane.setClipChildren(false);
            lane.setGravity(Gravity.TOP);
            boolean free = e.getKey().equals("Included") || e.getKey().equals("Free");
            lane.addView(new SideTab(c, e.getKey(), free ? NO_COST : COSTS),
                    new LinearLayout.LayoutParams(Ui.px(34), Ui.px(TILE_H + 2 * RING)));
            for (Catalog.Option o : e.getValue()) {
                View tile = tile(o, upId, onPick);
                if (best == null && Opener.canOpen(o)) best = tile;
                LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-2, -2);
                lp.leftMargin = Ui.px(10);
                lane.addView(tile, lp);
            }
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-2, -2);
            lp.leftMargin = firstGroup ? 0 : Ui.px(36);
            row.addView(lane, lp);
            firstGroup = false;
        }
        if (best != null) ((TextView) best.findViewWithTag("caption")).setText("▶  " + caption((Catalog.Option) best.getTag()));
        return best;
    }

    /** Free ways first, then paid, cheapest kind first. Extra = an add-on channel or another subscription. */
    private static final String[] GROUPS = {"Included", "Free", "Rent", "Buy", "Extra"};

    private static String group(Catalog.Option o) {
        switch (o.tier) {
            case Catalog.OWNED: case Catalog.MY_SERVICE: return "Included";
            case Catalog.FREE: return "Free";
            case Catalog.RENT: return "Rent";
            case Catalog.BUY: return "Buy";
            default: return "Extra";
        }
    }

    /** A logo with its caption underneath ("Your library", "With ads", "$3.99"). */
    private View tile(Catalog.Option o, int upId, OnPick onPick) {
        boolean can = Opener.canOpen(o);
        LinearLayout col = new LinearLayout(c);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setGravity(Gravity.CENTER_HORIZONTAL);
        col.setClipChildren(false);
        col.setTag(o);
        col.setFocusable(true);
        col.setFocusableInTouchMode(true);
        col.setNextFocusUpId(upId);

        FrameLayout frame = new FrameLayout(c);
        frame.setClipChildren(false);
        int pad = Ui.px(RING);
        frame.setPadding(pad, pad, pad, pad);
        FrameLayout card = new FrameLayout(c);
        Ui.clipRound(card, 14);
        Drawable logo = banner(o.service);
        if (logo != null) {
            ImageView img = new ImageView(c);
            img.setScaleType(ImageView.ScaleType.CENTER_CROP);
            img.setImageDrawable(logo);
            card.addView(img, new FrameLayout.LayoutParams(-1, -1));
        } else {
            // No app on this TV: the service's name on its brand colour.
            card.setBackground(Ui.rounded(o.color != 0 ? o.color : 0xFF2A3344, 14, 0x33FFFFFF, 1));
            TextView name = Ui.text(c, 26, 800, readableOn(o.color));
            name.setSingleLine(false);
            name.setMaxLines(2);
            name.setGravity(Gravity.CENTER);
            name.setText(o.serviceName);
            name.setPadding(Ui.px(10), 0, Ui.px(10), 0);
            card.addView(name, new FrameLayout.LayoutParams(-1, -1, Gravity.CENTER));
        }
        if (isUhd(o)) {
            TextView k = Ui.text(c, 18, 800, Color.WHITE);
            k.setText("4K");
            k.setPadding(Ui.px(8), Ui.px(3), Ui.px(8), Ui.px(3));
            k.setBackground(Ui.rounded(0xCC0B0D12, 8, 0, 0));
            FrameLayout.LayoutParams klp = new FrameLayout.LayoutParams(-2, -2, Gravity.BOTTOM | Gravity.END);
            klp.rightMargin = klp.bottomMargin = Ui.px(6);
            card.addView(k, klp);
        }
        frame.addView(card, new FrameLayout.LayoutParams(Ui.px(TILE_W), Ui.px(TILE_H)));
        col.addView(frame, new LinearLayout.LayoutParams(Ui.px(TILE_W + 2 * RING), Ui.px(TILE_H + 2 * RING)));

        TextView cap = Ui.text(c, 24, o.tier >= Catalog.RENT && o.price != null ? 800 : 600, Color.WHITE);
        cap.setTag("caption");
        cap.setText(caption(o));
        col.addView(cap, new LinearLayout.LayoutParams(-2, -2) {{ topMargin = Ui.px(2); }});

        if (!can) {
            frame.setAlpha(0.45f);
            cap.setTextColor(0x99FFFFFF);
        }
        GradientDrawable ring = Ui.rounded(Color.TRANSPARENT, 14 + RING, Color.WHITE, 4);
        col.setOnFocusChangeListener((v, has) -> {
            frame.setBackground(has ? ring : null);
            frame.animate().scaleX(has ? FOCUS_SCALE : 1f).scaleY(has ? FOCUS_SCALE : 1f)
                    .setDuration(200).setInterpolator(new DecelerateInterpolator(2f)).start();
            v.setZ(has ? 10 : 0);
        });
        col.setOnClickListener(v -> onPick.pick(o));
        return col;
    }

    /** What the option costs or means, in a few words. */
    private static String caption(Catalog.Option o) {
        switch (o.tier) {
            case Catalog.OWNED: return "Your library";
            case Catalog.MY_SERVICE: return o.addonId != null ? "In " + o.serviceName : "Your plan";
            case Catalog.FREE: return Opener.canOpen(o) ? "With ads" : "Needs app";
            case Catalog.RENT: case Catalog.BUY: return o.amount > 0 ? price(o) : (o.price != null ? o.price : "Price varies");
            case Catalog.ADDON: return o.addonName != null ? o.addonName : "Add-on";
            default: return "Not in plan";
        }
    }

    static String price(Catalog.Option o) {
        if (o.price != null && o.price.endsWith("USD")) return String.format(Locale.US, "$%.2f", o.amount);
        return o.price != null ? o.price : String.format(Locale.US, "%.2f", o.amount);
    }

    private static boolean isUhd(Catalog.Option o) {
        return o.quality != null && (o.quality.equalsIgnoreCase("uhd") || o.quality.equalsIgnoreCase("4k"));
    }

    private Drawable banner(String service) {
        if (banners.containsKey(service)) return banners.get(service);
        Drawable d = null;
        String pkg = APPS.get(service);
        if (pkg != null) {
            try {
                Intent i = pm.getLeanbackLaunchIntentForPackage(pkg);
                if (i != null && i.getComponent() != null) d = pm.getActivityBanner(i.getComponent());
                if (d == null) d = pm.getApplicationBanner(pkg);
            } catch (Exception ignored) { }
        }
        banners.put(service, d);
        return d;
    }

    private static int readableOn(int bg) {
        double lum = (0.299 * Color.red(bg) + 0.587 * Color.green(bg) + 0.114 * Color.blue(bg)) / 255;
        return lum > 0.6 ? 0xFF111111 : Color.WHITE;
    }

    /** The group's name written up the side of its tab, as JustWatch does. */
    private static final class SideTab extends View {
        private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG), text = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final String label;

        SideTab(Context c, String label, int color) {
            super(c);
            this.label = label;
            fill.setColor(color);
            text.setColor(0xFF0B0D12);
            text.setTextSize(Ui.px(20));
            text.setTypeface(Ui.font(c, 800));
            text.setTextAlign(Paint.Align.CENTER);
            setContentDescription(label);
        }

        @Override protected void onDraw(Canvas cv) {
            float w = getWidth(), h = getHeight(), r = Ui.px(10), inset = Ui.px(RING);
            cv.drawRoundRect(0, inset, w, h - inset, r, r, fill);
            cv.save();
            cv.rotate(-90, w / 2f, h / 2f);
            Paint.FontMetrics fm = text.getFontMetrics();
            cv.drawText(label, w / 2f, h / 2f - (fm.ascent + fm.descent) / 2f, text);
            cv.restore();
        }
    }
}
