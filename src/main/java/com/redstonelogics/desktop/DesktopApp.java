package com.redstonelogics.desktop;

import com.redstonelogics.core.RegionSnapshot;
import com.redstonelogics.export.ArduinoExporter;
import com.redstonelogics.export.DiagramLayout;
import com.redstonelogics.export.DiagramPainter;
import com.redstonelogics.export.NetlistReport;
import com.redstonelogics.export.PngExporter;
import com.redstonelogics.export.SvgExporter;
import com.redstonelogics.mapper.RedstoneLogicMapper;

import javax.imageio.ImageIO;
import javax.swing.JFileChooser;
import javax.swing.JFrame;
import javax.swing.JMenu;
import javax.swing.JMenuBar;
import javax.swing.JMenuItem;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSplitPane;
import javax.swing.JTextArea;
import javax.swing.SwingUtilities;
import javax.swing.filechooser.FileNameExtensionFilter;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Font;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

public final class DesktopApp extends JFrame {

    private final Canvas canvas = new Canvas();
    private final JTextArea details = new JTextArea();
    private RedstoneLogicMapper mapped;
    private String title = "no circuit loaded";
    private boolean dark;

    private DesktopApp() {
        super("RedstoneLogics");
        setDefaultCloseOperation(EXIT_ON_CLOSE);
        setSize(1180, 760);

        details.setEditable(false);
        details.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));
        details.setText("File > Open to load a .rlogic.json snapshot exported with\n"
                + "/rlogic export json");

        JSplitPane split = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT,
                new JScrollPane(canvas), new JScrollPane(details));
        split.setResizeWeight(0.68);
        add(split, BorderLayout.CENTER);
        setJMenuBar(menu());
    }

    private JMenuBar menu() {
        JMenuBar bar = new JMenuBar();

        JMenu file = new JMenu("File");
        file.add(item("Open snapshot...", this::open));
        file.addSeparator();
        file.add(item("Export SVG...", () -> save("svg", "Scalable Vector Graphics",
                () -> SvgExporter.render(mapped.diagram(), title).getBytes(StandardCharsets.UTF_8))));
        file.add(item("Export PNG...", () -> save("png", "PNG image", () -> {
            try {
                return PngExporter.toBytes(mapped.diagram(), title, palette(), 2.0);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        })));
        file.add(item("Export Arduino sketch...", () -> save("ino", "Arduino sketch",
                () -> ArduinoExporter.render(mapped.diagram(), title)
                        .getBytes(StandardCharsets.UTF_8))));
        file.add(item("Export report...", () -> save("md", "Markdown report",
                () -> NetlistReport.render(mapped, title).getBytes(StandardCharsets.UTF_8))));
        bar.add(file);

        JMenu view = new JMenu("View");
        view.add(item("Toggle dark background", () -> {
            dark = !dark;
            canvas.repaint();
        }));
        bar.add(view);
        return bar;
    }

    private JMenuItem item(String label, Runnable action) {
        JMenuItem mi = new JMenuItem(label);
        mi.addActionListener(e -> {
            try {
                action.run();
            } catch (RuntimeException ex) {
                JOptionPane.showMessageDialog(this, String.valueOf(ex.getMessage()),
                        "Failed", JOptionPane.ERROR_MESSAGE);
            }
        });
        return mi;
    }

    private DiagramPainter.Palette palette() {
        return dark ? DiagramPainter.Palette.DARK : DiagramPainter.Palette.LIGHT;
    }

    private void open() {
        JFileChooser chooser = new JFileChooser();
        chooser.setFileFilter(new FileNameExtensionFilter("Snapshot (*.json)", "json"));
        if (chooser.showOpenDialog(this) != JFileChooser.APPROVE_OPTION) {
            return;
        }
        File f = chooser.getSelectedFile();
        try {
            RegionSnapshot snapshot = RegionSnapshot.fromJson(
                    Files.readString(f.toPath(), StandardCharsets.UTF_8));
            mapped = RedstoneLogicMapper.map(snapshot);
            title = f.getName() + "  " + mapped.summary();
            setTitle("RedstoneLogics - " + f.getName());
            details.setText(NetlistReport.render(mapped, title));
            details.setCaretPosition(0);
            canvas.reload();
        } catch (IOException | IllegalArgumentException e) {
            JOptionPane.showMessageDialog(this, "Could not read that snapshot:\n" + e.getMessage(),
                    "Open failed", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void save(String extension, String description, java.util.function.Supplier<byte[]> body) {
        if (mapped == null) {
            JOptionPane.showMessageDialog(this, "Open a snapshot first.");
            return;
        }
        JFileChooser chooser = new JFileChooser();
        chooser.setFileFilter(new FileNameExtensionFilter(description, extension));
        chooser.setSelectedFile(new File("circuit." + extension));
        if (chooser.showSaveDialog(this) != JFileChooser.APPROVE_OPTION) {
            return;
        }
        try {
            Files.write(chooser.getSelectedFile().toPath(), body.get());
        } catch (IOException e) {
            JOptionPane.showMessageDialog(this, "Write failed: " + e.getMessage());
        }
    }

    private final class Canvas extends JPanel {
        private DiagramLayout layout;

        void reload() {
            layout = mapped == null ? null : DiagramLayout.of(mapped.diagram());
            if (layout != null) {
                setPreferredSize(new Dimension((int) layout.width() + 20,
                        (int) layout.height() + 60));
            }
            revalidate();
            repaint();
        }

        @Override
        protected void paintComponent(Graphics g) {
            super.paintComponent(g);
            if (layout == null) {
                return;
            }
            DiagramPainter.paint((Graphics2D) g.create(), layout, palette(), title);
        }
    }
    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> new DesktopApp().setVisible(true));
    }
}
