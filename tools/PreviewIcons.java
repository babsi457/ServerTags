import com.google.gson.*;

import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.*;
import java.nio.file.*;
import java.util.zip.*;

import javax.imageio.ImageIO;

/** Creates an icon sheet from the registry and resource-pack assets. */
public class PreviewIcons {
    public static void main(String[] args) throws Exception {
        try (ZipFile pack = new ZipFile(args[0]);
                ZipFile client = new ZipFile(args[1])) {
            JsonObject icons =
                    JsonParser.parseString(Files.readString(Path.of(args[2]))).getAsJsonObject();
            int columns = 8, cellWidth = 150, cellHeight = 115;
            BufferedImage out =
                    new BufferedImage(
                            columns * cellWidth,
                            65 + ((icons.size() + columns - 1) / columns) * cellHeight,
                            BufferedImage.TYPE_INT_RGB);
            Graphics2D g = out.createGraphics();
            g.setColor(new Color(24, 29, 39));
            g.fillRect(0, 0, out.getWidth(), out.getHeight());
            g.setColor(new Color(230, 237, 248));
            g.setFont(new Font("SansSerif", Font.BOLD, 25));
            g.drawString("ServerTags · " + icons.size() + " Item-Icons ohne Rahmen", 24, 39);
            g.setRenderingHint(
                    RenderingHints.KEY_INTERPOLATION,
                    RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
            int index = 0;
            for (var e : icons.entrySet()) {
                String[] id =
                        e.getValue().getAsJsonObject().get("texture").getAsString().split(":", 2);
                ZipFile archive = id[0].equals("minecraft") ? client : pack;
                var entry = archive.getEntry("assets/" + id[0] + "/textures/" + id[1]);
                if (entry == null) throw new FileNotFoundException(e.getKey());
                BufferedImage image;
                try (var stream = archive.getInputStream(entry)) {
                    image = ImageIO.read(stream);
                }
                int x = (index % columns) * cellWidth, y = 65 + (index / columns) * cellHeight;
                g.drawImage(image, x + (cellWidth - 48) / 2, y + 3, 48, 48, null);
                g.setFont(new Font("SansSerif", Font.PLAIN, 12));
                g.setColor(new Color(204, 216, 235));
                String label = e.getKey();
                int width = g.getFontMetrics().stringWidth(label);
                if (width < cellWidth - 10)
                    g.drawString(label, x + (cellWidth - width) / 2, y + 77);
                else {
                    int at = label.lastIndexOf('_');
                    String first = label.substring(0, at), last = label.substring(at + 1);
                    g.drawString(
                            first,
                            x + (cellWidth - g.getFontMetrics().stringWidth(first)) / 2,
                            y + 72);
                    g.drawString(
                            last,
                            x + (cellWidth - g.getFontMetrics().stringWidth(last)) / 2,
                            y + 89);
                }
                index++;
            }
            g.dispose();
            Path target = Path.of(args[3]);
            Files.createDirectories(target.getParent());
            ImageIO.write(out, "PNG", target.toFile());
        }
    }
}
