/*
 * This file is part of Arc3D.
 *
 * Copyright (C) 2026 BloCamLimb <pocamelards@gmail.com>
 *
 * Arc3D is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 3 of the License, or (at your option) any later version.
 *
 * Arc3D is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with Arc3D. If not, see <https://www.gnu.org/licenses/>.
 */

package icyllis.arc3d.core.test;

import icyllis.arc3d.core.image.ChannelImageInputStream;
import icyllis.arc3d.core.image.ChannelImageOutputStream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.channels.FileChannel;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;

public class TestChannelImageIO {

    public static final Logger LOGGER = LoggerFactory.getLogger(TestChannelImageIO.class);

    // -Djdk.nio.maxCachedBufferSize=262144
    public static void main(String[] args) {
        if (args.length == 0) {
            LOGGER.info("Require the source image path");
            return;
        }

        BufferedImage bufferedImage = null;
        try (var fc = FileChannel.open(Path.of(args[0]),
                StandardOpenOption.READ)) {
            bufferedImage = ImageIO.read(new ChannelImageInputStream(fc));

            LOGGER.info("Got buffered image, width {} height {}, colorSpace {}", bufferedImage.getWidth(), bufferedImage.getHeight(),
                    bufferedImage.getColorModel().getColorSpace());
        } catch (IOException e) {
            LOGGER.error("Failed to read", e);
        }

        if (bufferedImage != null) {

            try (var fc = FileChannel.open(Path.of("run/test_imageio_write_via_channel.tiff"),
                    StandardOpenOption.CREATE, StandardOpenOption.READ, StandardOpenOption.WRITE, StandardOpenOption.TRUNCATE_EXISTING);
                 var stream = new ChannelImageOutputStream(fc)) {

                ImageWriter writer = ImageIO.getImageWritersByFormatName("tiff").next();
                var writeParams = writer.getDefaultWriteParam();

                writeParams.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
                writeParams.setCompressionType("ZLib");
                // is computed as (int)(1 + 8*quality), we want the default compression level 6
                writeParams.setCompressionQuality((6 - 1) / 8f);

                writer.setOutput(stream);
                try {
                    writer.write(null, new IIOImage(bufferedImage, null, null), writeParams);
                } finally {
                    writer.dispose();
                }
            } catch (IOException e) {
                LOGGER.error("Failed to write", e);
            }
        }
    }
}
