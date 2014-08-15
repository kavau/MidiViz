package org.voelkerweb.midiviz;

import java.util.List;

/**
 * An abstract interface for UsbMidiHelper and FakeMidiGenerator.
 */
public interface MidiInterface
{
    // Scans for an appropriate USB-MIDI device and establishes a connection.
    public boolean findAndConnectDevice();

    // Returns true iff a USB-MIDI device is registered and ready for use.
    public boolean ready();

    // Enables reception of MIDI messages. Requires established connection to USB-MIDI device.
    public void startReceiving();

    // Disables reception of MIDI messages.
    public void stopReceiving();

    // Retrieves all new MIDI messages.
    public List<MidiMessage> getMessages();
}
