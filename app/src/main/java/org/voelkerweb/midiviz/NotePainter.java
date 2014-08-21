package org.voelkerweb.midiviz;

import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.DashPathEffect;
import android.graphics.Paint;
import android.graphics.Path;

/**
 * Draws a musical measure and the contained notes on a canvas.
 */
public class NotePainter
{
    private static int ALPHA = 50;  // [0..255]
    private static float SATURATION = 1.0f;  // [0..1]
    private static float VALUE = 1.0f;  // [0..1]
    private float[] hsv = {0.0f, SATURATION, VALUE};  // mutable
    private static float MARKER_SIZE = 10.0f;

    Parameters mParameters;

    // TODO: a lot of this stuff can be static.
    private Paint linePaint = new Paint(Paint.ANTI_ALIAS_FLAG);  // for thick lines
    private Paint beatLinePaint = new Paint(Paint.ANTI_ALIAS_FLAG);  // for beat lines
    private Paint subBeatLinePaint = new Paint(Paint.ANTI_ALIAS_FLAG);  // for sub-beat lines
    private Paint levelLinePaint = new Paint(Paint.ANTI_ALIAS_FLAG);  // for level lines

    private Paint areaPaint = new Paint(Paint.ANTI_ALIAS_FLAG);  // for rectangles
    private Path path = new Path();  // TODO: needed?

    public NotePainter(Parameters parameters)
    {
        mParameters = parameters;

        linePaint.setColor(Color.BLACK);
        linePaint.setStyle(Paint.Style.STROKE);

        beatLinePaint.setColor(Color.LTGRAY);
        beatLinePaint.setStyle(Paint.Style.STROKE);
        beatLinePaint.setPathEffect(new DashPathEffect(new float[]{5.0f, 5.0f}, 0.0f));

        subBeatLinePaint.setColor(Color.LTGRAY);
        subBeatLinePaint.setStyle(Paint.Style.STROKE);
        subBeatLinePaint.setPathEffect(new DashPathEffect(new float[]{5.0f, 5.0f}, 0.0f));

        levelLinePaint.setColor(Color.GRAY);

        areaPaint.setStyle(Paint.Style.FILL);
    }

    // Computes the x coordinate for the given beat value.
    private float getX(float beat, int numBeats, Area area)
    {
        return area.x0 + (area.x1 - area.x0) * beat / numBeats;
    }

    // Computes the y coordinate for the given note level.
    private float getY(float level, Area area)
    {
        return area.y1 + (area.y0 - area.y1) * level;
    }

    public void drawEmptyMeasure(int numBeats, int numSubBeats, Canvas canvas, Area area)
    {
        // Draw horizontal lines at volume levels.
        for (float level = 0.0f; level <= 1.0; level += mParameters.levelMarkerInterval()) {
            float y = getY(level, area);
            canvas.drawLine(area.x0, y, area.x1, y, levelLinePaint);
        }

        // Draw vertical lines at beginning and end of measure.
        canvas.drawLine(area.x0, area.y0, area.x0, area.y1, linePaint);
        canvas.drawLine(area.x1, area.y0, area.x1, area.y1, linePaint);

        // Draw beat and sub-beat markers. Skip first and last because they coincide with measure
        // boundaries.
        for (int b = 0; b < numBeats; ++b) {
            if (b > 0) {
                float x = getX(b, numBeats, area);
                myDrawLine(canvas, x, area.y0, x, area.y1, beatLinePaint);
            }
            for (int sb = 1; sb < numSubBeats; ++sb) {
                float x = getX(b + ((float) sb) / ((float) numSubBeats), numBeats, area);
                myDrawLine(canvas, x, (area.y0 + area.y1) / 2, x, area.y1, subBeatLinePaint);
            }
        }
    }

    /**
     * There is a bug in Android that prevents Canvas.drawLine from drawing dashed lines. We need to
     * use Canvas.drawPath as a workaround.
     * See https://code.google.com/p/android/issues/detail?id=29944
     * <p/>
     * However, drawPath seems to be wildly more expensive. So let's just stick with a solid line.
     */
    private void myDrawLine(Canvas canvas, float x0, float y0, float x1, float y1, Paint paint)
    {
        /*
        path.moveTo(x0, y0);
        path.lineTo(x1, y1);
        canvas.drawPath(path, paint);
        path.reset();
        */
        canvas.drawLine(x0, y0, x1, y1, paint);
    }

    // Draws the measure, plus a time indicator at the position corresponding to currentTime.
    public void drawMeasure(Measure measure, Canvas canvas, Area area, long currentTime)
    {
        drawEmptyMeasure(measure.getNumBeats(), mParameters.subBeats(), canvas, area);

        float currentBeat = measure.getBeatForTime(currentTime);
        float currentX = getX(currentBeat, measure.getNumBeats(), area);

        // Draw notes.
        // TODO: we currently draw vertical lines even for notes that extend the measure boundaries.
        // If this leads to graphics glitches, we have to introduce additional flags for these.
        boolean hasHeldNotes = false;
        float y0 = area.y1;  // convenient alias since y1 is 'bottom'
        for (Measure.Note note : measure.getNotes()) {
            float x0 = getX(note.startBeat, measure.getNumBeats(), area);
            float x1 = note.held ? currentX : getX(note.endBeat, measure.getNumBeats(), area);
            float y1 = getY(note.level, area);

            hsv[0] = getHue(note.key);
            areaPaint.setColor(Color.HSVToColor(ALPHA, hsv));
            // It seems that rectangles *have* to be drawn up-left to down-right!
            canvas.drawRect(x0, y1, x1, y0, areaPaint);

            // Draw a little line at the beginning of the note so we can more easily see at exactly
            // what time the note was played.
            // TODO: control this via settings.
            float blipSize = 0.05f * (area.y0 - area.y1);
            canvas.drawLine(x0, y0 - blipSize, x0, y1, linePaint);
            canvas.drawLine(x0, y1, x1, y1, linePaint);

            // Draw time marker for held notes.
            if (note.held) {
                canvas.drawCircle(currentX, y1, MARKER_SIZE, areaPaint);
                canvas.drawCircle(currentX, y1, MARKER_SIZE, linePaint);
                hasHeldNotes = true;
            } else {
                canvas.drawLine(x1, y0, x1, y1, linePaint);
            }
        }

        if (measure.isActive()) {
            canvas.drawLine(area.x0, y0, currentX, y0, linePaint);
            if (!hasHeldNotes) {
                // If there are no held notes, draw time marker on the base line.
                canvas.drawCircle(currentX, y0, MARKER_SIZE, linePaint);
            }
        } else {
            canvas.drawLine(area.x0, y0, area.x1, y0, linePaint);
        }
    }

    // Returns the hue in which to paint a given note.
    private float getHue(int key)
    {
        return (key % 12) * 360 / 12;
    }

    /**
     * Represents a screen area.
     */
    public static class Area
    {
        public float x0, y0, x1, y1;

        public void assign(float startX, float startY, float width, float height)
        {
            x0 = startX;
            y0 = startY;
            x1 = startX + width;
            y1 = startY + height;
        }
    }
}
