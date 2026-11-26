/*
 * This file is part of Arc3D.
 *
 * Copyright (C) 2024-2025 BloCamLimb <pocamelards@gmail.com>
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

package icyllis.arc3d.sketch.j2d;

import icyllis.arc3d.core.*;
import icyllis.arc3d.sketch.Glyph;
import icyllis.arc3d.sketch.PathBuilder;
import icyllis.arc3d.sketch.ScalerContext;
import icyllis.arc3d.sketch.StrikeDesc;
import org.jspecify.annotations.NonNull;

import java.awt.Color;
import java.awt.Font;
import java.awt.*;
import java.awt.font.FontRenderContext;
import java.awt.font.GlyphVector;
import java.awt.geom.AffineTransform;
import java.awt.geom.Path2D;
import java.awt.image.BufferedImage;
import java.awt.image.DataBufferByte;
import java.nio.ByteBuffer;

/**
 * Wraps Java2D's freetype scaler.
 * <p>
 * Only suitable for outline fonts, bitmap fonts are not supported.
 */
public class FreetypeScalerContext extends ScalerContext {

    private final Font mFont;
    private final FontRenderContext mFRC;

    private final BufferedImage mBufferedImage;
    private final Graphics2D mG2D;

    public FreetypeScalerContext(FreetypeTypeface typeface, StrikeDesc desc) {
        super(typeface, desc);
        // init the device transform, AA hint, and FM hint
        var transform = new AffineTransform(
                mDesc.getPostScaleX(),
                mDesc.getPostShearY(),
                mDesc.getPostShearX(),
                mDesc.getPostScaleY(),
                0, 0);
        Object aa;
        Object fm;
        if (isHinting() && isSubpixel() && getMaskFormat() != Glyph.kBW_Format) {
            // we rely on FreeType Harmony LCD rendering
            aa = switch (computeAxisAlignmentForHText()) {
                case StrikeDesc.kX_AxisAlignment -> RenderingHints.VALUE_TEXT_ANTIALIAS_LCD_HRGB;
                case StrikeDesc.kY_AxisAlignment -> RenderingHints.VALUE_TEXT_ANTIALIAS_LCD_VRGB;
                default -> {
                    // captured by Typeface
                    assert false;
                    yield RenderingHints.VALUE_TEXT_ANTIALIAS_ON;
                }
            };
        } else if (getMaskFormat() != Glyph.kBW_Format) {
            aa = RenderingHints.VALUE_TEXT_ANTIALIAS_ON;
        } else {
            aa = RenderingHints.VALUE_TEXT_ANTIALIAS_OFF;
        }
        if (!isHinting()) {
            fm = RenderingHints.VALUE_FRACTIONALMETRICS_ON;
        } else {
            fm = RenderingHints.VALUE_FRACTIONALMETRICS_OFF;
        }
        mFont = typeface.getFont()
                .deriveFont(transform)
                .deriveFont(mDesc.getTextSize());
        // we pass the device transform to mFont instead of here
        // because JDK returns metrics back to user space, not in device space
        mFRC = new FontRenderContext(
                null,
                aa,
                fm
        );
        int imageType;
        if (mFRC.getAntiAliasingHint() == RenderingHints.VALUE_TEXT_ANTIALIAS_LCD_HRGB ||
                mFRC.getAntiAliasingHint() == RenderingHints.VALUE_TEXT_ANTIALIAS_LCD_VRGB) {
            imageType = BufferedImage.TYPE_INT_RGB;
        } else {
            imageType = BufferedImage.TYPE_BYTE_GRAY;
        }
        mBufferedImage = new BufferedImage(Glyph.kMaxDimensionForAtlas, Glyph.kMaxDimensionForAtlas,
                imageType);
        var g2d = mBufferedImage.createGraphics();
        g2d.setColor(Color.WHITE);
        g2d.setBackground(Color.BLACK);
        g2d.setComposite(AlphaComposite.Src);
        g2d.setRenderingHint(RenderingHints.KEY_RENDERING,
                RenderingHints.VALUE_RENDER_QUALITY);
        g2d.setRenderingHint(RenderingHints.KEY_COLOR_RENDERING,
                RenderingHints.VALUE_COLOR_RENDER_QUALITY);
        g2d.setRenderingHint(RenderingHints.KEY_ALPHA_INTERPOLATION,
                RenderingHints.VALUE_ALPHA_INTERPOLATION_QUALITY);
        g2d.setRenderingHint(RenderingHints.KEY_TEXT_LCD_CONTRAST, 100);
        mG2D = g2d;
    }

    @Override
    protected GlyphMetrics generateMetrics(Glyph glyph) {
        GlyphMetrics metrics = new GlyphMetrics(glyph.getMaskFormat());
        GlyphVector gv = mFont.createGlyphVector(mFRC, new int[]{glyph.getGlyphID()});
        final boolean hinting = isHinting();
        Rectangle pixelBounds = null;
        if (hinting) {
            // JDK has a rasterization limit of 1024x1024 for getPixelBounds,
            // which can cause pixel bounds to be empty if glyph large. Switch to
            // gv.getVisualBounds() is equivalent to FT_Outline_Get_BBox
            // However, it's fixed in jdk-8328896: Fontmetrics for large Fonts has zero width
            pixelBounds = gv.getPixelBounds(null, 0, 0);
        }
        // use floating-point bounds if not hinting or large
        if (!hinting || pixelBounds.isEmpty()) {
            var bounds = gv.getVisualBounds();
            metrics.mLeft = (float) bounds.getMinX();
            metrics.mTop = (float) bounds.getMinY();
            metrics.mRight = (float) bounds.getMaxX();
            metrics.mBottom = (float) bounds.getMaxY();
            if (isSubpixel() &&
                    metrics.mLeft < metrics.mRight &&
                    metrics.mTop < metrics.mBottom) {
                float subX = glyph.getSubX();
                float subY = glyph.getSubY();
                metrics.mLeft += subX;
                metrics.mTop += subY;
                metrics.mRight += subX;
                metrics.mBottom += subY;
            }
        } else {
            // use pixel bounds if hinting, this includes subpixel
            // 2/3 subpixel is -1/3 shifted by 1
            metrics.mLeft = (float) pixelBounds.getMinX();
            metrics.mTop = (float) pixelBounds.getMinY();
            metrics.mRight = (float) pixelBounds.getMaxX() + (glyph.getSubXIndex() == 2 ? 1 : 0);
            metrics.mBottom = (float) pixelBounds.getMaxY() + (glyph.getSubYIndex() == 2 ? 1 : 0);
        }
        return metrics;
    }

    @Override
    protected void generateImage(Glyph glyph, Object imageBase, long imageAddress) {
        GlyphVector gv = mFont.createGlyphVector(mFRC, new int[]{glyph.getGlyphID()});
        var g2d = mG2D;
        if (mFRC.getAntiAliasingHint() == RenderingHints.VALUE_TEXT_ANTIALIAS_LCD_HRGB ||
                mFRC.getAntiAliasingHint() == RenderingHints.VALUE_TEXT_ANTIALIAS_LCD_VRGB) {
            g2d.clearRect(0, 0, glyph.getWidth(), glyph.getHeight());
            g2d.drawGlyphVector(gv, -glyph.getLeft(), -glyph.getTop());

            // we rely on FreeType Harmony LCD rendering, so extract subpixel data
            int shift = 8;
            int offsetX = 0;
            int offsetY = 0;
            if (mFRC.getAntiAliasingHint() == RenderingHints.VALUE_TEXT_ANTIALIAS_LCD_HRGB) {
                switch (glyph.getSubXIndex()) {
                    case 1:
                        shift = 16;
                        break;
                    case 2:
                        shift = 0;
                        // 2/3 subpixel is -1/3 shifted by 1
                        offsetX = 1;
                        break;
                }
            } else {
                switch (glyph.getSubYIndex()) {
                    case 1:
                        shift = 16;
                        break;
                    case 2:
                        shift = 0;
                        // 2/3 subpixel is -1/3 shifted by 1
                        offsetY = 1;
                        break;
                }
            }

            ByteBuffer dst = PixelUtils.toByteBufferUncapped(imageBase, imageAddress);
            int dstOffset = offsetY * glyph.getRowBytes() + offsetX;
            for (int y = 0; y < glyph.getHeight() - offsetY; y++) {
                for (int x = 0; x < glyph.getWidth() - offsetX; x++) {
                    dst.put(dstOffset + x, (byte) ((mBufferedImage.getRGB(x, y) >> shift) & 0xFF));
                }
                dstOffset += glyph.getRowBytes();
            }
        } else {
            float subX = glyph.getSubX();
            float subY = glyph.getSubY();
            g2d.setTransform(new AffineTransform());
            g2d.clearRect(0, 0, glyph.getWidth(), glyph.getHeight());
            if (subX == 0 && subY == 0) {
                // JDK will use freetype if text size < 100, otherwise use outline
                g2d.drawGlyphVector(gv, -glyph.getLeft(), -glyph.getTop());
            } else {
                // outline rendering, no hinting
                var outline = gv.getOutline(subX, subY);
                g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
                        glyph.getMaskFormat() != Glyph.kBW_Format
                                ? RenderingHints.VALUE_ANTIALIAS_ON
                                : RenderingHints.VALUE_ANTIALIAS_OFF);
                g2d.translate(-glyph.getLeft(), -glyph.getTop());
                g2d.fill(outline);
            }

            var data = ((DataBufferByte) mBufferedImage.getRaster().getDataBuffer()).getData();

            switch (glyph.getMaskFormat()) {
                case Glyph.kBW_Format -> {
                    PixelUtils.packA8ToBW(
                            data,
                            0,
                            mBufferedImage.getWidth(),
                            imageBase,
                            imageAddress,
                            glyph.getRowBytes(),
                            glyph.getWidth(),
                            glyph.getHeight()
                    );
                }
                case Glyph.kA8_Format -> {
                    PixelUtils.copyImage(
                            data,
                            0,
                            mBufferedImage.getWidth(),
                            imageBase,
                            imageAddress,
                            glyph.getRowBytes(),
                            glyph.getWidth(),
                            glyph.getHeight()
                    );
                }
            }
        }
    }

    @Override
    protected boolean generatePath(@NonNull Glyph glyph, @NonNull PathBuilder dst) {
        GlyphVector gv = mFont.createGlyphVector(mFRC, new int[]{glyph.getGlyphID()});
        var outline = gv.getOutline();

        // the above code should return GeneralPath and WIND_NON_ZERO,
        // but set it explicitly here to make sense
        if (outline instanceof Path2D) {
            dst.setWindingRule(((Path2D) outline).getWindingRule());
        } // else PathBuilder default is WIND_NON_ZERO anyway

        dst.addShape(outline, null);

        // even if the glyph has no path, gv.getOutline() will return an empty path,
        // so we cannot distinguish between 'have no path' and 'have empty path',
        // always return true here by assuming it has path
        return true;
    }
}
