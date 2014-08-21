package org.voelkerweb.midiviz;

import android.content.Context;
import android.content.SharedPreferences;
import android.preference.PreferenceManager;

/**
 * Contains parameters such as BPM, beats, and derived quantities.
 * NOTE: we currently use separate instances of Parameters throughout the code, so it's essential
 * that the Parameter class is stateless, except for SharedPreferences.
 */
public class Parameters
{
    private SharedPreferences prefs;

    public Parameters(Context context) {
        prefs = PreferenceManager.getDefaultSharedPreferences(context);
    }

    /**
     * Measures and timing parameters
     */

    public int beatsPerMinute() { return prefs.getInt(SettingsActivity.BEATS_PER_MINUTE, 100); }

    public int beatsPerMeasure() { return prefs.getInt(SettingsActivity.BEATS_PER_MEASURE, 4); }

    public int subBeats() { return 2; }

    /**
     * Display parameters
     */

    public int numMeasuresPerRow() { return 2; }

    public int numRowsToDisplay() { return 2; }

    // TODO: parametrize these constants by screen size.
    public float measureWidth() { return 560.0f; }

    public float measureHeight() { return 200.0f; }

    public float rowSpacing() { return 60.0f; }

    // The levels are: MIN, PP, P, MP, MF, F, FF, MAX
    public float levelMarkerInterval() { return 1.0f / 7.0f; }

    public long updateIntervalMillis() { return 20;  /* 20 msec ~ 50 Hz */ }

    // Never keep more than this many measures in memory.
    public int numMeasuresToKeep() { return 12; }

    // Maximum level for Midi notes. This level will be converted to a volume of 1.0 internally.
    public int maxLevel() { return 0x80; }

    /**
     * Sound parameters
     */

    public boolean metronomeOn() { return prefs.getBoolean(SettingsActivity.METRONOME_ON, true); }

    public boolean metronomeBell()
    {
        return prefs.getBoolean(SettingsActivity.METRONOME_BELL, true);
    }

    public float metronomeVolume() { return 0.5f; }

    /**
     * Debug parameters
     */

    public boolean fakeMidi() { return prefs.getBoolean(SettingsActivity.FAKE_MIDI, false); }

    /**
     * Derived parameters
     */

    public long measureDurationMillis()
    {
        return beatsPerMeasure() * 60 * 1000 / beatsPerMinute();
    }
}
