# R02 native playtest: owner instructions

This is how you run the first visual playtest of the R02 interface inside a real RuneLite window, using **simulated
data only**. It does not read your account, your bank or your RuneLite settings, and it does not change your normal
RuneLite.

**Do not run the `launch` step until Solis has cleared the native launch.** You can do everything up to `check` now.

## What you need (once)

1. **A dedicated standard Windows account** whose name starts with `RaccoonTest` (for example `RaccoonTest`), the
   same one used for the FR-006 kit. It must **not** be an administrator, and you must never have run RuneLite in it.
   Everything below happens while signed in to Windows as that account. This keeps Windows Credential Manager, AppData
   and the registry of your own account completely out of reach. To remove everything later, delete that Windows
   account.
2. **Temurin 11.0.33** installed, with `JAVA_HOME` pointing at it in that account.
3. **Git for Windows**, and this repository checked out in that account at the commit Solis names. That commit must
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
Manager: if you added a Jagex account, delete the whole `RaccoonTest` Windows account instead. The next launch refuses
to start until sign-in files are gone.

## If the launcher says STOPPED

A run changed something outside the playtest profile. The launcher refuses every later launch. Send that run's folder
to Solis. Only Solis decides when the `PLAYTEST-STOPPED.txt` file may be removed; `wipe` does not remove it.

## What keeps this safe

- **A separate Windows account.** Credential Manager, AppData and the registry used here are the test account's own.
- **Its own folder.** The client's home, temporary files (including the Jagex sign-in browser's data) and Java
  preferences all live in the playtest profile. Your real `.runelite`, the game cache, the Jagex files, the shared
  Java preferences, the RuneLite SessionStore credential and the AppData folder names are recorded before and after
  every run. Any change stops the launcher.
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
| `not the dedicated test account` / `is an administrator` | Sign in to Windows as the standard `RaccoonTest` account. |
| `has its own RuneLite folder` / `RuneLite is installed in this account` | Use a fresh test account that has never run RuneLite. |
| `close RuneLite and the Jagex Launcher first` | Close them and run the check again. |
| `JDK is ...` | Set `JAVA_HOME` to Temurin 11.0.33. |
| `uncommitted or untracked changes` | Run `git status`. Remove the files (ask before committing anything). |
| `not on a fetched feralenterprises GitHub remote` | Run `git fetch`. If it still fails, the commit has not been pushed yet. |
| `host JAR was built from ...` / `preferences-isolation JAR` | Run the `prepare` step again. |
| `no classpath lock` / `not marked independently reviewed` | Stop and ask Feral. The reviewed lock comes from his independent check. |
| `... changed since it was resolved` / `differs from the lock` | Do not launch. Send the `check` output to Solis. |
| `frozen package is not the accepted R02 package` | Re-extract the review ZIP and point at the `Project-Raccoon-R02` folder. |
| `lane inputs ZIP is not the accepted one` | Use the accepted Lane Inputs ZIP, or leave `--lane-inputs` out. |
| `profile holds saved sign-in files` | Run the `wipe` step. |
| `protected locations could not be read` | Send the `check` output to Solis. |
| `changed a protected location` | See *If the launcher says STOPPED*. |
| `another playtest is already running` | Close the other RuneLite playtest window. |
