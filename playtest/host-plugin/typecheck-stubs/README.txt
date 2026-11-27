COMPILE/TEST-ONLY doubles of the RuneLite 1.13.8 types the R02 playtest host uses. They let the host plugin compile and
run its lifecycle test offline (no repo.runelite.net here). Every signature is checked against the pinned RuneLite
source (runelite-parent-1.13.8, 8397ec71) by the hostStubCheck task, which also checks each source file's git blob id.
Behaviour here is a test double only (ClientToolbar records buttons, ClientThread runs immediately, ItemManager names
come from a map). They are never on the owner's build path: the real build compiles against net.runelite:client:1.13.8.
