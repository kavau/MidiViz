package org.voelkerweb.midiviz;

import android.util.Log;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Represents all notes contained in a musical measure.
 */
public class Measure
{
    private static final String TAG = "Measure";
    private boolean active = false;
    private long t0, t1;
    private int numBeats;
    private List<Note> notes = new ArrayList<Note>();
    private Map<Integer, Note> heldNotes = new HashMap<Integer, Note>();

    // Creates a measure with the given startTime.
    public Measure(long startTime, long duration, int beats, boolean isActive)
    {
        active = isActive;
        numBeats = beats;
        t0 = startTime;
        t1 = t0 + duration;
    }

    // Creates a measure immediately succeeding the lastMeasure, and carrying over any held notes.
    public static Measure FromLastMeasure(Measure lastMeasure, long duration, int beats,
                                          boolean isActive)
    {
        Measure newMeasure = new Measure(lastMeasure.getEndTime(), duration, beats, isActive);
        // Carry over held notes.
        for (Note note : lastMeasure.getHeldNotes()) {
            Note newNote = new Note(0.0f, note.key, note.level);
            newMeasure.notes.add(newNote);
            newMeasure.heldNotes.put(newNote.key, newNote);
        }
        return newMeasure;
    }

    public boolean isActive()
    {
        return active;
    }

    public long getStartTime()
    {
        return t0;
    }

    public long getEndTime()
    {
        return t1;
    }

    public Collection<Note> getNotes()
    {
        return notes;
    }

    public Collection<Note> getHeldNotes()
    {
        return heldNotes.values();
    }

    public int getNumBeats()
    {
        return numBeats;
    }

    public float getBeatForTime(long t)
    {
        return ((float) (t - t0)) / ((float) (t1 - t0)) * getNumBeats();
    }

    // Returns the timestamps of all the beats in the measure, including first and last.
    public long[] getDingTimes() {
        long[] times = new long[numBeats + 1];
        for (int n = 0; n <= numBeats; ++n) {
            times[n] = t0 + (t1 - t0) * n / numBeats;
        }
        return times;
    }

    // Converts the Midi "velocity" (byte between 0 and 127, I think) to a float level between 0
    // and 1.
    //
    // Experimental: use a nonlinear scale.
    private static class VelocityLevelPair {
        public VelocityLevelPair(int v, float l) { velocity = v; level = l; }
        public int velocity;
        public float level;
    }

    private static ArrayList<VelocityLevelPair> velocityToLevel =
            new ArrayList<VelocityLevelPair>() {{
                add(new VelocityLevelPair(10, 0.0f));          // MIN
                add(new VelocityLevelPair(20, 1.0f / 7.0f));   // PP
                add(new VelocityLevelPair(30, 2.0f / 7.0f));   // P
                add(new VelocityLevelPair(40, 3.0f / 7.0f));   // MP
                add(new VelocityLevelPair(55, 4.0f / 7.0f));   // MF
                add(new VelocityLevelPair(70, 5.0f / 7.0f));   // F
                add(new VelocityLevelPair(90, 6.0f / 7.0f));   // FF
                add(new VelocityLevelPair(127, 7.0f / 7.0f));  // MAX
            }};

    private static float velocityToLevel(int velocity, Parameters parameters) {
        Log.d(TAG, "VELOCITY: " + velocity);
        // Traditional:
        //return Util.safeDiv(velocity, parameters.maxLevel());

        // First convert the velocity to a level between 0 and 7.
        // TODO: we're ignoring Parameters.maxLevel() for now.
        VelocityLevelPair last = null;
        for (VelocityLevelPair current : velocityToLevel) {
            if (velocity <= current.velocity) {
                if (last == null) {
                    Log.e(TAG, "Velocity is too small: " + velocity);
                    return 0.0f;
                } else {
                    return last.level +
                            Util.safeDiv(velocity - last.velocity, current.velocity - last.velocity)
                                    * (current.level - last.level);
                }
            }
            last = current;
        }
        // We only get here if the velocity is greater than the (assumed) max velocity of 127.
        Log.e(TAG, "Velocity too big: " + velocity);
        return 1.0f;
    }

    // Updates the notes according to the given Midi message, either adding a new note or releasing
    // a currently held note.
    public void updateFromMessage(MidiMessage message, Parameters parameters)
    {
        if (message.data.length > 0) {
            int cmd = Util.byteToUnsignedInt(message.data[0]);
            if (cmd == 0x80 || cmd == 0x90) {
                if (message.data.length != 3) {
                    Log.e(TAG, "Invalid message: " + message);
                    return;
                }
                int key = Util.byteToUnsignedInt(message.data[1]);
                int velocity = Util.byteToUnsignedInt(message.data[2]);
                if (cmd == 0x80) {
                    releaseNote(key, message.timestamp);
                } else {  // cmd == 0x90
                    float level = velocityToLevel(velocity, parameters);
                    Log.d(TAG, "Velocity " + velocity + " -> Level " + (7 * level));
                    startNote(key, level, message.timestamp);
                }
            } else {
                Log.e(TAG, "Unknown Midi command " + cmd);
            }
        } else {
            Log.e(TAG, "Encountered empty Midi message.");
        }
    }

    private void releaseNote(int key, long time)
    {
        if (heldNotes.containsKey(key)) {
            Note note = heldNotes.get(key);
            note.endBeat = getBeatForTime(time);
            note.held = false;
            heldNotes.remove(key);
        } else {
            Log.e(TAG, "Trying to release note " + key + ", which is not held.");
        }
    }

    private void startNote(int key, float level, long time)
    {
        if (heldNotes.containsKey(key)) {
            Log.e(TAG, "Trying to play note " + key + ", which is already held.");
        } else {
            Note note = new Note(getBeatForTime(time), key, level);
            notes.add(note);
            heldNotes.put(key, note);
        }
    }

    // To be called when current time > this measure's end time. Makes the measure inactive.
    public void complete()
    {
        for (Note note : heldNotes.values()) {
            note.endBeat = getNumBeats();
            note.held = false;
        }
        heldNotes.clear();
        active = false;
    }

    public String toString()
    {
        StringBuilder s = new StringBuilder();
        s.append("[");
        for (Note note : notes) {
            s.append(note.toString()).append(" ");
        }
        s.append("Held: ");
        for (int key : heldNotes.keySet()) {
            s.append(key).append(" ");
        }
        s.append("]");
        return s.toString();
    }

    public static class Note
    {
        // We use fractional "beats" instead of "seconds" to keep track of when a note was played.
        // This will make handling tempo changes easier.
        public float startBeat;
        public float endBeat;
        public float level;  // ranges from 0 to 1
        public int key;
        boolean held;

        // Creates a currently held note.
        public Note(float start, int k, float lvl)
        {
            startBeat = start;
            endBeat = 0.0f;
            key = k;
            level = lvl;
            held = true;
        }

        // Creates an already released note.
        public Note(float start, float end, int k, float lvl)
        {
            startBeat = start;
            endBeat = end;
            key = k;
            level = lvl;
            held = false;
        }

        public String toString()
        {
            StringBuilder s = new StringBuilder();
            s.append(String.format("(%.2f-%.2f %d %.2f", startBeat, endBeat, key, level));
            if (held) s.append(" h");
            s.append(")");
            return s.toString();
        }
    }
}
