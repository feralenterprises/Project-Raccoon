# R02 native playtest: owner instructions

This is how you run the first visual playtest of the R02 interface inside a real RuneLite window, using **simulated
data only**. It does not read your account, your bank or your RuneLite settings, and it does not change your normal
RuneLite.

**Do not run the `launch` step until Solis has cleared the native launch.** You can do everything up to `check` now.

## GitHub Desktop quick launch

**Once:** On your regular Windows account (no separate account needed), you install nothing and change no settings: START-R02.cmd downloads its own Java (Temurin 11.0.33+10, checked by SHA-256) into `%USERPROFILE%\RaccoonPlaytest\jdk` the first time and uses it only for Raccoon. Your `JAVA_HOME`, `PATH` and other Java installs (for example Java 17 for Burrow) are never changed. Open the Project Raccoon checkout in GitHub Desktop and select `test/r02-windows-playtest`. Copy these two intact ZIPs from the cleared private owner package into `%USERPROFILE%\RaccoonPlaytest\inputs`, outside the repository:

- `Project-Raccoon-R02-Review.zip`
- `Project-Raccoon-R02-Lane-Inputs.zip`

**Every playtest:** Pull origin in GitHub Desktop. Choose **Repository → Show in Explorer** and double-click **START-R02.cmd** at the repository root (a normal double-click, not *Run as administrator*). Close your normal RuneLite and the Jagex Launcher first. It prepares the build automatically when needed and starts RuneLite only after the existing preflight reports READY. Stay at the RuneLite login screen. If the console stops on an error, keep its message for Solis.

Solis promotes explicitly cleared commits to the canonical playtest branch. The quick launcher checks that the local branch, its tracked origin SHA and the live GitHub branch SHA all agree. The manual commands below are available for troubleshooting.

## What you need (once)

1. **Your regular Windows account.** Your normal RuneLite, its settings and your saved sign-in can stay where they are:
   the playtest never uses, changes or removes them. Start it normally, not elevated (*Run as administrator* is
   refused). A dedicated `RaccoonTest...` account still works with its stricter FR-006 rules, but is not required.
2. **Nothing else to install.** `START-R02.cmd` provisions Temurin 11.0.33+10 for itself. For the manual commands below,
   set `JAVA_HOME` in that Command Prompt only (`set JAVA_HOME=%USERPROFILE%\RaccoonPlaytest\jdk\jdk-11.0.33+10`).
3. **Git for Windows** (or GitHub Desktop), and this repository checked out at the commit Solis names. That commit must
   already be pushed to the `feralenterprises` GitHub repository and fetched.
4. **The frozen R02 review package**, extracted outside the repository (for example `C:\Raccoon\Project-Raccoon-R02`).
   To check the ZIP first, run `certutil -hashfile Project-Raccoon-R02-Review.zip SHA256`. It should print
   `ff4a67ab234b101c60c7d68ac5b714643c5952358683a90e1630189156f1d3ba`.
5. **Optional: the private Lane Inputs ZIP** (`1f6a821d…cece`), kept outside the repository (for example
   `C:\Raccoon\Project-Raccoon-R02-Lane-Inputs.zip`). Without it every panel shows its placeholder. With it, the panels
   built so far appear.

## Steps

Open **Command Prompt** in the repository folder.

1. **Prepare.** This needs internet once. Run:

   `playtest\r02-playtest.cmd prepare`

   It builds the playtest host and downloads RuneLite 1.13.8. It does not start anything.

2. **Check.** Run:

   `playtest\r02-playtest.cmd check "C:\Raccoon\Project-Raccoon-R02" --lane-inputs "C:\Raccoon\Project-Raccoon-R02-Lane-Inputs.zip"`

   The last line should be `RESULT: READY`. Every line marked `FAIL` explains what is wrong; see *If something is
   refused*. A check never starts the game and never creates any folders.

3. **Launch (only after clearance).** Run the same line with `launch` instead of `check`.

   Close RuneLite and the Jagex Launcher first; the launcher checks this. RuneLite opens in developer mode with a
   **separate, empty profile**. The first start takes longer because it downloads a fresh game cache.

4. **Playtest on the login screen. Do not log in or add a Jagex account.** In the sidebar there are two new tabs:
   - **Raccoon R02 playtest** (tan circle): the R02 interface.
   - **R02 playtest controls** (blue-grey circle): Demo A, Demo B, Signed out, and the bank, archive, quest, Pathfinder
     and activity states.

   Look at:
   - the rail, Back and every built panel;
   - switching accounts, which should always return to Home with no old data;
   - resizing the window and the sidebar.

   If you log in by mistake, the simulated data disappears on purpose.

5. **Finish.** Close the RuneLite window, or press **Ctrl+C** in the Command Prompt. Either way stops the client and
   everything it started.

6. **Display-scaling rows (when asked).** Add `--scale 1.25`, `--scale 1.5` and so on (or `--scale launcher-default`)
   to the launch line.

## What to send back

Each run writes a folder at:

`%USERPROFILE%\.raccoon-r02-playtest\profile\.runelite\raccoon-r02-playtest\run-YYYYMMDD-HHMMSS\`

Send these files from it:
- `launcher.txt`: the checks, the protected-location result and the exit code;
- `events.log`: what the playtest host did, including panel sizes and the teardown result;
- `native-name-audit.csv`: every item name compared with the game's own name.

Also send screenshots of the RuneLite window. Don't send `client-console.log` without reading it, and never send the
whole profile folder.

## If you signed in by accident

Run `playtest\r02-playtest.cmd wipe`. It deletes only the playtest profile folder. It refuses if that folder is not
the launcher's own, or contains any link or junction that could lead elsewhere. It never touches Windows Credential
Manager. If you added a Jagex account in the playtest client, tell Solis: the playtest copy is wiped, and the key
RuneLite keeps in Credential Manager (`RuneLite SessionStore`), shared with your normal RuneLite, is left alone on
purpose. The next launch refuses
to start until sign-in files are gone.

## If the launcher says STOPPED

A run changed something outside the playtest profile. The launcher refuses every later launch. Send that run's folder
to Solis. Only Solis decides when the `PLAYTEST-STOPPED.txt` file may be removed; `wipe` does not remove it.

## What keeps this safe

- **Your account's own RuneLite is never used.** On a regular account the launcher refuses an elevated start, refuses a
  playtest profile holding any sign-in file, and watches before and after every run: your `.runelite` and
  `jagexcache` folders (every file), the `RuneLite`, `Jagex` and `Jagex Launcher` folders in AppData (every file), new
  or removed top-level entries in your home folder and AppData, the shared Java preferences and whether the
  `RuneLite SessionStore` credential exists. Any change stops the launcher. Other programs' activity elsewhere in your
  home folder is not watched (it changes constantly). RuneLite 1.13.8 touches Credential Manager only to read or create
  the `RuneLite SessionStore` key when a Jagex account is added or loaded in its own folder, and never deletes it;
  staying on the login screen with an empty profile touches it not at all. On a dedicated `RaccoonTest` account the
  whole home folder and AppData are watched as before.
- **Its own folder.** The client's home, temporary files (including the Jagex sign-in browser's data), Java
  preferences, and the per-user folders Windows programs use (`USERPROFILE`, `APPDATA`, `LOCALAPPDATA`, `HOMEDRIVE`/
  `HOMEPATH`) all point into the playtest profile. The locations listed above are recorded before and after
  every run. Links and junctions are recorded, never followed. Any change, or anything that
  cannot be read, stops the launcher. Windows itself sometimes writes in AppData while programs run; if that stops the
  launcher, send the run folder to Solis as usual.
- **No sign-in passed in.** Jagex launcher sign-in variables (`JX_...`) and injected Java options are removed. The
  client gets exactly `--developer-mode --disable-telemetry`.
- **Nothing shown while signed in.** The playtest reads only whether you are on the login screen. Anywhere else, every
  simulated value is hidden.
- **Exact versions only.**
  - Every RuneLite file is checked against the reviewed SHA-256 list.
  - The playtest code must come from the exact pushed commit.
  - The review package and the lane inputs must be the accepted ones.
  - The playtest code refuses to run outside the launcher's profile.

## If something is refused

| Message | What to do |
| --- | --- |
| `running elevated` | Close the window and double-click `START-R02.cmd` normally, not with *Run as administrator*. |
| `is an administrator` / `has its own RuneLite folder` / `RuneLite is installed in this account` | Only on a `RaccoonTest...` account: use your regular account instead, or a fresh test account. |
| `close RuneLite and the Jagex Launcher first` | Close them and run the check again. |
| `JDK is ...` | Use `START-R02.cmd` (it provisions the right Java), or set `JAVA_HOME` in that Command Prompt as above. |
| `uncommitted or untracked changes` | Run `git status`. Remove the files (ask before committing anything). |
| `not on a fetched feralenterprises GitHub remote` | Run `git fetch`. If it still fails, the commit has not been pushed yet. |
| `host JAR was built from ...` / `preferences-isolation JAR` | Run the `prepare` step again. |
| `no classpath lock` / `not marked independently reviewed` | Stop and ask Feral. The reviewed lock comes from his independent check. |
| `goes through a link or junction` / `contains links, junctions or other redirections` | Don't remove anything yourself. Send the message to Solis. |
| `protected locations could not be read` | Send the message to Solis. Nothing was started. |
| `package payload` / `does not match SHA256SUMS.txt` / `not listed in SHA256SUMS.txt` | The review package folder was changed. Extract the accepted ZIP again into an empty folder. |
| `... changed since it was resolved` / `differs from the lock` | Do not launch. Send the `check` output to Solis. |
| `frozen package is not the accepted R02 package` | Re-extract the review ZIP and point at the `Project-Raccoon-R02` folder. |
| `lane inputs ZIP is not the accepted one` | Use the accepted Lane Inputs ZIP, or leave `--lane-inputs` out. |
| `profile holds saved sign-in files` | Run the `wipe` step. |
| `protected locations could not be read` | Send the `check` output to Solis. |
| `changed a protected location` | See *If the launcher says STOPPED*. |
| `another playtest is already running` | Close the other RuneLite playtest window. |
