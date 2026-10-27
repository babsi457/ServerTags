import java.awt.*;
import java.awt.geom.*;
import java.awt.image.BufferedImage;
import java.nio.file.*;
import java.util.zip.*;

import javax.imageio.ImageIO;

/** Projects block textures from a local client JAR into an inventory icon. */
public class GenerateBlockIcon {
    public static void main(String[] args) throws Exception {
        try (ZipFile client = new ZipFile(args[0])) {
            BufferedImage top =
                    ImageIO.read(
                            client.getInputStream(
                                    client.getEntry(
                                            "assets/minecraft/textures/block/ancient_debris_top.png")));
            BufferedImage side =
                    ImageIO.read(
                            client.getInputStream(
                                    client.getEntry(
                                            "assets/minecraft/textures/block/ancient_debris_side.png")));
            BufferedImage out = new BufferedImage(32, 32, BufferedImage.TYPE_INT_ARGB);
            Graphics2D g = out.createGraphics();
            g.setRenderingHint(
                    RenderingHints.KEY_INTERPOLATION,
                    RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
            face(g, top, new AffineTransform(.875, .4375, -.875, .4375, 16, 1), 1);
            face(g, side, new AffineTransform(.875, .4375, 0, 1, 2, 8), .78f);
            face(g, side, new AffineTransform(.875, -.4375, 0, 1, 16, 15), .60f);
            g.dispose();
            Path target = Path.of(args[1]);
            Files.createDirectories(target.getParent());
            ImageIO.write(out, "PNG", target.toFile());
            System.out.println("Ancient-Debris-Inventarsymbol: " + target);
        }
    }

    static void face(Graphics2D g, BufferedImage source, AffineTransform transform, float light) {
        BufferedImage tinted = new BufferedImage(16, 16, BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < 16; y++)
            for (int x = 0; x < 16; x++) {
                int p = source.getRGB(x, y);
                tinted.setRGB(
                        x,
                        y,
                        (p & 0xff000000)
                                | ((int) (((p >> 16) & 255) * light) << 16)
                                | ((int) (((p >> 8) & 255) * light) << 8)
                                | (int) ((p & 255) * light));
            }
        g.drawImage(tinted, transform, null);
    }
}
