package org.voelkerweb.midiviz;

import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.hardware.usb.UsbConstants;
import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbDeviceConnection;
import android.hardware.usb.UsbEndpoint;
import android.hardware.usb.UsbInterface;
import android.hardware.usb.UsbManager;
import android.util.Log;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * Helper class for establishing a connection to a Midi device and for receiving data transfers.
 * See http://developer.android.com/guide/topics/connectivity/usb/host.html for an overview of
 * Android USB programming.`
 */
public class UsbMidiHelper implements MidiInterface
{
    private static final String TAG = "UsbMidiHelper";
    private static final String ACTION_USB_PERMISSION = "org.voelkerweb.usbwatcher.USB_PERMISSION";

    private WaiterThread mWaiterThread;
    private UsbManager mUsbManager;
    private UsbDeviceConnection mDeviceConnection;
    private UsbInterface mInterface;
    private UsbEndpoint mEndpoint;
    private PendingIntent permissionIntent;
    private boolean permissionGranted;

    // We store all MIDI messages in this queue (with timestamps), to be retrieved by the client.
    private ConcurrentLinkedQueue<MidiMessage> mMessageQueue =
            new ConcurrentLinkedQueue<MidiMessage>();

    // Creates a UsbMidiHelper and register a context. The context is needed for access to system
    // services.
    public UsbMidiHelper(Context context)
    {
        mUsbManager = (UsbManager) context.getSystemService(Context.USB_SERVICE);

        // Register broadcast receiver for usb permission request.
        permissionIntent =
                PendingIntent.getBroadcast(context, 0, new Intent(ACTION_USB_PERMISSION), 0);
        context.registerReceiver(new UsbPermissionReceiver(),
                                 new IntentFilter(ACTION_USB_PERMISSION));
    }

    // Scans for a Midi device, asks for usage permission, and connects to the device.
    // TODO: instead of using the first device, look for one with appropriate interface.
    @Override
    public boolean findAndConnectDevice()
    {
        HashMap<String, UsbDevice> devices = mUsbManager.getDeviceList();

        UsbDevice device = null;
        for (Map.Entry<String, UsbDevice> entry : devices.entrySet()) {
            device = entry.getValue();
            break;  // use the first device.
        }
        if (device == null) {
            return false;
        }

        // Ask user for permission to connect to the USB device.
        mUsbManager.requestPermission(device, permissionIntent);
        return true;
    }

    // Registers the USB device after the user grants permission.
    private boolean registerDevice(UsbDevice device)
    {
        if (device == null) {
            Log.e(TAG, "Device is null.");
            return false;
        }

        UsbInterface suitableInterface = null;
        UsbEndpoint suitableEndpoint = null;
        for (int ni = 0; ni < device.getInterfaceCount(); ++ni) {
            UsbInterface intf = device.getInterface(ni);
            if (intf.getInterfaceClass() == UsbConstants.USB_CLASS_AUDIO && intf
                    .getInterfaceSubclass() == 3 /* MIDI_OUT_JACK? */) {
                for (int ne = 0; ne < intf.getEndpointCount(); ++ne) {
                    UsbEndpoint endpoint = intf.getEndpoint(ne);
                    if (endpoint.getType() == UsbConstants.USB_ENDPOINT_XFER_BULK && endpoint
                            .getDirection() == UsbConstants.USB_DIR_IN) {
                        // We found a suitable device.
                        suitableInterface = intf;
                        suitableEndpoint = endpoint;
                        break;  // TODO: break out of both loops!
                    }
                }
            }
        }

        if (suitableInterface == null) {
            Log.e(TAG, "No suitable MIDI interface found.");
            return false;
        }

        mEndpoint = suitableEndpoint;
        mInterface = suitableInterface;

        mDeviceConnection = mUsbManager.openDevice(device);  // @Nullable
        if (mDeviceConnection == null) {
            Log.e(TAG, "Could not open device connection.");
            return false;
        }

        permissionGranted = true;
        Log.d(TAG, "USB MIDI device connection established.");
        return true;
    }

    @Override
    public boolean ready()
    {
        return mDeviceConnection != null && mEndpoint != null && permissionGranted;
    }

    @Override
    public void startReceiving()
    {
        Log.d(TAG, "USB waiter thread starting");
        mDeviceConnection.claimInterface(mInterface, true);
        mWaiterThread = new WaiterThread();  // Note that Java threads can run only once.
        mWaiterThread.start();
    }

    @Override
    public void stopReceiving()
    {
        Log.d(TAG, "USB waiter thread stopping");
        synchronized (mWaiterThread) {
            mWaiterThread.mStop = true;
            mDeviceConnection.releaseInterface(mInterface);
        }
    }

    // Retrieves and clears all messages from the queue. Note that messages are only added to the
    // queue if no receiver is specified in the constructor.
    @Override
    public List<MidiMessage> getMessages()
    {
        List<MidiMessage> messages = new ArrayList<MidiMessage>();
        while (true) {
            // poll removes and returns the head of the queue, or null if the queue is empty.
            MidiMessage midi = mMessageQueue.poll();
            if (midi != null) {
                messages.add(midi);
            } else {
                break;  // We're done.
            }
        }
        return messages;
    }

    // Receives the intent generated when the user grants permission to use the USB device.
    private class UsbPermissionReceiver extends BroadcastReceiver
    {
        public void onReceive(Context context, Intent intent)
        {
            String action = intent.getAction();
            if (ACTION_USB_PERMISSION.equals(action)) {
                synchronized (this) {
                    UsbDevice device = intent.getParcelableExtra(UsbManager.EXTRA_DEVICE);
                    if (intent.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED, false)) {
                        if (device != null) {
                            if (!registerDevice(device)) {
                                Log.e(TAG, "Failed to register USB-MIDI device.");
                            }
                        }
                    } else {
                        Log.d(TAG, "permission denied for device " + device);
                    }
                }
            }
        }
    }

    // A separate thread that handles usb data transfers.
    // NOTE: this is copied almost verbatim from levien's UsbMidiDevice.java
    private class WaiterThread extends Thread
    {
        public boolean mStop;

        @Override
        public void run()
        {
            mStop = false;
            byte[] buf = new byte[mEndpoint.getMaxPacketSize()];
            while (true) {
                synchronized (this) {
                    if (mStop) {
                        Log.d(TAG, "USB waiter thread shutting down.");
                        return;
                    }
                }

                // The timeout is unnecessary since bulkTransfer returns when we release the
                // interface.
                final int TIMEOUT = 60000;  // Arbitrary timeout of 1 min.
                int nBytes = mDeviceConnection.bulkTransfer(mEndpoint, buf, buf.length, TIMEOUT);
                if (nBytes < 0) {
                    Log.e(TAG, "bulkTransfer error: " + nBytes);
                }
                // According to the USB-MIDI standard,  all packets are exactly 32bit. Shorter
                // messages are padded.
                for (int i = 0; i < nBytes; i += 4) {
                    int codeIndexNumber = buf[i] & 0xf;
                    int payloadBytes = 0;
                    if (codeIndexNumber == 8 || codeIndexNumber == 9 || codeIndexNumber == 11 ||
                            codeIndexNumber == 14) {
                        payloadBytes = 3;
                    } else if (codeIndexNumber == 12) {
                        payloadBytes = 2;
                    }
                    if (payloadBytes > 0) {
                        MidiMessage midi = new MidiMessage();
                        midi.timestamp = System.currentTimeMillis();
                        // TODO: this is strange. First byte seems redundant. Figure this out.
                        midi.data = new byte[payloadBytes];
                        for (int j = 1; j <= payloadBytes; ++j) {
                            midi.data[j - 1] = buf[i + j];
                        }
                        mMessageQueue.add(midi);
                    } else {
                        Log.d(TAG, "empty message");
                    }
                }
            }
        }
    }
}
