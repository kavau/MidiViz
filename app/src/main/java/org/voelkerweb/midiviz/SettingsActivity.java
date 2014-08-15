package org.voelkerweb.midiviz;

import android.app.Activity;
import android.os.Bundle;
import android.preference.PreferenceFragment;

public class SettingsActivity extends Activity {
    public static final String METRONOME_ON = "pref_metronome_on";
    public static final String METRONOME_BELL = "pref_metronome_bell";
    public static final String BEATS_PER_MEASURE = "pref_beats_per_measure";
    public static final String BEATS_PER_MINUTE = "pref_beats_per_minute";
    public static final String FAKE_MIDI = "pref_fake_midi";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getFragmentManager().beginTransaction()
                .replace(android.R.id.content, new SettingsFragment())
                .commit();
    }

    public static class SettingsFragment extends PreferenceFragment {

        @Override
        public void onCreate(Bundle savedInstanceState) {
            super.onCreate(savedInstanceState);
            addPreferencesFromResource(R.xml.preferences);
        }

    }
}
