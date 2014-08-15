package org.voelkerweb.midiviz;

import android.app.Activity;
import android.app.Fragment;
import android.app.FragmentTransaction;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;

/**
 * The main activity. It contains two fragments: VisualizerFragment and (optionally)
 * LoggingFragment.
 */
public class Main extends Activity
{
    private static final String TAG = "Main";
    private static final boolean showDebugWindow = false;

    private Handler handler = new Handler();
    private Parameters parameters;
    private Metronome metronome;
    private MidiInterface midi;
    private boolean metronomeRegistered = false;
    private boolean midiConnected = false;

    // Updates the view periodically.
    private Runnable updateViewTask = new Runnable()
    {
        public void run()
        {
            update();
            handler.postDelayed(updateViewTask, parameters.updateIntervalMillis());
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState)
    {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        if (savedInstanceState == null) {
            FragmentTransaction transaction = getFragmentManager().beginTransaction();
            transaction.add(R.id.container, new VisualizerFragment());
            if (showDebugWindow) {
                transaction.add(R.id.container, new LoggingFragment());
            }
            transaction.commit();
        }

        // Create member instances.  Note that we can't pass 'this' before this activity is created.
        parameters = new Parameters(this);
        metronome = new Metronome(this, parameters);

        // Find and register the USB MIDI device.
        midi = parameters.fakeMidi() ? new FakeMidiGenerator() : new UsbMidiHelper(this);
        if (!midi.findAndConnectDevice()) {
            Log.d(TAG, "No suitable device found.");
        }
        // Note that we can't call midiHelper.startReceiving() here. We first have to wait until
        // midiHelper.ready() is true. TODO: rework this using a callback.
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu)
    {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.main, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item)
    {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        switch (item.getItemId()) {
            case R.id.action_settings:
                Log.d(TAG, "settings menu selected");
                this.startActivity(new Intent(this, SettingsActivity.class));
                return true;
            default:
                return super.onOptionsItemSelected(item);
        }
    }

    @Override
    public void onResume()
    {
        super.onResume();
        Log.d(TAG, "resume");
        handler.removeCallbacks(updateViewTask);
        handler.postDelayed(updateViewTask, parameters.updateIntervalMillis());

        // TODO: it may happen that the midi listener thread hasn't stopped yet (timeout=1s).
        // Figure out what would happen in this case. Worst case: startReceiving() doesn't do
        // anything because thread is still running. However, after the timeout completes thread
        // shuts down and we're without midi.
        if (midi.ready()) {
            midi.startReceiving();
            midiConnected = true;
        }

        // Register metronome. We cannot do this in onCreate() since visualizer view is not yet
        // available.
        if (!metronomeRegistered) {
            Visualizer visualizer = (Visualizer) findViewById(R.id.visualization_view);
            if (visualizer != null) {
                visualizer.registerMetronome(metronome);
                metronomeRegistered = true;
            }
        }
    }

    @Override
    public void onPause()
    {
        super.onPause();
        Log.d(TAG, "pause");
        handler.removeCallbacks(updateViewTask);
        metronome.pause();

        midi.stopReceiving();
        midiConnected = false;
    }

    // Periodically updates the view, advancing the current-time marker and updating any new
    // or released notes.
    private void update()
    {
        // Get new Midi messages.
        List<MidiMessage> messages = new ArrayList<MidiMessage>();
        if (midi.ready()) {
            if (!midiConnected) {
                midi.startReceiving();
                midiConnected = true;
            }
            messages = midi.getMessages();
        }
        if (!messages.isEmpty()) {
            Log.d(TAG, "New messages: " + messages);
        }

        // Update graphics. This also creates new measures and forwards ding times to metronome.
        Visualizer visualizer = (Visualizer) findViewById(R.id.visualization_view);
        if (visualizer != null) {
            visualizer.update(messages);
        }

        // Update debug view.
        TextView debugView = (TextView) findViewById(R.id.debug_view);
        if (debugView != null && !messages.isEmpty()) {
            Log.d(TAG, "New messages: " + messages);
            for (MidiMessage msg : messages) {
                debugView.append("\n");
                debugView.append(msg.toString());
            }
        }
    }

    /**
     * This fragment contains the canvas on which the Midi visualizations are drawn.
     */
    public static class VisualizerFragment extends Fragment
    {
        @Override
        public View onCreateView(LayoutInflater inflater, ViewGroup container,
                                 Bundle savedInstanceState)
        {
            return inflater.inflate(R.layout.fragment_visualizer, container, false);
        }
    }

    /**
     * A fragment that displays log messages; for testing and debugging.
     */
    public static class LoggingFragment extends Fragment
    {
        @Override
        public View onCreateView(LayoutInflater inflater, ViewGroup container,
                                 Bundle savedInstanceState)
        {
            View rootView = inflater.inflate(R.layout.fragment_log, container, false);
            return rootView;
        }
    }
}
