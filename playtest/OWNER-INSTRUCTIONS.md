PROJECT RACCOON - WINDOWS PLAYTEST (FR-006)
===========================================

PLEASE DO NOT START until the reviewer (Solis) tells you this kit is approved for testing.

What this is
------------
A self-contained test folder. It checks that the Project Raccoon plugin behaves correctly in
the real RuneLite client on your PC. You don't need to install anything, use git, or set up any
development tools.

What it will and will not do
----------------------------
- Everything stays inside this folder: its own Java, its own RuneLite test client, its own
  RuneLite settings folder and temp folder.
- Your normal RuneLite, its settings, plugins and profiles are not used and not changed. The kit
  checks this before and after every step and tells you the result.
- The plugin only reads what the game already shows you. It never clicks, moves, types or
  changes anything in the game, and it sends nothing over the internet.
- Your password is never seen by the kit. If you sign in with a Jagex account, the sign-in is
  stored only inside this folder; menu option 7 deletes it. Deleting the folder removes
  everything.
- Results ("receipts") record states and timings only: no account names, passwords, chat
  from players, or bank contents.

You need
--------
- Windows 10 or 11 (64-bit), about 1.5 GB of free disk space, and an internet connection.
- Your Old School RuneScape account.
- About 30 minutes, and access to Brutus for step R3 (two kills).
- Close RuneLite and the Jagex Launcher before you start. The kit will remind you.

Steps
-----
1. Download the kit zip from the link the reviewer sends you.
2. Right-click the zip, choose "Extract All...", and extract it to a short path, for example
   your Desktop.
3. Open the extracted folder and double-click "Raccoon-Playtest.cmd". A blue or black window
   opens with a menu. If Windows asks whether to run it, choose "More info" and "Run anyway"
   (the kit is not signed).
4. Choose these menu options in order and wait for each one to finish:
     1  Check the kit            (a few seconds; must say "Kit integrity: all ... files match")
     2  R1 automatic replay      (about 1 minute, no game window)
     3  R2 automatic client run  (the test client opens and closes by itself; do not log in)
     4  R3 play and observe      (follow the checklist shown on screen; about 15 minutes)
     5  R4 official plugin file  (follow the short checklist; no login needed)
     6  Save receipts            (creates raccoon-receipts-....zip in the kit folder)
5. Send the receipts zip to the reviewer.
6. When the reviewer confirms they have everything, you can choose 7 to delete the test
   client's data, or simply delete the whole folder.

If something goes wrong
-----------------------
Don't try to fix anything. Take a screenshot of the window and send it to the reviewer.
Nothing outside this folder will have been changed. "BLOCKED" next to a check means that
something was not observed (for example no Brutus kill happened); it is not an error on your
part, and the reviewer will tell you if a step needs repeating.
