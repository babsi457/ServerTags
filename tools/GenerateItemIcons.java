import com.google.gson.*;

import java.awt.image.BufferedImage;
import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.zip.*;

import javax.imageio.ImageIO;

/** Renders transparent inventory icons from Vanilla block models. */
public class GenerateItemIcons {
    record Face(double[] uv, String texture) {}

    record Box(double[] min, double[] max, Map<Integer, Face> faces) {}

    static ZipFile client;
    static Map<String, BufferedImage> textures = new HashMap<>();

    public static void main(String[] args) throws Exception {
        try (ZipFile archive = new ZipFile(args[0])) {
            client = archive;
            Path output = Path.of(args[1]);
            Files.createDirectories(output);
            render(
                    List.of(cube("minecraft:block/oak_log_top", "minecraft:block/oak_log")),
                    output.resolve("oak_log.png"));
            JsonObject model;
            try (Reader reader =
                    new InputStreamReader(
                            client.getInputStream(
                                    client.getEntry(
                                            "assets/minecraft/models/block/dragon_egg.json")))) {
                model = JsonParser.parseReader(reader).getAsJsonObject();
            }
            Map<String, String> refs = new HashMap<>();
            model.getAsJsonObject("textures")
                    .entrySet()
                    .forEach(e -> refs.put(e.getKey(), e.getValue().getAsString()));
            List<Box> boxes = new ArrayList<>();
            for (JsonElement element : model.getAsJsonArray("elements")) {
                JsonObject obj = element.getAsJsonObject();
                Map<Integer, Face> faces = new HashMap<>();
                for (var axis : Map.of(0, "east", 1, "up", 2, "south").entrySet()) {
                    JsonObject face = obj.getAsJsonObject("faces").getAsJsonObject(axis.getValue());
                    if (face == null) continue;
                    String texture = face.get("texture").getAsString();
                    if (texture.startsWith("#")) texture = refs.get(texture.substring(1));
                    faces.put(axis.getKey(), new Face(vector(face.getAsJsonArray("uv")), texture));
                }
                boxes.add(
                        new Box(
                                vector(obj.getAsJsonArray("from")),
                                vector(obj.getAsJsonArray("to")),
                                faces));
            }
            render(boxes, output.resolve("dragon_egg.png"));
        }
    }

    static Box cube(String top, String side) {
        return new Box(
                new double[] {0, 0, 0},
                new double[] {16, 16, 16},
                Map.of(
                        0,
                        new Face(new double[] {0, 0, 16, 16}, side),
                        1,
                        new Face(new double[] {0, 0, 16, 16}, top),
                        2,
                        new Face(new double[] {0, 0, 16, 16}, side)));
    }

    static double[] vector(JsonArray array) {
        double[] result = new double[array.size()];
        for (int i = 0; i < result.length; i++) result[i] = array.get(i).getAsDouble();
        return result;
    }

    static void render(List<Box> boxes, Path output) throws Exception {
        int size = 64;
        BufferedImage image = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);
        double[] view = {Math.sqrt(.375), .5, Math.sqrt(.375)},
                right = {Math.sqrt(.5), 0, -Math.sqrt(.5)},
                up = {-Math.sqrt(.125), Math.sqrt(.75), -Math.sqrt(.125)};
        for (int py = 0; py < size; py++)
            for (int px = 0; px < size; px++) {
                double sx = (px + .5 - size / 2.0) * 26 / size,
                        sy = (size / 2.0 - py - .5) * 26 / size;
                double[] origin = new double[3];
                for (int i = 0; i < 3; i++)
                    origin[i] = 8 + view[i] * 60 + right[i] * sx + up[i] * sy;
                double best = Double.POSITIVE_INFINITY;
                Box hit = null;
                int axis = -1;
                for (Box box : boxes) {
                    double near = 0, far = Double.POSITIVE_INFINITY;
                    int face = -1;
                    for (int i = 0; i < 3; i++) {
                        double a = (origin[i] - box.max[i]) / view[i],
                                b = (origin[i] - box.min[i]) / view[i];
                        if (a > near) {
                            near = a;
                            face = i;
                        }
                        far = Math.min(far, b);
                    }
                    if (near <= far && near < best && box.faces.containsKey(face)) {
                        best = near;
                        hit = box;
                        axis = face;
                    }
                }
                if (hit == null) continue;
                double[] p = new double[3];
                for (int i = 0; i < 3; i++) p[i] = origin[i] - view[i] * best;
                Face face = hit.faces.get(axis);
                double u, v;
                if (axis == 1) {
                    u = (p[0] - hit.min[0]) / (hit.max[0] - hit.min[0]);
                    v = (p[2] - hit.min[2]) / (hit.max[2] - hit.min[2]);
                } else {
                    int horizontal = axis == 0 ? 2 : 0;
                    u =
                            (p[horizontal] - hit.min[horizontal])
                                    / (hit.max[horizontal] - hit.min[horizontal]);
                    if (axis == 0) u = 1 - u;
                    v = (hit.max[1] - p[1]) / (hit.max[1] - hit.min[1]);
                }
                BufferedImage texture = texture(face.texture);
                double tu = face.uv[0] + u * (face.uv[2] - face.uv[0]),
                        tv = face.uv[1] + v * (face.uv[3] - face.uv[1]);
                int color =
                        texture.getRGB(
                                Math.clamp(
                                        (int) (tu * texture.getWidth() / 16),
                                        0,
                                        texture.getWidth() - 1),
                                Math.clamp(
                                        (int) (tv * texture.getHeight() / 16),
                                        0,
                                        texture.getHeight() - 1));
                double light = axis == 1 ? 1 : axis == 0 ? .65 : .82;
                image.setRGB(
                        px,
                        py,
                        (color & 0xff000000)
                                | ((int) (((color >> 16) & 255) * light) << 16)
                                | ((int) (((color >> 8) & 255) * light) << 8)
                                | (int) ((color & 255) * light));
            }
        ImageIO.write(image, "PNG", output.toFile());
        System.out.println("Itemgrafik ohne Rahmen: " + output);
    }

    static BufferedImage texture(String id) throws IOException {
        if (!id.contains(":")) id = "minecraft:" + id;
        if (textures.containsKey(id)) return textures.get(id);
        String[] parts = id.split(":", 2);
        var entry = client.getEntry("assets/" + parts[0] + "/textures/" + parts[1] + ".png");
        if (entry == null) throw new FileNotFoundException(id);
        BufferedImage image;
        try (var in = client.getInputStream(entry)) {
            image = ImageIO.read(in);
        }
        textures.put(id, image);
        return image;
    }
}
