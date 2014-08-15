package org.voelkerweb.midiviz;

import android.content.Context;
import android.graphics.Canvas;
import android.util.AttributeSet;
import android.view.View;

import java.util.ArrayList;
import java.util.List;

/**
 * This is a view for the canvas on which the Midi visualizations are drawn. For creating custom
 * views, see http://developer.android.com/training/custom-views/index.html.
 * <p/>
 * We do all the heavy-lifting here. This is the 'main' class so to speak.
 * Note, however, that it's not recommended to do expensive stuff in onDraw (such as allocating or
 * deallocating memory).
 */
public class Visualizer extends View
{
    private static final String TAG = "Visualizer";

    private Parameters parameters;
    private NoteTracker noteTracker;
    private NotePainter notePainter;
    private NotePainter.Area area = new NotePainter.Area();

    private int currentColumn = -1;  // So the first measure starts in column 0.

    public Visualizer(Context context, AttributeSet attrs)
    {
        super(context, attrs);
        parameters = new Parameters(context);
        noteTracker = new NoteTracker(parameters);
        notePainter = new NotePainter(parameters);
        setKeepScreenOn(true);  // TODO: settings
    }

    public void registerMetronome(Metronome metronome) {
        noteTracker.registerMetronome(metronome);
    }

    /**
     * Processes the messages via NoteTracker and invalidates the view so it gets redrawn.
     */
    public void update(List<MidiMessage> messages)
    {
        int numMeasuresAdded = noteTracker.update(messages);
        currentColumn = (currentColumn + numMeasuresAdded) % parameters.numMeasuresPerRow();

        // TODO: it would be more efficient to just invalidate the currently active measure
        // via .invalidateDrawable(drawable).
        invalidate();
    }

    @Override
    protected void onDraw(Canvas canvas)
    {
        super.onDraw(canvas);

        // Since we want to complete the current row with empty measures, it's easiest to just
        // scan backwards through columns and rows until we either have drawn all required rows or
        // ran out of measures to draw.
        //
        // Cache display parameters, since they may require expensive lookups.
        int numRowsToDisplay = parameters.numRowsToDisplay();
        int numMeasuresPerRow = parameters.numMeasuresPerRow();
        float measureWidth = parameters.measureWidth();
        float measureHeight = parameters.measureHeight();
        float rowSpacing = parameters.rowSpacing();
        int beatsPerMeasure = parameters.beatsPerMeasure();
        int subBeats = parameters.subBeats();

        ArrayList<Measure> measures = noteTracker.getMeasures();
        int idx = measures.size() - 1;
        for (int row = 0; row < numRowsToDisplay && idx >= 0; ++row) {
            for (int col = numMeasuresPerRow - 1; col >= 0 && idx >= 0; --col) {
                area.assign(measureWidth * col, (measureHeight + rowSpacing) * row,
                            measureWidth, measureHeight);
                if (row == 0 && col > currentColumn) {
                    notePainter.drawEmptyMeasure(beatsPerMeasure, subBeats, canvas, area);
                } else {
                    Measure measure = measures.get(idx--);
                    notePainter.drawMeasure(measure, canvas, area, System.currentTimeMillis());
                }
            }
        }
    }
}
