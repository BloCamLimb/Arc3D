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

package icyllis.arc3d.core.zip;

import java.io.Closeable;
import java.io.File;
import java.io.IOException;
import java.nio.channels.FileChannel;
import java.nio.charset.Charset;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;

/**
 * High performance ZipFile.
 */
public class NeoZipFile implements Closeable {

    final FileChannel ch;

    public NeoZipFile(String name, Charset charset) throws IOException {
        this(Path.of(name), charset);
    }

    public NeoZipFile(File file, Charset charset) throws IOException {
        this(file.toPath(), charset);
    }

    public NeoZipFile(Path path, Charset charset) throws IOException {

        if ("jar".equals(path.getFileSystem().provider().getScheme())) {
            throw new UnsupportedOperationException("Cannot create from zip file system");
        }

        FileChannel ch;
        try {
            ch = FileChannel.open(path, StandardOpenOption.READ);
        } catch (UnsupportedOperationException e) {
            // if the path is not from the default file system, try again
            File file = path.toFile(); // throws UnsupportedOperationException
            ch = FileChannel.open(file.toPath(), StandardOpenOption.READ);
        }

        this.ch = ch;
    }

    @Override
    public void close() throws IOException {
        ch.close();
    }
}
