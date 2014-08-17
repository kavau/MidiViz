package org.voelkerweb.midiviz;

import android.content.Context;
import android.media.AudioManager;
import android.media.SoundPool;
import android.os.Handler;
import android.os.SystemClock;

/**
 * Getting timing accuracy better than ~10-20ms is quite tricky. Here are a few interesting pages:
 *
 * http://stackoverflow.com/questions/824110/accurate-sleep-for-java-on-windows
 * https://blogs.oracle.com/dholmes/entry/inside_the_hotspot_vm_clocks
 *
 * Basically: use a target time ~20ms before the desired execution time. Then use e.g.
 * Object.wait() or Thread.sleep() for millisecond accuracy, or
 * java.util.concurrent.locks.LockSupport.park for sub-millisecond accuracy
 * in conjunction with System.nanoTime() to put the thread to sleep for the remainder of the time.
 * Not clear how well this works on Android, though.
 *
 * Note that other sources (e.g. Dianne Hackborn) say that it's impossible to get millisecond
 * accuracy without resorting to native threads (due to GC among other reasons.)
 */

/**
 * Does tic toc.
 */
public class Metronome
{
    private static final String TAG = "Metronome";
    private static final boolean soundOn = true;

    // TODO: it's rather hacky to do the time conversion here. Maybe we should measure
    // everything in uptime. Note that currentTime is affected by e.g. daylight savings.
    private static long timeDelta = System.currentTimeMillis() - SystemClock.uptimeMillis();

    private Handler handler = new Handler();
    private Parameters mParameters;
    private SoundPool soundPool = new SoundPool(5, AudioManager.STREAM_MUSIC, 0);
    private int bellSound;
    private int tocSound;

    private Runnable bellTask = new Runnable() {
        public void run() {
            ding(true);
        }
    };

    private Runnable tocTask = new Runnable() {
        public void run() {
            ding(false);
        }
    };

    public Metronome(Context context, Parameters parameters)
    {
        mParameters = parameters;
        bellSound = soundPool.load(context, R.raw.dialog_information_trimmed, 1);
        tocSound = soundPool.load(context, R.raw.button_pressed_trimmed, 1);
        // TODO: should wait for soundPool.OnLoadCompleteListener callback.
    }

    // Sounds the metronome bell at a given time in the future. Multiple times can be queued, but
    // it is expected that consecutive calls to dingAt have monotonically increasing times.
    // 'bell' refers to the measure start bell, as opposed to the beat marker.
    public void dingAt(long time, boolean bell)
    {
        if (mParameters.metronomeOn()) {
            //Log.d(TAG, (bell ? "Ding" : "Toc") + " at " + Util.formatTime(time));
            // TODO: I'm sure we can pass arguments to the runnable object somehow.
            handler.postAtTime(bell ? bellTask : tocTask, currentTimeToUptime(time));
        }
    }

    // Removes all outstanding dings from the queue. For use when parent activity is paused.
    public void pause()
    {
        handler.removeCallbacks(bellTask);
        handler.removeCallbacks(tocTask);
    }

    private static long currentTimeToUptime(long currentTime) {
        return currentTime - timeDelta;
    }

    private void ding(boolean bell)
    {
        //Log.d(TAG, bell ? "ding!" : "toc!");
        if (!soundOn) {
            return;
        }
        // We always play the toc sound and, if bell==true, the bell sound on top. This leads to a
        // more consistent perception.
        // TODO: play tocSound at a lower volume if superimposed with bell?
        // TODO: volume settings
        final float tocVolume = 1.0f * mParameters.metronomeVolume();
        final float bellVolume = 0.5f * mParameters.metronomeVolume();
        soundPool.play(tocSound, tocVolume, tocVolume, 1, 0, 1.0f);
        if (mParameters.metronomeBell() && bell) {
            soundPool.play(bellSound, bellVolume, bellVolume, 1, 0, 1.0f);
        }
    }
}
