package vtm.masqueroll.dice;

import lombok.Getter;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class RollImageRenderer {

    private static final int PADDING = 14;
    private static final int GAP = 10;
    private static final int HEADER_HEIGHT = 48;
    private static final int ROW_LABEL_HEIGHT = 16;
    private static final int ROW_GAP = 10;
    private static final int FALLBACK_DIE_SIZE = 48;
    private static final Color BACKGROUND = new Color(26, 24, 21);
    private static final Color HEADER_TEXT = new Color(245, 222, 196);
    private static final Color SUBTLE_TEXT = new Color(184, 168, 146);
    private static final Color ALERT_TEXT = new Color(186, 32, 32);

    private final Map<String, BufferedImage> assets = new HashMap<>();
    @Getter
    private final boolean enabled;
    private final Font titleFont;
    private final Font successFont;
    private final Font rowFont;
    private final Font alertFont;

    public RollImageRenderer(Path imageDirectory) {
        boolean loaded;
        try {
            assets.put("normal-success", load(imageDirectory.resolve("normal-success.png")));
            assets.put("normal-fail", load(imageDirectory.resolve("normal-fail.png")));
            assets.put("normal-crit", load(imageDirectory.resolve("normal-crit.png")));
            assets.put("red-success", load(imageDirectory.resolve("red-success.png")));
            assets.put("red-fail", load(imageDirectory.resolve("red-fail.png")));
            assets.put("red-crit", load(imageDirectory.resolve("red-crit.png")));
            assets.put("bestial-fail", load(imageDirectory.resolve("bestial-fail.png")));
            loaded = true;
        } catch (IOException ignored) {
            loaded = false;
        }
        this.enabled = loaded;
        Path fontsDirectory = imageDirectory.resolveSibling("fonts");
        this.titleFont = loadFont(List.of(fontsDirectory.resolve("Cormorant.ttf")), 32f,
            new Font("Serif", Font.BOLD, 32)).deriveFont(Font.BOLD, 32f);
        this.successFont = loadFont(List.of(fontsDirectory.resolve("Cormorant.ttf")), 19f,
            new Font("Serif", Font.BOLD, 19)).deriveFont(Font.BOLD, 19f);
        this.rowFont = loadFont(List.of(fontsDirectory.resolve("Cormorant.ttf")), 16f,
            new Font("Serif", Font.BOLD, 16)).deriveFont(Font.BOLD, 16f);
        this.alertFont = loadFont(
            List.of(
                fontsDirectory.resolve("Bloodthirsty.ttf")
            ),
            24f,
            new Font("Dialog", Font.BOLD, 24)
        ).deriveFont(Font.PLAIN, 24f);
    }

    public byte[] render(RollSummary summary, String characterName, String characterImageUrl) {
        return render(summary);
    }

    public byte[] render(RollSummary summary) {
        List<DieResult> normalDice = summary.dice().stream().filter(die -> !die.hunger()).toList();
        List<DieResult> hungerDice = summary.dice().stream().filter(DieResult::hunger).toList();

        int dieWidth = getMaxWidth(summary.dice());
        int dieHeight = getMaxHeight(summary.dice());
        int widestRow = Math.max(getRowWidth(normalDice, dieWidth), getRowWidth(hungerDice, dieWidth));
        int rowCount = 1 + (hungerDice.isEmpty() ? 0 : 1);

        int width = Math.max(420, (PADDING * 2) + widestRow);
        int height = HEADER_HEIGHT + (rowCount * dieHeight) + (rowCount * ROW_LABEL_HEIGHT) + ((rowCount - 1) * ROW_GAP) + (PADDING * 2);

        BufferedImage canvas = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = canvas.createGraphics();
        graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);

        graphics.setColor(BACKGROUND);
        graphics.fillRect(0, 0, width, height);

        drawHeader(graphics, width, summary);
        int y = HEADER_HEIGHT + PADDING;
        y = drawDiceRow(graphics, "Normal", normalDice, y, dieWidth, dieHeight);
        if (!hungerDice.isEmpty()) {
            y += ROW_GAP;
            drawDiceRow(graphics, "Hunger", hungerDice, y, dieWidth, dieHeight);
        }
        graphics.dispose();

        try (ByteArrayOutputStream outputStream = new ByteArrayOutputStream()) {
            ImageIO.write(canvas, "png", outputStream);
            return outputStream.toByteArray();
        } catch (IOException ex) {
            throw new IllegalStateException("Could not render roll image.", ex);
        }
    }

    private void drawHeader(Graphics2D graphics, int width, RollSummary summary) {
        graphics.setColor(HEADER_TEXT);
        graphics.setFont(titleFont);
        String headline = summary.successes() + (summary.successes() == 1 ? " success" : " successes");
        graphics.drawString(headline, PADDING, 34);

        String resultText = summary.resultLabel() == null ? "" : summary.resultLabel().text();
        if (!resultText.isEmpty()) {
            boolean alertResult = summary.bestialFailure() || summary.messyCritical();
            graphics.setColor(alertResult ? ALERT_TEXT : SUBTLE_TEXT);
            graphics.setFont(alertResult ? alertFont : successFont);
            FontMetrics metrics = graphics.getFontMetrics();
            int textWidth = metrics.stringWidth(resultText);
            graphics.drawString(resultText, width - PADDING - textWidth, 32);
        }
    }

    private int drawDiceRow(
        Graphics2D graphics,
        String label,
        List<DieResult> dice,
        int y,
        int dieWidth,
        int dieHeight
    ) {
        graphics.setColor(SUBTLE_TEXT);
        graphics.setFont(rowFont);
        graphics.drawString(label.toUpperCase(), PADDING, y + 13);

        int x = PADDING;
        int drawTop = y + ROW_LABEL_HEIGHT;
        for (DieResult die : dice) {
            BufferedImage face = resolveFace(die);
            int drawY = drawTop + Math.max(0, (dieHeight - face.getHeight()) / 2);
            graphics.drawImage(face, x, drawY, null);
            x += dieWidth + GAP;
        }
        return drawTop + dieHeight;
    }

    private int getRowWidth(List<DieResult> dice, int dieWidth) {
        if (dice.isEmpty()) {
            return 0;
        }
        return (dice.size() * dieWidth) + ((dice.size() - 1) * GAP);
    }

    private int getMaxWidth(List<DieResult> dice) {
        return dice.stream()
            .mapToInt(die -> resolveFace(die).getWidth())
            .max()
            .orElse(FALLBACK_DIE_SIZE);
    }

    private int getMaxHeight(List<DieResult> dice) {
        return dice.stream()
            .mapToInt(die -> resolveFace(die).getHeight())
            .max()
            .orElse(FALLBACK_DIE_SIZE);
    }

    private BufferedImage resolveFace(DieResult die) {
        if (die.value() == 10) {
            return die.hunger() ? assets.get("red-crit") : assets.get("normal-crit");
        }
        if (die.value() >= 6) {
            return die.hunger() ? assets.get("red-success") : assets.get("normal-success");
        }
        if (die.hunger() && die.value() == 1) {
            return assets.get("bestial-fail");
        }
        return die.hunger() ? assets.get("red-fail") : assets.get("normal-fail");
    }

    private BufferedImage load(Path path) throws IOException {
        try (InputStream inputStream = Files.newInputStream(path)) {
            BufferedImage image = ImageIO.read(inputStream);
            if (image == null) {
                throw new IOException("Unsupported image: " + path);
            }
            return image;
        }
    }

    private Font loadFont(List<Path> candidates, float size, Font fallback) {
        for (Path path : candidates) {
            if (Files.exists(path)) {
                try (InputStream inputStream = Files.newInputStream(path)) {
                    return Font.createFont(Font.TRUETYPE_FONT, inputStream).deriveFont(size);
                } catch (Exception ignored) {
                }
            }
        }
        return fallback.deriveFont(size);
    }
}
