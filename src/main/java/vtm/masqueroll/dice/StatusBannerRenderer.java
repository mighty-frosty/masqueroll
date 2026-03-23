package vtm.masqueroll.dice;

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
import java.util.List;

public final class StatusBannerRenderer {

    private static final int PADDING_X = 16;
    private static final int PADDING_Y = 12;
    private static final int INNER_PADDING_X = 12;
    private static final int INNER_PADDING_Y = 8;
    private static final int LINE_GAP = 2;
    private static final Color BACKGROUND = new Color(18, 18, 20);
    private static final Color SUCCESS_BACKGROUND = new Color(31, 50, 40);
    private static final Color FAILURE_BACKGROUND = new Color(79, 23, 23);
    private static final Color SUCCESS_BORDER = new Color(106, 132, 112);
    private static final Color FAILURE_BORDER = new Color(138, 55, 55);
    private static final Color SUCCESS_TEXT = new Color(235, 225, 204);
    private static final Color FAILURE_TEXT = new Color(247, 226, 214);

    private final Font successFont;
    private final Font failureFont;

    public StatusBannerRenderer(Path fontsDirectory) {
        this.successFont = loadFont(List.of(fontsDirectory.resolve("Cormorant.ttf")), 24f, new Font("Serif", Font.BOLD, 24))
            .deriveFont(Font.BOLD, 24f);
        this.failureFont = loadFont(List.of(fontsDirectory.resolve("Bloodthirsty.ttf")), 28f, new Font("Dialog", Font.BOLD, 28))
            .deriveFont(Font.PLAIN, 28f);
    }

    public byte[] render(String text, boolean success) {
        Font font = success ? successFont : failureFont;
        List<String> lines = splitLines(text, success);

        BufferedImage probe = new BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB);
        Graphics2D probeGraphics = probe.createGraphics();
        probeGraphics.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        probeGraphics.setFont(font);
        FontMetrics metrics = probeGraphics.getFontMetrics();
        int textWidth = lines.stream().mapToInt(metrics::stringWidth).max().orElse(0);
        int textHeight = (lines.size() * metrics.getHeight()) + ((lines.size() - 1) * LINE_GAP);
        probeGraphics.dispose();

        int width = textWidth + (PADDING_X * 2) + (INNER_PADDING_X * 2);
        int height = textHeight + (PADDING_Y * 2) + (INNER_PADDING_Y * 2);

        BufferedImage banner = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = banner.createGraphics();
        graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        graphics.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        graphics.setRenderingHint(RenderingHints.KEY_FRACTIONALMETRICS, RenderingHints.VALUE_FRACTIONALMETRICS_ON);

        graphics.setColor(BACKGROUND);
        graphics.fillRoundRect(0, 0, width, height, 16, 16);
        graphics.setColor(success ? SUCCESS_BORDER : FAILURE_BORDER);
        graphics.fillRoundRect(5, 5, width - 10, height - 10, 12, 12);
        graphics.setColor(success ? SUCCESS_BACKGROUND : FAILURE_BACKGROUND);
        graphics.fillRoundRect(7, 7, width - 14, height - 14, 10, 10);

        graphics.setColor(success ? SUCCESS_TEXT : FAILURE_TEXT);
        graphics.setFont(font);
        FontMetrics finalMetrics = graphics.getFontMetrics();
        int contentHeight = (lines.size() * finalMetrics.getHeight()) + ((lines.size() - 1) * LINE_GAP);
        int y = ((height - contentHeight) / 2) + finalMetrics.getAscent();
        for (String line : lines) {
            graphics.drawString(line, PADDING_X + INNER_PADDING_X, y);
            y += finalMetrics.getHeight() + LINE_GAP;
        }
        graphics.dispose();

        try (ByteArrayOutputStream outputStream = new ByteArrayOutputStream()) {
            ImageIO.write(banner, "png", outputStream);
            return outputStream.toByteArray();
        } catch (IOException ex) {
            throw new IllegalStateException("Could not render status banner.", ex);
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

    private List<String> splitLines(String text, boolean success) {
        if (success || text.length() <= 20) {
            return List.of(text);
        }
        if (text.contains(" WITH ")) {
            String[] parts = text.split(" WITH ", 2);
            return List.of(parts[0], "WITH " + parts[1]);
        }
        int splitIndex = text.lastIndexOf(' ', text.length() / 2);
        if (splitIndex <= 0 || splitIndex >= text.length() - 1) {
            return List.of(text);
        }
        return List.of(text.substring(0, splitIndex), text.substring(splitIndex + 1));
    }
}
