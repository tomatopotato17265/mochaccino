package tomatopotato.mochaccino.client;

import javax.imageio.ImageIO;
import java.awt.AlphaComposite;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.Shape;
import java.awt.geom.RoundRectangle2D;
import java.awt.image.BufferedImage;
import java.awt.image.ConvolveOp;
import java.awt.image.Kernel;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public final class RoundedIcon {
	// 1024 canvas with an 824 rounded square in the middle
	private static final int CANVAS = 1024;
	private static final int TILE = 824;
	private static final int CORNER = 185;

	private RoundedIcon() {
	}

	public static byte[] compose(byte[] png, int background, double content) throws IOException {
		BufferedImage image = ImageIO.read(new ByteArrayInputStream(png));
		if (image == null) {
			throw new IOException("Not a readable image");
		}

		BufferedImage icon = new BufferedImage(CANVAS, CANVAS, BufferedImage.TYPE_INT_ARGB);
		Graphics2D g = icon.createGraphics();
		g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
		g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
		g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);

		int margin = (CANVAS - TILE) / 2;
		Shape tile = new RoundRectangle2D.Double(margin, margin, TILE, TILE, CORNER * 2, CORNER * 2);

		g.drawImage(shadow(), 0, 0, null);
		g.setColor(new Color(background));
		g.fill(tile);

		double scale = Math.min(TILE * content / image.getWidth(), TILE * content / image.getHeight());
		int width = (int) Math.round(image.getWidth() * scale);
		int height = (int) Math.round(image.getHeight() * scale);
		g.setClip(tile);
		g.drawImage(image, (CANVAS - width) / 2, (CANVAS - height) / 2, width, height, null);
		g.dispose();

		ByteArrayOutputStream out = new ByteArrayOutputStream();
		ImageIO.write(icon, "png", out);
		return out.toByteArray();
	}

	private static BufferedImage shadow() {
		int size = CANVAS / 4;
		BufferedImage small = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);
		Graphics2D g = small.createGraphics();
		g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
		g.setColor(new Color(0, 0, 0, 90));
		double margin = (CANVAS - TILE) / 2.0 / 4;
		g.fill(new RoundRectangle2D.Double(margin, margin + 3, TILE / 4.0, TILE / 4.0, CORNER / 2.0, CORNER / 2.0));
		g.dispose();

		int radius = 4;
		int diameter = radius * 2 + 1;
		float[] weights = new float[diameter * diameter];
		java.util.Arrays.fill(weights, 1f / weights.length);
		ConvolveOp blur = new ConvolveOp(new Kernel(diameter, diameter, weights), ConvolveOp.EDGE_NO_OP, null);
		BufferedImage blurred = blur.filter(blur.filter(small, null), null);

		BufferedImage shadow = new BufferedImage(CANVAS, CANVAS, BufferedImage.TYPE_INT_ARGB);
		Graphics2D big = shadow.createGraphics();
		big.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
		big.setComposite(AlphaComposite.SrcOver);
		big.drawImage(blurred, 0, 0, CANVAS, CANVAS, null);
		big.dispose();
		return shadow;
	}

	// 1024 PNG -> .icns bytes (every size macOS wants via sips and iconutil)
	public static byte[] toIcns(byte[] png) throws IOException, InterruptedException {
		Path work = Files.createTempDirectory("mochaccino-rounded");
		try {
			Path icns = work.resolve("AppIcon.icns");
			NotifierApp.makeIcns(png, icns);
			return Files.readAllBytes(icns);
		} finally {
			try (var files = Files.walk(work)) {
				files.sorted(java.util.Comparator.reverseOrder()).forEach(path -> path.toFile().delete());
			}
		}
	}
}
