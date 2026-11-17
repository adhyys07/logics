# RedstoneLogics

A Fabric mod that reads a redstone build and tells you what the circuit *does*.
It follows the signal through torches, repeaters and comparators, recovers the
boolean logic, and draws it as a gate diagram with a truth table and timing.

**You build it → it reads it back.** It does not build circuits, only reads them.

```
23 blocks  ->  6 components  ->  4 gates

A ──┐
    ├─ AND ── Y0        Y0 = A AND B
B ──┘                   critical path: 2 redstone ticks
```

---

## Setup

**Requires** Minecraft 26.3 · [Fabric Loader](https://fabricmc.net/use/) 0.20.1+ ·
[Fabric API](https://modrinth.com/mod/fabric-api) · Java 25

> Already have Fabric installed? Check the version — this needs loader
> **0.20.1 or newer**, and an older profile will refuse to start with an
> "Incompatible mods found" dialog. Re-run the installer and pick 0.20.1,
> then switch to the new `fabric-loader-0.20.1-26.3` profile in the launcher.

1. Download **`redstonelogics-0.1.0.jar`** from
   **[the latest release](https://github.com/adhyys07/logics/releases/latest)**
   ([direct link](https://github.com/adhyys07/logics/releases/download/v0.1.0/redstonelogics-0.1.0.jar)).
2. Put it in your `mods` folder, next to Fabric API. Nothing to configure.
3. Launch Minecraft, load any world, and look at a redstone build.
4. Press **V** to pick the circuit, then **K** to open the analyser.

> The `-desktop.jar` on the same page is **not a mod** — don't put it in `mods`.
> It's a standalone viewer: `java -jar redstonelogics-0.1.0-desktop.jar`.

The analyser has five tabs: **Diagram** (click a gate, the real blocks light up
in the world), **Simulate**, **Tables** (truth table + timing), **Compare**
against a circuit you saved earlier, and **Export**.

Prefer commands? With cheats on: `/rlogic pos1`, `pos2`, `scan`, `export all`.

---

## Reviewer's guide

Two routes. The first proves the analysis is correct and takes a minute. The
second proves the mod works in game and takes about ten.

### Route 1 — verify the logic, no Minecraft needed

```bash
git clone https://github.com/adhyys07/logics
cd logics
./gradlew selfTest
```

This builds eight known circuits in memory and checks each recovered function
against its expected logic. Expected last line:

```
8/8 circuits matched their expected logic
```

It also writes every export format for all eight circuits to `build/selftest`
(72 files). Open `torch_and.svg` or `sr_latch.report.md` to see real output
without launching the game.

Needs JDK 25 on your PATH. Nothing else — no Minecraft, no Fabric, no account.

### Route 2 — run it in Minecraft

**Option A: from source (easiest, nothing to install).**

```bash
./gradlew runClient
```

Launches Minecraft 26.3 with Fabric, Fabric API and the mod already wired up.
First run downloads game assets and takes a few minutes. No launcher setup, no
Fabric installer, no mods folder. Exports go to `run/redstonelogics/`.

**Option B: as a user would, to test the released jar.**

1. Run the [Fabric installer](https://fabricmc.net/use/) → Minecraft **26.3**,
   Loader **0.20.1** → Install. If the loader dropdown offers older builds, you
   have *Show unstable loader versions* ticked; untick it.
2. In the Minecraft Launcher, select the **`fabric-loader-0.20.1-26.3`**
   profile. Installing a loader creates a *new* profile — if you skip this you
   will relaunch the old one.
3. Put two jars in `%APPDATA%\.minecraft\mods\` (macOS:
   `~/Library/Application Support/minecraft/mods/`):
   - [`fabric-api`](https://modrinth.com/mod/fabric-api/versions) for 26.3
   - [`redstonelogics-0.1.0.jar`](https://github.com/adhyys07/logics/releases/download/v0.1.0/redstonelogics-0.1.0.jar)
4. Play. Java is **not** a separate install — Minecraft 26.3 ships its own
   Java 25 runtime, which is what this mod needs.

### Once it is running — what to check

Create a **Creative** world with cheats on, then:

**It loaded.** `Options → Controls → Key Binds` shows a **RedstoneLogics**
category with *Open circuit analyser* (K) and *Pick circuit under cursor* (V).

**It reads a circuit.** Build each of these, look at any block in it, press
**V** then **K**, and read the **Tables** tab:

| Build | Expected |
|---|---|
| lever on a block → torch on the **side** of that block → lamp | `Y0 = NOT A` |
| two levers → one dust line → torch → lamp | `Y0 = NOT (A OR B)` |
| lever → repeater set to 3 ticks → lamp | `Y0 = A`, critical path 3 ticks |

**It traces back to the world.** On the **Diagram** tab, click a gate — the
blocks it came from highlight in the world.

**It exports.** **Export → Everything** writes ~14 files to
`<game folder>/redstonelogics/`. Open the SVG in a browser.

---

## What it recognises

NOT · AND · OR · NAND · NOR · XOR · XNOR · INHIBIT · buffers · delays · SR latches

Structurally: full adder · half adder · 2:1 mux · gated latch — collapsed into
one box each on large builds.

Torches are NOT gates. Repeaters are delays and keep their tick count.
Comparators are comparison or subtraction, including ones reading a chest.
Observers are edge detectors. Anything it can't name is still drawn with its
minimised expression underneath; signals too wide for a truth table go through
a decision diagram instead.

**Exports:** SVG · PNG · Verilog (+ testbench) · KiCad · Logisim · Graphviz ·
Mermaid · CSV · Arduino sketch · Markdown report · JSON.
**Imports:** `.rlogic.json` snapshots, Sponge `.schem`, Litematica `.litematic`.

---


AI was used throughout, reviewed before committing. Licence: **CC0 1.0** —
public domain.
