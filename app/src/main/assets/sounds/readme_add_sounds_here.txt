Optional custom sound for the in-app "تقبل الله" (accepted) chime that
plays when you mark a prayer done. Add a file named exactly:

    accepted.mp3

into this same folder (app/src/main/assets/sounds/). If it's missing,
the app automatically falls back to a short chime it generates in code
(no file needed) - so nothing breaks either way.

This is separate from the native system notification sound - see
app/src/main/res/raw/readme_add_athan_here.txt for that one.
