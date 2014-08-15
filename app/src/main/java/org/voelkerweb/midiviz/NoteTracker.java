package org.voelkerweb.midiviz;

import android.util.Log;

import java.util.ArrayList;
import java.util.List;
import java.util.ListIterator;

/**
 * Processes Midi messages and keeps track of notes and measures.
 */
public class NoteTracker
{
    private static final String TAG = "NoteTracker";
    private ArrayList<Measure> measures = new ArrayList<Measure>();
    private Metronome metronome;
    private Parameters mParameters;

    public NoteTracker(Parameters parameters) {
        mParameters = parameters;
    }

    // Register a handle to metronome so we can notify the metronome of ding times.
    public void registerMetronome(Metronome metro) {
        metronome = metro;
    }

    /**
     * Iteratively adds notes from the iterator to the measure, as long as they fall within the
     * measure's time interval. Any notes with a timestamp earlier than the measure's start time
     * are dropped. If a note with a timestamp later than the measure's end time is encountered, the
     * function returns with the iterator pointing to this note, so it can be processed in the next
     * measure.
     */
    private void updateMeasureFromMessages(Measure measure, ListIterator<MidiMessage> iter)
    {
        while (iter.hasNext()) {
            MidiMessage message = iter.next();
            // We drop any notes with timestamp < measure.getStartTime()
            if (message.timestamp >= measure.getStartTime()) {
                if (message.timestamp < measure.getEndTime()) {
                    // add this note to the measure
                    measure.updateFromMessage(message, mParameters);
                } else {
                    // this note should go into the next measure, so we're done.
                    iter.previous();
                    return;
                }
            }
        }
    }

    private void dropExpiredMeasures(ArrayList<Measure> measures)
    {
        // Dropping from the front of an ArrayList is inefficient, but this should be insignificant
        // since the number of elements in the list is small.
        int numToDrop = measures.size() - mParameters.numMeasuresToKeep();
        if (numToDrop > 0) {
            measures.subList(0, numToDrop).clear();
        }
    }

    public ArrayList<Measure> getMeasures()
    {
        return measures;
    }

    /**
     * Adds notes to the current measure, creates a new measure when required, and drops expired
     * measures. Then invalidates the view so it gets redrawn.
     * <p/>
     * Returns the number of new measures that were started in this update (if any.)
     */
    public int update(List<MidiMessage> messages)
    {
        // TODO: we need some delay before 1st measure; and send ding time explicitly
        long time = System.currentTimeMillis();
        int newMeasures = 0;
        if (measures.isEmpty()) {
            measures.add(new Measure(time,
                                     mParameters.measureDurationMillis(),
                                     mParameters.beatsPerMeasure(),
                                     true));
            ++newMeasures;
        }

        ListIterator<MidiMessage> messageIterator = messages.listIterator();

        Measure latestMeasure = measures.get(measures.size() - 1);
        updateMeasureFromMessages(latestMeasure, messageIterator);

        while (latestMeasure.getEndTime() < time) {
            measures.add(Measure.FromLastMeasure(latestMeasure,
                                                 mParameters.measureDurationMillis(),
                                                 mParameters.beatsPerMeasure(),
                                                 true));
            ++newMeasures;
            latestMeasure.complete();  // Must be called after held notes are carried over.
            latestMeasure = measures.get(measures.size() - 1);
            updateMeasureFromMessages(latestMeasure, messageIterator);
        }

        // We send ding times for the latest measure only; it's too late for any in-betweens anyway.
        if (newMeasures > 0) {
            sendDingTimes(latestMeasure);
        }

        dropExpiredMeasures(measures);
        return newMeasures;
    }

    // Sends this measure's metronome ding times to the metronome.
    private void sendDingTimes(Measure measure) {
        if (metronome == null) {
            Log.e(TAG, "No metronome registered.");
            return;
        }
        long[] times = measure.getDingTimes();
        for (int n = 1; n < times.length; ++n) {  // Don't send first ding, but do send last.
            metronome.dingAt(times[n], n == times.length - 1);
        }
    }
}
