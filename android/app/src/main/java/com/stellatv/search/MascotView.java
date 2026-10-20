package com.stellatv.search;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.view.View;
import android.view.animation.AccelerateDecelerateInterpolator;
import android.view.animation.DecelerateInterpolator;

/**
 * Stella the labradoodle, the focus indicator: she peeks over the selected item: her head bobs, blinks and sways
 * its ears while her two front paws rest on the tile's top edge.
 *
 * Drawn on a 120 x 132 grid: the top 20 units are headroom for the hop, and the paws are centred
 * on y = PAW_Y, which the caller lines up with the tile's top edge.
 */
final class MascotView extends View {
    static final float GRID_W = 120f, GRID_H = 132f, PAW_Y = 124f;
    private static final float TOP = 20f; // hop headroom

    private static final int FUR = 0xFFD9A066, FUR_LIGHT = 0xFFF1D2A2, EAR = 0xFFB8733F, NOSE = 0xFF4A2A22,
            NOSE_SHINE = 0xFF8A5A4A, EYE = 0xFF2E1C12, HARNESS = 0xFFD62B2B, TAG = 0xFFF2C94C;

    private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint line = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path path = new Path();
    private float bob = 0f;     // 0..1
    private float blink = 1f;   // 1 = eyes open
    private float sway = 0f;    // -1..1, ear sway
    private float hop = 0f;     // 0..1, height of the hop between tiles
    private final ValueAnimator bobAnim, blinkAnim, swayAnim;
    private ValueAnimator hopAnim;

    MascotView(Context c) {
        super(c);
        line.setStyle(Paint.Style.STROKE);
        line.setStrokeCap(Paint.Cap.ROUND);

        bobAnim = ValueAnimator.ofFloat(0f, 1f, 0f);
        bobAnim.setDuration(2600);
        bobAnim.setRepeatCount(ValueAnimator.INFINITE);
        bobAnim.setInterpolator(new AccelerateDecelerateInterpolator());
        bobAnim.addUpdateListener(a -> { bob = (float) a.getAnimatedValue(); invalidate(); });

        blinkAnim = ValueAnimator.ofFloat(1f, 1f, 1f, 1f, 1f, 1f, 1f, 1f, 1f, 1f, 1f, 1f, 1f, 0.1f, 1f);
        blinkAnim.setDuration(4200);
        blinkAnim.setRepeatCount(ValueAnimator.INFINITE);
        blinkAnim.addUpdateListener(a -> blink = (float) a.getAnimatedValue());

        swayAnim = ValueAnimator.ofFloat(-1f, 1f, -1f);
        swayAnim.setDuration(3400);
        swayAnim.setRepeatCount(ValueAnimator.INFINITE);
        swayAnim.setInterpolator(new AccelerateDecelerateInterpolator());
        swayAnim.addUpdateListener(a -> sway = (float) a.getAnimatedValue());
    }

    /** A little jump, played while she moves to the next tile. */
    void hop() {
        if (hopAnim != null) hopAnim.cancel();
        hopAnim = ValueAnimator.ofFloat(0f, 1f, 0f);
        hopAnim.setDuration(360);
        hopAnim.setInterpolator(new DecelerateInterpolator());
        hopAnim.addUpdateListener(a -> { hop = (float) a.getAnimatedValue(); invalidate(); });
        hopAnim.start();
    }

    @Override protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        bobAnim.start();
        blinkAnim.start();
        swayAnim.start();
    }

    @Override protected void onDetachedFromWindow() {
        bobAnim.cancel();
        blinkAnim.cancel();
        swayAnim.cancel();
        if (hopAnim != null) hopAnim.cancel();
        super.onDetachedFromWindow();
    }

    @Override protected void onDraw(Canvas c) {
        float s = getWidth() / GRID_W;
        c.save();
        c.scale(s, s);
        c.translate(0, TOP - 18f * hop);

        // Head (bobs on its own; the paws stay planted on the tile)
        c.save();
        c.translate(60, 54 - 3f * bob);
        c.scale(1.2f, 1.2f);
        drawHead(c);
        c.restore();

        // Front paws over the tile edge
        oval(c, 38, 104, 12, 7.5f, FUR);
        oval(c, 82, 104, 12, 7.5f, FUR);
        line.setColor(EAR);
        line.setStrokeWidth(1.3f);
        for (float x : new float[] {34, 42, 78, 86}) c.drawLine(x, 103, x, 107, line);
        c.drawLine(38, 102, 38, 107, line);
        c.drawLine(82, 102, 82, 107, line);
        c.restore();
    }

    /** Her head, centred on 0,0. */
    private void drawHead(Canvas c) {
        for (int a = 0; a < 360; a += 30) {       // curly outline
            double r = Math.toRadians(a);
            circle(c, (float) Math.cos(r) * 24, (float) Math.sin(r) * 23, 10, FUR);
        }
        circle(c, 0, 0, 26, FUR);
        circle(c, -9, -25, 8, FUR_LIGHT);         // lighter top-knot
        circle(c, 3, -28, 8.5f, FUR_LIGHT);
        circle(c, 14, -23, 7, FUR_LIGHT);

        drawEar(c, -27, 14, 4f * sway);
        drawEar(c, 27, -14, -4f * sway);

        oval(c, 0, 12, 14, 11, FUR_LIGHT);        // muzzle
        oval(c, -10, -7, 7, 3.6f, FUR_LIGHT);     // eyebrows
        oval(c, 10, -7, 7, 3.6f, FUR_LIGHT);

        float ey = 3.8f * blink;
        oval(c, -10, 0, 3.8f, ey, EYE);
        oval(c, 10, 0, 3.8f, ey, EYE);
        if (blink > 0.6f) {
            circle(c, -8.8f, -1.3f, 1.2f, 0xFFFFFFFF);
            circle(c, 11.2f, -1.3f, 1.2f, 0xFFFFFFFF);
        }

        oval(c, 0, 8, 5.6f, 4.2f, NOSE);
        oval(c, -1.6f, 6.6f, 1.8f, 1f, NOSE_SHINE);
        line.setColor(NOSE);
        line.setStrokeWidth(1.4f);
        path.reset();
        path.moveTo(-4, 14);
        path.quadTo(0, 17.5f, 4, 14);
        c.drawPath(path, line);

        line.setColor(HARNESS);                   // red harness + yellow tag
        line.setStrokeWidth(5);
        path.reset();
        path.moveTo(-17, 24);
        path.quadTo(0, 33, 17, 24);
        c.drawPath(path, line);
        circle(c, 0, 31.5f, 2.6f, TAG);
    }

    private void drawEar(Canvas c, float x, float tilt, float swayDeg) {
        c.save();
        c.rotate(swayDeg, x, -10);
        c.save();
        c.rotate(tilt, x, 10);
        oval(c, x, 10, 10, 20, EAR);
        c.restore();
        circle(c, x - 4, 27, 6, EAR);
        circle(c, x + 4, 28, 6, EAR);
        c.restore();
    }

    private void circle(Canvas c, float x, float y, float r, int color) {
        fill.setColor(color);
        c.drawCircle(x, y, r, fill);
    }

    private void oval(Canvas c, float x, float y, float rx, float ry, int color) {
        fill.setColor(color);
        c.drawOval(x - rx, y - ry, x + rx, y + ry, fill);
    }
}
