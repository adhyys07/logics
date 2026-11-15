package com.redstonelogics;

import com.redstonelogics.analysis.BooleanAbstraction;
import com.redstonelogics.analysis.TruthTable;
import com.redstonelogics.core.BlockKind;
import com.redstonelogics.core.Direction;
import com.redstonelogics.core.Pos;
import com.redstonelogics.core.RegionSnapshot;
import com.redstonelogics.core.ScannedBlock;
import com.redstonelogics.export.DiagramPainter;
import com.redstonelogics.export.NetlistReport;
import com.redstonelogics.export.PngExporter;
import com.redstonelogics.export.SvgExporter;
import com.redstonelogics.graph.Netlist;
import com.redstonelogics.mapper.RedstoneLogicMapper;
import com.redstonelogics.recognize.LogicDiagram;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

/**
 * Builds known circuits in memory, runs the full analysis over each, and checks
 * the recovered boolean function against the logic the circuit is meant to
 * implement. Writes the snapshot, report and SVG diagram for every case.
 *
 * <p>No Minecraft and no JUnit on the classpath: {@code ./gradlew selfTest}.
 */
public final class SelfTest {

    private static final Path OUT = Path.of("build", "selftest");

    // ------------------------------------------------------------------ harness

    /** One circuit plus the logic it is supposed to implement. */
    private record Case(String name,
                        String description,
                        RegionSnapshot snapshot,
                        List<String> inputs,
                        Function<Map<String, Boolean>, Boolean> expected,
                        String expectedText) {
    }

    public static void main(String[] args) throws IOException {
        System.setProperty("java.awt.headless", "true");
        Files.createDirectories(OUT);

        List<Case> cases = List.of(
                notGate(), orGate(), repeaterDelay(), torchAnd(),
                lampBypass(), lampUnderWire());

        int failed = 0;
        for (Case c : cases) {
            if (!run(c)) {
                failed++;
            }
        }

        System.out.println();
        System.out.println("=".repeat(70));
        System.out.println("artifacts: " + OUT.toAbsolutePath());
        System.out.println((cases.size() - failed) + "/" + cases.size()
                + " circuits matched their expected logic");
        if (failed > 0) {
            System.out.println(failed + " FAILED");
            System.exit(1);
        }
    }

    private static boolean run(Case c) throws IOException {
        System.out.println();
        System.out.println("=".repeat(70));
        System.out.println(c.name() + " - " + c.description());
        System.out.println("expected: " + c.expectedText());
        System.out.println("-".repeat(70));

        RedstoneLogicMapper mapped = RedstoneLogicMapper.map(c.snapshot());
        System.out.println("  " + mapped.summary());

        System.out.println("  gates:");
        for (LogicDiagram.Gate g : mapped.diagram().gates()) {
            StringBuilder line = new StringBuilder("    #" + g.id + " "
                    + g.type.shortName + " " + g.label);
            if (!g.inputs.isEmpty()) {
                line.append(" <- ");
                for (int i = 0; i < g.inputs.size(); i++) {
                    line.append(i == 0 ? "" : ", ").append('#').append(g.inputs.get(i).gate());
                }
            }
            if (g.delayTicks != 0) {
                line.append("  [").append(g.delayTicks).append(" ticks]");
            }
            if (g.note != null && !g.note.isBlank()) {
                line.append("  (").append(g.note).append(')');
            }
            System.out.println(line);
        }

        for (String pattern : mapped.diagram().patterns()) {
            System.out.println("  pattern: " + pattern);
        }
        for (String warning : mapped.netlist().warnings()) {
            System.out.println("  warn: " + warning);
        }

        boolean ok = verify(mapped, c);

        String title = c.name() + "  " + mapped.summary();
        write(c.name() + ".snapshot.json", c.snapshot().toJson());
        write(c.name() + ".report.md", NetlistReport.render(mapped, title));
        write(c.name() + ".svg", SvgExporter.render(mapped.diagram(), title));
        Files.write(OUT.resolve(c.name() + ".png"),
                PngExporter.toBytes(mapped.diagram(), title, DiagramPainter.Palette.LIGHT, 2.0));

        System.out.println("  " + (ok ? "PASS" : "FAIL"));
        return ok;
    }

    /** Compares the recovered function for the first output against the expectation. */
    private static boolean verify(RedstoneLogicMapper mapped, Case c) {
        BooleanAbstraction logic = mapped.logic();
        List<Netlist.Element> outputs = mapped.netlist().outputs();
        if (outputs.isEmpty()) {
            System.out.println("  no output element found");
            return false;
        }
        Netlist.Element out = outputs.get(0);
        BooleanAbstraction.Fn fn = logic.function(out.id);
        if (!fn.known()) {
            System.out.println("  function for " + out.label + " could not be determined");
            return false;
        }

        List<String> names = logic.namesFor(fn);
        TruthTable table = fn.table();
        System.out.println("  recovered: " + out.label + " = " + table.toExpression(names));

        boolean ok = true;
        for (String name : names) {
            if (!c.inputs().contains(name)) {
                System.out.println("  unexpected variable " + name + " in the result");
                ok = false;
            }
        }

        // Enumerate the declared inputs rather than the table's own variables, so a
        // result that legitimately collapses to a constant still gets compared
        // against every input combination instead of being waved through.
        System.out.println("  " + String.join("  ", c.inputs()) + "  |  got  want");
        int count = c.inputs().size();
        for (int combo = 0; combo < (1 << count); combo++) {
            Map<String, Boolean> env = new LinkedHashMap<>();
            for (int i = 0; i < count; i++) {
                env.put(c.inputs().get(i), ((combo >>> i) & 1) != 0);
            }
            int row = 0;
            for (int v = 0; v < names.size(); v++) {
                if (env.getOrDefault(names.get(v), false)) {
                    row |= 1 << v;
                }
            }
            boolean got = table.get(row);
            boolean want = c.expected().apply(env);
            if (got != want) {
                ok = false;
            }
            StringBuilder bits = new StringBuilder();
            for (String in : c.inputs()) {
                bits.append(env.get(in) ? '1' : '0').append("  ");
            }
            System.out.println("  " + bits + "|   " + (got ? 1 : 0) + "     "
                    + (want ? 1 : 0) + (got != want ? "   <-- MISMATCH" : ""));
        }
        return ok;
    }

    private static void write(String file, String body) throws IOException {
        Files.writeString(OUT.resolve(file), body, StandardCharsets.UTF_8);
    }

    private static boolean in(Map<String, Boolean> env, String name) {
        return env.getOrDefault(name, false);
    }

    // ----------------------------------------------------------------- circuits

    /**
     * Lever on a block, wall torch on the same block, dust to a lamp.
     * The torch inverts whatever its support block carries.
     */
    private static Case notGate() {
        Build b = new Build();
        b.solid(0, 0, 0);                        // support for both lever and torch
        b.lever(0, 1, 0, Direction.DOWN);        // input A, mounted on top
        b.torch(1, 0, 0, Direction.WEST);        // wall torch on the east face
        b.solid(2, -1, 0).solid(3, -1, 0);       // floor under the dust
        b.dust(2, 0, 0).dust(3, 0, 0);
        b.lamp(4, 0, 0);                         // output Y0
        return new Case("not_gate", "lever -> block -> torch -> dust -> lamp",
                b.done(), List.of("A"), env -> !in(env, "A"), "Y0 = NOT A");
    }

    /** Two levers feeding one dust net: redstone merges with max, so the net is an OR. */
    private static Case orGate() {
        Build b = new Build();
        b.solid(0, 0, 0).solid(0, 0, 2);
        b.lever(0, 1, 0, Direction.DOWN);        // input A
        b.lever(0, 1, 2, Direction.DOWN);        // input B
        b.solid(1, 0, 0).solid(1, 0, 1).solid(1, 0, 2).solid(2, 0, 1).solid(3, 0, 1);
        b.dust(1, 1, 0).dust(1, 1, 1).dust(1, 1, 2).dust(2, 1, 1).dust(3, 1, 1);
        b.lamp(4, 1, 1);                         // output Y0
        return new Case("or_gate", "two levers onto a shared dust net -> lamp",
                b.done(), List.of("A", "B"),
                env -> in(env, "A") || in(env, "B"), "Y0 = A OR B");
    }

    /** Lever through a 3-tick repeater: logically a buffer, with delay recorded. */
    private static Case repeaterDelay() {
        Build b = new Build();
        b.solid(0, 0, 0);
        b.lever(0, 1, 0, Direction.DOWN);        // input A
        b.solid(1, 0, 0).solid(2, 0, 0).solid(3, 0, 0);
        b.dust(1, 1, 0);
        b.repeater(2, 1, 0, Direction.EAST, 3);  // front faces east
        b.dust(3, 1, 0);
        b.lamp(4, 1, 0);                         // output Y0
        return new Case("repeater_delay", "lever -> dust -> 3-tick repeater -> dust -> lamp",
                b.done(), List.of("A"), env -> in(env, "A"), "Y0 = A, delayed 3 ticks");
    }

    /**
     * The classic torch AND: invert each input, merge the two inverted signals on
     * one dust net, then invert again. NOT(NOT A OR NOT B) is A AND B.
     */
    private static Case torchAnd() {
        Build b = new Build();
        // Input A and its inverter.
        b.solid(0, 0, 0);
        b.lever(0, 1, 0, Direction.DOWN);        // input A
        b.torch(1, 0, 0, Direction.WEST);
        // Input B and its inverter.
        b.solid(0, 0, 4);
        b.lever(0, 1, 4, Direction.DOWN);        // input B
        b.torch(1, 0, 4, Direction.WEST);
        // Dust net joining both inverted signals, running east into the final block.
        for (int z = 0; z <= 4; z++) {
            b.solid(2, -1, z);
            b.dust(2, 0, z);
        }
        b.solid(3, -1, 2);
        b.dust(3, 0, 2);
        // Final inverter: a block driven by that dust, with a torch on top.
        b.solid(4, 0, 2);
        b.torch(4, 1, 2, Direction.DOWN);
        b.solid(5, 0, 2);
        b.dust(5, 1, 2);
        b.lamp(6, 1, 2);                         // output Y0
        return new Case("torch_and", "two torch inverters merged on dust, inverted again",
                b.done(), List.of("A", "B"),
                env -> in(env, "A") && in(env, "B"), "Y0 = A AND B");
    }

    /**
     * A lamp sitting beside a wire that runs straight past it. Dust is not a
     * signal source as far as a lamp is concerned, so the wire never connects
     * sideways into it: the lamp stays dark no matter what the lever does.
     */
    private static Case lampBypass() {
        Build b = new Build();
        b.solid(0, 0, 0);
        b.lever(0, 1, 0, Direction.DOWN);        // input A
        b.solid(1, 0, 0).solid(2, 0, 0).solid(3, 0, 0).solid(4, 0, 0);
        b.dust(1, 1, 0).dust(2, 1, 0).dust(3, 1, 0).dust(4, 1, 0);
        b.lamp(2, 1, 1);                         // output Y0, beside the middle of the run
        return new Case("lamp_bypass", "lamp alongside a wire that continues past it",
                b.done(), List.of("A"), env -> false, "Y0 = 0 (wire runs past, never into it)");
    }

    /**
     * The counterpart to {@link #lampBypass()}: dust climbing over a lamp still
     * powers it from above, so tightening the sideways rule must not make lamps
     * unpowerable.
     */
    private static Case lampUnderWire() {
        Build b = new Build();
        b.solid(0, 0, 0);
        b.lever(0, 1, 0, Direction.DOWN);        // input A
        b.solid(1, 0, 0);
        b.dust(1, 1, 0);
        b.lamp(2, 1, 0);                         // output Y0
        b.dust(2, 2, 0);                         // wire climbing over the lamp
        return new Case("lamp_under_wire", "dust running over the top of a lamp",
                b.done(), List.of("A"), env -> in(env, "A"), "Y0 = A (powered from above)");
    }

    // ------------------------------------------------------------------ builder

    /** Thin wrapper so the circuits above read like a parts list. */
    private static final class Build {
        private final RegionSnapshot.Builder b = RegionSnapshot.builder("selftest");

        Build solid(int x, int y, int z) {
            return put(x, y, z, ScannedBlock.solid("minecraft:stone"));
        }

        Build dust(int x, int y, int z) {
            return put(x, y, z, ScannedBlock.dust(0));
        }

        Build lever(int x, int y, int z, Direction towardsSupport) {
            return put(x, y, z, ScannedBlock.lever(towardsSupport, false));
        }

        Build torch(int x, int y, int z, Direction towardsSupport) {
            return put(x, y, z, ScannedBlock.torch(towardsSupport, true));
        }

        Build repeater(int x, int y, int z, Direction front, int delay) {
            return put(x, y, z, ScannedBlock.repeater(front, false, delay, false));
        }

        Build lamp(int x, int y, int z) {
            return put(x, y, z, ScannedBlock.of(BlockKind.LAMP, "minecraft:redstone_lamp"));
        }

        private Build put(int x, int y, int z, ScannedBlock block) {
            b.put(new Pos(x, y, z), block);
            return this;
        }

        RegionSnapshot done() {
            return b.build();
        }
    }

    private SelfTest() {
    }
}
