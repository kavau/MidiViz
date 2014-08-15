package org.voelkerweb.midiviz;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * A mock implementation of MidiInterface that creates fake Midi messages at random times.
 */
public class FakeMidiGenerator implements MidiInterface
{
    private static final String TAG = "FakeMidiGenerator";

    private static final int MIN_TIME_INTERVAL = 200;  // msecs
    private static final int MAX_TIME_INTERVAL = 1000;  // msecs

    private Random mRandom = new Random();
    private List<MidiMessage> currentNotes = new ArrayList<MidiMessage>();
    private long nextEventTime = 0;
    private boolean running = false;

    @Override
    public boolean findAndConnectDevice() {
        return true;
    }

    @Override
    public boolean ready() {
        return true;
    }

    @Override
    public void startReceiving() {
        running = true;
    }

    @Override
    public void stopReceiving() {
        running = false;
    }

    @Override
    public List<MidiMessage> getMessages()
    {
        long currentTime = System.currentTimeMillis();
        initializeNextEventTime(currentTime);
        List<MidiMessage> messages = new ArrayList<MidiMessage>();
        while (nextEventTime <= currentTime) {
            int n = mRandom.nextInt(currentNotes.size() + 1);
            if (n < currentNotes.size()) {
                // Release a currently held note.
                MidiMessage msg = currentNotes.remove(n);
                msg.data[0] = (byte) 0x80;  // the other fields remain the same
                msg.timestamp = nextEventTime;
                messages.add(msg);
            } else {
                // Add a new note.
                byte key = (byte) (0x40 + mRandom.nextInt(24));
                if (!isHeld(key)) {
                    MidiMessage msg = new MidiMessage();
                    msg.data = new byte[3];
                    msg.data[0] = (byte) 0x90;
                    msg.data[1] = key;
                    msg.data[2] = (byte) (0x20 + mRandom.nextInt(0x50));  // volume
                    msg.timestamp = nextEventTime;
                    currentNotes.add(msg);
                    messages.add(copyMessage(msg));
                }
            }
            updateNextEventTime();
        }
        return messages;
    }

    private boolean isHeld(byte key)
    {
        for (MidiMessage note : currentNotes) {
            if (note.data[1] == key) return true;
        }
        return false;
    }

    private void initializeNextEventTime(long currentTime)
    {
        if (nextEventTime == 0) {
            nextEventTime = currentTime;
            updateNextEventTime();
        }
    }

    private void updateNextEventTime()
    {
        nextEventTime += MIN_TIME_INTERVAL + mRandom.nextInt(MAX_TIME_INTERVAL - MIN_TIME_INTERVAL);
    }

    // We generally don't have much need for copying midi messages, so we have this utility method
    // buried here.
    private MidiMessage copyMessage(MidiMessage msg)
    {
        MidiMessage copy = new MidiMessage();
        copy.data = msg.data.clone();
        copy.timestamp = msg.timestamp;
        return copy;
    }
}