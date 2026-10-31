package at.laborg.briss.model;

import java.awt.image.BufferedImage;
import java.awt.image.WritableRaster;

public final class CropFinder {

	private static final int PROFILE_WINDOW_SIZE = 5;
	private static final double MIN_DARKNESS_RATIO = 0.002;

	private CropFinder() {
	}

	public static float[] getAutoCropFloats(final BufferedImage image) {

		WritableRaster raster = image.getRaster();

		int positionXLeft = findContentBoundary(raster, ImageFinderUtil.X_AXIS, false);
		int positionYTop = findContentBoundary(raster, ImageFinderUtil.Y_AXIS, false);
		int positionXRight = findContentBoundary(raster, ImageFinderUtil.X_AXIS, true);
		int positionYBottom = findContentBoundary(raster, ImageFinderUtil.Y_AXIS, true);

		float[] result = new float[4];
		result[0] = (positionXLeft / (float) image.getWidth());
		result[1] = ((image.getHeight() - 1 - positionYBottom) / (float) image.getHeight());
		result[2] = ((image.getWidth() - 1 - positionXRight) / (float) image.getWidth());
		result[3] = (positionYTop / (float) image.getHeight());
		return result;
	}

	private static int findContentBoundary(final WritableRaster raster, final int axis, final boolean fromEnd) {
		int length = axis == ImageFinderUtil.X_AXIS ? raster.getWidth() : raster.getHeight();
		double[] profile = createDarknessProfile(raster, axis);
		int start = fromEnd ? length - 1 : 0;
		int end = fromEnd ? -1 : length;
		int step = fromEnd ? -1 : 1;

		for (int position = start; position != end; position += step) {
			if (hasContentInWindow(profile, position, fromEnd)) {
				return getContentPosition(profile, position, fromEnd);
			}
		}

		// A blank page has no content boundary. Keep the whole page instead of
		// producing a zero-sized crop rectangle.
		return fromEnd ? length - 1 : 0;
	}

	private static int getContentPosition(final double[] profile, final int position, final boolean fromEnd) {
		int first = fromEnd ? Math.max(0, position - PROFILE_WINDOW_SIZE + 1) : position;
		int last = fromEnd ? position : Math.min(profile.length - 1, position + PROFILE_WINDOW_SIZE - 1);
		if (fromEnd) {
			for (int i = last; i >= first; i--) {
				if (profile[i] >= MIN_DARKNESS_RATIO) {
					return i;
				}
			}
		} else {
			for (int i = first; i <= last; i++) {
				if (profile[i] >= MIN_DARKNESS_RATIO) {
					return i;
				}
			}
		}
		return position;
	}

	private static double[] createDarknessProfile(final WritableRaster raster, final int axis) {
		int length = axis == ImageFinderUtil.X_AXIS ? raster.getWidth() : raster.getHeight();
		int otherLength = axis == ImageFinderUtil.X_AXIS ? raster.getHeight() : raster.getWidth();
		double[] profile = new double[length];
		double maxSample = getMaxSampleValue(raster);

		for (int position = 0; position < length; position++) {
			double darkness = 0;
			for (int otherPosition = 0; otherPosition < otherLength; otherPosition++) {
				int x = axis == ImageFinderUtil.X_AXIS ? position : otherPosition;
				int y = axis == ImageFinderUtil.X_AXIS ? otherPosition : position;
				darkness += (maxSample - raster.getSample(x, y, 0)) / maxSample;
			}
			profile[position] = darkness / otherLength;
		}
		return profile;
	}

	private static boolean hasContentInWindow(final double[] profile, final int position, final boolean fromEnd) {
		int first = fromEnd ? Math.max(0, position - PROFILE_WINDOW_SIZE + 1) : position;
		int last = fromEnd ? position : Math.min(profile.length - 1, position + PROFILE_WINDOW_SIZE - 1);
		double darkness = 0;
		for (int i = first; i <= last; i++) {
			darkness += profile[i];
		}
		return darkness / (last - first + 1) >= MIN_DARKNESS_RATIO;
	}

	private static double getMaxSampleValue(final WritableRaster raster) {
		int sampleBits = raster.getSampleModel().getSampleSize(0);
		return sampleBits < 31 ? (1L << sampleBits) - 1 : Integer.MAX_VALUE;
	}
}
