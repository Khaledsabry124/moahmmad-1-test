Android's res/raw folder can't actually contain sub-folders (a flat
namespace is a platform rule) - so categories below are separated by
NAME PREFIX instead. Same effect, different mechanism.

=== Athan (full audio, played exactly at the synced prayer time) ===
  - One file for every prayer:      athan.mp3
  - A different file per prayer:    athan_fajr.mp3, athan_dhuhr.mp3,
                                     athan_asr.mp3, athan_maghrib.mp3,
                                     athan_isha.mp3
    (useful since Fajr's athan traditionally has an extra phrase)
    Per-prayer files are checked first; athan.mp3 is the fallback.

  Plays via a foreground service so it always finishes, even for long
  recordings. While it plays: pressing either volume button stops it
  (and the vibration/flash below) immediately; the vibration and
  flashlight strobe options are in Settings and need no extra files.

A GENUINELY FREE, PUBLIC-DOMAIN-LICENSED SOURCE:
    https://archive.org/details/adhan.recordings.from.doha.qatar
    (marked "Public Domain Mark 1.0"). Has separate Fajr/Dhuhr/Asr/
    Maghrib/Isha recordings already, matching the naming above.

=== Notification sounds - two categories per prayer ===
There is now only ONE alert per prayer transition (not two), so it's
also only ever one of two possible sounds:

1) "ON TRACK" - the prayer before this one was already logged, so this
   is a routine heads-up:
        notification_fajr.mp3      notification_dhuhr.mp3
        notification_asr.mp3       notification_maghrib.mp3
        notification_isha.mp3

2) "MISSED" - the prayer before this one has NOT been logged yet, so
   the alert is really about THAT prayer (named after it, not the one
   approaching):
        missed_fajr.mp3      missed_dhuhr.mp3
        missed_asr.mp3       missed_maghrib.mp3
        missed_isha.mp3

Example walking through Dhuhr -> Asr, with the alert set to fire 20
minutes before Asr:
  - Dhuhr marked done  -> plays notification_asr.mp3
  - Dhuhr NOT done yet -> plays missed_dhuhr.mp3 instead

All go in this same folder (app/src/main/res/raw/).
Fallback order: its own file -> notification_sound.mp3 -> system default.
(Older names from earlier instructions - notification_<id>_missed.mp3,
reminder_<id>.mp3, notification_reminder.mp3 - still work too, as
additional fallbacks.)

--- Filename rule for everything in this folder ---
Android resource files must be lowercase, letters/numbers/underscores
only - "Athan.MP3" or "athan-1.mp3" are NOT valid, "athan_1.mp3" is.

--- If you're changing sounds on an app you've already installed ---
Android locks a notification channel's sound once it's first created
on the device. If you add/change a sound file and reinstall but don't
hear the new sound, that's why - not a bug. Fully uninstalling the app
before reinstalling clears the old channels so new sounds take effect.
(Vibration is NOT locked this way - it's controlled live by the
Settings toggle every time, no reinstall needed for that one.)
