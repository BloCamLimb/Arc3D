/*
 * This file is part of Arc3D.
 *
 * Copyright (C) 2022-2025 BloCamLimb <pocamelards@gmail.com>
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

package icyllis.arc3d.sketch.test;

import icyllis.arc3d.sketch.AnalyticSDFGenerator;
import icyllis.arc3d.sketch.Paint;
import icyllis.arc3d.sketch.Path;
import icyllis.arc3d.sketch.PathBuilder;
import icyllis.arc3d.sketch.PathStroker;
import icyllis.arc3d.sketch.StrokeRec;

import javax.imageio.ImageIO;
import java.awt.BasicStroke;
import java.awt.Font;
import java.awt.Rectangle;
import java.awt.font.FontRenderContext;
import java.awt.geom.AffineTransform;
import java.awt.geom.Area;
import java.awt.geom.Path2D;
import java.awt.geom.Rectangle2D;
import java.awt.image.BufferedImage;
import java.awt.image.DataBufferByte;
import java.io.File;
import java.io.IOException;

public class TestPath {

    public static void main(String[] args) {
        Path src = new PathBuilder()
                .moveTo(170, 120)
                .lineTo(130, 160)
                .lineTo(100, 200)
                .lineTo(180, 140)
                .lineTo(170, 130)
                .lineTo(170, 120)
                .quadTo(/*160, 130, */120, 100, 190, 60)
                .close()
                .build();
        System.out.println("Src path:");
        src.forEach(TestPathUtils.PRINTER);

        Path result;
        //result = testMiterJoin();
        result = testRoundJoin(src);
        TestPathUtils.writePath(result, true, "run/test_path_stroke_round_join.png");
        result = testRoundJoin2(src);
        TestPathUtils.writePath(result, true, "run/test_path_stroke_round_join2.png");
        System.out.println("Empty path bytes: " + new Path().estimatedByteSize());

        try {
            Font font = new Font("Microsoft YaHei UI", Font.PLAIN, 1);
            font = font.deriveFont(32f);

            var gv = font.layoutGlyphVector(new FontRenderContext(null, true, true),
                    new char[]{'♂'}, 0, 1, Font.LAYOUT_LEFT_TO_RIGHT);
            testSDF(gv.getOutline(), 6);

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static void testSDF(java.awt.Shape path, float scale) {
        Rectangle2D bounds = path.getBounds2D();
        float dx = (float) Math.floor(bounds.getX() * scale);
        float dy = (float) Math.floor(bounds.getY() * scale);
        bounds.setRect(bounds.getX() * scale - dx, bounds.getY() * scale - dy, bounds.getWidth() * scale, bounds.getHeight() * scale);

        AffineTransform drawMatrix = AffineTransform.getTranslateInstance(-dx, -dy);
        drawMatrix.scale(scale, scale);
        drawMatrix.preConcatenate(AffineTransform.getTranslateInstance(
                AnalyticSDFGenerator.SK_DistanceFieldPad, AnalyticSDFGenerator.SK_DistanceFieldPad
        ));
        Rectangle devBounds = bounds.getBounds();
        Path2D.Float devPath = new Path2D.Float(path, drawMatrix);
        assert devBounds.x == 0 && devBounds.y == 0;

        int width = devBounds.width + 2 * AnalyticSDFGenerator.SK_DistanceFieldPad;
        int height = devBounds.height + 2 * AnalyticSDFGenerator.SK_DistanceFieldPad;
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_BYTE_GRAY);
        byte[] sdf = ((DataBufferByte) image.getRaster().getDataBuffer()).getData();

        Area processed = new Area(devPath);
        System.out.println("Processed path");
        TestPathUtils.print(processed);
        TestPathUtils.writePath(processed, true, "run/test_path_preprocessed.png");


        AnalyticSDFGenerator.generateDistanceFieldFromPath(
                sdf, 0, processed, false,
                width, height, width, new AnalyticSDFGenerator.Scratch()
        );

        try {
            ImageIO.write(image, "png", new File("run/test_distance_field_from_path.png"));
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    /*public static Path testMiterJoin() {
        Path src = new Path();
        src.moveTo(100, 120);
        src.lineTo(130, 160);
        src.lineTo(50, 120);
        System.out.println("Src path:");
        src.forEach(TestPathUtils.PRINTER);

        Path dst = new Path();
        StrokeRec strokeRec = new StrokeRec();
        strokeRec.setWidth(10);
        strokeRec.setStrokeParams(Paint.CAP_ROUND, Paint.JOIN_MITER, Paint.ALIGN_CENTER, 4);
        strokeRec.applyToPath(src, dst);
        System.out.println("Dst path:");
        dst.forEach(TestPathUtils.PRINTER);

        System.out.println("Src bounds: " + src.getBounds() + ", bytes: " + src.estimatedByteSize());
        for (int i = 0; i < 2; i++) {
            System.out.println("Dst bounds: " + dst.getBounds() + ", bytes: " + dst.estimatedByteSize());
            dst.trimToSize();
        }

        return dst;
    }*/

    public static Path testRoundJoin(Path src) {
        PathBuilder dst = new PathBuilder();
        PathStroker stroker = new PathStroker();
        stroker.init(
                dst, 10 * 0.5f, Paint.CAP_ROUND, Paint.JOIN_ROUND, 4, 1
        );
        src.forEach(stroker);
        var result = dst.build();
        System.out.println("Result path:");
        result.forEach(TestPathUtils.PRINTER);

        System.out.println("Src bounds: " + src.getBounds2D() + ", bytes: " + src.estimatedByteSize());
        System.out.println("Result bounds: " + result.getBounds2D() + ", bytes: " + result.estimatedByteSize());

        return result;
    }

    public static Path testRoundJoin2(Path src) {
        PathBuilder dst = new PathBuilder();
        BasicStroke stroker = new BasicStroke(10, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER, 4);
        var resultShape = stroker.createStrokedShape(src);
        StrokeRec.appendStrokedShape(resultShape, dst);
        var result = dst.build();
        System.out.println("Result path:");
        result.forEach(TestPathUtils.PRINTER);

        System.out.println("Src bounds: " + src.getBounds2D() + ", bytes: " + src.estimatedByteSize());
        System.out.println("Result bounds: " + result.getBounds2D() + ", bytes: " + result.estimatedByteSize());

        return result;
    }
}
