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

## How to run

Prerequisites:
- JDK 17 installed (the project compiles and runs with Java 17)
- Windows PowerShell or any terminal. The Gradle Wrapper is included, so no separate Gradle install is required

Build:
- Windows: .\\gradlew.bat build
- macOS/Linux: ./gradlew build

Run the Web UI (recommended):
- This starts a Ktor server with an HTMX UI to browse recent generated levels.
- Command: .\\gradlew.bat run
- Then open http://localhost:8080
- The UI shows a list on the left and an SVG preview + WAD download on the right. Levels are read from WADTool\\data\\levels

Generate a level with the Koog agent (optional, requires OpenAI):
- Set your OpenAI key in the environment before running:
  - Windows PowerShell: $Env:OPENAI_API_KEY = "sk-..."
  - Command: .\\gradlew.bat run -PmainClass=wadtool.KoogAgentDslRunnerKt
  - The agent uses a hardcoded example prompt for now, writes GENAI.WAD + GENAI.svg to WADTool\\data, and also saves into WADTool\\data\\levels so it appears in the Web UI list.
- If OPENAI_API_KEY is not set, the agent sample prints a message and exits without error.

Run the classic parser demo (reads ATTACK.WAD and regenerates it):
- Command: .\\gradlew.bat run -PmainClass=wadtool.MainKt
- Outputs a summary, an SVG, and writes WADTool\\data\\ATTACK_GEN.WAD

Run the prompt-based generator CLI directly:
- Command: .\\gradlew.bat run -PmainClass=wadtool.PromptLevelGeneratorKt --args="prompt.txt WADTool\\data\\GENAI.WAD"
- You can omit the output path to use the default

Generate a starter agent map plan:
- Command: .\\gradlew.bat "-PmainClass=wadtool.agent.AgentMapPlanGeneratorKt" run "--args=prompt.txt WADTool\\data\\map_plan.json WADTool\\data\\AGENT_GEN.WAD"
- This runs the root orchestrator, topology agent, connector/door agent, room layout agent, map-plan validator, and deterministic MapPlan compiler.
- The output is a JSON MapPlan, a compiled multi-room WAD, and an SVG preview.

Play a generated level in the browser:
- Open the Web UI, select a saved level, then click Play in Browser.
- The player page expects webDOOM runtime files under WADTool\\data\\webdoom: doom2.js, doom2.wasm, and doom2.data.
- Local setup command:
  - PowerShell: New-Item -ItemType Directory -Force WADTool\\data\\webdoom; Invoke-WebRequest https://raw.githubusercontent.com/UstymUkhman/WebDOOM/master/public/doom2.js -OutFile WADTool\\data\\webdoom\\doom2.js; Invoke-WebRequest https://raw.githubusercontent.com/UstymUkhman/WebDOOM/master/public/doom2.wasm -OutFile WADTool\\data\\webdoom\\doom2.wasm; Invoke-WebRequest https://raw.githubusercontent.com/UstymUkhman/WebDOOM/master/public/doom2.data -OutFile WADTool\\data\\webdoom\\doom2.data
- Do not commit IWAD/runtime bundles. For redistributable use, build or provide a Freedoom-compatible webDOOM data bundle instead of commercial Doom assets.

Where files are stored:
- Generated artifacts: WADTool\\data (e.g., GENAI.WAD, GENAI.svg)
- Persisted recent levels (used by the Web UI): WADTool\\data\\levels\\<level-id> with files: prompt.txt, map.svg, map.wad, created.txt

Launch Doom with the generated WAD (optional):
- See WADTool\\data\\run-doom-with-genai.cmd as a starting point and adjust paths for your Doom source port.

Notes:
- OPENAI_API_KEY is only read from the environment; no keys are stored in the repo.
- The storage abstraction is file-based now and can later be swapped with S3.

Gradle wrapper:
- If gradlew/gradlew.bat are missing, run: gradle wrapper --gradle-version 8.9
  - Windows PowerShell: .\\gradlew.bat tasks
  - Unix/macOS: ./gradlew tasks


## Run from IntelliJ IDEA

You can start the Ktor webserver directly from IntelliJ without using the terminal.

Option A — Use the provided Run Configuration (easiest):
- Open the project in IntelliJ IDEA and let it import the Gradle build.
- Make sure Project SDK is set to JDK 17 (File > Project Structure > Project > SDK: 17).
- In the Run/Debug configurations dropdown (top-right), choose: Run Web UI (Ktor).
- Click Run. Gradle will build and start the server.
- Open http://localhost:8080 in your browser.

Option B — Run via Gradle tool window:
- Open the Gradle tool window (View > Tool Windows > Gradle).
- Expand Tasks > application.
- Double-click run. This runs the Gradle task which is already configured to start wadtool.web.WebServerKt.

Option C — Create an IntelliJ Application configuration manually:
- Run > Edit Configurations… > + > Kotlin.
- Name: WebServer (Ktor)
- Main class: wadtool.web.WebServerKt
- Use classpath of module: select the Gradle module for this project (after import).
- JRE: 17
- Apply and Run.

Notes:
- The server binds to port 8080 by default (see WADTool/src/web/WebServer.kt). Change the port in that file if needed.
- Recent levels are read from WADTool\data\levels. If empty, the UI will show a helper message until you generate a level.
