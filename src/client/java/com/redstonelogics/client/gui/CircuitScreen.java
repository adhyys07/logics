package com.redstonelogics.client.gui;

import com.redstonelogics.client.ClientAnalysis;
import com.redstonelogics.client.ClientSelection;
import com.redstonelogics.client.RedstoneLogicsClient;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * The in-game analyser. Four pages over one analysis: the diagram, an
 * interactive simulator, the truth table with timing, and the exports.
 */
public class CircuitScreen extends Screen {

    /** One page of the analyser. */
    public interface Tab {

        String title();

        /** Add any widgets. Called on open and whenever the active tab changes. */
        default void layout(CircuitScreen screen, int x, int y, int width, int height) {
        }

        void render(GuiGraphicsExtractor g, int x, int y, int width, int height,
                int mouseX, int mouseY);

        default boolean click(double mouseX, double mouseY, int button) {
            return false;
        }

        default boolean drag(double mouseX, double mouseY, double dragX, double dragY) {
            return false;
        }

        default boolean scroll(double mouseX, double mouseY, double amount) {
            return false;
        }
    }

    static final int BG = 0xF0101014;
    static final int PANEL = 0xFF1B1B22;
    static final int BORDER = 0xFF3A3A48;
    static final int TEXT = 0xFFE6E6EE;
    static final int DIM = 0xFF8A8A9A;
    static final int ACCENT = 0xFFE4604A;
    static final int OK = 0xFF5FBF7F;
    static final int WARN = 0xFFE0B050;

    private static final int TOP = 30;
    private static final int BAR = 20;

    private final List<Tab> tabs = new ArrayList<>();
    private int active;

    private int contentX;
    private int contentY;
    private int contentW;
    private int contentH;

    public CircuitScreen() {
        super(Component.literal("RedstoneLogics"));
        tabs.add(new DiagramTab());
        tabs.add(new SimulatorTab());
        tabs.add(new TablesTab());
        tabs.add(new ExportTab());
    }

    /** Keeps the world ticking behind the screen so live values keep moving. */
    @Override
    public boolean isPauseScreen() {
        return false;
    }

    /**
     * Lets a tab register a widget. addRenderableWidget is protected on Screen,
     * and protected access does not reach across instances, so a tab cannot
     * call it directly even from this package.
     */
    public Button addButton(Button button) {
        return addRenderableWidget(button);
    }

    @Override
    protected void init() {
        contentX = 8;
        contentY = TOP + BAR + 6;
        contentW = this.width - 16;
        contentH = this.height - contentY - 8;

        int x = 8;
        for (int i = 0; i < tabs.size(); i++) {
            final int index = i;
            Tab tab = tabs.get(i);
            int w = this.font.width(tab.title()) + 16;
            addRenderableWidget(Button.builder(Component.literal(tab.title()), b -> {
                if (active != index) {
                    active = index;
                    rebuild();
                }
            }).bounds(x, TOP, w, BAR).build());
            x += w + 4;
        }

        addRenderableWidget(Button.builder(Component.literal("Pick"), b -> {
            RedstoneLogicsClient.pickAndScan(Minecraft.getInstance());
            rebuild();
        }).bounds(this.width - 172, TOP, 48, BAR).build());

        addRenderableWidget(Button.builder(Component.literal("Rescan"), b -> {
            ClientAnalysis.rescan();
            rebuild();
        }).bounds(this.width - 120, TOP, 58, BAR).build());

        addRenderableWidget(Button.builder(Component.literal("Close"), b -> onClose())
                .bounds(this.width - 58, TOP, 50, BAR).build());

        tabs.get(active).layout(this, contentX, contentY, contentW, contentH);
    }

    /** Rebuilds every widget, so the newly active tab can add its own. */
    void rebuild() {
        this.clearWidgets();
        this.init();
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY,
            float partialTick) {
        g.fill(0, 0, this.width, this.height, BG);

        g.text(this.font, "RedstoneLogics", 8, 10, TEXT);
        int statusX = 8 + this.font.width("RedstoneLogics") + 10;
        g.text(this.font, status(), statusX, 10, statusColour());

        g.fill(contentX, contentY, contentX + contentW, contentY + contentH, PANEL);
        box(g, contentX, contentY, contentW, contentH, BORDER);

        g.enableScissor(contentX + 1, contentY + 1,
                contentX + contentW - 1, contentY + contentH - 1);
        tabs.get(active).render(g, contentX + 1, contentY + 1,
                contentW - 2, contentH - 2, mouseX, mouseY);
        g.disableScissor();

        super.extractRenderState(g, mouseX, mouseY, partialTick);

        // Underline the active tab, after the widgets so it sits on top.
        int x = 8;
        for (int i = 0; i < tabs.size(); i++) {
            int w = this.font.width(tabs.get(i).title()) + 16;
            if (i == active) {
                g.fill(x, TOP + BAR - 2, x + w, TOP + BAR, ACCENT);
            }
            x += w + 4;
        }
    }

    private String status() {
        return switch (ClientAnalysis.state()) {
            case EMPTY -> ClientSelection.complete()
                    ? ClientSelection.volume() + " blocks selected - press Rescan"
                    : "nothing selected - press Pick while looking at a build";
            case SCANNING -> "analysing...";
            case READY -> ClientAnalysis.result().summary();
            case FAILED -> String.valueOf(ClientAnalysis.error());
        };
    }

    private int statusColour() {
        return switch (ClientAnalysis.state()) {
            case READY -> OK;
            case FAILED -> ACCENT;
            case SCANNING -> WARN;
            case EMPTY -> DIM;
        };
    }

    /**
     * A one pixel border. GuiGraphicsExtractor has its own outline(int, int,
     * int, int, int), but whether it takes width and height or a second corner
     * is not obvious from the signature, so this stays explicit.
     */
    static void box(GuiGraphicsExtractor g, int x, int y, int w, int h, int colour) {
        g.fill(x, y, x + w, y + 1, colour);
        g.fill(x, y + h - 1, x + w, y + h, colour);
        g.fill(x, y, x + 1, y + h, colour);
        g.fill(x + w - 1, y, x + w, y + h, colour);
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        if (inContent(event.x(), event.y())
                && tabs.get(active).click(event.x(), event.y(), event.button())) {
            return true;
        }
        return super.mouseClicked(event, doubleClick);
    }

    @Override
    public boolean mouseDragged(MouseButtonEvent event, double dragX, double dragY) {
        if (inContent(event.x(), event.y())
                && tabs.get(active).drag(event.x(), event.y(), dragX, dragY)) {
            return true;
        }
        return super.mouseDragged(event, dragX, dragY);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontal,
            double vertical) {
        if (inContent(mouseX, mouseY) && tabs.get(active).scroll(mouseX, mouseY, vertical)) {
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, horizontal, vertical);
    }

    private boolean inContent(double mouseX, double mouseY) {
        return mouseX >= contentX && mouseX < contentX + contentW
                && mouseY >= contentY && mouseY < contentY + contentH;
    }
}
