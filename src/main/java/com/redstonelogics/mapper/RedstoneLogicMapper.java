package com.redstonelogics.mapper;

import com.redstonelogics.analysis.BooleanAbstraction;
import com.redstonelogics.core.RegionSnapshot;
import com.redstonelogics.graph.Netlist;
import com.redstonelogics.graph.NetlistExtractor;
import com.redstonelogics.recognize.GateRecognizer;
import com.redstonelogics.recognize.LogicDiagram;


public final class RedstoneLogicMapper {

    private final RegionSnapshot snapshot;
    private final Netlist netlist;
    private final BooleanAbstraction logic;
    private final LogicDiagram diagram;

    private RedstoneLogicMapper(RegionSnapshot snapshot, Netlist netlist, BooleanAbstraction logic, LogicDiagram diagram) {
        this.snapshot = snapshot;
        this.netlist = netlist;
        this.logic = logic;
        this.diagram = diagram;
    }

    public static RedstoneLogicMapper map(RegionSnapshot snapshot) {
        Netlist netlist = NetlistExtractor.extract(snapshot);
        BooleanAbstraction logic = BooleanAbstraction.of(netlist);
        LogicDiagram diagram = GateRecognizer.recognize(logic);
        return new RedstoneLogicMapper(snapshot, netlist, logic, diagram);
    }

    public RegionSnapshot snapshot() {
        return snapshot;
    }

    public Netlist netlist() {
        return netlist;
    }
    
    public LogicDiagram diagram() {
        return diagram;
    }

    public  BooleanAbstraction logic() {
        return logic;
    }

    public String summary() {
        StringBuilder sb = new StringBuilder();
        sb.append(snapshot.size()).append(" blocks, ")
          .append(netlist.elements().size()).append(" components, ")
          .append(diagram.gates().size()).append(" gates");
        if (!diagram.patterns().isEmpty()) {
            sb.append("; recognised ").append(diagram.patterns().size()).append(" pattern(s)");
        }
        if (!diagram.notes().isEmpty()) {
            sb.append("; ").append(diagram.notes().size()).append(" note(s)");
        }
        return sb.toString();
    }
}