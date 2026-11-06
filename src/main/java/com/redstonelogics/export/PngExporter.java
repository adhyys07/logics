package com.redstonelogics.export;

import com.redstonelogics.recognize.LogicDiagram;

import javax.imageio.ImageIO;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;

public final class PngExporter {
    
    private PngExporter() {
    }

    public static BufferedImage render(LogicDiagram diagram, String caption,
                                       DiagramPainter.Palette palette, double scale) {
        DiagramLayout layout = DiagramLayout.of(diagram);
        int w = (int) Math.ceil(Math.max(layout.width(), 320) * scale);
        int h = (int) Math.ceil((Math.max(layout.height(), 160) + 46) * scale);
        BufferedImage image = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
        Graphics2D g2 = image.createGraphics();
        g2.scale(scale, scale);
        DiagramPainter.paint(g2, layout, palette, caption);
        g2.dispose();
        return image;
    }

    public static byte[] toBytes(LogicDiagram diagram, String caption,
                                 DiagramPainter.Palette palette, double scale) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(render(diagram, caption, palette, scale), "png", out);
        return out.toByteArray();
    }
}

