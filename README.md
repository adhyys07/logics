# RedstoneLogics

Point it at a redstone build. It tells you what the circuit *does*.

It reads the blocks, follows the signal, and works out the boolean logic
behind them. You get a gate diagram, a truth table, and the timing.

**You build it → it reads it back.**

It does not build circuits. Only reads them.

```
23 blocks  ->  6 components  ->  4 gates

A ──┐
    ├─ AND ── Y0        Y0 = A AND B
B ──┘                   critical path: 2 redstone ticks
```

---

## Download

**[Download the latest release](https://github.com/adhyys07/logics/releases/latest)**
&nbsp;·&nbsp;
[v0.1.0 jar direct](https://github.com/adhyys07/logics/releases/download/v0.1.0/redstonelogics-0.1.0.jar)

You need:

- Minecraft **26.3**
- [Fabric Loader](https://fabricmc.net/use/) **0.20.1** or newer
- [Fabric API](https://modrinth.com/mod/fabric-api)
- Java **25**

Grab `redstonelogics-0.1.0.jar` and put it in your `mods` folder, next to
Fabric API. Nothing to configure.

> `redstonelogics-0.1.0-desktop.jar` on the same release page is **not a mod** -
> don't put it in `mods`. It's the standalone viewer, run with
> `java -jar redstonelogics-0.1.0-desktop.jar`.

A Modrinth listing is coming; for now releases here are the way to get it.

---

## What You Do

1. Look at any part of a redstone build and press **V**. It follows the
   wiring outwards and works out where the circuit ends.
2. Press **K**.

That's it. No commands, no cheats — the whole analysis runs on your client.

Five pages: the **diagram** (click a gate, the real blocks light up),
a **simulator**, the **tables and timing**, **compare** against a circuit you
saved earlier, and **export**.

If you'd rather type, and have cheats on: `/rlogic pos1`, `pos2`, `scan`,
`export all`.

---

## What It Knows

NOT · AND · OR · NAND · NOR · XOR · XNOR · INHIBIT · buffers · delays · SR latches

…and, structurally: full adder · half adder · 2:1 mux · gated latch. On a big
build those collapse into one box each, so a row of adders reads as a row of
adders rather than a wall of gates.

Torches are NOT gates. Repeaters are delays and keep their tick count.
Comparators are comparison or subtraction, including ones reading a chest.
Observers are edge detectors. Levers and buttons are inputs. Anything it can't
name is still drawn, with its expression written underneath — wide signals
included, since those go through a decision diagram instead of a truth table.

## Exports

SVG · PNG · Verilog (+ testbench) · KiCad netlist · Logisim · Graphviz ·
Mermaid · CSV · Arduino sketch · Markdown report · JSON

Written to `<game folder>/redstonelogics/`.

There's also a desktop app for looking at circuits away from the game, and
`webapp/index.html`, a single static page you can drop an export onto. Both
read `.rlogic.json` snapshots, Sponge `.schem`, and Litematica `.litematic`.

---

## 🛠 Running Locally

```bash
git clone https://github.com/<your-repo>/redstonelogics
cd redstonelogics
./gradlew build          # the mod jar, in build/libs; runs selfTest too
./gradlew desktopJar     # the standalone app
./gradlew runClient      # launch Minecraft with the mod loaded
```

`selfTest` dumps every export format into `build/selftest`, which is the
quickest way to see what the output looks like without starting the game.

---

## How to Set It up?


## For Transparency

This is early, and the honest version is worth more to you than a tidy one.

The analysis core is well exercised: eight known circuits are checked against
their expected logic on every build, and the decision diagram and its
sum-of-products cover are both verified against brute force. **The in-game
scanning path has had very little real-world use.** If a result surprises you,
suspect the tool first.

Known gaps:

- **Observers** pulse correctly in the simulator, but the diagram carries the
  pulse as a free input — it shows what you do with the pulse, not when it
  arrives.
- **Quasi-connectivity** is modelled from a snapshot. A real build only acts
  on it when a block update arrives, and one snapshot can't see that.
- **Comparators reading containers** become a free 0–15 input. It can't know
  what's in the chest.
- **Locked repeaters** are reported as having no fixed function. Honest, not
  useful.
- A cross-coupled latch has two stable states and this picks neither.
- **Litematica import** is round-tripped against our own writer, not yet
  against a file saved by Litematica itself.
- Selections cap at 200,000 blocks.

AI was used throughout, reviewed before committing.