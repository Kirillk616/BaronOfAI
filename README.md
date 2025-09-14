# BaronOfAI
Classic Doom level generative LLM.

New: Prompt-driven level generator CLI using WadModels.
Classic Doom, basically (Ultimate/Registered) Doom 1, Doom 2, Final Doom (TNT Evilution and Plutonia) and Doom 64.

I chose Doom, instead of Half-Life (Goldsrc) or even Quake, because true 3D engines are simply too much for the current state gen AI to figure out. By contrast, 2.5D like the Classic Doom engine (id tech 1) is much simpler ,lightweight, and low on usage.

Rules for this LLM:

Do not make levels too hard (100 cyberdemons, barely any medikits, etc)

Do not make levels too easy either (invulnerability, megasphere and BFG9000s everywhere)

Keep at least some realism, don’t make nonsensical poorly designed levels

Also keep gameplay in the shooter mindset, don’t make maps like the infamous Habitat from TNT, keep toxic tunnels and nukage platforms to an absolute minimum

Do not put monsters in inaccessible locations. Keep 100% kills possible

Do not tag sectors as secrets if they have obstacles in the way, don’t pull an E4M3, E4M7 or Industrial Zone. Again, MAX should be possible.

Keep giant rooms to a minimum. If hiding some items or weapons, don’t hide them in the middle of nowhere, like the armor bonus in E1M7.

ALWAYS change floor height when making a damaging floor (nukage, lava, blood, etc), and make sure the floor height change makes the hazard’s sector have a height SMALLER than the bordering sectors, unless it’s a river or fall.

Always keep enough fighting space.

Don’t make ammo scarce. Infighting of monsters should be optional , not required.

Always keep a proportion of medikits/stimpacks to the map size.

Keep super-powerful enemies to a minimum. Super shotgun should work on most enemies in the level

Do not make up slop. Use common sense in most well-designed Official Doom maps, especially John Romeros.

Keep optional keys to a minimum. There should be at least some keys that are required to exit the map.

To prevent visplane overflows in the original DOS executable, do NOT put too many sectors in one “beautiful” view.

Also, do not put too many lifts/platforms moving at once, which will prevent another error in the original DOS Doom, “no more plats” error. Make sure crushers only start when approaching them, and stop when they are out of sight.

Keep damaging floors that are inescapable to an absolute minimum. There should always be at least SOME way to escape an area without softlocking yourself.

Make sure that if the player can see outside, they should be able to get there somehow.

Difficulty should always be fair. Do not put hitscan (troopers, sergeants, chaingun dudes) enemies in far away areas where you cannot see them (cough cough, TNT Map27 Mount Pain)

In general, always place hitscan enemies carefully. Projectile-firing enemies are easier to dodge for pro players.

## Prompt CLI

A new command-line tool reads a text file prompt and generates a minimal DOOM2 level using WadModels.

Build (Gradle):
- ./gradlew shadowFatJar  (Windows: .\\gradlew.bat shadowFatJar)

Run existing parser demo (default shaded main):
- java -jar build\\libs\\BaronOfAI-1.0-SNAPSHOT-all.jar

Run the prompt-based generator (alternate main class from the same fat JAR):
- java -cp build\\libs\\BaronOfAI-1.0-SNAPSHOT-all.jar wadtool.PromptLevelGeneratorKt <path-to-prompt.txt> [output.wad]
  - Example: java -cp build\\libs\\BaronOfAI-1.0-SNAPSHOT-all.jar wadtool.PromptLevelGeneratorKt prompt.txt WADTool/data/GENAI.WAD

Notes:
- Prompt processing is wired to a Koog AI adapter (KoogPromptProcessor). It now calls a Koog agent connected to OpenAI when configured, and falls back to a heuristic offline mode when not.
- Configure environment variables to enable Koog/OpenAI:
  - OPENAI_API_KEY: your OpenAI API key (required for online mode)
  - KOOG_MODEL: optional, defaults to gpt-4o-mini
- Security: the app reads the API key only from environment variables; no key is stored in files.
- Failure handling: if the online call fails or returns invalid JSON, the tool logs a warning and continues using the heuristic to produce a valid LevelSpec.
- The generated WAD uses the provided WadModels and WadWriter. Advanced lumps (e.g., NODES, BLOCKMAP) are not currently generated; some engines or tools may need to rebuild them.

Gradle wrapper:
- If gradlew/gradlew.bat are not present, run: gradle wrapper --gradle-version 8.9
  - Windows PowerShell: ./gradlew.bat tasks
  - Unix/macOS: ./gradlew tasks
