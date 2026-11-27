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
import icyllis.arc3d.sketch.GeometryUtils;
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
            //Font font = Font.createFont(Font.TRUETYPE_FONT, new File("E:/Free Fonts/biliw.otf"));
            font = font.deriveFont(32f);

            var gv = font.layoutGlyphVector(new FontRenderContext(null, true, true),
                    new char[]{'活'}, 0, 1, Font.LAYOUT_LEFT_TO_RIGHT);

            /*Path path = new PathBuilder()
                    .moveTo(84.187500f, 5.000000f)
                    //.cubicTo(65.531250f, 11.562500f, 35.062500f, 17.656250f, 8.343750f, 21.687500f)
                    //.quadTo(73.574646f, 8.733163f, 58.713863f, 12.177143f)
                    .lineTo(*//*43.853081f, 15.621124f, *//*28.139744f, 18.428200f)
                    .quadTo(18.078072f, 20.221468f, 8.343750f, 21.687500f)
                    //.lineTo(8.343750f, 21.687500f)
                    .lineTo(13.875000f, 36.500000f)
                    .close()
                    .build();*/

            Path path = new PathBuilder()
                    .moveTo(22.312500f, 4.781250f)
                    .lineTo(12.937500f, 15.843750f)
                    .quadTo(28.312500f, 27.468750f, 45.750000f, 42.656250f)
                    .quadTo(51.000000f, 36.281250f, 56.062500f, 29.906250f)
                    .quadTo(40.312500f, 17.156250f, 22.312500f, 4.781250f)
                    .close()
                    .build();
            
            testSDF(path, 6);

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static void testSDF(java.awt.Shape path, float scale) {
        var generator = new AnalyticSDFGenerator();

        //generator.setDistanceFieldMagnitude(8);
        int distanceFieldPad = generator.getDistanceFieldMagnitude();

        Rectangle2D bounds = path.getBounds2D();
        bounds.setRect(bounds.getX() * scale, bounds.getY() * scale, bounds.getWidth() * scale, bounds.getHeight() * scale);
        Rectangle devBounds = bounds.getBounds();
        int width = devBounds.width + 2 * distanceFieldPad;
        int height = devBounds.height + 2 * distanceFieldPad;
        int dx = devBounds.x;
        int dy = devBounds.y;

        AffineTransform drawMatrix = AffineTransform.getScaleInstance(scale, scale);
        drawMatrix.preConcatenate(AffineTransform.getTranslateInstance(
                distanceFieldPad - dx, distanceFieldPad - dy
        ));
        Path2D.Float devPath = new Path2D.Float(path, drawMatrix);

        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_BYTE_GRAY);
        byte[] sdf = ((DataBufferByte) image.getRaster().getDataBuffer()).getData();

        GeometryUtils.convertCubicToQuads(
                84.187500f, 5.000000f,
                65.531250f, 11.562500f, 35.062500f, 17.656250f, 8.343750f, 21.687500f,
                1, (x0, y0, x1, y1, x2, y2) -> {
                    System.out.printf("addQuad(%f, %f, %f, %f, %f, %f);\n", x0, y0, x1, y1, x2, y2);
                }
        );

        Area processed = new Area(devPath);
        System.out.println("Processed path");
        TestPathUtils.print(processed);
        TestPathUtils.writePath(processed, true, "run/test_path_preprocessed.png");


        generator.generateDistanceFieldFromPath(
                processed, false,
                width, height, sdf, 0, width
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
