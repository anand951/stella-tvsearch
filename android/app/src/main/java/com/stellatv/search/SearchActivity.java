package com.stellatv.search;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
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
import android.widget.ScrollView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

/**
 * One search across the Plex library and the streaming services. Type a title; the poster row
 * shows every match (your library first, then the most popular). Where each title streams is
 * looked up when you highlight it. OK on a poster opens it; Down saves it to the watchlist, and
 * Right from there shows the ways to watch. With nothing typed, the watchlist shows instead.
 */
public class SearchActivity extends Activity {
    private static final String TAG = "StellaSearch";
    private static final long TYPING_PAUSE_MS = 350;          // local list: cheap to search often
    private static final long TYPING_PAUSE_NO_INDEX_MS = 900; // streaming search costs an API call
    private static final int MIN_STREAMING_CHARS = 3;
    private static final int MAX_RESULTS = 30;
    private static final int MIN_FULL_WORD_FALLBACK = 4;
    private static final float POSTER_W = 200, POSTER_H = 300, RING = 11, FOCUS_SCALE = 1.07f;

    private final Handler ui = new Handler(Looper.getMainLooper());
    private final ExecutorService io = Executors.newFixedThreadPool(2);
    private PlexSource plex;
    private StreamingSource streaming;
    private YouTubeSource youtube;
    private TitleIndex index;
    private final Set<Catalog.Title> lookingUp = new HashSet<>();
    private final Map<Catalog.Title, TextView> badges = new HashMap<>();
    private final Map<Catalog.Title, View> marks = new HashMap<>();
    private Set<String> myServices;
    private Watchlist watchlist;
    private boolean showingWatchlist;
    private Catalog.Title shown;   // the title whose details are showing

    private FrameLayout root;
    private ImageView backdrop;
    private EditText input;
    private TextView status, hint, dTitle, dMeta, dOverview, saveLabel, savedNote, listCount;
    private LinearLayout posters, chips, details, saveButton, listButton;
    private Icon saveIcon, listIcon, aboutIcon;
    private FrameLayout aboutButton;
    private TextView aboutLabel;
    private AboutPanel about;
    private View emptyList;
    private View watchPanel, watchFade;
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
        youtube = new YouTubeSource(this, BuildConfig.TMDB_TOKEN, BuildConfig.COUNTRY);
        watchlist = new Watchlist(this);
        build();
        prepareIndex();
        setContentView(root);
        root.getViewTreeObserver().addOnGlobalFocusChangeListener((oldF, newF) -> onFocus(newF));
        showWatchlist(false);   // with nothing typed yet, the watchlist fills the screen
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
        bar.setBackground(Ui.rounded(0x1AFFFFFF, 40, 0x59FFFFFF, 2));
        root.addView(bar, at(112, 40, 900, 80));
        bar.addView(new Glass(this), new FrameLayout.LayoutParams(Ui.px(44), Ui.px(44), Gravity.CENTER_VERTICAL | Gravity.START) {{
            leftMargin = Ui.px(36);
        }});
        input = new EditText(this);
        input.setBackground(null);
        input.setTextColor(Color.WHITE);
        input.setHintTextColor(0x80FFFFFF);
        input.setHint("Search movies and shows");
        input.setTextSize(TypedValue.COMPLEX_UNIT_PX, Ui.px(38));
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

        // Watchlist, beside the search bar
        listButton = new LinearLayout(this);
        listButton.setId(View.generateViewId());
        listButton.setGravity(Gravity.CENTER_VERTICAL);
        listButton.setPadding(Ui.px(28), 0, Ui.px(22), 0);
        listButton.setFocusable(true);
        listButton.setFocusableInTouchMode(true);
        listIcon = new Icon(this);
        listButton.addView(listIcon, new LinearLayout.LayoutParams(Ui.px(34), Ui.px(34)));
        TextView listLabel = Ui.text(this, 30, 700, Color.WHITE);
        listLabel.setText("Watchlist");
        listButton.addView(listLabel, margins(16, 0, 0, 0));
        listCount = Ui.text(this, 24, 800, 0xFF0B0D12);
        listCount.setGravity(Gravity.CENTER);
        listCount.setMinWidth(Ui.px(42));
        listCount.setPadding(Ui.px(12), 0, Ui.px(12), 0);
        LinearLayout.LayoutParams clp = new LinearLayout.LayoutParams(-2, Ui.px(42));
        clp.leftMargin = Ui.px(16);
        listButton.addView(listCount, clp);
        FrameLayout.LayoutParams blp = new FrameLayout.LayoutParams(-2, Ui.px(80));
        blp.leftMargin = Ui.px(1040);
        blp.topMargin = Ui.px(40);
        root.addView(listButton, blp);
        listButton.setOnClickListener(v -> openWatchlist());
        listButton.setOnFocusChangeListener((v, has) -> {
            styleListButton();
            v.animate().scaleX(has ? 1.05f : 1f).scaleY(has ? 1.05f : 1f).setDuration(160).start();
        });
        listButton.setNextFocusLeftId(withId(input));
        input.setNextFocusRightId(listButton.getId());

        status = Ui.text(this, 26, 500, 0x99FFFFFF);
        status.setSingleLine(false);
        status.setMaxLines(2);
        status.setEllipsize(TextUtils.TruncateAt.END);
        status.setGravity(Gravity.CENTER_VERTICAL);
        root.addView(status, at(1390, 40, 310, 80));

        // About, in the top-right corner: sources and credits
        aboutButton = new FrameLayout(this);
        aboutButton.setId(View.generateViewId());
        aboutButton.setFocusable(true);
        aboutButton.setFocusableInTouchMode(true);
        aboutButton.setContentDescription("About");
        aboutIcon = new Icon(this);
        aboutButton.addView(aboutIcon, new FrameLayout.LayoutParams(Ui.px(40), Ui.px(40), Gravity.CENTER));
        root.addView(aboutButton, at(1728, 40, 80, 80));
        aboutLabel = Ui.text(this, 24, 700, Color.WHITE);
        aboutLabel.setText("About");
        aboutLabel.setGravity(Gravity.CENTER_HORIZONTAL);
        aboutLabel.setVisibility(View.INVISIBLE);
        root.addView(aboutLabel, at(1688, 136, 160, 32));
        aboutButton.setOnClickListener(v -> openAbout());
        aboutButton.setOnFocusChangeListener((v, has) -> {
            styleAboutButton();
            v.animate().scaleX(has ? 1.05f : 1f).scaleY(has ? 1.05f : 1f).setDuration(160).start();
        });
        styleAboutButton();
        listButton.setNextFocusRightId(aboutButton.getId());
        aboutButton.setNextFocusLeftId(listButton.getId());
        aboutButton.setNextFocusRightId(aboutButton.getId());
        aboutButton.setNextFocusUpId(aboutButton.getId());

        // Poster row
        posterScroll = new HorizontalScrollView(this);
        posterScroll.setHorizontalScrollBarEnabled(false);
        posterScroll.setClipChildren(false);
        posterScroll.setClipToPadding(false);
        posterScroll.setOnScrollChangeListener((v, x, y, ox, oy) -> placeMascot(false));
        posters = new LinearLayout(this);
        posters.setClipChildren(false);
        posters.setClipToPadding(false);   // the focus ring grows past the padding; don't cut it off
        posters.setPadding(Ui.px(112 - RING), Ui.px(30), Ui.px(112), Ui.px(30));
        posterScroll.addView(posters);
        root.addView(posterScroll, at(0, 215, 1920, POSTER_H + 2 * RING + 60));

        // Details for the focused title
        details = new LinearLayout(this);
        details.setOrientation(LinearLayout.VERTICAL);
        details.setClipChildren(false);
        details.setVisibility(View.INVISIBLE);
        root.addView(details, at(112, 616, 760, 464));
        dTitle = Ui.text(this, 56, 800, Color.WHITE);
        dTitle.setEllipsize(TextUtils.TruncateAt.END);
        details.addView(dTitle);
        dMeta = Ui.text(this, 26, 500, 0xBFFFFFFF);
        details.addView(dMeta, margins(0, 12, 0, 0));
        dOverview = Ui.text(this, 26, 400, 0xBFFFFFFF);
        dOverview.setSingleLine(false);
        dOverview.setMaxLines(4);   // leaves room for the watchlist button below
        dOverview.setEllipsize(TextUtils.TruncateAt.END);
        dOverview.setLineSpacing(0, 1.3f);
        details.addView(dOverview, margins(0, 12, 0, 0));

        saveButton = new LinearLayout(this);
        saveButton.setId(View.generateViewId());
        saveButton.setGravity(Gravity.CENTER_VERTICAL);
        saveButton.setPadding(Ui.px(24), 0, Ui.px(30), 0);
        saveButton.setFocusable(true);
        saveButton.setFocusableInTouchMode(true);
        saveButton.setNextFocusDownId(saveButton.getId());
        saveButton.setNextFocusLeftId(saveButton.getId());
        saveIcon = new Icon(this);
        saveButton.addView(saveIcon, new LinearLayout.LayoutParams(Ui.px(30), Ui.px(30)));
        saveLabel = Ui.text(this, 26, 800, Color.WHITE);
        saveButton.addView(saveLabel, margins(14, 0, 0, 0));
        LinearLayout.LayoutParams slp = new LinearLayout.LayoutParams(-2, Ui.px(64));
        slp.topMargin = Ui.px(26);
        details.addView(saveButton, slp);
        saveButton.setOnClickListener(v -> toggleSaved());
        saveButton.setOnFocusChangeListener((v, has) -> styleSaveButton());
        savedNote = Ui.text(this, 22, 500, 0xC7FFFFFF);
        savedNote.setSingleLine(false);
        savedNote.setVisibility(View.GONE);
        details.addView(savedNote, margins(0, 14, 0, 0));

        // Where to watch: a list on the right that scrolls when a title is on many services.
        ScrollView panel = new ScrollView(this);
        panel.setVerticalScrollBarEnabled(false);
        panel.setVisibility(View.INVISIBLE);
        panel.setOnScrollChangeListener((v, x, y, ox, oy) -> placeMascot(false));
        chips = new LinearLayout(this);
        chips.setOrientation(LinearLayout.VERTICAL);
        chips.setClipChildren(false);
        chips.setPadding(0, 0, 0, Ui.px(120));   // the last rows can scroll clear of the fade
        panel.addView(chips, new ScrollView.LayoutParams(-1, -2));
        FrameLayout clip = new FrameLayout(this);   // root lets things overflow (for Stella); the list must not
        clip.addView(panel, new FrameLayout.LayoutParams(-1, -1));
        root.addView(clip, at(1002, 604, 806, 476));
        watchPanel = panel;
        watchFade = new View(this);
        watchFade.setBackground(new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,
                new int[]{0x000B0D12, 0xF20B0D12}));
        watchFade.setVisibility(View.INVISIBLE);
        root.addView(watchFade, at(1002, 980, 806, 100));
        whereToWatch = new WhereToWatch(this);

        hint = Ui.text(this, 34, 500, 0x99FFFFFF);
        hint.setGravity(Gravity.CENTER);
        hint.setSingleLine(false);
        root.addView(hint, at(0, 480, 1920, 120));
        hint.setVisibility(View.GONE);

        emptyList = emptyWatchlist();
        emptyList.setVisibility(View.GONE);
        root.addView(emptyList, at(112, 250, 1500, 320));

        mascot = new MascotView(this);
        mascot.setVisibility(View.INVISIBLE);
        root.addView(mascot, new FrameLayout.LayoutParams(Ui.px(120), Math.round(Ui.px(120) * MascotView.GRID_H / MascotView.GRID_W)));

        about = new AboutPanel(this, this::closeAbout);
        root.addView(about.view, new FrameLayout.LayoutParams(-1, -1));
    }

    // ---- Searching -------------------------------------------------------------------------
    private void search(boolean submitted) {
        String q = input.getText().toString().trim();
        focusResultsWhenDone = submitted;
        if (q.isEmpty()) {
            generation++;
            showWatchlist(submitted);
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
        setWatchlistMode(false);
        status.setText(titles.size() == 1 ? "1 result" : titles.size() + " results");
        if (!index.ready() && streaming.enabled() && q.length() < MIN_STREAMING_CHARS) {
            // Short queries search only the library (free); streaming lookups cost API calls.
            status.setText(titles.size() + " in your library · type " + MIN_STREAMING_CHARS + "+ letters for streaming");
        }
        showPosters(titles);
        if (titles.isEmpty()) {
            boolean sourceOff = !streaming.enabled() || !index.enabled();
            showHint("Nothing found for “" + q + "”"
                    + (sourceOff ? "\nSome sources are off. About, at the top right, says why." : ""));
            return;
        }
        hint.setVisibility(View.GONE);
        if (focusResultsWhenDone) {
            hideKeyboard();
            posters.getChildAt(0).requestFocus();
        }
    }

    private void showPosters(List<Catalog.Title> titles) {
        results = titles;
        badges.clear();
        marks.clear();
        posters.removeAllViews();
        posterScroll.scrollTo(0, 0);
        for (int i = 0; i < titles.size(); i++) posters.addView(poster(titles.get(i), i));
        if (titles.isEmpty()) {
            setDetailsVisible(false);
            setBackdrop(null);
        } else {
            showDetails(titles.get(0), posters.getChildAt(0));
            if (showingWatchlist) lookUp(titles.get(0), posters.getChildAt(0));   // results do this on focus, as you type
        }
    }

    private void showHint(String text) {
        hint.setText(text);
        hint.setVisibility(View.VISIBLE);
    }

    // ---- Watchlist ---------------------------------------------------------------------------
    /** The Watchlist button: shows the saved titles, clearing the search. */
    private void openWatchlist() {
        if (input.getText().length() > 0) {
            input.setText("");
            ui.removeCallbacks(searchSoon);   // setText schedules a search; this replaces it
        }
        showWatchlist(true);
    }

    private void showWatchlist(boolean focusFirst) {
        setWatchlistMode(true);
        List<Catalog.Title> saved = watchlist.titles();
        status.setText(saved.isEmpty() ? "" : "Newest first");
        hint.setVisibility(View.GONE);
        emptyList.setVisibility(saved.isEmpty() ? View.VISIBLE : View.GONE);
        showPosters(saved);
        if (focusFirst && !saved.isEmpty()) {
            hideKeyboard();
            posters.getChildAt(0).requestFocus();
        }
    }

    private void setWatchlistMode(boolean on) {
        showingWatchlist = on;
        if (!on) emptyList.setVisibility(View.GONE);
        styleListButton();
    }

    private void toggleSaved() {
        Catalog.Title t = shown;
        if (t == null) return;
        if (watchlist.contains(t)) {
            watchlist.remove(t);
            savedNote.setText("Removed from your watchlist.");
        } else {
            watchlist.add(t);
            savedNote.setText("Saved. Find it later under Watchlist at the top. Press OK again to remove it.");
        }
        savedNote.setVisibility(View.VISIBLE);
        styleSaveButton();
        styleListButton();
        updateMark(t);
    }

    private void styleSaveButton() {
        boolean saved = shown != null && watchlist.contains(shown);
        boolean focused = saveButton.isFocused();
        int color = saved ? WhereToWatch.NO_COST : Color.WHITE;
        saveButton.setBackground(Ui.rounded(saved ? (focused ? 0x405FD39B : 0x1F5FD39B) : (focused ? 0x24FFFFFF : 0x14FFFFFF), 32,
                focused ? Color.WHITE : saved ? WhereToWatch.NO_COST : 0x59FFFFFF, focused ? 4 : 2));
        saveIcon.set(saved ? Icon.CHECK : Icon.PLUS, color);
        saveLabel.setText(saved ? "On your watchlist" : "Add to watchlist");
        saveLabel.setTextColor(color);
    }

    /** White while the watchlist is showing; the count of saved titles in a pill. */
    private void styleListButton() {
        boolean focused = listButton.isFocused();
        int n = watchlist.size();
        int ink = showingWatchlist ? 0xFF0B0D12 : Color.WHITE;
        listButton.setBackground(showingWatchlist ? Ui.rounded(Color.WHITE, 40, 0, 0)
                : Ui.rounded(focused ? 0x33FFFFFF : 0x1AFFFFFF, 40, focused ? Color.WHITE : 0x59FFFFFF, focused ? 4 : 2));
        listIcon.set(showingWatchlist ? Icon.BOOKMARK_FILLED : Icon.BOOKMARK, ink);
        ((TextView) listButton.getChildAt(1)).setTextColor(ink);
        listCount.setText(String.valueOf(n));
        listCount.setVisibility(n == 0 ? View.GONE : View.VISIBLE);
        listCount.setTextColor(showingWatchlist ? Color.WHITE : 0xFF0B0D12);
        listCount.setBackground(Ui.rounded(showingWatchlist ? 0xFF0B0D12 : Color.WHITE, 21, 0, 0));
    }

    /** White when focused, like the other buttons in the top row; the "About" label shows below it. */
    private void styleAboutButton() {
        boolean focused = aboutButton.isFocused();
        aboutButton.setBackground(focused ? Ui.rounded(Color.WHITE, 40, 0, 0) : Ui.rounded(0x1AFFFFFF, 40, 0x59FFFFFF, 2));
        aboutIcon.set(Icon.INFO, focused ? 0xFF0B0D12 : Color.WHITE);
        aboutLabel.setVisibility(focused ? View.VISIBLE : View.INVISIBLE);
    }

    // ---- About -------------------------------------------------------------------------------
    private void openAbout() {
        hideKeyboard();
        about.clearSources();
        String country = new Locale("", BuildConfig.COUNTRY.toUpperCase(Locale.ROOT)).getDisplayCountry(Locale.US);
        if (streaming.enabled()) {
            about.addSource("Where to watch", AboutPanel.ON, "On, for " + country);
        } else {
            about.addSource("Where to watch", AboutPanel.PROBLEM, "Off: no Streaming Availability key was found when "
                    + "this app was built. See docs/SETUP.md, step 1.");
        }
        if (!index.enabled()) {
            about.addSource("Title list", AboutPanel.PROBLEM, "Off: no TMDB token was found when this app was built, "
                    + "so search finds whole words only. See docs/SETUP.md, step 1.");
        } else if (index.ready()) {
            long updated = index.updated();
            about.addSource("Title list", AboutPanel.ON, String.format(Locale.US, "%,d titles", index.size())
                    + (updated > 0 ? ", updated " + new SimpleDateFormat("MMM d", Locale.US).format(new Date(updated)) : ""));
        } else {
            about.addSource("Title list", AboutPanel.OFF, "Getting ready. Until then, press Search to search.");
        }
        if (!plex.enabled()) {
            about.addSource("Plex", AboutPanel.OFF, "Not set up (optional)");
        } else {
            TextView line = about.addSource("Plex", AboutPanel.OFF, "Checking…");
            io.submit(() -> {
                boolean ok = plex.reachable();
                ui.post(() -> AboutPanel.setState(line, ok ? AboutPanel.ON : AboutPanel.PROBLEM, ok
                        ? "Connected to your server"
                        : "Can't reach your server. Is it on, and is this TV on its allowed list? See docs/SETUP.md, step 3."));
            });
        }
        about.open();
    }

    private void closeAbout() {
        about.hide();
        aboutButton.requestFocus();
    }

    /** A green bookmark on a search result that's already on the watchlist. */
    private void updateMark(Catalog.Title t) {
        View mark = marks.get(t);
        if (mark != null) mark.setVisibility(!showingWatchlist && watchlist.contains(t) ? View.VISIBLE : View.GONE);
    }

    private View emptyWatchlist() {
        FrameLayout box = new FrameLayout(this);
        box.setClipChildren(false);
        LinearLayout row = new LinearLayout(this);
        row.setGravity(Gravity.CENTER_VERTICAL);
        FrameLayout slot = new FrameLayout(this);
        GradientDrawable dashed = Ui.rounded(Color.TRANSPARENT, 16, 0, 0);
        dashed.setStroke(Ui.px(3), 0x4DFFFFFF, Ui.px(14), Ui.px(10));
        slot.setBackground(dashed);
        Icon mark = new Icon(this);
        mark.set(Icon.BOOKMARK, 0x73FFFFFF);
        slot.addView(mark, new FrameLayout.LayoutParams(Ui.px(64), Ui.px(64), Gravity.CENTER));
        row.addView(slot, new LinearLayout.LayoutParams(Ui.px(POSTER_W), Ui.px(POSTER_H)));
        LinearLayout text = new LinearLayout(this);
        text.setOrientation(LinearLayout.VERTICAL);
        TextView head = Ui.text(this, 48, 800, Color.WHITE);
        head.setText("Nothing saved yet");
        text.addView(head);
        TextView body = Ui.text(this, 28, 400, 0xC7FFFFFF);
        body.setSingleLine(false);
        body.setLineSpacing(0, 1.3f);
        body.setText("Search for a movie or show, then choose Add to watchlist under its description. "
                + "It waits here until you're ready to watch, with fresh info on where it's playing.");
        text.addView(body, margins(0, 16, 0, 0));
        LinearLayout.LayoutParams tlp = new LinearLayout.LayoutParams(Ui.px(820), -2);
        tlp.leftMargin = Ui.px(56);
        row.addView(text, tlp);
        box.addView(row, new FrameLayout.LayoutParams(-2, -1));
        MascotView stella = new MascotView(this);
        FrameLayout.LayoutParams mlp = new FrameLayout.LayoutParams(Ui.px(150), Math.round(Ui.px(150) * MascotView.GRID_H / MascotView.GRID_W));
        mlp.leftMargin = Ui.px(1138);
        mlp.topMargin = Ui.px(50);
        box.addView(stella, mlp);
        return box;
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

        Icon mark = new Icon(this);
        mark.set(Icon.BOOKMARK_FILLED, WhereToWatch.NO_COST);
        FrameLayout.LayoutParams mlp = new FrameLayout.LayoutParams(Ui.px(40), Ui.px(40), Gravity.TOP | Gravity.END);
        mlp.rightMargin = Ui.px(12);
        mlp.topMargin = -Ui.px(4);
        card.addView(mark, mlp);
        marks.put(t, mark);
        updateMark(t);
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
        setDetailsVisible(true);
        dTitle.setText(t.title);
        List<String> meta = new ArrayList<>();
        if (t.year > 0) meta.add(String.valueOf(t.year));
        meta.add(t.kind.equals("movie") ? "Movie" : "Series");
        if (t.runtimeMin > 0 && t.kind.equals("movie")) meta.add(t.runtimeMin / 60 > 0 ? (t.runtimeMin / 60) + "h " + (t.runtimeMin % 60) + "m" : t.runtimeMin + "m");
        long savedAt = showingWatchlist ? watchlist.savedAt(t) : 0;
        if (savedAt > 0) meta.add("Saved " + new SimpleDateFormat("MMM d", Locale.getDefault()).format(new Date(savedAt)));
        else if (!t.genres.isEmpty()) meta.add(TextUtils.join(", ", t.genres));
        dMeta.setText(TextUtils.join("  ·  ", meta));
        dOverview.setText(t.overview == null ? "" : t.overview);
        dOverview.setVisibility(t.overview == null ? View.GONE : View.VISIBLE);
        if (t != shown) savedNote.setVisibility(View.GONE);
        shown = t;
        styleSaveButton();

        // Down from the poster: the watchlist button. Right from there: the ways to watch.
        posterView.setNextFocusDownId(saveButton.getId());
        saveButton.setNextFocusUpId(posterView.getId());
        saveButton.setNextFocusRightId(saveButton.getId());
        if (t.options.isEmpty()) {
            chips.removeAllViews();
            boolean checking = (!t.availabilityLoaded && t.tmdb != null && streaming.enabled()) || t.checkPlex;
            TextView note = Ui.text(this, 28, 500, 0x99FFFFFF);
            note.setText(checking ? "Checking where to watch…" : "Not available to stream here");
            chips.addView(note, margins(8, 8, 0, 0));
        } else {
            View best = whereToWatch.fill(chips, t, posterView.getId(), saveButton.getId(), o -> open(t, o));
            watchPanel.scrollTo(0, 0);
            View right = best != null ? best : firstRow();
            if (right != null) saveButton.setNextFocusRightId(withId(right));
        }
        setBackdrop(t.backdrop);
    }

    private View firstRow() {
        for (int i = 0; i < chips.getChildCount(); i++) if (chips.getChildAt(i).isFocusable()) return chips.getChildAt(i);
        return null;
    }

    private void setDetailsVisible(boolean on) {
        int v = on ? View.VISIBLE : View.INVISIBLE;
        details.setVisibility(v);
        watchPanel.setVisibility(v);
        watchFade.setVisibility(v);
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
        boolean needStreaming = t.tmdb != null && !t.availabilityLoaded && streaming.enabled();
        boolean needYouTube = t.tmdb != null && !t.youtubeChecked && youtube.enabled();
        boolean needPlex = t.checkPlex && plex.enabled();
        if (!(needStreaming || needYouTube || needPlex) || !lookingUp.add(t)) return;
        io.execute(() -> {
            Catalog.Title found = null, inLibrary = null;
            Catalog.Option onYouTube = null;
            try {
                if (needPlex) inLibrary = findInPlex(t);
            } catch (Exception e) {
                Log.w(TAG, "Plex check failed for " + t.title, e);
            }
            try {
                if (needStreaming) found = streaming.lookup(t.tmdb);
            } catch (Exception e) {
                Log.w(TAG, "Lookup failed for " + t.tmdb, e);
            }
            try {
                if (needYouTube) onYouTube = youtube.freeOption(t);
            } catch (Exception e) {
                Log.w(TAG, "YouTube check failed for " + t.tmdb, e);
            }
            Catalog.Title result = found, library = inLibrary;
            Catalog.Option yt = onYouTube;
            ui.post(() -> {
                lookingUp.remove(t);
                t.availabilityLoaded = true;
                t.youtubeChecked = true;
                t.checkPlex = false;
                if (library != null) {
                    t.options.addAll(library.options);
                    Catalog.sortOptions(t, myServices);
                }
                if (yt != null) {
                    t.options.add(yt);
                    Catalog.sortOptions(t, myServices);
                }
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
                // Refresh the details if they're for this title, unless a way to watch is selected
                // (rebuilding the list would take the selection away).
                View f = getCurrentFocus();
                if (f == posterView || (shown == t && !(f != null && f.getTag() instanceof WhereToWatch.Row))) showDetails(t, posterView);
            });
        });
    }

    /** A saved title in the Plex library now (it may have been added since it was saved). */
    private Catalog.Title findInPlex(Catalog.Title t) throws Exception {
        for (Catalog.Title p : plex.search(t.title)) {
            if (t.tmdb != null ? t.tmdb.equals(p.tmdb) : Catalog.norm(p.title).equals(Catalog.norm(t.title)) && p.year == t.year) return p;
        }
        return null;
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
        if (v == null || v == input || v == aboutButton || about.isOpen() || v.getParent() == null) {
            mascotTarget = null;
            mascot.setVisibility(View.INVISIBLE);
            return;
        }
        if (v.getTag() instanceof WhereToWatch.Row) {
            // Keep the chosen row clear of the fade at the bottom, with the row above it in view.
            ui.post(() -> ((ScrollView) watchPanel).smoothScrollTo(0, Math.max(0, v.getTop() - Ui.px(110))));
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
        boolean isRow = v.getTag() instanceof WhereToWatch.Row || v == saveButton;
        int size = Ui.px(isPoster ? 120 : isRow ? 92 : 76);
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
        if (isRow) {
            // No room above a row: she sits just to its left, in the gap beside the description.
            x = r.left - size - Ui.px(4);
            y = r.exactCenterY() - height * 0.62f;
        } else if (v == listButton) {
            // Nothing above it but the screen edge: she sits just to its right.
            x = r.right + Ui.px(8);
            y = r.exactCenterY() - height * 0.62f;
        }
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
        if (about.isOpen()) {
            closeAbout();
            return;
        }
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
                && (getCurrentFocus() == input || getCurrentFocus() == listButton || getCurrentFocus() == aboutButton)
                && posters.getChildCount() > 0) {
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

    @Override protected void onPause() {
        super.onPause();
        hideKeyboard();   // otherwise it stays up over whatever opens next, and the home screen slides up for it
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

    /** Small line icons: plus, check, a bookmark (outline or filled), and an "i" in a circle. Drawn on a 24-unit grid. */
    private static final class Icon extends View {
        static final int PLUS = 0, CHECK = 1, BOOKMARK = 2, BOOKMARK_FILLED = 3, INFO = 4;
        private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Path path = new Path();
        private int shape;

        Icon(Context c) {
            super(c);
            p.setStrokeCap(Paint.Cap.ROUND);
            p.setStrokeJoin(Paint.Join.ROUND);
            p.setColor(Color.WHITE);
        }

        void set(int shape, int color) {
            this.shape = shape;
            p.setColor(color);
            invalidate();
        }

        @Override protected void onDraw(Canvas c) {
            float s = getWidth() / 24f;
            path.reset();
            p.setStyle(shape == BOOKMARK_FILLED ? Paint.Style.FILL_AND_STROKE : Paint.Style.STROKE);
            switch (shape) {
                case PLUS:
                    p.setStrokeWidth(2.4f * s);
                    c.drawLine(12 * s, 5 * s, 12 * s, 19 * s, p);
                    c.drawLine(5 * s, 12 * s, 19 * s, 12 * s, p);
                    return;
                case CHECK:
                    p.setStrokeWidth(2.6f * s);
                    path.moveTo(5 * s, 12.5f * s);
                    path.lineTo(9.5f * s, 17 * s);
                    path.lineTo(19 * s, 7.5f * s);
                    break;
                case INFO:
                    p.setStrokeWidth(2.2f * s);
                    c.drawCircle(12 * s, 12 * s, 9.5f * s, p);
                    c.drawLine(12 * s, 11 * s, 12 * s, 17 * s, p);
                    p.setStyle(Paint.Style.FILL);
                    c.drawCircle(12 * s, 7.4f * s, 1.4f * s, p);
                    return;
                default:
                    p.setStrokeWidth(2 * s);
                    path.moveTo(6.5f * s, 3.5f * s);
                    path.lineTo(17.5f * s, 3.5f * s);
                    path.lineTo(17.5f * s, 20.5f * s);
                    path.lineTo(12 * s, 16.5f * s);
                    path.lineTo(6.5f * s, 20.5f * s);
                    path.close();
            }
            c.drawPath(path, p);
        }
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
