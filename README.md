MidiViz
=======

MidiViz is an Android app to visualize input from a Midi keyboard. This app aims to help beginning piano students to perfect their timing and dynamics. All key strokes from a Midi keyboard are visualized on a time line, with color representing the pitch, and height representing the velocity (or loudness) of the key stroke. Piano students that don't have a good ear for timing and dynamics will be able to achieve smooth play faster through this visual feedback.

Known Issues
============

 - Midi device needs to be connected before the app is started. There is currently no way to enable 
   a Midi device when the app is already running. If the app was started without a connected device,
   you need to go into the android settings and explicitly "force stop" the app, then connect the device
   and restart the app.
 - screen layout such as number of rows and number of measures per row is currently hardcoded. It should
   be determined dynamically according to the device's screen size, and/or configurable in the settings.
 - in certain cases, when switching num beats in the settings, current and past measures are changed as well.
 - missing an option to keep the screen on
 - settings menu is not very user friendly (multiple issues)
 - stopping the midi receiver thread when app is paused might have unintended consequences
   such as dropped notes or release events. We should probably just "release" all notes when the
   activity pauses.

Changelog
=========

In V0.05:

 - settings for beats per second, beats per measure etc.
 - bug fix: resuming app (e.g. after settings change) led to a "Thread already started" error.
   Resolution: threads can't be re-used in Java, we had to create a new thread.

 In V0.04:
 - common abstract interface for UsbMidiHelper and FakeMidiGenerator
 - resolved awkward timing issues; NoteTracker now notifies Metronome of ding times
 - fixed "Measure is expired" errors
 - stop/restart child threads when the application is paused/resumed
