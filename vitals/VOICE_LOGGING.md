# Voice food logging

Say *"I just ate a banana"* into your watch. It shows up on the dashboard.

This document covers how that works, what you have to set up, and where it
breaks.

---

## The flow

```
Galaxy Watch voice recorder
        ↓  (Wearable app auto-transfer)
Phone: /Recordings/Sounds/Watch/note.m4a
        ↓  (MediaStore indexes it)
Android wakes Vitals via a MediaStore content trigger
        ↓  Whisper
"I just ate a banana"
        ↓  Claude Haiku (forced tool call)
{ banana, one medium, 118 g, confidence: high }
        ↓  USDA → Open Food Facts → estimate → ask you
105 kcal, source: USDA
        ↓
"What I ate today" card
```

The key discovery: **you don't need a watch app.** Notes recorded on a Galaxy
Watch are copied to the phone by the Wearable app and land under `Recordings/`,
which is exactly where Samsung's phone recorder writes. One MediaStore query
covers both.

---

## Setup

### 1. Turn on watch → phone transfer

In the **Galaxy Wearable** app on your phone: **Apps → Voice Recorder → App
settings**, and make sure the transfer setting is on.

This is undocumented by Samsung and has a known failure mode — it silently stops
working, and toggling it off and on again fixes it. If notes stop appearing,
check here first.

### 2. Add your API keys

Create `local.properties` in the project root (it's git-ignored):

```properties
OPENAI_API_KEY=sk-...
ANTHROPIC_API_KEY=sk-ant-...
USDA_API_KEY=...
```

- **OpenAI** — Whisper transcription. Around $0.006/minute, so a year of daily
  10-second notes costs well under a dollar.
- **Anthropic** — Claude Haiku parses the transcript. Fractions of a cent per note.
- **USDA FoodData Central** — free, [sign up at data.gov](https://fdc.nal.usda.gov/api-guide).
  1,000 requests/hour.

Open Food Facts needs no key.

> **Keys in an APK are not secret.** Anyone with the file can extract them. This
> is acceptable for a personal build. Before you publish this to anyone else,
> the API calls must move behind a backend you control, with the keys on the
> server. There is no way around this.

### 3. Grant the permission

On first run the food card asks for access to your recordings
(`READ_MEDIA_AUDIO`). Unlike the health permissions, this one needs no Play
Console declaration.

---

## How a food becomes a number

Four tiers, stopping at the first that answers honestly:

| Tier | Source | Used for |
|---|---|---|
| 1 | USDA FoodData Central | Generic whole foods — banana, chicken breast, rice |
| 2 | Open Food Facts | Branded and packaged items, better non-US coverage |
| 3 | Claude's estimate | Only when the model itself said it was confident |
| 4 | **Ask you** | Everything else |

Tier 4 is the one that matters. The obvious design — always take the first
database hit — fails badly, because USDA's search is fuzzy and returns *some*
top result for almost any query. "Chicken shawarma" resolves to "Chicken, raw"
and gets stamped as sourced data. So matches are only accepted when every
meaningful word of the search term appears in the matched food's description;
anything else falls through.

Unresolved items appear in the list with **Add kcal** instead of a number, are
excluded from the day's total until you fill them in, and open a dialog
pre-filled with the model's guess so the usual case is one tap.

**The transcript is always shown under each item.** Voice logging fails in ways
you need to be able to see — if "a banana" came back as "a bandana", only the
transcript explains why the number is odd.

---

## The hook

New recordings are picked up by a **MediaStore content trigger** — Android's
sanctioned mechanism for "wake my app when this changes." The observer is
registered inside `system_server`, not inside the app, so **the app can be fully
dead and Android will still start it** when a file lands. No polling, no
persistent notification, no foreground service.

Three layers, overlapping on purpose, because none is reliable alone:

| Layer | When it fires | Why it exists |
|---|---|---|
| Content trigger | Seconds after a file appears | The real hook. Works with the app dead. |
| Periodic worker | Every 15 minutes | Catches what the trigger missed in Doze |
| ContentObserver + resume scan | Instantly, app open | What you actually watch happen |

Two non-obvious things about the trigger, both of which will silently break it
if changed:

- **It cannot be periodic.** JobScheduler throws on a periodic job with a
  content trigger, so the worker re-arms itself as the *last* statement of its
  own run. Re-arming earlier makes the system stop the job mid-work.
- **The re-arm policy differs by caller.** From the worker it must be `REPLACE`
  (the current run hasn't finished, so `KEEP` would drop the re-arm and the hook
  would fire exactly once, ever). From app startup it must be `KEEP` — the system
  starting the process to run a triggered job calls `Application.onCreate` first,
  and `REPLACE` there would cancel the very job it was starting.

**The trigger is a wake-up signal, not a list of changes.** The system caps its
change report at 50 URIs, and changes landing between one run finishing and the
next arming are lost outright. So it only ever means "look now" — what actually
gets processed comes from re-querying MediaStore over a 24-hour window, with the
processed-recordings table preventing duplicates. That window also covers the
watch transfer lagging behind when you actually spoke.

### Latency, honestly

- **Screen on, phone in use:** a few seconds.
- **Doze (phone idle in a pocket, overnight):** minutes to hours. JobScheduler
  work is deferred to maintenance windows, which get rarer the longer the device
  stays idle.
- **Restricted App Standby bucket:** up to a day.

No notification-free mechanism on modern Android does better than this. The
alternatives are worse: a `dataSync` foreground service needs a persistent
notification, a Play Console justification with a demo video, is capped at 6
hours per 24 on Android 15, and can't restart itself after a reboot.
`FileObserver` is ruled out entirely — it's in-process inotify, so it can't wake
a dead app, and it's unreliable across FUSE-mediated storage anyway.

Every recording is processed exactly once. Failures retry up to three times and
are then written off, so a dropped connection doesn't lose a meal and a corrupt
file doesn't burn API calls forever.

Notes longer than two minutes are skipped — that's a meeting, not a meal, and
there's no reason to ship it to a transcription API.

---

## What this changes about publishing

Everything in the README about the Play health declaration still applies, plus:

**Audio now leaves the device.** The Data Safety section has to say so: audio
recordings and derived text are transmitted to third-party services. This is a
materially harder review than the read-only version was, and it's why the
privacy screen (`PermissionsRationaleActivity`) spells out exactly what is sent
and what isn't.

**`READ_MEDIA_AUDIO` reads all your audio, not just food notes.** There's no
audio equivalent of the photo picker's partial access, so it is all-or-nothing.
The app filters to `Recordings/` and to clips under two minutes, but the
permission itself is broad, and reviewers will read it that way.

---

## Known gaps

- **The watch transfer is Samsung's, undocumented, and user-toggleable.** If it's
  off, nothing arrives and the app has no way to tell you why. Worth a
  troubleshooting screen eventually.
- **Portion sizes are guesses.** "A bowl of rice" is a wide range. The gram
  estimate comes from the model, and the calorie count is only as good as it.
- **`DATE_TAKEN` isn't always set** on audio. When it's missing the app falls back
  to when the file arrived on the phone, which for a late-evening note that
  synced after midnight can land it on the wrong day.
- **No editing yet.** You can confirm a calorie count or remove an entry, but not
  correct a misheard food name — you'd delete it and re-record.
