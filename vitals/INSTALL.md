# Build in the cloud, install on your phone

No local compiling. GitHub builds and signs the APK; your phone installs it and
picks up every future update by itself. Android Studio isn't needed at all —
only later, if you want to edit code.

About 30 minutes, once.

---

## Before you start

You need a **GitHub account** (free) and **GitHub Desktop**
([desktop.github.com](https://desktop.github.com)) — a normal Windows app, no
command line.

One step needs a terminal: creating the signing key. It's a single command and
it doesn't touch Gradle, so none of the sync trouble applies to it.

---

## Step 1 — Start from a clean copy

The folder you've been editing has hand-applied changes in it. Rather than
reason about what's in there, extract the **latest zip into a new folder**:

```
E:\AI\wellness-hub\vitals-app
```

You should end up with `settings.gradle.kts`, `app\`, `gradlew.bat` and
`.github\` directly inside it. The old folder can be deleted once this works.

> **Windows hides `.github` by default.** In File Explorer: View → Show → Hidden
> items. If that folder doesn't make it into the repo, no build ever runs.

---

## Step 2 — Create the signing key ⚠️

This is the one irreversible decision. Android identifies an app by its package
name *and* its signing key. Install with one key, update with another, and the
update is rejected — the only way out is uninstalling, which deletes your food
log and every permission you granted.

Open **PowerShell** (Start → type `powershell`) and run:

```powershell
mkdir E:\AI\wellness-hub\keys

& "C:\Program Files\Android\Android Studio\jbr\bin\keytool.exe" -genkeypair -v -keystore E:\AI\wellness-hub\keys\wellness-release.jks -alias wellness -keyalg RSA -keysize 4096 -validity 10950
```

It asks for a password twice, then name and organisation fields — only the first
matters and even that's cosmetic. Press Enter through the rest, and type **`yes`**
at `Is CN=... correct?`.

Modern keytool creates a PKCS12 keystore, which uses **one password for both**
the store and the key. Remember it.

**Back up `wellness-release.jks` and its password in two places.** Losing it
costs you the app's data permanently.

The key lives outside the project folder on purpose — that folder becomes a
public repo.

---

## Step 3 — Encode the key for GitHub

Still in PowerShell:

```powershell
[Convert]::ToBase64String([IO.File]::ReadAllBytes("E:\AI\wellness-hub\keys\wellness-release.jks")) | Set-Clipboard
```

That copies one long line to your clipboard. Paste it somewhere temporarily —
Notepad is fine — because you'll need it in a moment and the clipboard is easy
to lose.

---

## Step 4 — Put the project on GitHub

1. Open **GitHub Desktop** → sign in.
2. **File → Add local repository** → choose `E:\AI\wellness-hub\vitals-app`.
   It'll say it isn't a git repository and offer to **create one** — do that.
3. Bottom left: type a summary like `Vitals` → **Commit to main**.
4. Top: **Publish repository**.
   **Untick "Keep this code private."** Obtainium's private-repo support is
   unreliable; nothing sensitive is in here, because the keystore and API keys
   live in secrets rather than in the repo.

Check the file list before publishing — `.github/workflows/release.yml` must be
there. If it isn't, hidden items are switched off in Explorer and the folder
didn't get copied.

---

## Step 5 — Add the secrets

On github.com, open your repo → **Settings** → *Secrets and variables* →
**Actions** → **New repository secret**. Add these one at a time:

| Name | Value |
|---|---|
| `KEYSTORE_BASE64` | the long line from step 3 |
| `KEYSTORE_PASSWORD` | your keystore password |
| `KEY_ALIAS` | `wellness` |
| `KEY_PASSWORD` | the same password again |

Names must match exactly — they're case-sensitive.

These three are optional; skip them and the app still builds, the voice and
label features just stay inactive until you add them:

| Name | For |
|---|---|
| `OPENAI_API_KEY` | transcribing voice notes |
| `ANTHROPIC_API_KEY` | turning a transcript into food |
| `USDA_API_KEY` | calorie lookup |

---

## Step 6 — Watch the first build

Publishing already triggered one. Open the **Actions** tab.

- **Green tick** — done. The APK is attached at the bottom of the run page under
  *Artifacts*.
- **Red X** — click into it, open the failed step, and send me what it says. CI
  logs are complete, unlike the IDE's, so whatever's wrong will be stated plainly.

This is the moment the local Gradle trouble stops mattering: the build runs on a
clean machine with the exact files, so it either works for everyone or fails
with a real reason.

---

## Step 7 — Prepare the phone

**Turn off Auto Blocker.** Settings → Security and privacy → **Auto Blocker** →
off. It's on by default on One UI 6.1.1+ and blocks every install that isn't
from the Play Store or Galaxy Store. Nothing below works until it's off.

**Check you have a screen lock.** Health Connect refuses to run without a PIN,
pattern or password.

---

## Step 8 — Cut the first release

Back in GitHub Desktop, or on github.com under **Releases → Draft a new
release**: create a tag `v1.0`, target `main`, and publish it.

*(In GitHub Desktop: History → right-click the latest commit → Create tag →
`v1.0`, then Push.)*

That kicks off a build which attaches `app-release.apk` to a Release.

---

## Step 9 — Install Obtainium and point it at the repo

1. On the phone, download the Obtainium APK from
   [its latest release](https://github.com/ImranR98/Obtainium/releases). Chrome
   will ask whether to allow installs from it — allow.
2. Open Obtainium → **Add App** → paste
   `https://github.com/YOUR-USERNAME/vitals-app` → **Add**.
3. In that app's settings, turn on **background updates**.

Tap install. You're done.

**On first launch**, grant the two permissions: *Connect your health data* opens
Health Connect's own sheet, and *Allow access to recordings* on the food card
enables voice logging.

---

## Every update after this

I hand you changed files. You:

1. Drop them into `E:\AI\wellness-hub\vitals-app` (replacing the old ones)
2. GitHub Desktop → write a summary → **Commit to main** → **Push origin**
3. Create a tag `v1.1` and push it

GitHub builds and signs it, Obtainium notices within a few hours — or right away
if you pull to refresh — and you tap install.

**What survives every update:** your food and supplement logs, Health Connect
permissions, the recordings permission, and the armed voice-note trigger. The
signing key is what makes that true, which is why step 2 mattered.

---

## If something goes wrong

| Symptom | Cause | Fix |
|---|---|---|
| No workflow runs at all | `.github` folder never made it to GitHub | Explorer → View → Show → Hidden items, re-copy, commit again |
| CI: "KEYSTORE_BASE64 secret is empty" | Secret missing or misnamed | Names are case-sensitive; re-add it |
| CI: "invalid keystore format" | Base64 got line-wrapped | Re-run step 3 exactly — the PowerShell version produces one unbroken line |
| Phone: "App not installed" | Auto Blocker | Settings → Security and privacy → Auto Blocker → off |
| `INSTALL_FAILED_UPDATE_INCOMPATIBLE` | A different signing key | You installed a build signed with another key. If the original key is genuinely lost, uninstalling is the only path and the data goes with it. |
| Obtainium sees nothing | Repo is private, or no tag pushed | Make it public; check Releases has an entry |
| App runs but shows no data | Nothing is writing to Health Connect | Health Connect → App permissions → let Samsung Health and your scale app write |
| Voice notes never arrive | Samsung's watch transfer is off | Galaxy Wearable → Apps → Voice Recorder → App settings → toggle transfer off and on |

---

## Later, if you want Android Studio working

You don't need it to use the app. When you do want to edit code, the sync
failure is worth solving then — with the CI build passing, you'll have a known-good
reference, which makes diagnosing a local-only problem far easier than debugging
both at once.
