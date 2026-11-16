package com.redstonelogics.export;

import com.redstonelogics.analysis.TimingAnalysis;
import com.redstonelogics.core.Pos;
import com.redstonelogics.mapper.RedstoneLogicMapper;
import com.redstonelogics.recognize.LogicDiagram;

import java.util.HashSet;
import java.util.Set;

/**
 * Maps the netlist's critical path onto diagram gate ids, so the SVG and the
 * PNG can highlight the same thing.
 *
 * <p>Lived in SelfTest, which meant the in-game and desktop exports quietly
 * shipped without it.
 */
public final class CriticalPath {

    private CriticalPath() {
    }

    public static Set<Integer> gates(RedstoneLogicMapper mapped) {
        Set<Integer> hot = new HashSet<>();
        for (int element : TimingAnalysis.of(mapped.netlist()).criticalPath()) {
            Pos at = mapped.netlist().element(element).pos;
            for (LogicDiagram.Gate g : mapped.diagram().gates()) {
                // Synthetic gates from emit() carry no elements, so fall back to
                // position: they are created at the position they drive.
                if (g.elements.contains(element)
                        || (g.origin != null && g.origin.equals(at))) {
                    hot.add(g.id);
                }
            }
        }
        return hot;
    }
}
