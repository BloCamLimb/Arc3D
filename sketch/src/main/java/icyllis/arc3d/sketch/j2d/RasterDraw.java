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

import icyllis.arc3d.core.Rect2f;
import icyllis.arc3d.sketch.BlendMode;
import icyllis.arc3d.sketch.Matrixc;
import icyllis.arc3d.sketch.Paint;
import icyllis.arc3d.sketch.Path;
import icyllis.arc3d.sketch.PathBuilder;
import icyllis.arc3d.sketch.PathUtils;
import org.jetbrains.annotations.ApiStatus;

import java.awt.*;
import java.awt.Color;
import java.awt.geom.AffineTransform;

//TODO
@ApiStatus.Internal
public class RasterDraw {

    private static final float MAX_FOR_MATH = Float.MAX_VALUE * 0.25f;
    private static final BasicStroke sHairlineStroke = new BasicStroke(0);

    // const args
    public static void drawPath(Graphics2D g2d, Matrixc ctm,
                                Path origPath, Paint paint) {
        boolean doFill;
        if (ctm.hasPerspective()) {

            PathBuilder builder = new PathBuilder();

            if ((paint.getStyle() != Paint.FILL && paint.getStrokeWidth() > 0) ||
                    paint.getPathEffect() != null) {
                doFill = PathUtils.fillPathWithPaint(
                        origPath, paint, builder, null, ctm
                );
            } else {
                doFill = paint.getStyle() == Paint.FILL;
                builder.set(origPath);
            }

            builder.transform(ctm);
            Path devPath = builder.build();

            var bounds = new Rect2f();
            devPath.getBounds(bounds);
            // use ! expression so we return true if bounds contains NaN
            if (!(bounds.left() >= -MAX_FOR_MATH && bounds.top() >= -MAX_FOR_MATH &&
                    bounds.right() <= MAX_FOR_MATH && bounds.bottom() <= MAX_FOR_MATH)) {
                return;
            }

            var tx = new AffineTransform();
            g2d.setTransform(tx);
            preparePaint(g2d, paint);

            if (doFill) {
                g2d.fill(devPath);
            } else {
                g2d.setStroke(sHairlineStroke);
                g2d.draw(devPath);
            }
        } else {
            //TODO path effect, stroke-and-fill

            var tx = new AffineTransform();
            prepareTransform(tx, ctm);
            g2d.setTransform(tx);
            preparePaint(g2d, paint);

            if (paint.getStyle() == Paint.FILL) {
                g2d.fill(origPath);
            } else {
                BasicStroke stroke = toStroke(paint);
                g2d.setStroke(stroke);
                g2d.draw(origPath);
            }
        }
    }

    public static BasicStroke toStroke(Paint paint) {
        int cap = switch (paint.getStrokeCap()) {
            case Paint.CAP_BUTT -> BasicStroke.CAP_BUTT;
            case Paint.CAP_ROUND -> BasicStroke.CAP_ROUND;
            case Paint.CAP_SQUARE -> BasicStroke.CAP_SQUARE;
            default -> throw new AssertionError();
        };
        int join = switch (paint.getStrokeJoin()) {
            case Paint.JOIN_MITER -> BasicStroke.JOIN_MITER;
            case Paint.JOIN_ROUND -> BasicStroke.JOIN_ROUND;
            case Paint.JOIN_BEVEL -> BasicStroke.JOIN_BEVEL;
            default -> throw new AssertionError();
        };

        return new BasicStroke(paint.getStrokeWidth(),
                cap, join, paint.getStrokeMiter());
    }

    public static boolean prepareTransform(AffineTransform dst, Matrixc src) {
        if (src.hasPerspective()) {
            return false;
        }

        if (src.isIdentity()) {
            dst.setToIdentity();
        } else if (src.isTranslate()) {
            dst.setToTranslation(src.getTranslateX(), src.getTranslateY());
        } else {
            dst.setTransform(
                    src.getScaleX(), src.getShearY(),
                    src.getShearX(), src.getScaleY(),
                    src.getTranslateX(), src.getTranslateY()
            );
        }

        return true;
    }

    public static void preparePaint(Graphics2D g2d, Paint paint) {
        g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
                paint.isAntiAlias() ? RenderingHints.VALUE_ANTIALIAS_ON : RenderingHints.VALUE_ANTIALIAS_OFF);
        g2d.setRenderingHint(RenderingHints.KEY_DITHERING,
                paint.isDither() ? RenderingHints.VALUE_DITHER_ENABLE : RenderingHints.VALUE_DITHER_DEFAULT);
        g2d.setRenderingHint(RenderingHints.KEY_RENDERING,
                RenderingHints.VALUE_RENDER_QUALITY);
        g2d.setRenderingHint(RenderingHints.KEY_COLOR_RENDERING,
                RenderingHints.VALUE_COLOR_RENDER_QUALITY);
        g2d.setRenderingHint(RenderingHints.KEY_ALPHA_INTERPOLATION,
                RenderingHints.VALUE_ALPHA_INTERPOLATION_QUALITY);
        g2d.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL,
                RenderingHints.VALUE_STROKE_PURE);

        g2d.setColor(new Color(paint.getRed(), paint.getGreen(), paint.getBlue(), paint.getAlpha()));
        var bm = paint.getBlendMode();
        if (bm == null) {
            bm = BlendMode.SRC_OVER;
        }
        var comp = switch (bm) {
            case CLEAR -> AlphaComposite.Clear;
            case SRC -> AlphaComposite.Src;
            case DST -> AlphaComposite.Dst;
            case SRC_OVER -> AlphaComposite.SrcOver;
            case DST_OVER -> AlphaComposite.DstOver;
            case SRC_IN -> AlphaComposite.SrcIn;
            case DST_IN -> AlphaComposite.DstIn;
            case SRC_OUT -> AlphaComposite.SrcOut;
            case DST_OUT -> AlphaComposite.DstOut;
            case SRC_ATOP -> AlphaComposite.SrcAtop;
            case DST_ATOP -> AlphaComposite.DstAtop;
            case XOR -> AlphaComposite.Xor;
            default -> AlphaComposite.SrcOver;
        };
        g2d.setComposite(comp);
    }
}
