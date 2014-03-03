package edu.virginia.vcgr.genii.ui;

import java.awt.image.BufferedImage;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;

import javax.imageio.ImageIO;

import org.morgan.util.io.StreamUtils;

import edu.virginia.vcgr.genii.client.GenesisIIConstants;

public class Images {
	static protected BufferedImage loadImage(String resourcePath)
			throws IOException {
		InputStream in = null;

		try {
			in = ClassLoader.getSystemResourceAsStream(String.format("%s%s",
					GenesisIIConstants.IMAGE_RELATIVE_LOCATION, resourcePath));
			if (in == null)
				throw new FileNotFoundException(String.format(
						"Couldn't find resource %s.", resourcePath));
			BufferedImage image = ImageIO.read(in);
			return image;
		} finally {
			StreamUtils.close(in);
		}
	}
}
