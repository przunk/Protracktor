/*
 * Generates every Protracktor launcher-icon resource from one geometry definition.
 *
 * Run from the repository root with:
 *
 *     java -Djava.awt.headless=true scripts/GenerateLauncherIcons.java [repository-root]
 *
 * The adaptive icon keeps the mark as a vector. Pre-Android-8 launchers receive antialiased PNGs
 * at their native densities, including a genuinely round variant with transparent corners.
 */

import java.awt.Color;
import java.awt.GradientPaint;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Path2D;
import java.awt.geom.Rectangle2D;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import javax.imageio.ImageIO;

public final class GenerateLauncherIcons {
    private static final double VIEWPORT = 108.0;
    private static final double MARK_SCALE = 0.82;
    private static final String BACKGROUND_HEX = "#05070C";
    private static final String FOREGROUND_HEX = "#00E7E8";
    private static final String DARK_CELL_HEX = "#00A9E8";
    private static final String LIGHT_CELL_HEX = "#D8FFFF";
    private static final Color BACKGROUND = Color.decode(BACKGROUND_HEX);
    private static final Color FOREGROUND = Color.decode(FOREGROUND_HEX);
    private static final Color DARK_CELL = Color.decode(DARK_CELL_HEX);
    private static final Color LIGHT_CELL = Color.decode(LIGHT_CELL_HEX);

    // Google Play's two fixed sizes. The store icon is the launcher icon at 512 px and opaque:
    // Play rounds the corners itself and refuses transparency, and an icon that differs from the
    // one on the phone is an icon somebody installs twice by accident.
    private static final int STORE_ICON_SIZE = 512;
    private static final int FEATURE_WIDTH = 1024;
    private static final int FEATURE_HEIGHT = 500;
    // The far corner of the feature graphic's wash, a few points off the background rather than a
    // different colour: enough to stop 1024x500 of flat near-black reading as a rendering fault.
    private static final Color FEATURE_WASH = Color.decode("#0B1220");

    private GenerateLauncherIcons() {}

    public static void main(String[] args) throws IOException {
        // The workshop has no display server; BufferedImage rendering is intentionally headless.
        System.setProperty("java.awt.headless", "true");
        if (args.length > 1) {
            throw new IllegalArgumentException("Usage: java scripts/GenerateLauncherIcons.java [repository-root]");
        }
        Path root = (args.length == 1 ? Path.of(args[0]) : Path.of(""))
                .toAbsolutePath()
                .normalize();
        Path resources = root.resolve("app/src/main/res");
        if (!Files.isDirectory(resources) || !Files.isRegularFile(root.resolve("settings.gradle.kts"))) {
            throw new IllegalStateException("Run this generator from the Protracktor repository root");
        }

        Logo logo = createLogo();
        writeVectorResources(resources, logo);

        Map<String, Integer> densities = new LinkedHashMap<>();
        densities.put("mdpi", 48);
        densities.put("hdpi", 72);
        densities.put("xhdpi", 96);
        densities.put("xxhdpi", 144);
        densities.put("xxxhdpi", 192);

        for (Map.Entry<String, Integer> density : densities.entrySet()) {
            Path directory = resources.resolve("mipmap-" + density.getKey());
            Files.createDirectories(directory);
            writePng(directory.resolve("ic_launcher.png"), density.getValue(), false, logo);
            writePng(directory.resolve("ic_launcher_round.png"), density.getValue(), true, logo);
        }

        Path artwork = root.resolve("artwork");
        Files.createDirectories(artwork);
        writePng(artwork.resolve("protracktor-launcher-icon-512.png"), 512, false, logo);

        Path graphics = root.resolve("store/graphics");
        Files.createDirectories(graphics);
        writeStoreIcon(graphics.resolve("play-store-icon-512.png"), logo);
        writeFeatureGraphic(graphics.resolve("feature-graphic-1024x500.png"), logo);

        System.out.println("Generated adaptive vectors, 10 fallback PNGs, the 512 px artwork,");
        System.out.println("and store/graphics: play-store-icon-512.png, feature-graphic-1024x500.png");
    }

    private static Logo createLogo() {
        Mark body = new Mark();

        // Four separated tracker channels reproduce the selected concept's P silhouette.
        body.roundedRectangle(29, 22, 39, 86, 3.5)
                .roundedRectangle(41, 22, 51, 86, 3.5)

                // The third channel is one concave contour. Its notch forms Play without an
                // even-odd/XOR path, and stops one unit before the far edge to keep the rail whole.
                .moveTo(56.5, 22)
                .horizontalTo(59.5)
                .cubicTo(61.4, 22, 63, 23.6, 63, 25.5)
                .verticalTo(59.5)
                .cubicTo(63, 61.4, 61.4, 63, 59.5, 63)
                .horizontalTo(56.5)
                .cubicTo(54.6, 63, 53, 61.4, 53, 59.5)
                .verticalTo(53)
                .lineTo(62, 44.5)
                .lineTo(53, 36)
                .verticalTo(25.5)
                .cubicTo(53, 23.6, 54.6, 22, 56.5, 22)
                .close()

                .moveTo(68, 22)
                .horizontalTo(69)
                .cubicTo(74.5, 22, 78, 27.5, 78, 35)
                .verticalTo(50)
                .cubicTo(78, 57.5, 74.5, 63, 69, 63)
                .horizontalTo(68)
                .cubicTo(66.3, 63, 65, 61.7, 65, 60)
                .verticalTo(25)
                .cubicTo(65, 23.3, 66.3, 22, 68, 22)
                .close();

        Mark darkCells = new Mark();
        darkCells.rectangle(30.5, 37, 37.5, 42)
                .rectangle(30.5, 55, 37.5, 60)
                .rectangle(30.5, 73, 37.5, 78)
                .rectangle(42.5, 29, 49.5, 34)
                .rectangle(42.5, 47, 49.5, 52)
                .rectangle(42.5, 65, 49.5, 70)
                .rectangle(42.5, 80, 49.5, 84)
                .rectangle(54.5, 28.5, 61.5, 33.5)
                .rectangle(68, 29, 75, 34)
                .rectangle(69, 47, 76, 52);

        Mark lightCells = new Mark();
        lightCells.rectangle(30.5, 28, 37.5, 34)
                .rectangle(30.5, 46, 37.5, 52)
                .rectangle(30.5, 64, 37.5, 70)
                .rectangle(30.5, 80, 37.5, 84)
                .rectangle(42.5, 38, 49.5, 44)
                .rectangle(42.5, 56, 49.5, 62)
                .rectangle(42.5, 72, 49.5, 77)
                .rectangle(54.5, 57, 61.5, 61)
                .rectangle(69, 38, 76, 44)
                .rectangle(67, 55, 74, 60);

        return new Logo(body, darkCells, lightCells);
    }

    private static void writePng(Path output, int size, boolean round, Logo logo) throws IOException {
        BufferedImage image = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = image.createGraphics();
        try {
            graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            graphics.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
            graphics.scale(size / VIEWPORT, size / VIEWPORT);

            graphics.setColor(BACKGROUND);
            if (round) {
                graphics.fill(new Ellipse2D.Double(0, 0, VIEWPORT, VIEWPORT));
            } else {
                graphics.fill(new Rectangle2D.Double(0, 0, VIEWPORT, VIEWPORT));
            }

            graphics.translate(VIEWPORT / 2, VIEWPORT / 2);
            graphics.scale(MARK_SCALE, MARK_SCALE);
            graphics.translate(-VIEWPORT / 2, -VIEWPORT / 2);

            graphics.setColor(FOREGROUND);
            graphics.fill(logo.body().shape());
            graphics.setColor(DARK_CELL);
            graphics.fill(logo.darkCells().shape());
            graphics.setColor(LIGHT_CELL);
            graphics.fill(logo.lightCells().shape());
        } finally {
            graphics.dispose();
        }
        long markPixels = 0;
        int foregroundRgb = FOREGROUND.getRGB() & 0x00FFFFFF;
        int darkCellRgb = DARK_CELL.getRGB() & 0x00FFFFFF;
        int lightCellRgb = LIGHT_CELL.getRGB() & 0x00FFFFFF;
        for (int y = 0; y < size; y++) {
            for (int x = 0; x < size; x++) {
                int pixel = image.getRGB(x, y);
                int rgb = pixel & 0x00FFFFFF;
                if ((pixel >>> 24) != 0
                        && (rgb == foregroundRgb || rgb == darkCellRgb || rgb == lightCellRgb)) {
                    markPixels++;
                }
            }
        }
        if (markPixels < size * size / 50L) {
            throw new IllegalStateException("Launcher mark is missing or too small in " + output);
        }
        ImageIO.write(image, "png", output.toFile());
    }

    /**
     * The 512 px icon Google Play asks for.
     *
     * The same square as the launcher's own fallback PNG, drawn opaque. Play masks the corners and
     * casts the shadow itself, so neither is baked in, and `TYPE_INT_RGB` is what guarantees the
     * file carries no alpha channel for Play to refuse.
     */
    private static void writeStoreIcon(Path output, Logo logo) throws IOException {
        BufferedImage image = new BufferedImage(STORE_ICON_SIZE, STORE_ICON_SIZE, BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = image.createGraphics();
        try {
            quality(graphics);
            graphics.setColor(BACKGROUND);
            graphics.fillRect(0, 0, STORE_ICON_SIZE, STORE_ICON_SIZE);
            graphics.scale(STORE_ICON_SIZE / VIEWPORT, STORE_ICON_SIZE / VIEWPORT);
            graphics.translate(VIEWPORT / 2, VIEWPORT / 2);
            graphics.scale(MARK_SCALE, MARK_SCALE);
            graphics.translate(-VIEWPORT / 2, -VIEWPORT / 2);
            paintMark(graphics, logo);
        } finally {
            graphics.dispose();
        }
        requireMark(image, output);
        ImageIO.write(image, "png", output.toFile());
    }

    /**
     * The 1024x500 feature graphic.
     *
     * **No text on it, deliberately.** Play crops this differently on every surface it appears on
     * and overlays a play button on some of them, a word would have to exist in both listing
     * languages, and the font a headless container happens to have is not a thing to make a brand
     * depend on. What is left is the mark and the pattern it is made of.
     *
     * The mark sits left of centre, clear of the edges. The pattern field to its right is the same
     * tracker cells the letter is built from, laid on a fixed grid and faded out towards the edge,
     * so the graphic reads as one idea rather than as a logo on a background.
     */
    private static void writeFeatureGraphic(Path output, Logo logo) throws IOException {
        BufferedImage image = new BufferedImage(FEATURE_WIDTH, FEATURE_HEIGHT, BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = image.createGraphics();
        try {
            quality(graphics);
            graphics.setPaint(new GradientPaint(0, FEATURE_HEIGHT, BACKGROUND, FEATURE_WIDTH, 0, FEATURE_WASH));
            graphics.fillRect(0, 0, FEATURE_WIDTH, FEATURE_HEIGHT);

            paintPatternField(graphics);

            // Measured off the geometry rather than guessed, so moving a channel moves the mark
            // with it instead of leaving it a few pixels off centre.
            Rectangle2D bounds = logo.body().shape().getBounds2D();
            bounds = bounds.createUnion(logo.darkCells().shape().getBounds2D());
            bounds = bounds.createUnion(logo.lightCells().shape().getBounds2D());

            double markHeight = FEATURE_HEIGHT * 0.62;
            double scale = markHeight / bounds.getHeight();
            double left = FEATURE_WIDTH * 0.11;
            double top = (FEATURE_HEIGHT - markHeight) / 2.0;
            graphics.translate(left - bounds.getX() * scale, top - bounds.getY() * scale);
            graphics.scale(scale, scale);
            paintMark(graphics, logo);
        } finally {
            graphics.dispose();
        }
        requireMark(image, output);
        ImageIO.write(image, "png", output.toFile());
    }

    /**
     * Cells on a fixed grid, fading east.
     *
     * Deterministic: a fixed seed, so running the generator twice produces the same file and a
     * change in the image is always a change somebody made.
     */
    private static void paintPatternField(Graphics2D graphics) {
        Random pattern = new Random(19_840_217L);
        double columnWidth = 46;
        double columnGap = 26;
        double cellHeight = 20;
        double cellGap = 12;
        double firstColumn = FEATURE_WIDTH * 0.42;
        for (double x = firstColumn; x < FEATURE_WIDTH + columnWidth; x += columnWidth + columnGap) {
            double distance = (x - firstColumn) / (FEATURE_WIDTH - firstColumn);
            double offset = pattern.nextDouble() * (cellHeight + cellGap);
            for (double y = -offset; y < FEATURE_HEIGHT; y += cellHeight + cellGap) {
                if (pattern.nextDouble() < 0.34) {
                    continue;
                }
                Color colour = switch (pattern.nextInt(3)) {
                    case 0 -> FOREGROUND;
                    case 1 -> LIGHT_CELL;
                    default -> DARK_CELL;
                };
                // Bright near the mark, gone by the far edge, and never solid enough to compete
                // with the letter itself.
                double alpha = 0.30 * (1.0 - distance) * (1.0 - distance);
                graphics.setColor(new Color(colour.getRed(), colour.getGreen(), colour.getBlue(),
                        (int) Math.round(Math.max(0, alpha) * 255)));
                graphics.fill(new Rectangle2D.Double(x, y, columnWidth, cellHeight));
            }
        }
    }

    private static void quality(Graphics2D graphics) {
        graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        graphics.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        graphics.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);
    }

    private static void paintMark(Graphics2D graphics, Logo logo) {
        graphics.setColor(FOREGROUND);
        graphics.fill(logo.body().shape());
        graphics.setColor(DARK_CELL);
        graphics.fill(logo.darkCells().shape());
        graphics.setColor(LIGHT_CELL);
        graphics.fill(logo.lightCells().shape());
    }

    /** The same guard the launcher PNGs carry: an empty canvas is a silent failure. */
    private static void requireMark(BufferedImage image, Path output) {
        long markPixels = 0;
        int foregroundRgb = FOREGROUND.getRGB() & 0x00FFFFFF;
        int darkCellRgb = DARK_CELL.getRGB() & 0x00FFFFFF;
        int lightCellRgb = LIGHT_CELL.getRGB() & 0x00FFFFFF;
        for (int y = 0; y < image.getHeight(); y++) {
            for (int x = 0; x < image.getWidth(); x++) {
                int rgb = image.getRGB(x, y) & 0x00FFFFFF;
                if (rgb == foregroundRgb || rgb == darkCellRgb || rgb == lightCellRgb) {
                    markPixels++;
                }
            }
        }
        long pixels = (long) image.getWidth() * image.getHeight();
        if (markPixels < pixels / 100L) {
            throw new IllegalStateException("The mark is missing or too small in " + output);
        }
    }

    private static void writeVectorResources(Path resources, Logo logo) throws IOException {
        Path drawable = resources.resolve("drawable");
        Files.writeString(drawable.resolve("ic_launcher_background.xml"), backgroundVector(), StandardCharsets.UTF_8);
        Files.writeString(drawable.resolve("ic_launcher_foreground.xml"), foregroundVector(logo), StandardCharsets.UTF_8);
        Files.writeString(
                drawable.resolve("ic_launcher_monochrome.xml"),
                monochromeVector(logo.body().pathData()),
                StandardCharsets.UTF_8);

        Path version26 = resources.resolve("mipmap-anydpi-v26");
        Files.createDirectories(version26);
        Files.writeString(version26.resolve("ic_launcher.xml"), adaptiveIcon(false), StandardCharsets.UTF_8);
        Files.writeString(version26.resolve("ic_launcher_round.xml"), adaptiveIcon(false), StandardCharsets.UTF_8);

        Path version33 = resources.resolve("mipmap-anydpi-v33");
        Files.createDirectories(version33);
        Files.writeString(version33.resolve("ic_launcher.xml"), adaptiveIcon(true), StandardCharsets.UTF_8);
        Files.writeString(version33.resolve("ic_launcher_round.xml"), adaptiveIcon(true), StandardCharsets.UTF_8);
    }

    private static String backgroundVector() {
        return """
                <?xml version="1.0" encoding="utf-8"?>
                <!-- Near-black canvas matching the selected icon concept. -->
                <vector xmlns:android="http://schemas.android.com/apk/res/android"
                    android:width="108dp"
                    android:height="108dp"
                    android:viewportWidth="108"
                    android:viewportHeight="108">
                    <path
                        android:fillColor="%s"
                        android:pathData="M0,0 H108 V108 H0 Z" />
                </vector>
                """.formatted(BACKGROUND_HEX);
    }

    private static String foregroundVector(Logo logo) {
        return """
                <?xml version="1.0" encoding="utf-8"?>
                <!-- Generated by scripts/GenerateLauncherIcons.java; edit the geometry there. -->
                <vector xmlns:android="http://schemas.android.com/apk/res/android"
                    android:width="108dp"
                    android:height="108dp"
                    android:viewportWidth="108"
                    android:viewportHeight="108">
                    <group
                        android:pivotX="54"
                        android:pivotY="54"
                        android:scaleX="%s"
                        android:scaleY="%s">
                        <path
                            android:fillColor="%s"
                            android:fillType="evenOdd"
                            android:pathData="%s" />
                        <path
                            android:fillColor="%s"
                            android:pathData="%s" />
                        <path
                            android:fillColor="%s"
                            android:pathData="%s" />
                    </group>
                </vector>
                """.formatted(
                scaleNumber(),
                scaleNumber(),
                FOREGROUND_HEX,
                logo.body().pathData(),
                DARK_CELL_HEX,
                logo.darkCells().pathData(),
                LIGHT_CELL_HEX,
                logo.lightCells().pathData());
    }

    private static String monochromeVector(String pathData) {
        return """
                <?xml version="1.0" encoding="utf-8"?>
                <!-- Generated by scripts/GenerateLauncherIcons.java; edit the geometry there. -->
                <vector xmlns:android="http://schemas.android.com/apk/res/android"
                    android:width="108dp"
                    android:height="108dp"
                    android:viewportWidth="108"
                    android:viewportHeight="108">
                    <group
                        android:pivotX="54"
                        android:pivotY="54"
                        android:scaleX="%s"
                        android:scaleY="%s">
                        <path
                            android:fillColor="#000000"
                            android:fillType="evenOdd"
                            android:pathData="%s" />
                    </group>
                </vector>
                """.formatted(scaleNumber(), scaleNumber(), pathData);
    }

    private static String adaptiveIcon(boolean monochrome) {
        String themed = monochrome
                ? "\n    <monochrome android:drawable=\"@drawable/ic_launcher_monochrome\" />"
                : "";
        return """
                <?xml version="1.0" encoding="utf-8"?>
                <adaptive-icon xmlns:android="http://schemas.android.com/apk/res/android">
                    <background android:drawable="@drawable/ic_launcher_background" />
                    <foreground android:drawable="@drawable/ic_launcher_foreground" />%s
                </adaptive-icon>
                """.formatted(themed);
    }

    private static final class Mark {
        private final Path2D shape = new Path2D.Double(Path2D.WIND_EVEN_ODD);
        private final StringBuilder pathData = new StringBuilder();

        private Mark() {
        }

        private Mark append(String command, PathOperation operation) {
            operation.apply(shape);
            pathData.append(command);
            return this;
        }

        Path2D shape() {
            return shape;
        }

        String pathData() {
            return pathData.toString().trim();
        }

        Mark moveTo(double x, double y) {
            return append("M" + number(x) + "," + number(y) + " ", path -> path.moveTo(x, y));
        }

        Mark lineTo(double x, double y) {
            return append("L" + number(x) + "," + number(y) + " ", path -> path.lineTo(x, y));
        }

        Mark horizontalTo(double x) {
            double y = shape.getCurrentPoint().getY();
            return append("H" + number(x) + " ", path -> path.lineTo(x, y));
        }

        Mark verticalTo(double y) {
            double x = shape.getCurrentPoint().getX();
            return append("V" + number(y) + " ", path -> path.lineTo(x, y));
        }

        Mark cubicTo(double x1, double y1, double x2, double y2, double x, double y) {
            String command = "C" + number(x1) + "," + number(y1) + " "
                    + number(x2) + "," + number(y2) + " " + number(x) + "," + number(y) + " ";
            return append(command, path -> path.curveTo(x1, y1, x2, y2, x, y));
        }

        Mark close() {
            return append("Z ", Path2D::closePath);
        }

        Mark rectangle(double left, double top, double right, double bottom) {
            return moveTo(left, top)
                    .horizontalTo(right)
                    .verticalTo(bottom)
                    .horizontalTo(left)
                    .close();
        }

        Mark roundedRectangle(double left, double top, double right, double bottom, double radius) {
            double control = radius * 0.55228475;
            return moveTo(left + radius, top)
                    .horizontalTo(right - radius)
                    .cubicTo(right - radius + control, top, right, top + radius - control, right, top + radius)
                    .verticalTo(bottom - radius)
                    .cubicTo(right, bottom - radius + control, right - radius + control, bottom, right - radius, bottom)
                    .horizontalTo(left + radius)
                    .cubicTo(left + radius - control, bottom, left, bottom - radius + control, left, bottom - radius)
                    .verticalTo(top + radius)
                    .cubicTo(left, top + radius - control, left + radius - control, top, left + radius, top)
                    .close();
        }

        private static String number(double value) {
            return value == Math.rint(value)
                    ? Long.toString(Math.round(value))
                    : String.format(Locale.ROOT, "%.1f", value);
        }
    }

    private record Logo(Mark body, Mark darkCells, Mark lightCells) {
    }

    private static String scaleNumber() {
        return String.format(Locale.ROOT, "%.2f", MARK_SCALE);
    }

    @FunctionalInterface
    private interface PathOperation {
        void apply(Path2D path);
    }
}
