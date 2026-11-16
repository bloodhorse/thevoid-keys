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
  point is under `min_swipe_length`, otherwise the direction of the furthest point**, mapped through Thumb-Key's
  own `swipeDirection` so the slices and `SwipeNWay` stay theirs. no aspect-ratio step: his keys are square.
  `onDragEnd`'s plain-flick branch calls it; drag-return and circular drag still use upstream's code. two
  things this fixed besides the arc: Thumb-Key's drag offsets start only after Compose's touch slop (8 dp, 21 px
  on his phone), so "60 px" really needed ~81; and when the curl-back test passed with drag-return and circles
  both off, upstream typed the tap letter. JVM tests: `./gradlew testDebugUnitTest` (`GestureReaderTest`,
  Wurstfinger's pins plus arcs at his phone's numbers).
- **hold for a digit**: holding a letter still types the digit in its slot on the number panel
  (`numericKey`, the same `[i][j]` of `modes.numeric`); a key's own `longPress` wins. the timeout is a setting
  (Behavior → "Hold for a digit", 300–1500 ms, default 700), fed to `combinedClickable` through a
  `ViewConfiguration` that delegates everything but `longPressTimeoutMillis`, provided around each key.
- **the number panel is a PC numpad** (`keyboards/NumericENMessagEase.kt`, shared by every MessagEase layout):
  7 8 9 on top, 1 2 3 at the bottom, 0 where it was; only the digits moved, the symbols around them stayed.
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
  in px with density. password fields are never written. 8 MB, then it rolls to `flicks.old.jsonl`.
  `python3 tools/flicks.py` pulls it and reports: travel histograms for taps and flicks, the misfires he
  corrected (a touch, backspace within 2.5s, the same key again with a different result), and which
  distance-and-speed rule would rescue the missed flicks without breaking honest taps. `--tail N` lists touches.

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
