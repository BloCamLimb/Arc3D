/*
 * This file is part of Arc3D.
 *
 * Copyright (C) 2022-2026 BloCamLimb <pocamelards@gmail.com>
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

package icyllis.arc3d.image;

import icyllis.arc3d.core.ColorInfo;
import icyllis.arc3d.core.ColorProfile;
import icyllis.arc3d.core.ColorSpace;
import icyllis.arc3d.core.ColorSpaces;
import icyllis.arc3d.core.ImageInfo;
import icyllis.arc3d.core.Pixmap;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import org.lwjgl.system.MemoryUtil;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.Arrays;

import static icyllis.arc3d.image.GIF.*;

/**
 * GIF decoder that created with compressed data and decode frame by frame,
 * GIFDecoder is not thread safe, but can be locked and use across threads.
 *
 * @author BloCamLimb
 */
public class GIFDecoder extends Decoder {

    private int mHeaderPos;

    private int mScreenWidth;
    private int mScreenHeight;

    private int[] mGlobalPalette; // rgba0 rgba1 ...
    private int[] mTmpPalette; // rgba0 rgba1 ...
    private byte[] mTmpRow; // index0 index1 ...

    private boolean mHasGlobalPalette;

    private int mNextControlData = 0;

    private int mLoopCount = -1;

    private ColorProfile colorProfile;
    private ColorSpace colorSpace;

    public GIFDecoder() {
    }

    @Override
    public void reset() {

        mNextControlData = 0;
        mLoopCount = -1;

        colorProfile = null;
        colorSpace = null;
    }

    @Override
    public int getWidth() {
        return mScreenWidth;
    }

    @Override
    public int getHeight() {
        return mScreenHeight;
    }

    public int getScreenWidth() {
        return mScreenWidth;
    }

    public int getScreenHeight() {
        return mScreenHeight;
    }

    @Override
    public @Nullable ColorProfile getColorProfile() {
        return colorProfile;
    }

    @Override
    public @Nullable ColorSpace getColorSpace() {
        return colorSpace;
    }

    @Override
    public void readHeader() throws IOException {
        ensureReadBuffer();

        int b;
        if (readByte() != 'G' || readByte() != 'I' || readByte() != 'F' ||
                readByte() != '8' || ((b = readByte()) != '7' && b != '9') || readByte() != 'a') {
            throw new DecoderException("Not GIF");
        }

        mScreenWidth = readShort();
        mScreenHeight = readShort();
        int packedField = readByte();
        int backgroundColorIndex = readByte();
        int pixelAspectRatio = readByte();

        mHasGlobalPalette = (packedField & 0x80) != 0;

        if (mHasGlobalPalette) {
            if (mGlobalPalette == null) {
                mGlobalPalette = new int[256];
            }
            readPalette(2 << (packedField & 7), mGlobalPalette);
        }
        if (mTmpRow == null || mTmpRow.length < mScreenWidth) {
            mTmpRow = new byte[mScreenWidth];
        }

        //mHeaderPos = mBuf.position();
    }

    @Override
    public @NonNull ImageInfo getInfo() {

        boolean isTransparent = ((mNextControlData >>> 24) & 1) != 0;

        ColorSpace colorSpace = this.colorSpace;
        if (colorProfile != null && colorSpace == null) {
            // non-parametric, convert to sRGB
            colorSpace = ColorSpaces.SRGB;
        }

        return ImageInfo.make(mScreenWidth, mScreenHeight,
                isTransparent ? ColorInfo.CT_RGBA_8888 : ColorInfo.CT_RGB_888,
                isTransparent ? ColorInfo.AT_UNPREMUL : ColorInfo.AT_OPAQUE, colorSpace);
    }

    /**
     * Read blocks until next image. Return true if there's next image, returns false
     * when the trailer is reached.
     */
    public boolean readBlocks() throws IOException {
        mNextControlData = 0;
        // @formatter:off
        for (;;) {
            // @formatter:on
            int ch = readByte();
            switch (ch) {
                case 0x2C -> { // Image Separator
                    unget();
                    return true;
                }
                case 0x21 -> { // Extension Introducer
                    int ext = readByte();
                    if (ext == 0xF9) { // Graphic Control Extension
                        mNextControlData = readControlCode();
                    } else if (ext == 0xFF) { // Application Extension
                        readApplication();
                    } else {
                        // Plain Text Extension, Comment Extension, and others are ignored
                        skipExtension();
                    }
                }
                case 0x3B -> {  // Trailer
                    return false;
                }
                default -> throw new DecoderException("Unknown block label 0x" + Integer.toHexString(ch));
            }
        }
    }

    public int getNextControlData() {
        return mNextControlData;
    }

    private int[] readPalette(int packedField) throws IOException {
        boolean localPalette = (packedField & 0x80) != 0;
        int[] palette;
        if (localPalette) {
            if (mTmpPalette == null) {
                mTmpPalette = new int[256];
            }
            int paletteSize = 2 << (packedField & 7);
            readPalette(paletteSize, mTmpPalette);
            palette = mTmpPalette;
        } else {
            if (!mHasGlobalPalette) {
                throw new DecoderException("No global palette");
            }
            palette = mGlobalPalette;
        }

        return palette;
    }

    @Override
    public void decodeImage(@NonNull Pixmap dstPixels) throws IOException {
        if (readByte() != 0x2C) {
            throw new DecoderException("Not image separator");
        }

        int left = readShort(), top = readShort(), width = readShort(), height = readShort();

        if (left + width > mScreenWidth || top + height > mScreenHeight) {
            throw new DecoderException("Image out of canvas bounds");
        }

        int packedField = readByte();

        decodeImage(left, top, width, height, packedField, mNextControlData, dstPixels);
    }

    @Override
    public int getPlayCount() {
        int loopCount = mLoopCount;
        if (loopCount == -1) {
            // loop count is unknown, play once
            return 1;
        }
        if (loopCount == 0) {
            // loop count is infinite
            return 0;
        }
        return loopCount + 1;
    }

    @Override
    public boolean decodeNextFrame(@Nullable Pixmap backupFrame, @NonNull Pixmap canvasFrame,
                                   @NonNull FrameInfo outInfo) throws IOException {
        int imageControlCode = syncNextFrame();

        if (imageControlCode < 0) {
            throw new IOException();
        }

        int left = readShort(), top = readShort(), width = readShort(), height = readShort();

        // check if the image is in the virtual screen boundaries
        if (left + width > mScreenWidth || top + height > mScreenHeight) {
            throw new DecoderException("Image out of canvas bounds");
        }

        int packedField = readByte();

        boolean isTransparent = ((imageControlCode >>> 24) & 1) != 0;

        int delayTime = imageControlCode & 0xFFFF; // frame duration in centi-seconds

        int disposalCode = (imageControlCode >>> 26) & 7;

        outInfo.delay = delayTime * 10;
        outInfo.disposal = switch (disposalCode) {
            case 2 -> FrameInfo.DISPOSAL_BACKGROUND;
            case 3 -> FrameInfo.DISPOSAL_PREVIOUS;
            default -> FrameInfo.DISPOSAL_NONE;
        };
        outInfo.blend = FrameInfo.BLEND_SRC_OVER;
        outInfo.frameLeft = left;
        outInfo.frameTop = top;
        outInfo.frameWidth = width;
        outInfo.frameHeight = height;
        outInfo.hasAlphaWithinBounds = isTransparent;

        if (disposalCode == 3 && backupFrame != null) {
            // restore to previous, make a backup
            backupFrame.setPixels(canvasFrame, 0, 0, 0, 0, mScreenWidth, mScreenHeight);
        }

        decodeImage(left, top, width, height, packedField,
                imageControlCode, canvasFrame);

        return true;
    }

    public void skipImage() throws IOException {
        if (readByte() != 0x2C) {
            throw new DecoderException("Not image separator");
        }
        int left = readShort(), top = readShort(), width = readShort(), height = readShort();

        int packedField = readByte();

        boolean localPalette = (packedField & 0x80) != 0;
        if (localPalette) {
            int paletteSize = 2 << (packedField & 7);
            skip(paletteSize * 3L);
        }

        // initial code size
        readByte();
        // data sub blocks
        skipExtension();
    }

    private void readPalette(int size, int @NonNull[] palette) throws IOException {
        // max size is 256, flatten the array [r0 g0 b0 r1 g1 b1 ...]
        for (int i = 0; i < size; ++i) {
            int r = readByte();
            int g = readByte();
            int b = readByte();
            palette[i] = (r) | (g << 8) | (b << 16) | 0xFF000000;
        }
        for (int i = size; i < palette.length; i++) {
            palette[i] = 0;
        }
    }

    public void skipExtension() throws IOException {
        for (int blockSize = readByte();
             blockSize != 0; // Block Terminator
             blockSize = readByte()) {
            skip(blockSize);
        }
    }

    private int readControlCode() throws IOException {
        int blockSize = readByte();
        if (blockSize != 4) {
            throw new DecoderException("Bad block size of Graphic Control Extension");
        }
        int packedField = readByte();
        int delayTime = readShort();
        int transparentIndex = readByte();

        if (readByte() != 0) { // Block Terminator
            throw new DecoderException("Block is not terminated");
        }
        return ((packedField & 0x1F) << 24) | (transparentIndex << 16) | delayTime;
    }

    private void readApplication() throws IOException {
        int blockSize = readByte();
        if (blockSize != 11) {
            throw new DecoderException("Bad block size of Application Extension");
        }

        byte[] id = new byte[11];
        for (int i = 0; i < id.length; i++) {
            id[i] = nextRawByte();
        }

        if (Arrays.equals(id, APP_NETSCAPE2_0) || Arrays.equals(id, APP_ANIMEXTS1_0)) {
            int size = readByte();
            if (size != 3) {
                throw new DecoderException("Bad block size of NETSCAPE/2.0 or ANIMEXTS/1.0");
            }
            int first = readByte();
            if (first == 0x01) {
                mLoopCount = readShort();
            } else {
                readShort();
            }
            if (readByte() != 0) {
                throw new DecoderException("NETSCAPE/2.0 or ANIMEXTS/1.0 block is not terminated");
            }
            return;
        }

        if (Arrays.equals(id, APP_ICC)) {

            ByteBuffer concatData = readConcatBlocks();
            byte[] data = concatData.array();
            // trim the array
            if (concatData.limit() < concatData.capacity()) {
                data = Arrays.copyOf(data, concatData.limit());
            }

            ColorProfile colorProfile = null;
            try {
                colorProfile = ColorProfile.parseICC(data);
            } catch (IllegalArgumentException e) {
            }

            this.colorProfile = colorProfile;

            ColorSpace colorSpace = null;
            if (colorProfile != null) {
                colorSpace = colorProfile.toColorSpace(useBT1886);
            }
            this.colorSpace = colorSpace;

            return;
        }

        if (Arrays.equals(id, APP_XMP)) {

        }

        //TODO XMP and others

        skipExtension();
    }

    private ByteBuffer readConcatBlocks() throws IOException {
        ByteBuffer dst = ByteBuffer.allocate(255);

        for (int blockSize = readByte();
             blockSize != 0;
             blockSize = readByte()) {
            if (!dst.hasRemaining()) {
                if (dst.capacity() >= Integer.MAX_VALUE / 2) {
                    throw new DecoderException("Concat block data is too big, failed to allocate buffer");
                }
                ByteBuffer newDst =
                        ByteBuffer.allocate(dst.capacity() * 2);
                dst.flip();
                newDst.put(dst);
                dst = newDst;
            }

            dst.limit(dst.position() + blockSize);
            readFully(dst);
        }

        return dst.flip();
    }

    private int syncNextFrame() throws IOException {
        int controlData = 0;
        boolean restarted = false;
        // @formatter:off
        for (;;) {
            // @formatter:on
            int ch = readByte();
            switch (ch) {
                case 0x2C -> { // Image Separator
                    return controlData;
                }
                case 0x21 -> { // Extension Introducer
                    if (readByte() == 0xF9) { // Graphic Control Extension
                        controlData = readControlCode();
                    } else {
                        skipExtension();
                    }
                }
                case -1, 0x3B -> {  // EOF or Trailer
                    if (restarted) {
                        // Dead loop or no data
                        return -1;
                    }
                    //mBuf.position(mHeaderPos); // Return to beginning
                    controlData = 0;
                    restarted = true;
                }
                default -> throw new IOException(String.valueOf(ch));
            }
        }
    }

    // Decode the one frame of GIF form the input stream using internal LZWDecoder class
    private void decodeImage(int left, int top, int width, int height, int packedField,
                             int imageControlCode, Pixmap dstPixels) throws IOException {

        final boolean isInterlaced = (packedField & 0x40) != 0;
        final boolean isTransparent = ((imageControlCode >>> 24) & 1) != 0;
        final int transparentIndex = isTransparent ? (imageControlCode >>> 16) & 0xFF : -1;

        final int[] palette = readPalette(packedField);

        final boolean hasAlpha = dstPixels.getColorType() == ColorInfo.CT_RGBA_8888;

        final int minRB = dstPixels.getInfo().minRowBytes();
        final Object dstBase = dstPixels.getBase();

        final byte[] row = mTmpRow;

        final int initialCodeSize = readByte();

        final LZWDecoder dec = LZWDecoder.getInstance();
        final byte[] string = dec.init(this, initialCodeSize);
        int y = 0, xr = width;
        int pass = 0, realY = 0;
        // @formatter:off
        for (;;) {
            // @formatter:on
            int len = dec.readString();
            if (len == -1) {
                throw new DecoderException("Unexpected end of info, not enough LZW codes provided");
            }
            for (int pos = 0; pos < len; ) {
                int ax = Math.min(xr, (len - pos));
                System.arraycopy(string, pos, row, width - xr, ax);
                pos += ax;
                if ((xr -= ax) > 0) {
                    continue;
                }

                long dstAddr = dstPixels.getAddress(left, realY + top);
                ByteBuffer dst = dstBase == null
                        ? MemoryUtil.memByteBuffer(dstAddr, minRB)
                        : ByteBuffer.wrap((byte[]) dstBase, (int) dstAddr, minRB);

                if (hasAlpha && transparentIndex < 0) {
                    for (int j = 0; j < width; j++) {
                        int index = row[j] & 0xFF;
                        dst.putInt(palette[index]);
                    }
                } else {
                    for (int j = 0; j < width; j++) {
                        int index = row[j] & 0xFF;
                        if (index != transparentIndex) {
                            int color = palette[index];
                            if (hasAlpha) {
                                dst.putInt(j << 2, color);
                            } else {
                                dst.put(j * 3, (byte) (color & 0xFF))
                                        .put(j * 3 + 1, (byte) ((color >> 8) & 0xFF))
                                        .put(j * 3 + 2, (byte) ((color >> 16) & 0xFF));
                            }
                        }
                    }
                }

                if (++y == height) { // image is full
                    if (readByte() != 0) { // Block Terminator
                        throw new DecoderException("Image block is not terminated");
                    }
                    return;
                }

                if (!isInterlaced) {
                    realY++;
                } else {
                    realY += interlaceStep[pass];
                    // skip empty passes
                    while (realY >= height) {
                        pass++;
                        realY = interlaceOffset[pass];
                    }
                }
                xr = width;
            }
        }
    }

    // GIF specification states that restore to background should fill the frame
    // with background color, but actually all modern programs fill with transparent color.
    private void restoreToBackground(byte[] image, int left, int top, int width, int height) {
        for (int y = 0; y < height; ++y) {
            int iPos = ((top + y) * mScreenWidth + left) * 4;
            for (int x = 0; x < width; iPos += 4, ++x) {
                image[iPos + 3] = 0;
            }
        }
    }

    private void decodePalette(byte[] srcImage, byte[] palette, int transparentIndex,
                               int left, int top, int width, int height, int disposalCode,
                               Pixmap restoreFrame, Pixmap canvasFrame) {
        // Restore to previous
        /*if (disposalCode == 3) {
            if (restoreFrame != null) {
                canvasFrame.setPixels(restoreFrame,
                        0, 0, 0, 0, mScreenWidth, mScreenHeight);
            }
            for (int y = 0; y < height; ++y) {
                int iPos = ((top + y) * mScreenWidth + left) * 4;
                int i = y * width;
                if (transparentIndex < 0) {
                    for (int x = 0; x < width; ++x) {
                        int index = 0xFF & srcImage[i + x];
                        pixels.put(iPos, palette, index * 4, 4);
                        iPos += 4;
                    }
                } else {
                    for (int x = 0; x < width; ++x) {
                        int index = 0xFF & srcImage[i + x];
                        if (index != transparentIndex) {
                            pixels.put(iPos, palette, index * 4, 4);
                        }
                        iPos += 4;
                    }
                }
            }
        } else {
            final byte[] image = mImage;
            for (int y = 0; y < height; ++y) {
                int iPos = ((top + y) * mScreenWidth + left) * 4;
                int i = y * width;
                if (transparentIndex < 0) {
                    for (int x = 0; x < width; ++x) {
                        int index = 0xFF & srcImage[i + x];
                        System.arraycopy(palette, index * 4, image, iPos, 4);
                        iPos += 4;
                    }
                } else {
                    for (int x = 0; x < width; ++x) {
                        int index = 0xFF & srcImage[i + x];
                        if (index != transparentIndex) {
                            System.arraycopy(palette, index * 4, image, iPos, 4);
                        }
                        iPos += 4;
                    }
                }
            }

            pixels.put(image).rewind();
            // Restore to background color
            if (disposalCode == 2) {
                restoreToBackground(mImage, left, top, width, height);
            }
        }*/
    }

    private int readByte() throws IOException {
        return nextRawByte() & 0xFF;
    }

    private int readShort() throws IOException {
        int lsb = readByte(), msb = readByte();
        return lsb | (msb << 8);
    }
}
