package com.stellatv.search;

import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.View;
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
 * The "where to watch" list beside a title's details: one row per service, each with its logo, a
 * line saying what it means for you, and what it costs on the right. Ways that cost nothing come
 * first under "No extra cost"; renting, buying and other subscriptions follow under "Costs extra".
 */
final class WhereToWatch {
    static final int NO_COST = 0xFF5FD39B, COSTS = 0xFFFF8FA3;
    private static final int LOGO_W = 136, LOGO_H = 84;

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
        APPS.put("youtube", "com.google.android.youtube.tv");
    }

    interface OnPick { void pick(Catalog.Option o); }

    /** One line of the list: an option, or a service's rent and buy prices together. */
    static final class Row {
        final Catalog.Option open;   // what OK opens
        Catalog.Option rent, buy;
        Row(Catalog.Option open) { this.open = open; }
    }

    private final Context c;
    private final PackageManager pm;
    private final Map<String, Drawable> banners = new HashMap<>();

    WhereToWatch(Context c) {
        this.c = c;
        this.pm = c.getPackageManager();
    }

    /** Fills {@code list}. Returns the row Down from the poster should land on, or null. */
    View fill(LinearLayout list, Catalog.Title t, int upId, OnPick onPick) {
        list.removeAllViews();
        List<Row> free = new ArrayList<>(), paid = new ArrayList<>(), extras = new ArrayList<>();
        Map<String, Row> purchases = new LinkedHashMap<>();
        for (Catalog.Option o : t.options) {
            if (o.tier <= Catalog.FREE) {
                free.add(new Row(o));
            } else if (o.tier == Catalog.RENT || o.tier == Catalog.BUY) {
                Row r = purchases.get(o.service);
                if (r == null) {
                    purchases.put(o.service, r = new Row(o));
                    paid.add(r);
                }
                if (o.tier == Catalog.RENT && r.rent == null) r.rent = o;
                if (o.tier == Catalog.BUY && r.buy == null) r.buy = o;
            } else {
                extras.add(new Row(o));
            }
        }
        paid.addAll(extras);   // renting or buying before add-ons and other subscriptions
        View best = null, first = null;
        for (int g = 0; g < 2; g++) {
            List<Row> rows = g == 0 ? free : paid;
            if (rows.isEmpty()) continue;
            TextView head = Ui.text(c, 24, 800, g == 0 ? NO_COST : COSTS);
            head.setText(g == 0 ? "No extra cost" : "Costs extra");
            LinearLayout.LayoutParams hl = new LinearLayout.LayoutParams(-2, -2);
            hl.leftMargin = Ui.px(8);
            hl.topMargin = Ui.px(list.getChildCount() == 0 ? 0 : 18);
            hl.bottomMargin = Ui.px(10);
            list.addView(head, hl);
            for (Row r : rows) {
                boolean isBest = best == null && Opener.canOpen(r.open);
                View row = row(r, isBest, onPick);
                if (isBest) best = row;
                if (first == null) {
                    first = row;
                    row.setNextFocusUpId(upId);
                }
                LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, Ui.px(LOGO_H + 16));
                lp.bottomMargin = Ui.px(6);
                list.addView(row, lp);
            }
        }
        return best;
    }

    private View row(Row r, boolean isBest, OnPick onPick) {
        Catalog.Option o = r.open;
        boolean can = Opener.canOpen(o);
        LinearLayout row = new LinearLayout(c);
        row.setTag(r);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(Ui.px(8), Ui.px(8), Ui.px(22), Ui.px(8));
        row.setFocusable(true);
        row.setFocusableInTouchMode(true);
        row.setId(View.generateViewId());
        row.setNextFocusLeftId(row.getId());
        row.setNextFocusRightId(row.getId());

        View logo = logo(o);
        if (!can) logo.setAlpha(0.45f);
        row.addView(logo, new LinearLayout.LayoutParams(Ui.px(LOGO_W), Ui.px(LOGO_H)));

        LinearLayout text = new LinearLayout(c);
        text.setOrientation(LinearLayout.VERTICAL);
        TextView name = Ui.text(c, 28, 800, Color.WHITE);
        name.setText(o.tier == Catalog.ADDON && o.addonName != null ? o.addonName : o.serviceName);
        TextView detail = Ui.text(c, 22, 500, can ? 0xBFFFFFFF : 0x99FFFFFF);
        detail.setText(detail(r, can));
        text.addView(name);
        text.addView(detail, new LinearLayout.LayoutParams(-2, -2) {{ topMargin = Ui.px(6); }});
        LinearLayout.LayoutParams tl = new LinearLayout.LayoutParams(0, -2, 1);
        tl.leftMargin = Ui.px(22);
        row.addView(text, tl);

        TextView right = Ui.text(c, 28, 800, Color.WHITE);
        right.setText(isBest ? "▶ Play" : action(r));
        if (!isBest) right.setTextColor(o.tier <= Catalog.FREE ? NO_COST : COSTS);
        if (!can) right.setAlpha(0.6f);
        row.addView(right, new LinearLayout.LayoutParams(-2, -2));

        GradientDrawable focused = Ui.rounded(0x24FFFFFF, 18, Color.WHITE, 4);
        row.setOnFocusChangeListener((v, has) -> v.setBackground(has ? focused : null));
        row.setOnClickListener(v -> onPick.pick(o));
        return row;
    }

    /** The grey line under the name: what this way of watching means for you. */
    private static String detail(Row r, boolean can) {
        Catalog.Option o = r.open;
        String q = quality(r.rent != null ? r.rent : r.buy != null ? r.buy : o);
        String s;
        switch (o.tier) {
            case Catalog.OWNED: s = "Your library"; break;
            case Catalog.MY_SERVICE: s = "Included in your plan"; break;
            case Catalog.FREE: s = o.service.equals("youtube") ? "With ads. Opens a YouTube search" : "With ads"; break;
            case Catalog.ADDON: s = "Add-on channel on " + o.serviceName; break;
            case Catalog.OTHER_SUBSCRIPTION: s = "Not in your plans"; break;
            default: s = "";
        }
        if (q != null) s = s.isEmpty() ? q : s + ", " + q;
        if (!can) s = (s.isEmpty() ? "" : s + ". ") + "No " + o.serviceName + " app on this TV";
        return s;
    }

    /** The coloured words on the right: "Included", "Free", "Rent $3.99   Buy $4.99". */
    private static String action(Row r) {
        Catalog.Option o = r.open;
        switch (o.tier) {
            case Catalog.OWNED: return "Play";
            case Catalog.MY_SERVICE: return "Included";
            case Catalog.FREE: return "Free";
            case Catalog.RENT: case Catalog.BUY:
                List<String> parts = new ArrayList<>();
                if (r.rent != null) parts.add("Rent " + price(r.rent));
                if (r.buy != null) parts.add("Buy " + price(r.buy));
                return String.join("   ", parts);
            default: return "Subscribe";
        }
    }

    static String price(Catalog.Option o) {
        if (o.price != null && o.price.endsWith("USD")) return String.format(Locale.US, "$%.2f", o.amount);
        if (o.price != null) return o.price;
        return o.amount > 0 ? String.format(Locale.US, "%.2f", o.amount) : "";
    }

    /** "4K" or "1080p" when worth saying; HD from a streaming service is the norm. */
    private static String quality(Catalog.Option o) {
        String q = o.quality;
        if (q == null) return null;
        if (q.equalsIgnoreCase("uhd") || q.equalsIgnoreCase("4k")) return "4K";
        if (q.matches("\\d+")) return q + "p";   // Plex reports "1080", "720"
        return null;
    }

    private View logo(Catalog.Option o) {
        FrameLayout card = new FrameLayout(c);
        Ui.clipRound(card, 14);
        Drawable banner = banner(o.service);
        if (banner != null) {
            ImageView img = new ImageView(c);
            img.setScaleType(ImageView.ScaleType.CENTER_CROP);
            img.setImageDrawable(banner);
            card.addView(img, new FrameLayout.LayoutParams(-1, -1));
        } else {
            // No app on this TV: the service's name on its brand colour.
            card.setBackground(Ui.rounded(o.color != 0 ? o.color : 0xFF2A3344, 14, 0x33FFFFFF, 1));
            TextView name = Ui.text(c, 24, 800, readableOn(o.color));
            name.setSingleLine(false);
            name.setMaxLines(2);
            name.setGravity(Gravity.CENTER);
            name.setText(o.serviceName);
            name.setPadding(Ui.px(8), 0, Ui.px(8), 0);
            card.addView(name, new FrameLayout.LayoutParams(-1, -1, Gravity.CENTER));
        }
        return card;
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
}
