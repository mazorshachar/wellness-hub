# Vitals — a calorie balance app built on Health Connect

Reads steps, calories burned, heart rate, sleep, workouts, weight, body fat and
blood pressure from **Health Connect**, and shows them on one screen. It never
writes to Health Connect.

It also logs what you eat from voice notes: say *"I just ate a banana"* into your
Galaxy Watch and it lands on the dashboard with a calorie count. That half is
documented separately in [VOICE_LOGGING.md](VOICE_LOGGING.md) — it's the only
part of the app that sends anything off the device.

Health Connect is the right foundation here because it is the aggregation point:
Samsung Health, Fitbit, Withings, Google Fit and most third-party apps all write
into it. Reading Health Connect once gets you all of them, without a separate
integration per vendor.

---

## Build it

You need **Android Studio** (Ladybug or newer) and a phone running **Android 10 or
higher**.

If you want voice food logging, add your API keys first — see
[VOICE_LOGGING.md](VOICE_LOGGING.md). The app builds and runs fine without them;
the food card just stays empty.

**Start with [INSTALL.md](INSTALL.md)** — a step-by-step walkthrough from empty
phone to automatic updates. Creating a signing key is the one decision there
that can't be undone: get it wrong and every future update forces an uninstall,
which wipes your food log and Health Connect grants.

1. Open Android Studio → *Open* → select this folder.
2. Let Gradle sync. It downloads the Android SDK components and dependencies on
   first run.
3. Plug in your phone with USB debugging on, or start an emulator.
4. Press **Run**.

To build an installable APK from the command line instead:

```bash
./gradlew assembleDebug
# output: app/build/outputs/apk/debug/app-debug.apk
```

Note that the emulator has no Health Connect data, so the app will show its
empty state. You need a real phone with real data to see anything useful.

---

## First run

The app will ask you to grant read permissions. That sheet is drawn by Health
Connect itself, not by this app — each data type can be granted or denied
individually, and the dashboard degrades gracefully: deny blood pressure and
every other card still fills in.

If the screen says "No data yet", the problem is upstream. Open **Health Connect**
(Settings → Security & privacy → More privacy settings → Health Connect on Android
14+, or the standalone app below that) and check the **App permissions** list —
each source app needs *write* permission before anything reaches you.

---

## What actually feeds it, in your case

**Samsung Watch → Samsung Health → Health Connect.** Samsung syncs steps,
exercise, heart rate and sleep. You have to turn this on explicitly inside
Samsung Health: *Settings → Health Connect*. It is off by default.

**Smart scale → Health Connect.** Withings, and most competitors, write weight
and body fat percentage. Withings and Samsung added direct sync in May 2026, but
going through Health Connect is more reliable and is what this app reads.

**Blood pressure** only appears if something writes it. A connected cuff (Withings
BPM, Omron) is the clean path. Manual entry in a supported app also works.

**A caveat worth knowing:** Samsung Health syncs activity, heart rate and sleep to
Health Connect, but does *not* currently share blood pressure or body composition
that way. So those two numbers need to come from your scale's own app and your
cuff's own app writing to Health Connect directly, not via Samsung Health.

**Visceral fat specifically:** Health Connect has no visceral fat data type. No
consumer scale writes it in a standard way. Body fat percentage is the closest
proxy available, which is what the app tracks. If you get a DEXA or InBody scan,
that number stays a manual note.

---

## Publishing it so other people can use it

This is where most Health Connect projects stall, so plan for it early.

**1. Health apps declaration.** In Play Console → *App content* → **Health apps
declaration form**. You declare every data type you read and justify each one in
plain language tied to a visible feature. Google rejects vague or over-broad
requests. Example of the register they want:

> We request body fat percentage to display the user's body composition trend on
> the dashboard home screen.

Only ask for what you show. The manifest currently requests nine types — delete
any you drop from the UI.

**2. Data safety section.** Standard Play declaration of what you collect, share
and how you secure it. Health Connect data stays on the device, which is the easy
part — but voice logging sends audio and derived text to third-party services,
and that must be declared. If you don't need voice logging, leaving it out makes
this review materially easier.

**3. Privacy policy.** Must be reachable from the Play listing *and* it must be
the same policy Health Connect shows when the user taps through from the
permission sheet.

**4. Review time.** Health permissions are reviewed manually and there is no
published SLA. Budget weeks, not days, and submit before you plan to launch.

Allow-listing is per package name, so version updates don't need a new
declaration — but adding a new data type does.

---

## Project layout

```
app/src/main/
├── AndroidManifest.xml              permissions + rationale intent filters
└── java/com/vitals/app/
    ├── MainActivity.kt              permission launchers, lifecycle refresh
    ├── VitalsApp.kt                 DI wiring + the 15-minute voice scan worker
    ├── PermissionsRationaleActivity.kt  privacy screen Play requires
    ├── DashboardViewModel.kt        state machine: loading/unavailable/permissions/ready
    ├── data/
    │   ├── HealthConnectManager.kt  every Health Connect call lives here
    │   ├── HealthModels.kt          snapshot + reading models
    │   ├── voice/
    │   │   ├── RecordingScanner.kt  finds watch + phone voice notes in MediaStore
    │   │   ├── Transcriber.kt       speech to text (swappable)
    │   │   └── VoiceLogPipeline.kt  note → transcript → food → storage
    │   └── food/
    │       ├── FoodParser.kt        Claude structured extraction
    │       ├── NutritionLookup.kt   USDA FoodData Central
    │       ├── NutritionResolver.kt the four-tier resolution ladder
    │       └── FoodLog.kt           Room entities + DAO
    └── ui/
        ├── DashboardScreen.kt       the screen
        ├── FoodCard.kt              "what I ate today" + calorie review dialog
        ├── Components.kt            stat tiles, week bars, status pill
        └── Theme.kt                 colors
```

`HealthConnectManager` is the only file that touches the Health Connect SDK. If
you swap in a different data source later, that is the seam.

---

## Where to take it next

The app currently reads. The obvious additions, roughly in order of effort:

- **Longer history.** Android 15+ caps reads at 30 days unless you request
  `READ_HEALTH_DATA_HISTORY`. The permission is already in the manifest, commented
  out, with a note.
- **Background sync.** `READ_HEALTH_DATA_IN_BACKGROUND` plus a WorkManager job, if
  you want notifications rather than pull-to-refresh.
- **Editing a misheard entry.** You can confirm a calorie count or delete an entry,
  but not correct a wrong food name.
- **Writing intake back to Health Connect.** `NutritionRecord` would let other apps
  see what you logged. Needs write permission and a new Play declaration entry.
- **AI Q&A over your own data.** The piece that would make this yours rather than
  another tracker. The plumbing is already here — the Claude client, the food log,
  the Health Connect snapshot.

---

## Honest limitations

- Health Connect is **Android only**. There is no iOS equivalent; that would be
  HealthKit and a separate app.
- History is capped at **30 days** without `READ_HEALTH_DATA_HISTORY` on Android
  15+. The app reads 30 and labels it 30 rather than asking for 90 and silently
  getting 30.
- Health Connect is **on-device**. There is no cloud API — you cannot read a
  user's data from a server. Anything cross-device requires you to sync it
  yourself, which is a much bigger privacy and compliance surface.
- Data quality is whatever the source app wrote. Two apps writing steps can
  double-count; Health Connect has priority ordering to mitigate this, but it is
  the user's setting, not yours.
