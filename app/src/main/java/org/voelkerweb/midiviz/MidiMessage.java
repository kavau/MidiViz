package org.voelkerweb.midiviz;

/**
 * Represents an incoming message received from a Midi device.
 */
public class MidiMessage
{
    public byte[] data;
    public long timestamp;

    public String toString()
    {
        StringBuilder s = new StringBuilder();
        for (byte b : data) {
            s.append(String.format("%X", b)).append(" ");
        }
        s.append(" ").append(Util.formatTime(timestamp));
        return s.toString();
    }
}
