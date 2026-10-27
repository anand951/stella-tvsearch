package com.stellatv.search;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

/**
 * The About panel: slides over the right of the screen from the ⓘ button. Says which sources
 * are working (and what to do about the ones that aren't), and carries the credits TMDB's terms
 * require. Close is its only button, so focus stays inside it until it's closed.
 */
final class AboutPanel {
    static final int ON = 0, PROBLEM = 1, OFF = 2;
    private static final int DIM = 0xB8FFFFFF, BODY = 0xD9FFFFFF, PROBLEM_COLOR = 0xFFFFB86B;
    private static final float WIDTH = 960, PAD = 80;

    final FrameLayout view;
    final View close;
    private final Context c;
    private final LinearLayout sources;

    AboutPanel(Context c, Runnable onClose) {
        this.c = c;
        view = new FrameLayout(c);
        view.setBackgroundColor(0xB80B0D12);
        view.setVisibility(View.GONE);
        view.setClickable(true);   // nothing behind it reacts while it's open

        LinearLayout sheet = new LinearLayout(c);
        sheet.setOrientation(LinearLayout.VERTICAL);
        sheet.setBackgroundColor(0xFF141821);
        sheet.setPadding(Ui.px(PAD), Ui.px(56), Ui.px(PAD), Ui.px(56));
        view.addView(sheet, new FrameLayout.LayoutParams(Ui.px(WIDTH), -1, Gravity.END));
        View edge = new View(c);
        edge.setBackgroundColor(0x1AFFFFFF);
        view.addView(edge, new FrameLayout.LayoutParams(Math.max(1, Ui.px(1.5f)), -1, Gravity.END) {{
            rightMargin = Ui.px(WIDTH);
        }});

        TextView name = Ui.text(c, 60, 800, Color.WHITE);
        name.setText("Search");
        sheet.addView(name);
        TextView version = Ui.text(c, 26, 500, DIM);
        version.setText("Version " + BuildConfig.VERSION_NAME + " · github.com/anand951/stella-tvsearch");
        sheet.addView(version, margins(0, 10, 0, 0));

        sheet.addView(heading("Sources"), margins(0, 36, 0, 0));
        sources = new LinearLayout(c);
        sources.setOrientation(LinearLayout.VERTICAL);
        sheet.addView(sources);

        sheet.addView(heading("Credits"), margins(0, 36, 0, 0));
        ImageView tmdb = new ImageView(c);
        tmdb.setImageResource(R.drawable.tmdb_logo);   // smaller than the app's name, as TMDB asks
        tmdb.setScaleType(ImageView.ScaleType.FIT_START);
        tmdb.setContentDescription("TMDB");
        sheet.addView(tmdb, new LinearLayout.LayoutParams(Ui.px(210), Ui.px(27)) {{ topMargin = Ui.px(18); }});
        sheet.addView(paragraph("Movie and show details and posters come from TMDB. This application uses TMDB "
                + "and the TMDB APIs but is not endorsed, certified, or otherwise approved by TMDB.", BODY), margins(0, 16, 0, 0));
        sheet.addView(paragraph("Where-to-watch data, prices and service logos come from the Streaming "
                + "Availability API by Movie of the Night.", BODY), margins(0, 14, 0, 0));
        sheet.addView(paragraph("Open source, Apache License 2.0. Figtree font: SIL Open Font License.", DIM),
                margins(0, 14, 0, 0));

        sheet.addView(new View(c), new LinearLayout.LayoutParams(-1, 0, 1));   // pushes Close to the foot
        LinearLayout bottom = new LinearLayout(c);
        bottom.setGravity(Gravity.CENTER_VERTICAL);
        sheet.addView(bottom, margins(0, 28, 0, 0));
        TextView closeButton = Ui.text(c, 30, 800, 0xFF0B0D12);
        closeButton.setText("Close");
        closeButton.setGravity(Gravity.CENTER);
        closeButton.setPadding(Ui.px(48), 0, Ui.px(48), 0);
        closeButton.setBackground(Ui.rounded(Color.WHITE, 39, 0, 0));
        closeButton.setFocusable(true);
        closeButton.setFocusableInTouchMode(true);
        closeButton.setId(View.generateViewId());
        // The only button here: every direction stays on it.
        closeButton.setNextFocusUpId(closeButton.getId());
        closeButton.setNextFocusDownId(closeButton.getId());
        closeButton.setNextFocusLeftId(closeButton.getId());
        closeButton.setNextFocusRightId(closeButton.getId());
        closeButton.setOnClickListener(v -> onClose.run());
        bottom.addView(closeButton, new LinearLayout.LayoutParams(-2, Ui.px(78)));
        TextView orBack = Ui.text(c, 24, 500, DIM);
        orBack.setText("or press Back");
        bottom.addView(orBack, margins(26, 0, 0, 0));
        close = closeButton;
    }

    boolean isOpen() {
        return view.getVisibility() == View.VISIBLE;
    }

    void open() {
        view.setVisibility(View.VISIBLE);
        close.requestFocus();
    }

    void hide() {
        view.setVisibility(View.GONE);
    }

    void clearSources() {
        sources.removeAllViews();
    }

    /** Adds a line under Sources. Returns its detail text, for checks that finish later. */
    TextView addSource(String name, int state, String detail) {
        LinearLayout row = new LinearLayout(c);
        Mark mark = new Mark(c);
        row.addView(mark, new LinearLayout.LayoutParams(Ui.px(36), Ui.px(36)) {{ rightMargin = Ui.px(21); topMargin = Ui.px(2); }});
        LinearLayout words = new LinearLayout(c);
        words.setOrientation(LinearLayout.VERTICAL);
        TextView title = Ui.text(c, 30, 700, Color.WHITE);
        title.setText(name);
        words.addView(title);
        TextView d = paragraph(detail, DIM);
        d.setTextSize(android.util.TypedValue.COMPLEX_UNIT_PX, Ui.px(24));
        words.addView(d, margins(0, 6, 0, 0));
        row.addView(words, new LinearLayout.LayoutParams(0, -2, 1));
        d.setTag(mark);
        setState(d, state, detail);
        sources.addView(row, margins(0, 20, 0, 0));
        return d;
    }

    /** Changes a source's line once a check finishes. */
    static void setState(TextView detail, int state, String text) {
        detail.setText(text);
        detail.setTextColor(state == PROBLEM ? PROBLEM_COLOR : DIM);
        ((Mark) detail.getTag()).set(state);
    }

    private TextView heading(String text) {
        TextView t = Ui.text(c, 28, 700, DIM);
        t.setText(text);
        return t;
    }

    private TextView paragraph(String text, int color) {
        TextView t = Ui.text(c, 24, 400, color);
        t.setSingleLine(false);
        t.setLineSpacing(0, 1.3f);
        t.setText(text);
        return t;
    }

    private static LinearLayout.LayoutParams margins(float l, float t, float r, float b) {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-2, -2);
        lp.setMargins(Ui.px(l), Ui.px(t), Ui.px(r), Ui.px(b));
        return lp;
    }

    /** A green check, an orange "!" in a circle, or a grey dash. Drawn on a 24-unit grid. */
    private static final class Mark extends View {
        private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        private int state;

        Mark(Context c) {
            super(c);
            p.setStyle(Paint.Style.STROKE);
            p.setStrokeCap(Paint.Cap.ROUND);
            p.setStrokeJoin(Paint.Join.ROUND);
        }

        void set(int state) {
            this.state = state;
            invalidate();
        }

        @Override protected void onDraw(Canvas c) {
            float s = getWidth() / 24f;
            p.setStrokeWidth(2.6f * s);
            p.setStyle(Paint.Style.STROKE);
            if (state == ON) {
                p.setColor(WhereToWatch.NO_COST);
                c.drawLine(4.5f * s, 12.5f * s, 9.5f * s, 17.5f * s, p);
                c.drawLine(9.5f * s, 17.5f * s, 19.5f * s, 7 * s, p);
            } else if (state == PROBLEM) {
                p.setColor(PROBLEM_COLOR);
                c.drawCircle(12 * s, 12 * s, 9.5f * s, p);
                c.drawLine(12 * s, 7 * s, 12 * s, 13 * s, p);
                p.setStyle(Paint.Style.FILL);
                c.drawCircle(12 * s, 16.8f * s, 1.5f * s, p);
            } else {
                p.setColor(0x8CFFFFFF);
                c.drawLine(6 * s, 12 * s, 18 * s, 12 * s, p);
            }
        }
    }
}
