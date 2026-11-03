package com.redstonelogics.recognize;

/** The symbols a {@link LogicDiagram} can contain. */
public enum GateType {
    INPUT("IN", 0),
    OUTPUT("OUT", 1),
    CONST_0("0", 0),
    CONST_1("1", 0),
    BUFFER("BUF", 1),
    DELAY("DELAY", 1),
    NOT("NOT", 1),
    AND("AND", -1),
    OR("OR", -1),
    NAND("NAND", -1),
    NOR("NOR", -1),
    XOR("XOR", -1),
    XNOR("XNOR", -1),
    /** a AND NOT b, the shape a subtract-mode comparator produces. */
    INHIBIT("INH", 2),
    /** Cross-coupled inverter pair: set, reset, Q and inverted Q. */
    SR_LATCH("SR", 2),
    /** A feedback loop that is not a recognised latch. */
    FEEDBACK("STATE", -1),
    /** Matched nothing; carries a minimised expression in its note instead. */
    GENERIC("LOGIC", -1);

    public final String shortName;
    /** Fixed input count, or -1 when the gate takes any number. */
    public final int arity;

    GateType(String shortName, int arity) {
        this.shortName = shortName;
        this.arity = arity;
    }

    public boolean isTerminal() {
        return this == INPUT || this == OUTPUT || this == CONST_0 || this == CONST_1;
    }

    public boolean isSequential() {
        return this == SR_LATCH || this == FEEDBACK;
    }

    /** Drawn with an inversion bubble on the output. */
    public boolean negatedOutput() {
        return this == NOT || this == NAND || this == NOR || this == XNOR;
    }

    /** The symbol body to draw, ignoring any output bubble. */
    public GateType body() {
        return switch (this) {
            case NAND -> AND;
            case NOR -> OR;
            case XNOR -> XOR;
            default -> this;
        };
    }
}
