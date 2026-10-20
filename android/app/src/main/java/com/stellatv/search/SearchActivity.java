package com.stellatv.search;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.InputType;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.util.Log;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.animation.DecelerateInterpolator;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

/**
 * One search across the Plex library and the streaming services. Type a title; the poster row
 * shows every match (your library first, then the most popular). Where each title streams is
 * looked up when you highlight it. OK on a poster opens it; Down shows other ways to watch.
 */
public class SearchActivity extends Activity {
    private static final String TAG = "StellaSearch";
    private static final long TYPING_PAUSE_MS = 350;          // local list: cheap to search often
    private static final long TYPING_PAUSE_NO_INDEX_MS = 900; // streaming search costs an API call
    private static final int MIN_STREAMING_CHARS = 3;
    private static final int MAX_RESULTS = 30;
    private static final int MIN_FULL_WORD_FALLBACK = 4;
    private static final float POSTER_W = 230, POSTER_H = 345, RING = 11, FOCUS_SCALE = 1.07f;

    private final Handler ui = new Handler(Looper.getMainLooper());
    private final ExecutorService io = Executors.newFixedThreadPool(2);
    private PlexSource plex;
    private StreamingSource streaming;
    private TitleIndex index;
    private final Set<Catalog.Title> lookingUp = new HashSet<>();
    private final Map<Catalog.Title, TextView> badges = new HashMap<>();
    private Set<String> myServices;

    private FrameLayout root;
    private ImageView backdrop;
    private EditText input;
    private TextView status, hint, dTitle, dMeta, dOverview;
    private LinearLayout posters, chips, details;
    private WhereToWatch whereToWatch;
    private HorizontalScrollView posterScroll;
    private MascotView mascot;
    private View mascotTarget;

    private List<Catalog.Title> results = new ArrayList<>();
    private int generation = 0;
    private boolean focusResultsWhenDone = false;
    private String shownBackdrop;

    private final Runnable searchSoon = () -> search(false);

    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        Ui.init(this);
        getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_FULLSCREEN
                | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY);
        plex = new PlexSource(BuildConfig.PLEX_URL);
        streaming = new StreamingSource(this, BuildConfig.STREAMING_API_KEY, BuildConfig.COUNTRY);
        myServices = Catalog.csv(BuildConfig.MY_SERVICES);
        index = new TitleIndex(this, BuildConfig.TMDB_TOKEN);
        build();
        prepareIndex();
        setContentView(root);
        root.getViewTreeObserver().addOnGlobalFocusChangeListener((oldF, newF) -> onFocus(newF));
        input.requestFocus();
    }

    // ---- Layout ----------------------------------------------------------------------------
    private void build() {
        root = new FrameLayout(this);
        root.setBackgroundColor(0xFF0B0D12);
        root.setClipChildren(false);

        backdrop = new ImageView(this);
        backdrop.setScaleType(ImageView.ScaleType.CENTER_CROP);
        backdrop.setAlpha(0f);
        root.addView(backdrop, new FrameLayout.LayoutParams(-1, -1));
        View shadeLeft = new View(this);
        shadeLeft.setBackground(new GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT,
                new int[]{0xF20B0D12, 0xB30B0D12, 0x660B0D12}));
        root.addView(shadeLeft, new FrameLayout.LayoutParams(-1, -1));
        View shadeBottom = new View(this);
        shadeBottom.setBackground(new GradientDrawable(GradientDrawable.Orientation.BOTTOM_TOP,
                new int[]{0xF20B0D12, 0x000B0D12}));
        root.addView(shadeBottom, new FrameLayout.LayoutParams(-1, Ui.px(520), Gravity.BOTTOM));

        // Search bar
        FrameLayout bar = new FrameLayout(this);
        bar.setBackground(Ui.rounded(0x1AFFFFFF, 48, 0x59FFFFFF, 2));
        root.addView(bar, at(112, 72, 1000, 96));
        bar.addView(new Glass(this), new FrameLayout.LayoutParams(Ui.px(44), Ui.px(44), Gravity.CENTER_VERTICAL | Gravity.START) {{
            leftMargin = Ui.px(36);
        }});
        input = new EditText(this);
        input.setBackground(null);
        input.setTextColor(Color.WHITE);
        input.setHintTextColor(0x80FFFFFF);
        input.setHint("Search movies and shows");
        input.setTextSize(TypedValue.COMPLEX_UNIT_PX, Ui.px(42));
        input.setTypeface(Ui.font(this, 500));
        input.setSingleLine(true);
        input.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
        input.setImeOptions(EditorInfo.IME_ACTION_SEARCH | EditorInfo.IME_FLAG_NO_EXTRACT_UI);
        input.setPadding(0, 0, 0, 0);
        FrameLayout.LayoutParams ilp = new FrameLayout.LayoutParams(-1, -1);
        ilp.leftMargin = Ui.px(104);
        ilp.rightMargin = Ui.px(36);
        bar.addView(input, ilp);
        input.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int a, int b, int c) { }
            @Override public void onTextChanged(CharSequence s, int a, int b, int c) { }
            @Override public void afterTextChanged(Editable s) {
                ui.removeCallbacks(searchSoon);
                ui.postDelayed(searchSoon, index.ready() ? TYPING_PAUSE_MS : TYPING_PAUSE_NO_INDEX_MS);
            }
        });
        input.setOnEditorActionListener((v, actionId, e) -> {
            ui.removeCallbacks(searchSoon);
            search(true);
            return true;
        });

        status = Ui.text(this, 26, 500, 0x99FFFFFF);
        root.addView(status, at(1150, 107, 700, 40));

        // Poster row
        posterScroll = new HorizontalScrollView(this);
        posterScroll.setHorizontalScrollBarEnabled(false);
        posterScroll.setClipChildren(false);
        posterScroll.setClipToPadding(false);
        posterScroll.setOnScrollChangeListener((v, x, y, ox, oy) -> placeMascot(false));
        posters = new LinearLayout(this);
        posters.setClipChildren(false);
        posters.setPadding(Ui.px(112 - RING), Ui.px(30), Ui.px(112), Ui.px(30));
        posterScroll.addView(posters);
        root.addView(posterScroll, at(0, 250, 1920, POSTER_H + 2 * RING + 60));

        // Details for the focused title
        details = new LinearLayout(this);
        details.setOrientation(LinearLayout.VERTICAL);
        details.setClipChildren(false);
        details.setVisibility(View.INVISIBLE);
        root.addView(details, at(112, 690, 1808, 390));
        dTitle = Ui.text(this, 56, 800, Color.WHITE);
        dTitle.setEllipsize(TextUtils.TruncateAt.END);
        details.addView(dTitle);
        dMeta = Ui.text(this, 28, 500, 0xBFFFFFFF);
        details.addView(dMeta, margins(0, 10, 0, 0));
        dOverview = Ui.text(this, 28, 400, 0xBFFFFFFF);
        dOverview.setSingleLine(false);
        dOverview.setMaxLines(2);
        dOverview.setEllipsize(TextUtils.TruncateAt.END);
        dOverview.setLineSpacing(0, 1.25f);
        details.addView(dOverview, margins(0, 10, 0, 0));
        // Where to watch: one shelf of logos, scrolling sideways when a title is on many services.
        HorizontalScrollView shelf = new HorizontalScrollView(this);
        shelf.setHorizontalScrollBarEnabled(false);
        shelf.setClipChildren(false);
        shelf.setClipToPadding(false);
        shelf.setOnScrollChangeListener((v, x, y, ox, oy) -> placeMascot(false));
        chips = new LinearLayout(this);
        chips.setClipChildren(false);
        chips.setPadding(Ui.px(4), 0, Ui.px(112), 0);
        shelf.addView(chips);
        LinearLayout.LayoutParams slp = new LinearLayout.LayoutParams(-1, -2);
        slp.setMargins(Ui.px(-4), Ui.px(14), 0, 0);
        details.addView(shelf, slp);
        whereToWatch = new WhereToWatch(this);

        hint = Ui.text(this, 34, 500, 0x99FFFFFF);
        hint.setGravity(Gravity.CENTER);
        hint.setSingleLine(false);
        root.addView(hint, at(0, 480, 1920, 120));
        showHint("Find anything: your Plex library, Netflix, Prime Video, Hulu, HBO Max and more.");

        mascot = new MascotView(this);
        mascot.setVisibility(View.INVISIBLE);
        root.addView(mascot, new FrameLayout.LayoutParams(Ui.px(120), Math.round(Ui.px(120) * MascotView.GRID_H / MascotView.GRID_W)));
    }

    // ---- Searching -------------------------------------------------------------------------
    private void search(boolean submitted) {
        String q = input.getText().toString().trim();
        focusResultsWhenDone = submitted;
        if (q.isEmpty()) {
            generation++;
            showResults(new ArrayList<>(), q);
            return;
        }
        final int gen = ++generation;
        status.setText("Searching…");
        boolean useIndex = index.ready();
        // Without the local list, or for a submitted full word, also ask the streaming search
        // (whole words only, one API call) so rarer titles can still be found.
        boolean streamingSearch = streaming.enabled() && (useIndex
                ? submitted && q.length() >= MIN_FULL_WORD_FALLBACK
                : q.length() >= MIN_STREAMING_CHARS);
        Future<List<Catalog.Title>> fromPlex = io.submit(() -> plex.enabled() ? plex.search(q) : new ArrayList<>());
        Future<List<Catalog.Title>> fromStreaming = io.submit(() -> streamingSearch ? streaming.search(q) : new ArrayList<>());
        new Thread(() -> {
            List<List<Catalog.Title>> sources = new ArrayList<>();
            List<String> problems = new ArrayList<>();
            collect(fromPlex, sources, problems, "Plex");
            if (useIndex) {
                List<Catalog.Title> fromIndex = new ArrayList<>();
                for (TitleIndex.Entry e : index.search(q, MAX_RESULTS)) fromIndex.add(TitleIndex.toTitle(e));
                sources.add(fromIndex);
            }
            collect(fromStreaming, sources, problems, "Streaming");
            List<Catalog.Title> ranked = Catalog.rank(Catalog.merge(sources), q, myServices);
            if (ranked.size() > MAX_RESULTS) ranked = new ArrayList<>(ranked.subList(0, MAX_RESULTS));
            List<Catalog.Title> finalRanked = ranked;
            ui.post(() -> {
                if (gen != generation) return; // a newer search has started
                showResults(finalRanked, q);
                if (!problems.isEmpty()) status.setText(TextUtils.join(" · ", problems));
            });
        }).start();
    }

    private static void collect(Future<List<Catalog.Title>> f, List<List<Catalog.Title>> into, List<String> problems, String name) {
        try {
            into.add(f.get());
        } catch (Exception e) {
            Log.w(TAG, name + " search failed", e);
            problems.add(name + " unavailable");
        }
    }

    private void showResults(List<Catalog.Title> titles, String q) {
        results = titles;
        badges.clear();
        posters.removeAllViews();
        posterScroll.scrollTo(0, 0);
        status.setText(q.isEmpty() ? "" : titles.size() == 1 ? "1 result" : titles.size() + " results");
        if (!q.isEmpty() && !index.ready() && streaming.enabled() && q.length() < MIN_STREAMING_CHARS) {
            // Short queries search only the library (free); streaming lookups cost API calls.
            status.setText(titles.size() + " in your library · type " + MIN_STREAMING_CHARS + "+ letters for streaming");
        }
        if (titles.isEmpty()) {
            details.setVisibility(View.INVISIBLE);
            setBackdrop(null);
            showHint(q.isEmpty() ? "Find anything: your Plex library, Netflix, Prime Video, Hulu, HBO Max and more."
                    : "Nothing found for “" + q + "”");
            return;
        }
        hint.setVisibility(View.GONE);
        for (int i = 0; i < titles.size(); i++) posters.addView(poster(titles.get(i), i));
        showDetails(titles.get(0), posters.getChildAt(0));
        if (focusResultsWhenDone) {
            hideKeyboard();
            posters.getChildAt(0).requestFocus();
        }
    }

    private void showHint(String text) {
        hint.setText(text);
        hint.setVisibility(View.VISIBLE);
    }

    // ---- Posters -----------------------------------------------------------------------------
    private View poster(Catalog.Title t, int position) {
        FrameLayout frame = new FrameLayout(this);
        frame.setId(View.generateViewId());
        frame.setTag(position);
        frame.setFocusable(true);
        frame.setFocusableInTouchMode(true);
        frame.setClipChildren(false);
        int pad = Ui.px(RING);
        frame.setPadding(pad, pad, pad, pad);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(Ui.px(POSTER_W + 2 * RING), Ui.px(POSTER_H + 2 * RING));
        lp.rightMargin = Ui.px(18);
        frame.setLayoutParams(lp);

        FrameLayout card = new FrameLayout(this);
        card.setBackground(new GradientDrawable(GradientDrawable.Orientation.TL_BR, new int[]{0xFF2A3344, 0xFF141821}) {{
            setCornerRadius(Ui.px(18));
        }});
        Ui.clipRound(card, 18);
        card.setElevation(Ui.px(8));
        TextView name = Ui.text(this, 30, 800, Color.WHITE);
        name.setSingleLine(false);
        name.setMaxLines(4);
        name.setText(t.title + (t.year > 0 ? "\n" + t.year : ""));
        name.setPadding(Ui.px(20), 0, Ui.px(20), Ui.px(22));
        card.addView(name, new FrameLayout.LayoutParams(-1, -2, Gravity.BOTTOM));
        ImageView art = new ImageView(this);
        art.setScaleType(ImageView.ScaleType.CENTER_CROP);
        card.addView(art, new FrameLayout.LayoutParams(-1, -1));
        if (t.poster != null) Images.into(art, t.poster);

        TextView badge = Ui.text(this, 22, 700, Color.WHITE);
        badge.setPadding(Ui.px(14), Ui.px(8), Ui.px(14), Ui.px(8));
        FrameLayout.LayoutParams blp = new FrameLayout.LayoutParams(-2, -2, Gravity.TOP | Gravity.START);
        blp.leftMargin = blp.topMargin = Ui.px(12);
        card.addView(badge, blp);
        badges.put(t, badge);
        updateBadge(t);
        frame.addView(card, new FrameLayout.LayoutParams(-1, -1));

        GradientDrawable ring = Ui.rounded(Color.TRANSPARENT, 18 + RING, Color.WHITE, 5);
        frame.setOnFocusChangeListener((v, has) -> {
            v.setBackground(has ? ring : null);
            v.animate().scaleX(has ? FOCUS_SCALE : 1f).scaleY(has ? FOCUS_SCALE : 1f)
                    .setDuration(220).setInterpolator(new DecelerateInterpolator(2f)).start();
            card.setElevation(Ui.px(has ? 30 : 8));
            v.setZ(has ? 10 : 0);
            if (has) {
                showDetails(t, v);
                lookUp(t, v);
                // Also the next one, so moving right shows its details straight away.
                if (position + 1 < results.size()) lookUp(results.get(position + 1), posters.getChildAt(position + 1));
            }
        });
        frame.setOnClickListener(v -> {
            Catalog.Option o = t.bestLaunchable();
            if (o != null) open(t, o);
            else if (!t.availabilityLoaded && t.tmdb != null) Toast.makeText(this, "Still checking where to watch…", Toast.LENGTH_SHORT).show();
            else if (!t.options.isEmpty()) Toast.makeText(this, "Not included in your plans: see where to rent or buy below", Toast.LENGTH_SHORT).show();
        });
        return frame;
    }

    /** The label on a poster: where the best way to watch it is ("Plex", "Netflix", "Rent"). */
    private void updateBadge(Catalog.Title t) {
        TextView badge = badges.get(t);
        if (badge == null) return;
        Catalog.Option best = t.options.isEmpty() ? null : t.options.get(0);
        badge.setVisibility(best == null ? View.GONE : View.VISIBLE);
        if (best == null) return;
        badge.setText(best.tier == Catalog.OWNED ? "Plex" : best.tier <= Catalog.FREE ? best.serviceName : badgeText(best));
        badge.setTextColor(best.tier == Catalog.OWNED ? 0xFF111111 : Color.WHITE);
        badge.setBackground(Ui.rounded(best.tier == Catalog.OWNED ? 0xFFE5A00D : 0xCC0B0D12, 14, 0, 0));
    }

    private static String badgeText(Catalog.Option o) {
        return o.tier == Catalog.RENT ? "Rent" : o.tier == Catalog.BUY ? "Buy" : o.tier == Catalog.ADDON ? "Add-on" : "Not in plan";
    }

    // ---- Details + "watch on" buttons --------------------------------------------------------
    private void showDetails(Catalog.Title t, View posterView) {
        details.setVisibility(View.VISIBLE);
        dTitle.setText(t.title);
        List<String> meta = new ArrayList<>();
        if (t.year > 0) meta.add(String.valueOf(t.year));
        meta.add(t.kind.equals("movie") ? "Movie" : "Series");
        if (t.runtimeMin > 0 && t.kind.equals("movie")) meta.add(t.runtimeMin / 60 > 0 ? (t.runtimeMin / 60) + "h " + (t.runtimeMin % 60) + "m" : t.runtimeMin + "m");
        if (!t.genres.isEmpty()) meta.add(TextUtils.join(", ", t.genres));
        dMeta.setText(TextUtils.join("  ·  ", meta));
        dOverview.setText(t.overview == null ? "" : t.overview);
        dOverview.setVisibility(t.overview == null ? View.GONE : View.VISIBLE);

        if (t.options.isEmpty()) {
            chips.removeAllViews();
            boolean checking = !t.availabilityLoaded && t.tmdb != null && streaming.enabled();
            TextView note = Ui.text(this, 28, 500, 0x99FFFFFF);
            note.setText(checking ? "Checking where to watch…" : "Not available to stream here");
            chips.addView(note, margins(4, 16, 0, 0));
        } else {
            View best = whereToWatch.fill(chips, t, posterView.getId(), o -> open(t, o));
            if (best != null) posterView.setNextFocusDownId(withId(best));
        }
        setBackdrop(t.backdrop);
    }

    private void setBackdrop(String url) {
        if (url == null ? shownBackdrop == null : url.equals(shownBackdrop)) return;
        shownBackdrop = url;
        backdrop.animate().cancel();
        backdrop.animate().alpha(0f).setDuration(150).withEndAction(() -> {
            if (url == null) return;
            Images.load(url, bmp -> {
                if (!url.equals(shownBackdrop)) return;
                backdrop.setImageBitmap(bmp);
                backdrop.animate().alpha(0.55f).setDuration(400).start();
            });
        }).start();
    }

    /** Fetches where a title streams (one API call, cached a day), then refreshes its badge and details. */
    private void lookUp(Catalog.Title t, View posterView) {
        if (t.availabilityLoaded || t.tmdb == null || !streaming.enabled() || !lookingUp.add(t)) return;
        io.execute(() -> {
            Catalog.Title found = null;
            try {
                found = streaming.lookup(t.tmdb);
            } catch (Exception e) {
                Log.w(TAG, "Lookup failed for " + t.tmdb, e);
            }
            Catalog.Title result = found;
            ui.post(() -> {
                lookingUp.remove(t);
                t.availabilityLoaded = true;
                if (result != null) {
                    t.options.addAll(result.options);
                    if (t.overview == null) t.overview = result.overview;
                    if (t.genres.isEmpty()) t.genres = result.genres;
                    if (t.runtimeMin == 0) t.runtimeMin = result.runtimeMin;
                    if (t.backdrop == null) t.backdrop = result.backdrop;
                    if (t.poster == null) t.poster = result.poster;
                    Catalog.sortOptions(t, myServices);
                }
                updateBadge(t);
                if (getCurrentFocus() == posterView) showDetails(t, posterView);
            });
        });
    }

    /** Loads the local title list, and rebuilds it in the background when it's missing or a week old. */
    private void prepareIndex() {
        if (!index.enabled()) return;
        new Thread(() -> {
            index.load();
            // Anything typed while the list was loading only searched Plex: search again.
            if (index.ready()) ui.post(() -> { if (input.getText().length() > 0) search(false); });
            if (!index.stale()) return;
            boolean first = !index.ready();
            try {
                index.rebuild(pct -> ui.post(() -> {
                    if (first && input.getText().length() == 0) status.setText("Preparing the title list… " + pct + "%");
                }));
                ui.post(() -> { if (first && input.getText().length() == 0) status.setText("Title list ready"); });
            } catch (Exception e) {
                Log.w(TAG, "Could not build the title list", e);
                ui.post(() -> { if (first) status.setText("Couldn't get the title list (will retry next time)"); });
            }
        }, "title-index").start();
    }

    private void open(Catalog.Title t, Catalog.Option o) {
        Intent intent = Opener.intent(o);
        if (intent == null) {
            Toast.makeText(this, o.serviceName + " can't be opened from here yet. Its app isn't on this TV.", Toast.LENGTH_LONG).show();
            return;
        }
        try {
            startActivity(intent);
        } catch (Exception e) {
            Log.w(TAG, "Could not open " + o.service, e);
            Toast.makeText(this, "Couldn't open " + o.serviceName + ". Is the app installed?", Toast.LENGTH_LONG).show();
        }
    }

    // ---- Stella ----------------------------------------------------------------------------
    private void onFocus(View v) {
        if (v == null || v == input || v.getParent() == null) {
            mascotTarget = null;
            mascot.setVisibility(View.INVISIBLE);
            return;
        }
        if (v.getTag() instanceof Catalog.Option) {
            // No room above the where-to-watch logos without covering the description.
            mascotTarget = null;
            mascot.setVisibility(View.INVISIBLE);
            return;
        }
        mascotTarget = v;
        mascot.hop();
        // Let the focus scale and any scroll settle before measuring.
        ui.post(() -> placeMascot(true));
    }

    /** Puts Stella's paws on the top edge of the focused poster or button. */
    private void placeMascot(boolean animate) {
        View v = mascotTarget;
        if (v == null || !v.isAttachedToWindow()) return;
        boolean isPoster = v.getParent() == posters;
        int size = Ui.px(isPoster ? 120 : 76);
        int height = Math.round(size * MascotView.GRID_H / MascotView.GRID_W);
        FrameLayout.LayoutParams lp = (FrameLayout.LayoutParams) mascot.getLayoutParams();
        if (lp.width != size) {
            lp.width = size;
            lp.height = height;
            mascot.setLayoutParams(lp);
        }
        Rect r = new Rect(0, 0, v.getWidth(), v.getHeight());
        root.offsetDescendantRectToMyCoords(v, r);
        float edge = isPoster ? r.exactCenterY() - (r.height() / 2f - Ui.px(RING)) * FOCUS_SCALE : r.top;
        float x = r.exactCenterX() - size / 2f;
        float y = edge - size / MascotView.GRID_W * MascotView.PAW_Y;
        mascot.setVisibility(View.VISIBLE);
        mascot.animate().cancel();
        if (animate) {
            mascot.animate().translationX(x).translationY(y).setDuration(260)
                    .setInterpolator(new DecelerateInterpolator(2f)).start();
        } else {
            mascot.setTranslationX(x);
            mascot.setTranslationY(y);
        }
    }

    // ---- Keys --------------------------------------------------------------------------------
    @Override public void onBackPressed() {
        if (getCurrentFocus() != input && input.getText().length() > 0) {
            input.requestFocus();
            input.setSelection(input.getText().length());
            InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
            imm.showSoftInput(input, 0);
            return;
        }
        super.onBackPressed();
    }

    @Override public boolean dispatchKeyEvent(KeyEvent e) {
        // From the search bar, Down jumps to the results (when the keyboard isn't using the key).
        if (e.getAction() == KeyEvent.ACTION_DOWN && e.getKeyCode() == KeyEvent.KEYCODE_DPAD_DOWN
                && getCurrentFocus() == input && posters.getChildCount() > 0) {
            hideKeyboard();
            posters.getChildAt(0).requestFocus();
            return true;
        }
        return super.dispatchKeyEvent(e);
    }

    private void hideKeyboard() {
        InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
        imm.hideSoftInputFromWindow(input.getWindowToken(), 0);
    }

    @Override protected void onDestroy() {
        super.onDestroy();
        io.shutdownNow();
    }

    // ---- Helpers -----------------------------------------------------------------------------
    private static FrameLayout.LayoutParams at(float x, float y, float w, float h) {
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(Ui.px(w), Ui.px(h));
        lp.leftMargin = Ui.px(x);
        lp.topMargin = Ui.px(y);
        return lp;
    }

    private static int withId(View v) {
        if (v.getId() == View.NO_ID) v.setId(View.generateViewId());
        return v.getId();
    }

    private static LinearLayout.LayoutParams margins(float l, float t, float r, float b) {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-2, -2);
        lp.setMargins(Ui.px(l), Ui.px(t), Ui.px(r), Ui.px(b));
        return lp;
    }

    /** The magnifying-glass icon in the search bar. */
    private static final class Glass extends View {
        private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);

        Glass(Context c) {
            super(c);
            p.setStyle(Paint.Style.STROKE);
            p.setStrokeCap(Paint.Cap.ROUND);
            p.setColor(Color.WHITE);
        }

        @Override protected void onDraw(Canvas c) {
            float s = getWidth() / 24f;
            p.setStrokeWidth(2.2f * s);
            c.drawCircle(10.5f * s, 10.5f * s, 6.5f * s, p);
            c.drawLine(15.5f * s, 15.5f * s, 21 * s, 21 * s, p);
        }
    }
}
