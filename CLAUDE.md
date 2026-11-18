# thevoid-keys

the keyboard for thevoid (`~/tower/forge/thevoid`), bekh's Android shell. a fork of Thumb-Key
(upstream `dessalines/thumb-key`, AGPL; ours is `bloodhorse/thevoid-keys`, `origin`). never push upstream.

the deal: Thumb-Key's engine stays as it is (layouts, text processing, settings) except the flick
reader, which is Wurstfinger's; the look is ours. the bar is Wurstfinger, the iOS MessagEase keyboard bekh uses, made
darker: one solid black surface edge to edge, keys of faint glass with a hairline edge on it,
the big letter white, the flicks grey, symbols dimmer, nothing moves. the brief and the why live in
`~/tower/forge/thevoid/docs/input.md`, "the keyboard for messages".

## what we changed

mostly defaults and a handful of settings; a user can still pick any upstream theme or layout.

- **the flick reader** (`utils/GestureReader.kt`, Wurstfinger's `GesturePreprocessor` /
  `GestureFeatureCalculations` / `KeyGestureRecognizer` ported, MIT): Thumb-Key read a flick off the straight
  line from touch-down to lift-off; this takes the whole path the flick log already watches (`Flicklog.Trail`,
  px from touch-down), drops jitter under 3 dp and teleports over 50 dp (with Wurstfinger's velocity cone so a
  fast fling survives), smooths it (Savitzky–Golay, window from the setting), and reads **tap if the furthest
  point is under `min_swipe_length`, otherwise the direction of the furthest point**, cut by the key's slices
  (`utils/Slices.kt`, below). no aspect-ratio step: his keys are square.
  `onDragEnd`'s plain-flick branch calls it; drag-return and circular drag still use upstream's code. two
  things this fixed besides the arc: Thumb-Key's drag offsets start only after Compose's touch slop (8 dp, 21 px
  on his phone), so "60 px" really needed ~81; and when the curl-back test passed with drag-return and circles
  both off, upstream typed the tap letter. his phone's line is 32 px (~12 dp, kept by grant.sh): his taps move 0 dp,
  his short flicks 16–24 dp, and the smoothing shaves 0–7 px off his real drags, never across 32. JVM tests: `./gradlew testDebugUnitTest` (`GestureReaderTest`,
  Wurstfinger's pins plus arcs at his phone's numbers; `WordPickTest`, the slices, the word both ways, a clear
  flick untouched, a password field never asking).
- **unequal slices** (`utils/Slices.kt`): an eight-way key is cut into 60° straights and 30° diagonals, and a
  diagonal holding nothing on that key gives half to each straight beside it (on his layout 15, `e`'s empty
  up-left makes `w` run to 225°). an empty straight stays where it is and types the tap letter, as upstream.
  four- and two-way keys keep upstream's equal cuts; `swipeType` decides, a ghost key makes a key eight-way.
  only the plain-flick branch and the tick read these; drag-return and circular drag still use upstream's
  `swipeDirection`. on 15 `o`, `e`, `a` and `i` are eight-way: `-` right of `a` and `?` left of `i` (with `!` right of `n`, their MessagEase spots) leave `v` and `x` 30° diagonals.
- **the word decides the borderline flicks** (`utils/WordPick.kt`, `utils/CharModel.kt`): two lines a flick
  can land near, the edge between two slices (`ANGLE_BAND_DEG` 10 either side) and `min_swipe_length` between
  a flick and the tap (`TRAVEL_BAND_DP` 8 either side). inside a band geometry's weight for its own side runs
  from 0.5 on the line to 1 at the rim, the other side gets the rest, and each side's weight times
  P(letter | the word so far)^`WORD_WEIGHT` (1) decides; a strict win, so a word with no opinion never moves
  anything. the angle pair is settled first, then flick against tap, so there are never three-way fights.
  only a letter against a letter: if either side is a symbol, space, digit or action, geometry stands. an
  empty slice counts as the tap letter, which is how `v` against `a` gets in: a flick toward `v` that goes
  flat lands in `a`'s empty up-right quadrant. the model is asked only when a pair turns up; the context is
  `getTextBeforeCursor(8)`, cut at the last non-letter, the last three letters lowercased, kept nowhere. a
  password field (checked fresh each flick) gets geometry alone. a Compose tap (under the 8 dp slop) never
  reaches the reader, so the band's lower half only exists from 8 dp; his taps move 0 dp anyway.
  the model is `app/src/main/assets/charlm.bin` (239 KB): English character 4-grams inside a word, backing off
  to 3-grams, from Peter Norvig's `count_1w.txt` (the 333k commonest words of Google's trillion-word web
  corpus, with counts), each order mixed 2% with the one below, quantized to 1/8 bit. `python3
  tools/charlm.py` rebuilds it (downloads the list to `~/.cache` once); the format is in its docstring.
  the bands are the brief's starting points: his log was 24 lines under the old 60 px line when they were
  set, nothing to fit them to. every decision lands in the flick log; tune them from `flicks.py`'s "the word".
- **a tick per slice** (`sliceTickStep` in `KeyboardKey.kt`): on a key without a slide, once the furthest
  point of the raw path is past `min_swipe_length`, each move of it into a different slice that holds
  something gives `Tick.play`, when vibrate on tap is on. no tick
  for the first slice it arms in, none for an empty one. it reads the raw path, the reader the smoothed one,
  and the word can still override the slice he felt inside a band. logcat `slice tick:`.
- **hold for a digit**: holding a letter still types the digit in its slot on the number panel
  (`numericKey`, the same `[i][j]` of `modes.numeric`); a key's own `longPress` wins. the timeout is a setting
  (Behavior → "Hold for a digit", 300–1500 ms, default 700), fed to `combinedClickable` through a
  `ViewConfiguration` that delegates everything but `longPressTimeoutMillis`, provided around each key.
- **the number panel is a PC numpad** (`keyboards/NumericENMessagEase.kt`, shared by every MessagEase layout):
  7 8 9 on top, 1 2 3 at the bottom, 0 where it was; only the digits moved, the symbols around them stayed.
- **swiping left across backspace deletes as the thumb moves** (Wurstfinger's delete slide, `deleteSwipeStep`
  in `KeyboardKey.kt`, the `DELETE_SWIPE_*` constants at the top): past 28 dp left of touch-down it goes live,
  one character per 14 dp of travel times a gain from the thumb's speed over the last 40 ms of the trail (1x at
  200 dp/s and under, 4x at 1500 and over, smoothstep between), a `Tick.play` each (vibrate for
  slide gestures). nothing is selected; what it eats goes in a buffer, and dragging back right in the same
  swipe re-commits it in order through the same stepping, until the buffer is empty (no forward delete). lift
  ends it. this replaced upstream's slide-select-delete on backspace, so its "Backspace: allow normal swipes"
  deadzone switch does nothing now. a left flick under 28 dp is still delete-word, a right flick still
  delete-word-after. on his phone over a 71-character line: 152 dp slow (~76 dp/s), 8 characters; 223 dp in 150 ms,
  52; 114 dp left and back past the start, 5 eaten and 5 put back; a 23 dp flick, one word (logcat `backspace swipe:`,
  the flick log `live`).
- **holding backspace still repeats like iOS** (`KeyboardKey.kt`, the `BACKSPACE_REPEAT_*` constants at the
  top): the first repeat at 400 ms, then characters at an interval easing from 120 to 25 ms over 1.5 s, then
  from 2 s whole words (`deleteWordBeforeCursor`) every 300 ms; a light tick (`Tick.play`) on each, when
  vibrate on tap is on. it waits on the press, which a drag start cancels, so a flick or slide before 400 ms
  stays a flick or slide; once it repeats only lifting stops it (it checks the flick log's finger-down before
  each delete), and the release types nothing more. backspace has no `longPress` any more. on his phone:
  held 1 s, 7 characters; 2 s, 27 and a word; 3.5 s, 27 and 6 words (logcat `backspace repeat:`, the flick
  log `repeat`).
- **a circle on the space bar selects the word** the cursor was in at drag start (letters and digits either
  side, `selectWordAt` in `Utils.kt`). the circle test is Wurstfinger's (span over 270°, circularity 0.3, turn
  consistency 0.8, compactness 0.4, path over twice the swipe length). with slide on, the cursor walks while the
  circle is drawn, so it's put back to where it was before selecting.
- **slide on the space bar moves the cursor** for fresh installs: the first-row insert in `AppDB`'s `onOpen`
  puts `slide_enabled` 1 (see gotchas for why not the column default). with the deadzones on, the slide starts
  past ~233 px (¾ key + the swipe length); on backspace the same slide selects leftward and deletes the
  selection on lift-off. short flicks on both keys are untouched.
- **flick smoothing**: Behavior → "Flick smoothing", the Savitzky–Golay window, 3–11 odd, default 5 (3 is a
  plain moving average, as in Wurstfinger). every other knob of the reader is hardcoded at Wurstfinger's default.
- **the panel** (`IMEService.dressPanel`): the IME window is exactly the keyboard's rectangle and
  translucent by default, so its background is the panel: an opaque `ColorDrawable`, `#000000` dark,
  `#FAF4ED` light, the same colour as the palette's `background`. full screen width, square corners,
  no inset, no blur, flush with the screen's bottom edge. matching the palette matters: Thumb-Key's
  backdrop setting paints `background` over the keys' area (his phone's screenshot showed it on), so a
  panel of any other colour reads as a frame around a box. with them equal, backdrop on or off is one surface
  (on, it's 6 dp taller at the top). the keys' own 3 dp padding is the only margin.
- **the system's IME nav buttons** (back chevron, keyboard switcher) used to hang over the space bar
  and return; on API 35+ the IME hides its caption bar (`insetsController.hide(captionBar())`) and
  Android drops them.
- **tones** (`ui/theme/Color.kt` → `thevoid()`, `ThemeColor.Thevoid`, ordinal 13; ordinals are stored in
  the db, so new entries only go at the end): tiles white at 6%, pressed 18%, hairline outline white at
  13% (`DEFAULT_KEY_BORDER_WIDTH` 4 = 0.4 dp), flicks `#B5B5B7`, symbols the same at half alpha
  (`MUTED`). measured off Wurstfinger: panel `#22`, tile `#2D`, outline `#42`, flicks `#B5`, symbols `#6C`.
- **light**: `thevoid()` is a pair, Dawnfox for light (ink `#575279` keys at 6%, flicks `#797593`, panel
  `#FAF4ED`) and the dark above. thevoid's `light` word flips android's night mode, the system theme
  mode picks the pair's half, and `IMEService.onConfigurationChanged` re-dresses the panel to match.
- **sizes** (`fontSizeVariantToFontSize`): the big letter is the key / 2.9 (was 2.5), flicks / 4 (was
  5), Wurstfinger's proportions. big letter Medium weight, the rest Normal (upstream bolds everything).
- **small letters size**: Look and feel → "Small letters size", a percent on
  every flick (`SMALL`/`SMALLEST`; centers untouched), 50–150 in steps of 5, default 85. column
  `flick_size` (db version 30, `MIGRATION_29_30`), carried to `KeyText` as `LocalFlickScale` from
  `KeyboardScreen`. `shots/flicks.png` is 100 vs 85 side by side.
- **key outline colour and strength**: Look and feel, under "Small letters size". colour white / cyan / magenta
  (`outline_color` 0/1/2; cyan `#3EF2FF` dark, `#286983` light; magenta `#FF3EC8` dark, `#B4637A` light; white is
  the theme's own outline colour), strength the alpha, 0–100 in steps of 5, default 13 = `0x21`, today's look.
  white at 13 hands back the theme's outline untouched, so upstream themes don't change. the strength slider
  writes the db on every step, not on release: focus "Test out Thumb-Key" at the bottom of the screen, scroll
  the slider above the keyboard, and the keys change under the thumb as it drags (`shots/outline-live-drag.png`
  is cyan 85% mid-drag; `shots/outline-cyan30.png` is cyan 30%).
- **no press animation**: upstream flashed the key and dropped the typed letter down it; both
  `AnimatedVisibility` blocks are gone from `KeyboardKey`. the pressed tint stays.
- **`keyboards/CommonKeys.kt`** (shared by every layout): `EMOJI_KEY_ITEM` keeps its name but is the
  globe now: tap cycles the chosen layouts, swipe down hides, swipe up opens settings. no emoji key, no
  drawer of nine icons. the `123` / `abc` key is text, with four edit flicks (select all, copy, cut,
  paste; undo and redo gone). controller centers are white.
- **`db/AppDb.kt`** defaults: theme color Thevoid, layout 16 (`ENMessagEaseSymbols`: classic
  `a n i / h o r / t e s` with every direction filled; 8-way on every key, less forgiving than plain
  MessagEase, and that call is bekh's), key padding 3 dp, radius 22 (% of key), backdrop off (the
  panel is the window's), bottom inset ignored so the keys run to the screen's edge.
- **`app/build.gradle.kts`**: `applicationId` is `com.bekh.thevoid.keys` (debug adds `.debug`); the
  Kotlin namespace stays `com.dessalines.thumbkey` so upstream merges stay painless.

- **the flick log** (`utils/Flicklog.kt`): a passive pointer watcher on every key (Initial pass, consumes
  nothing) records the thumb's whole path with timestamps; every place a key decides (`logTouch` before each
  `doneKeyAction` in `KeyboardKey`) appends one json line to `files/flicks.jsonl`: the key, what it typed,
  which path decided it (`tap` is Compose's click, under touch slop; `drag` is the flick reader; `long` a
  hold; `circle` a word selected on the space bar), the points
  in px with density, and `word` when the word was asked: geometry's pick, the final pick, and per pair
  (`kind` angle/travel) both candidates, geometry's weight `w`, `p_geo`/`p_alt`, the scores, the pick.
  password fields are never written (`isPasswordField` checks the input class: upstream masked the
  variation alone, so every URL bar, `0x10` like a number password, counted as a password field). 8 MB, then it rolls to `flicks.old.jsonl`.
  `python3 tools/flicks.py` pulls it and reports: travel histograms for taps and flicks, the misfires he
  corrected (a touch, backspace within 2.5s, the same key again with a different result), and which
  distance-and-speed rule would rescue the missed flicks without breaking honest taps, and "the word": how
  often it was asked, how often it overrode geometry, and how often each was followed by his correction
  (backspace, then the same key again). `--tail N` lists touches. its direction labels use the 60/30 cut.

## build and install

```bash
export JAVA_HOME=/opt/homebrew/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home
export ANDROID_HOME=/opt/homebrew/share/android-commandlinetools
./gradlew assembleDebug -x lint
adb install -r app/build/outputs/apk/debug/app-debug.apk
adb shell ime enable com.bekh.thevoid.keys.debug/com.dessalines.thumbkey.IMEService
adb shell ime set com.bekh.thevoid.keys.debug/com.dessalines.thumbkey.IMEService
```

the emulator, and how to check the keyboard by script, is in `~/tower/forge/thevoid/docs/build.md`.
on nothing1 LatinIME and the F-Droid Thumb-Key are disabled (`ime disable`), so ours is the only one.

**judging the look:** `tools/frosttest/build.sh` builds and installs a 30-line app (no gradle) with
a loud gradient and big sharp text that stays put under the keyboard (`adjustNothing`);
`adb shell am start -n com.bekh.frosttest/.Main` for colour, add `--ez black true` for pure black or
`--ei color 2172715` (`0x21272B`, decimal) for any flat colour. `shots/` holds the current look:
`grey.png` over `#21272B`, `grey-backdrop.png` the same with backdrop on, `black.png` over black.

## gotchas

- **on his Nothing Phone (1) only `KEYBOARD_TAP` reaches the motor through `View.performHapticFeedback`.**
  `TEXT_HANDLE_MOVE` and `SEGMENT_FREQUENT_TICK` return without a buzz, and `dumpsys vibrator_manager` never
  shows them, so the delete swipe, the hold repeat, the slice tick and the cursor slide were silent for days.
  every light tick goes through `utils/Tick.kt` (a prebaked `EFFECT_TICK` straight to the vibrator, touch usage,
  `VIBRATE` in the manifest). to check a haptic, read `dumpsys vibrator_manager | grep keys.debug`.
- **the input connection is null after a rotation**: on a configuration change android re-runs `doStartInput`
  with whatever connection it holds, and the rotating app has already unbound its old one, so `onStartInput`
  gets none. `onStartInput` rebuilds the view, its first composition runs right there, and upstream's bare
  `ctx.currentInputConnection.requestCursorUpdates` in `KeyboardScreen` crashed on the null (four times on his
  phone on the 17th). `IMEService.getCurrentInputConnection()` is typed non-null but passes the framework's null
  through unchecked, so the compiler never warns: anything that can run with no field bound goes through
  `inputConnectionOrNull` / `appInputConnection` (both nullable). cursor updates go through
  `IMEService.requestCursorUpdates`, which remembers a request that found no connection and asks again in
  `onStartInputView`. upstream (5.1.22) still has the bare calls.
- **the frost app is the test field**: `am start -n com.bekh.frosttest/.Main --ez black true`, tap `540 265`
  for its `EditText`. with backdrop off his keys are 232 px squares, columns x 192/424/656/885, rows y
  1493/1723/1952/2181 (o at 424,1723). two spaces in a row are ". " (`ReplaceLastText`). a flick by script is
  `input motionevent DOWN`, a few `MOVE`s along the line, `UP`, all in one `adb shell`: floats are fine.
  back up `files/flicks.jsonl` first, put it back after (`adb exec-in run-as … sh -c 'cat > files/flicks.jsonl'`).
- **every angle the old 45° slices read as a diagonal and the new ones as a straight is within 7.5° of a new
  edge**, so inside the word's band: 12 can't be shown on the phone without 11 asking. `word.geo` in the
  flick log is geometry's pick on the new slices.
- **the real layouts don't load in JVM tests** (`ENMessagEase.kt` touches Android resources in its static
  init); `WordPickTest` builds `a`, `o`, `e` by hand, copied off layout 15.

- **wake his phone before any adb on it, installs included**: `adb -s 100.96.66.79:5555 shell input keyevent
  KEYCODE_WAKEUP`. an install landing while it dozes ends doze without a wake, and his tap-to-wake stays dead
  until the next proper wake (`~/tower/forge/thevoid/docs/phone.md` § tap to wake). then let it sleep on its own.
- **a gesture script checks the keyboard is up before every step** (thevoid in front, `isOnScreen=true`): `input`
  queues while the screen is off and lets go into whatever app is in front the moment anyone wakes it, his own
  chats included.

- **Room checks column defaults after a migration**: changing an existing column's `defaultValue` without
  rebuilding the table crashes the upgrade ("Migration didn't properly handle"). new columns go in with
  `ALTER TABLE ... DEFAULT` (db v31 is `MIGRATION_30_31`: `hold_time`, `smoothing_window`, `outline_color`,
  `outline_strength`); a new default for an old column goes in the `onOpen` first-row insert, which only a
  fresh database sees.
- **partial-update classes** (`LookAndFeelUpdate`, `BehaviorUpdate`) must not give a column a Kotlin default:
  a screen that forgets to pass it silently resets it on every save. ours have none, so the compiler finds
  every constructor.
- his phone's db is never `pm clear`ed; to change a setting by script, `am force-stop`, pull `databases/thumbkey*`
  through `run-as`, edit with the mac's `sqlite3`, push back, empty the wal, `ime set` again (what
  `~/tower/forge/thevoid/tools/grant.sh` does).
- `input motionevent` delivers about ten moves a second, so a scripted slide undercounts the cursor under
  linear acceleration (his mode); a real thumb doesn't. thevoid's drawer paints its own block cursor at the end
  of the line, so the real cursor is only visible by typing.

- the wrapper is needed: AGP 9.5 wants gradle 9.9, brew's gradle 8 can't build this. the first run
  downloads it and installs SDK platform 37 by itself.
- the defaults are SQL column defaults (Room's `@ColumnInfo(defaultValue)`), so they only apply to a
  fresh database: a changed default doesn't show until `adb shell pm clear com.bekh.thevoid.keys.debug`
  (then `ime enable` / `ime set` again).
- `am force-stop` on the keyboard (to rewrite its db by `run-as`, say) drops Android back to LatinIME
  on the emulator; `ime set` again before the next shot.
- a debug build cold-starts slowly (ART verifies the 200 layout classes for seconds), and the first tap
  on a field after an install or a fresh launch often doesn't raise it at all. `mInputShown=true` also
  flips long before anything is drawn, and a tap aimed at a key then lands on the app. wait for the
  window, and tap again if it never comes:
  `until adb shell dumpsys window InputMethod | grep -q isOnScreen=true; do sleep 1; done`.
- `adb shell am start -d 'data:text/html;base64,...'`: the remote shell splits at the `;` unless the
  whole command is quoted for the far side.
