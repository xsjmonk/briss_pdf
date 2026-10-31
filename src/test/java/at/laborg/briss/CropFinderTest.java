package at.laborg.briss;

import static org.junit.jupiter.api.Assertions.assertEquals;

import at.laborg.briss.model.CropFinder;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import org.junit.jupiter.api.Test;

class CropFinderTest {

	@Test
	void findsContentBoundsInSparsePage() {
		BufferedImage image = new BufferedImage(200, 200, BufferedImage.TYPE_BYTE_GRAY);
		Graphics2D graphics = image.createGraphics();
		graphics.setColor(Color.WHITE);
		graphics.fillRect(0, 0, image.getWidth(), image.getHeight());
		graphics.setColor(Color.BLACK);
		graphics.fillRect(20, 30, 140, 120);
		graphics.dispose();

		float[] crop = CropFinder.getAutoCropFloats(image);

		assertEquals(0.10f, crop[0], 0.01f);
		assertEquals(0.25f, crop[1], 0.01f);
		assertEquals(0.20f, crop[2], 0.01f);
		assertEquals(0.15f, crop[3], 0.01f);
	}

	@Test
	void keepsBlankPageUsable() {
		BufferedImage image = new BufferedImage(200, 200, BufferedImage.TYPE_BYTE_GRAY);
		Graphics2D graphics = image.createGraphics();
		graphics.setColor(Color.WHITE);
		graphics.fillRect(0, 0, image.getWidth(), image.getHeight());
		graphics.dispose();

		float[] crop = CropFinder.getAutoCropFloats(image);

		assertEquals(0.0f, crop[0]);
		assertEquals(0.0f, crop[1]);
		assertEquals(0.0f, crop[2]);
		assertEquals(0.0f, crop[3]);
	}
}
