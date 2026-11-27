PROJECT RACCOON - WINDOWS PLAYTEST (FR-006)
===========================================

PLEASE DO NOT START until the reviewer (Solis) tells you this kit is approved for testing.

What this is
------------
A self-contained test folder. It checks that the Project Raccoon plugin behaves correctly in
the real RuneLite client on your PC. You don't need to install anything, use git, or set up any
development tools.

What it is designed to do
-------------------------
- Run everything from this folder: its own Java, its own RuneLite test client, its own
  RuneLite settings folder and temp folder.
- Leave your normal RuneLite, its settings, plugins and profiles alone. The kit checks the
  places it could affect before and after every step. If a check finds a change, or cannot be
  completed, the kit stops itself (it writes KIT-STOPPED.txt) and will not open the test client
  again. Then send that file and the receipts to the reviewer.
- The Project Raccoon plugin only reads what the game already shows you. It does not click,
  move, type or change anything in the game, and it sends nothing over the internet.
- The kit does not ask for or store your password. You sign in only in Jagex's own sign-in
  window, which the test client opens (not your normal web browser).
- The play step (R3, menu 4) runs only in a separate Windows account made just for this test
  (see "Before R3" below). Everything the sign-in leaves behind (the test client's sign-in, the
  sign-in window's browser data and an encryption key RuneLite keeps in Windows Credential
  Manager) then belongs to that account and disappears when you remove it. The kit never
  deletes anything from Credential Manager itself.
- Results ("receipts") record states, counts and timings. They do not record account names,
  passwords, player chat, bank contents or your account's identifier.

You need
--------
- Windows 10 or 11 (64-bit), about 1.5 GB of free disk space, and an internet connection.
- Your Old School RuneScape account, and a second account for one step of R3 (one you own, or
  one whose owner has agreed to this test).
- About 40 minutes, and access to Brutus for step R3 (two kills in a row).
- Close RuneLite and the Jagex Launcher before you start. The kit will remind you.

Steps
-----
1. Download the kit zip from the link the reviewer sends you.
2. Right-click the zip, choose "Extract All...", and extract it to C:\RaccoonPlaytest
   (not to the Desktop or Documents if they are synced by OneDrive; the kit refuses synced or
   linked folders).
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

Before R3 (only when the reviewer says R3 may start): a separate Windows test account
-----------------------------------------------------------------------------------------
1. In your own account, copy the kit zip to C:\Users\Public\Downloads (every account can
   read that folder).
2. Open Settings > Accounts > Other users (Windows 10: Family & other users) > Add account >
   "I don't have this person's sign-in information" > "Add a user without a Microsoft account".
   Name it RaccoonTest and give it a password. Leave it as a Standard User (do not make it an
   administrator).
3. Sign out (or Switch user) and sign in as RaccoonTest. Windows prepares the account; this can
   take a minute.
4. As RaccoonTest: extract the zip from C:\Users\Public\Downloads to C:\RaccoonTest-Kit.
   Do not install RuneLite or the Jagex Launcher in this account.
5. Double-click Raccoon-Playtest.cmd, choose 1 (check), then 4 (R3) and follow the checklist.
   Then choose 6 and copy the receipts zip to C:\Users\Public\Documents.
6. Sign out of RaccoonTest and back in to your own account. Send the receipts zip from
   C:\Users\Public\Documents to the reviewer.
7. When the reviewer confirms they have everything: Settings > Accounts > Other users >
   RaccoonTest > Remove > "Delete account and data". This deletes the test account's sign-in,
   browser data and Credential Manager key together.

If something goes wrong
-----------------------
Don't try to fix anything. Stop, take a screenshot of the window and send it to the reviewer
together with the receipts zip (menu option 6) and KIT-STOPPED.txt if it exists.
"BLOCKED" next to a check means that something was not observed (for example no Brutus kill
happened); it is not an error on your part, and the reviewer will tell you if a step needs
repeating.
