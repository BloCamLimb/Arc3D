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
 *
 * This file incorporates work covered by the following copyright and
 * permission notice:
 *
 *   Copyright 2017 ARM Ltd.
 *
 *   Redistribution and use in source and binary forms, with or without
 *   modification, are permitted provided that the following conditions are
 *   met:
 *
 *     * Redistributions of source code must retain the above copyright
 *       notice, this list of conditions and the following disclaimer.
 *
 *     * Redistributions in binary form must reproduce the above copyright
 *       notice, this list of conditions and the following disclaimer in
 *       the documentation and/or other materials provided with the
 *       distribution.
 *
 *     * Neither the name of the copyright holder nor the names of its
 *       contributors may be used to endorse or promote products derived
 *       from this software without specific prior written permission.
 *
 *   THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS
 *   "AS IS" AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT
 *   LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR
 *   A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT
 *   OWNER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL,
 *   SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT
 *   LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE,
 *   DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY
 *   THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
 *   (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE
 *   OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */

package icyllis.arc3d.sketch;

import icyllis.arc3d.core.MathUtil;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import org.lwjgl.system.MemoryUtil;

import java.awt.geom.PathIterator;
import java.util.ArrayList;

/**
 * Analytic 2D signed distance field generation.
 * <p>
 * This is a port from Skia src/gpu/ganesh/GrDistanceFieldGenFromVector.cpp, with bug fixes.
 * See
 * <a href="https://dl.acm.org/doi/10.1145/2897839.2927417">Practical analytic 2D signed distance field generation</a>
 */
public final class AnalyticSDFGenerator {

    // TODO configurable
    public static final int   SK_DistanceFieldPad       = 4;
    public static final int   SK_DistanceFieldMagnitude = SK_DistanceFieldPad;

    /**
     * Scratch is an opaque object that provides buffers for SDF generation.
     * Callers may reuse the same object for multiple generate* calls to reduce
     * object allocation. Not thread safe for sure.
     */
    public static final class Scratch {
        // RowData fields
        int    rd_intersectionType;
        int    rd_quadXDirection;
        int    rd_scanlineXDirection;
        double rd_yAtIntersection;
        double rd_xAtIntersection1;
        double rd_xAtIntersection2;

        // SegSide
        int outSide;

        // Temp DPoint storage (reused across mapPoint calls)
        final double[] pt = new double[2];  // general purpose

        // Temp Point storage to obtain path segments
        final float[] pts = new float[6];

        // Storage for all working segments
        final ArrayList<PathSegment> segments = new ArrayList<>();

        // Storage for working distance field data
        // int[] interleaved: [distSqBits, deltaWinding, ...]
        // distSq: distance squared to nearest (so far) edge
        // deltaWinding: +1 or -1 whenever a scanline cross over a segment
        int[] dfData;
    }


    private static final double kClose            = 1.0 / 16.0;
    private static final double kCloseSqd         = kClose * kClose;
    private static final double kNearlyZero       = 1.0 / (1 << 18);
    private static final double kTangentTolerance = 1.0 / (1 << 11);
    private static final float  kConicTolerance   = 0.25f;

    // -- SegSide ---------------------------------------------------------------
    static final int kLeft_SegSide  = -1;
    static final int kOn_SegSide    =  0;
    static final int kRight_SegSide =  1;
    static final int kNA_SegSide    =  2;

    // -- RowData intersection types --------------------------------------------
    private static final int RD_NO_INTERSECTION       = 0;
    private static final int RD_VERTICAL_LINE         = 1;
    private static final int RD_TANGENT_LINE          = 2;
    private static final int RD_TWO_POINTS_INTERSECT  = 3;

    static final class PathSegment {
        // These enum values are assumed in member functions below.
        static final int kLine = 0;
        static final int kQuad = 1;

        int fType; // kLine or kQuad

        // fPts: 3 points, interleaved floats: [x0,y0, x1,y1, x2,y2]
        // line uses 2 pts, quad uses 3 pts
        final float[] fPts = new float[6];

        double fP0T_x, fP0T_y;
        double fP2T_x, fP2T_y;

        // DAffineMatrix: double[6]
        final double[] fXformMatrix = new double[6]; // transforms the segment into canonical space

        double fScalingFactor;
        double fScalingFactorSqd;
        double fNearlyZeroScaled;
        double fTangentTolScaledSqd;

        // Bounding box
        float fBB_left, fBB_top, fBB_right, fBB_bottom;

        int countPoints() { return fType + 2; }

        float endPtX() { return fPts[(fType + 1) * 2    ]; }
        float endPtY() { return fPts[(fType + 1) * 2 + 1]; }

        void init(Scratch s) {
            double p0x = fPts[0], p0y = fPts[1];
            double p2x = endPtX(), p2y = endPtY();

            // bounding box
            fBB_left   = Math.min(fPts[0], (float)p2x);
            fBB_left   = Math.min(fPts[2], fBB_left);
            fBB_right  = Math.max(fPts[0], (float)p2x);
            fBB_right  = Math.max(fPts[2], fBB_right);
            fBB_top    = Math.min(fPts[1], (float)p2y);
            fBB_bottom = Math.max(fPts[1], (float)p2y);
            //TODO need exact bounding box for quad, currently we overestimate

            if (fType == kLine) {
                fScalingFactorSqd = fScalingFactor = 1.0;
                double hypotenuse = Math.sqrt(distanceSquared(p0x, p0y, p2x, p2y));
                if (Math.abs(hypotenuse) < 1.0e-100) {
                    matrixReset(fXformMatrix);
                } else {
                    double cosTheta = (p2x - p0x) / hypotenuse;
                    double sinTheta = (p2y - p0y) / hypotenuse;

                    // rotates the segment to the x-axis, with p0 at the origin
                    matrixSetAffine(fXformMatrix,
                            cosTheta, sinTheta, -(cosTheta * p0x) - (sinTheta * p0y),
                            -sinTheta, cosTheta, (sinTheta * p0x) - (cosTheta * p0y)
                    );
                }
            } else {
                assert (fType == kQuad);

                // grow bounding box to include midpoint
                float mx = fPts[0]*0.25f + fPts[2]*0.5f + fPts[4]*0.25f; // midpoint of curve
                float my = fPts[1]*0.25f + fPts[3]*0.5f + fPts[5]*0.25f;
                //TODO need exact bounding box for quad, midpoint method from Skia is wrong
                // use workaround above
                fBB_left   = Math.min(fBB_left,   mx);
                fBB_right  = Math.max(fBB_right,  mx);
                fBB_top    = Math.min(fBB_top,    my);
                fBB_bottom = Math.max(fBB_bottom, my);

                final double p1x = fPts[2];
                final double p1y = fPts[3];

                final double p0xSqd = p0x * p0x;
                final double p0ySqd = p0y * p0y;
                final double p2xSqd = p2x * p2x;
                final double p2ySqd = p2y * p2y;
                final double p1xSqd = p1x * p1x;
                final double p1ySqd = p1y * p1y;

                final double p01xProd = p0x * p1x;
                final double p02xProd = p0x * p2x;
                final double b12xProd = p1x * p2x;
                final double p01yProd = p0y * p1y;
                final double p02yProd = p0y * p2y;
                final double b12yProd = p1y * p2y;

                // calculate quadratic params
                final double sqrtA = p0y - (2.0 * p1y) + p2y;
                final double a = sqrtA * sqrtA;
                final double h = -1.0 * (p0y - (2.0 * p1y) + p2y) * (p0x - (2.0 * p1x) + p2x);
                final double sqrtB = p0x - (2.0 * p1x) + p2x;
                final double b = sqrtB * sqrtB;
                final double c = (p0xSqd * p2ySqd) - (4.0 * p01xProd * b12yProd)
                        - (2.0 * p02xProd * p02yProd) + (4.0 * p02xProd * p1ySqd)
                        + (4.0 * p1xSqd * p02yProd) - (4.0 * b12xProd * p01yProd)
                        + (p2xSqd * p0ySqd);
                final double g = (p0x * p02yProd) - (2.0 * p0x * p1ySqd)
                        + (2.0 * p0x * b12yProd) - (p0x * p2ySqd)
                        + (2.0 * p1x * p01yProd) - (4.0 * p1x * p02yProd)
                        + (2.0 * p1x * b12yProd) - (p2x * p0ySqd)
                        + (2.0 * p2x * p01yProd) + (p2x * p02yProd)
                        - (2.0 * p2x * p1ySqd);
                final double f = -((p0xSqd * p2y) - (2.0 * p01xProd * p1y)
                        - (2.0 * p01xProd * p2y) - (p02xProd * p0y)
                        + (4.0 * p02xProd * p1y) - (p02xProd * p2y)
                        + (2.0 * p1xSqd * p0y) + (2.0 * p1xSqd * p2y)
                        - (2.0 * b12xProd * p0y) - (2.0 * b12xProd * p1y)
                        + (p2xSqd * p0y));

                final double cosTheta = Math.sqrt(a / (a + b));
                final double sinTheta = -1.0 * signOf((a + b) * h) * Math.sqrt(b / (a + b));

                final double gDef = cosTheta*g - sinTheta*f;
                final double fDef = sinTheta*g + cosTheta*f;


                final double x0 = gDef / (a + b);
                final double y0 = (1.0 / (2.0*fDef)) * (c - (gDef*gDef / (a + b)));


                final double lambda = -1.0 * ((a + b) / (2.0*fDef));
                fScalingFactor    = Math.abs(1.0 / lambda);
                fScalingFactorSqd = fScalingFactor * fScalingFactor;

                final double lambda_cosTheta = lambda * cosTheta;
                final double lambda_sinTheta = lambda * sinTheta;

                // transforms to lie on a canonical y = x^2 parabola
                matrixSetAffine(fXformMatrix,
                        lambda_cosTheta, -lambda_sinTheta, lambda*x0,
                        lambda_sinTheta,  lambda_cosTheta, lambda*y0);
            }

            fNearlyZeroScaled      = kNearlyZero / fScalingFactor;
            fTangentTolScaledSqd   = kTangentTolerance * kTangentTolerance / fScalingFactorSqd;

            // map p0 and p2 into canonical space
            double[] tmp = s.pt;
            matrixMapPoint(fXformMatrix, p0x, p0y, tmp);
            fP0T_x = tmp[0]; fP0T_y = tmp[1];
            matrixMapPoint(fXformMatrix, p2x, p2y, tmp);
            fP2T_x = tmp[0]; fP2T_y = tmp[1];
        }
    }

    // =========================================================================
    //  DAffineMatrix helpers  (operate on double[6])
    // =========================================================================
    private static void matrixSetAffine(double[] m,
                                        double m11, double m12, double m13,
                                        double m21, double m22, double m23) {
        m[0]=m11; m[1]=m12; m[2]=m13;
        m[3]=m21; m[4]=m22; m[5]=m23;
    }

    private static void matrixReset(double[] m) {
        m[0]=1; m[1]=0; m[2]=0;
        m[3]=0; m[4]=1; m[5]=0;
    }

    private static void matrixMapPoint(double[] m, double sx, double sy, double[] out) {
        out[0] = m[0]*sx + m[1]*sy + m[2];
        out[1] = m[3]*sx + m[4]*sy + m[5];
    }

    // =========================================================================
    //  DFData helpers  (int[] interleaved: [distSqBits, deltaWinding, ...])
    // =========================================================================
    private static float  dfDistSq(int[] d, int i)            { return Float.intBitsToFloat(d[i*2  ]); }
    private static void   dfSetDistSq(int[] d, int i, float v){ d[i*2  ] = Float.floatToRawIntBits(v); }
    private static int    dfDeltaWinding(int[] d, int i)       { return d[i*2+1]; }
    private static void   dfAddDeltaWinding(int[] d, int i, int v){ d[i*2+1] += v; }

    private static void initDistances(Scratch scratch, int width, int height) {
        // create temp data
        int dataSize = width * height * 2;
        if (scratch.dfData == null || scratch.dfData.length < dataSize) {
            scratch.dfData = new int[dataSize];
        }
        int[] dfData = scratch.dfData;

        // init distance to "far away"
        float far = SK_DistanceFieldMagnitude * SK_DistanceFieldMagnitude;
        for (int ix = 0; ix < dataSize; ix += 2) {
            dfData[ix  ] = Float.floatToRawIntBits(far);
            dfData[ix+1] = 0;
        }
    }

    // =========================================================================
    //  Math helpers
    // =========================================================================
    private static double signOf(double val) {
        return Math.signum(val);
    }

    private static double distanceSquared(double ax, double ay, double bx, double by) {
        double dx = ax-bx, dy = ay-by;
        return dx*dx + dy*dy;
    }

    private static boolean betweenClosedOpen(double a, double b, double c,
                                             double tolerance, boolean xformTol) {
        double tolB = tolerance, tolC = tolerance;
        if (xformTol) {
            tolB = tolerance / Math.sqrt(4.0*b*b + 1.0);
            tolC = tolerance / Math.sqrt(4.0*c*c + 1.0);
        }
        return b < c ? (a >= b-tolB && a < c-tolC)
                : (a >= c-tolC && a < b-tolB);
    }

    private static boolean betweenClosed(double a, double b, double c,
                                         double tolerance, boolean xformTol) {
        double tolB = tolerance, tolC = tolerance;
        if (xformTol) {
            tolB = tolerance / Math.sqrt(4.0*b*b + 1.0);
            tolC = tolerance / Math.sqrt(4.0*c*c + 1.0);
        }
        return b < c ? (a >= b-tolB && a <= c+tolC)
                : (a >= c-tolC && a <= b+tolB);
    }

    private static boolean nearlyZero(double x, double tolerance) {
        return Math.abs(x) <= tolerance;
    }

    private static boolean nearlyEqual(double x, double y, double tolerance, boolean xformTol) {
        if (xformTol) tolerance = tolerance / Math.sqrt(4.0*y*y + 1.0);
        return Math.abs(x-y) <= tolerance;
    }

    private static boolean isColinear(float[] pts6) {
        // pts6: [x0,y0, x1,y1, x2,y2]
        double v = (pts6[3]-pts6[1])*(pts6[2]-pts6[4]) - (pts6[3]-pts6[5])*(pts6[2]-pts6[0]);
        return nearlyZero(v, kCloseSqd);
    }

    private static double distSqPts2f(float[] pts, int i, int j) {
        double dx = pts[i*2]-pts[j*2], dy = pts[i*2+1]-pts[j*2+1];
        return dx*dx+dy*dy;
    }

    private static void addLine(float x0, float y0, float x1, float y1,
                                Scratch scratch) {
        if (x0 == x1 && y0 == y1) {
            // don't add degenerate lines
            return;
        }
        PathSegment seg = new PathSegment();
        seg.fType = PathSegment.kLine;
        seg.fPts[0]=x0; seg.fPts[1]=y0;
        seg.fPts[2]=x1; seg.fPts[3]=y1;
        seg.init(scratch);
        scratch.segments.add(seg);
    }

    private static void addQuad(float x0, float y0, float x1, float y1,
                                float x2, float y2,
                                Scratch scratch) {
        double dx01 = x0-x1, dy01 = y0-y1;
        double dx12 = x1-x2, dy12 = y1-y2;
        if (dx01*dx01+dy01*dy01 < kCloseSqd ||
                dx12*dx12+dy12*dy12 < kCloseSqd ||
                isColinear(x0,y0,x1,y1,x2,y2)) {
            addLine(x0, y0, x2, y2, scratch);
        } else {
            PathSegment seg = new PathSegment();
            seg.fType = PathSegment.kQuad;
            seg.fPts[0]=x0; seg.fPts[1]=y0;
            seg.fPts[2]=x1; seg.fPts[3]=y1;
            seg.fPts[4]=x2; seg.fPts[5]=y2;
            seg.init(scratch);
            scratch.segments.add(seg);
        }
    }

    private static boolean isColinear(float x0, float y0, float x1, float y1, float x2, float y2){
        double v = ((double)y1-y0)*((double)x1-x2) - ((double)y1-y2)*((double)x1-x0);
        return nearlyZero(v, kCloseSqd);
    }

    private static float calcNearestPointForQuad(PathSegment seg, double xfX, double xfY) {
        final float kThird        = 0.33333333333f;
        final float kTwentySeventh= 0.037037037f;

        float a = 0.5f - (float)xfY;
        float b = -0.5f * (float)xfX;

        float a3 = a*a*a;
        float b2 = b*b;
        float c  = b2*0.25f + a3*kTwentySeventh;

        if (c >= 0f) {
            float sqrtC = (float)Math.sqrt(c);
            return (float)Math.cbrt(-b*0.5f + sqrtC) + (float)Math.cbrt(-b*0.5f - sqrtC);
        } else {
            float cosPhi = (float)Math.sqrt(b2*0.25f * (-27f/a3)) * (b > 0 ? -1f : 1f);
            float phi    = (float)Math.acos(cosPhi);
            float result;
            float base1  = 2f * (float)Math.sqrt(-a*kThird);
            float base2a = (float)Math.cos(phi*kThird);
            float base2b = (float)Math.cos(phi*kThird + (float)Math.PI*2f*kThird);

            if (xfX > 0f) {
                result = base1 * base2a;
                if (!betweenClosed(result, seg.fP0T_x, seg.fP2T_x, 0, false)) {
                    result = base1 * base2b;
                }
            } else {
                result = base1 * base2b;
                if (!betweenClosed(result, seg.fP0T_x, seg.fP2T_x, 0, false)) {
                    result = base1 * base2a;
                }
            }
            return result;
        }
    }

    private static void precomputationForRow(Scratch s, PathSegment seg,
                                             float plX, float plY,
                                             float prX, float prY) {
        if (seg.fType != PathSegment.kQuad) return;

        // map left and right points
        double[] tmp = s.pt;
        matrixMapPoint(seg.fXformMatrix, plX, plY, tmp);
        double x1 = tmp[0], y1 = tmp[1];
        matrixMapPoint(seg.fXformMatrix, prX, prY, tmp);
        double x2 = tmp[0], y2 = tmp[1];

        s.rd_quadXDirection     = (int)signOf(seg.fP2T_x - seg.fP0T_x);
        s.rd_scanlineXDirection = (int)signOf(x2 - x1);

        if (nearlyEqual(x1, x2, seg.fNearlyZeroScaled, true)) {
            s.rd_intersectionType   = RD_VERTICAL_LINE;
            s.rd_yAtIntersection    = x1*x1;
            s.rd_scanlineXDirection = 0;
            return;
        }

        double m  = (y2-y1)/(x2-x1);
        double bv = -m*x1 + y1;
        double m2 = m*m;
        double cv = m2 + 4.0*bv;
        double tol= 4.0 * seg.fTangentTolScaledSqd / (m2 + 1.0);

        if (s.rd_scanlineXDirection == 1 &&
                (seg.fPts[1] == plY || seg.fPts[(seg.fType+1)*2+1] == plY) &&
                nearlyZero(cv, tol)) {
            s.rd_intersectionType   = RD_TANGENT_LINE;
            s.rd_xAtIntersection1   = m / 2.0;
            s.rd_xAtIntersection2   = m / 2.0;
        } else if (cv <= 0.0) {
            s.rd_intersectionType = RD_NO_INTERSECTION;
        } else {
            s.rd_intersectionType   = RD_TWO_POINTS_INTERSECT;
            double d = Math.sqrt(cv);
            s.rd_xAtIntersection1 = (m+d)/2.0;
            s.rd_xAtIntersection2 = (m-d)/2.0;
        }
    }

    private static int calculateSideOfQuad(PathSegment seg,
                                           float pointX, float pointY,
                                           double xfX, double xfY,
                                           Scratch s) {
        int side = kNA_SegSide;

        if (s.rd_intersectionType == RD_VERTICAL_LINE) {
            side = (int)(signOf(xfY - s.rd_yAtIntersection) * s.rd_quadXDirection);
        } else if (s.rd_intersectionType == RD_TWO_POINTS_INTERSECT) {
            double p1 = s.rd_xAtIntersection1;
            double p2 = s.rd_xAtIntersection2;
            int signP1 = (int)signOf(p1 - xfX);
            boolean includeP1 = true, includeP2 = true;

            // endPt y values
            float endY = seg.fPts[(seg.fType+1)*2+1]; // y of endPt

            if (s.rd_scanlineXDirection == 1) {
                if ((s.rd_quadXDirection == -1 && seg.fPts[1] <= pointY &&
                        nearlyEqual(seg.fP0T_x, p1, seg.fNearlyZeroScaled, true)) ||
                        (s.rd_quadXDirection ==  1 && endY <= pointY &&
                                nearlyEqual(seg.fP2T_x, p1, seg.fNearlyZeroScaled, true))) {
                    includeP1 = false;
                }
                if ((s.rd_quadXDirection == -1 && endY <= pointY &&
                        nearlyEqual(seg.fP2T_x, p2, seg.fNearlyZeroScaled, true)) ||
                        (s.rd_quadXDirection ==  1 && seg.fPts[1] <= pointY &&
                                nearlyEqual(seg.fP0T_x, p2, seg.fNearlyZeroScaled, true))) {
                    includeP2 = false;
                }
            }

            if (includeP1 && betweenClosed(p1, seg.fP0T_x, seg.fP2T_x,
                    seg.fNearlyZeroScaled, true)) {
                side = signP1 * s.rd_quadXDirection;
            }
            if (includeP2 && betweenClosed(p2, seg.fP0T_x, seg.fP2T_x,
                    seg.fNearlyZeroScaled, true)) {
                int signP2 = (int)signOf(p2 - xfX);
                if (side == kNA_SegSide || signP2 == 1) {
                    side = -signP2 * s.rd_quadXDirection;
                }
            }
        } else if (s.rd_intersectionType == RD_TANGENT_LINE) {
            double p  = s.rd_xAtIntersection1;
            int signP = (int)signOf(p - xfX);
            float endY = seg.fPts[(seg.fType+1)*2+1];
            if (s.rd_scanlineXDirection == 1) {
                if (seg.fPts[1] == pointY)  side =  signP;
                else if (endY  == pointY)   side = -signP;
            }
        }
        return side;
    }

    /**
     * Returns distSq (float). Writes RowData and side into scratch.
     */
    private static float distanceToSegment(float pointX, float pointY,
                                           PathSegment seg,
                                           Scratch scratch) {
        double[] tmp = scratch.pt;
        matrixMapPoint(seg.fXformMatrix, pointX, pointY, tmp);
        double xfX = tmp[0], xfY = tmp[1];

        if (seg.fType == PathSegment.kLine) {
            float result;
            if (betweenClosed(xfX, seg.fP0T_x, seg.fP2T_x, 0, false)) {
                result = (float)(xfY*xfY);
            } else if (xfX < seg.fP0T_x) {
                result = (float)(xfX*xfX + xfY*xfY);
            } else {
                double dx = xfX - seg.fP2T_x;
                result = (float)(dx*dx + xfY*xfY);
            }
            if (betweenClosedOpen(pointY, seg.fBB_top, seg.fBB_bottom, 0, false)) {
                scratch.outSide = (int)signOf(xfY);
            } else {
                scratch.outSide = kNA_SegSide;
            }
            return result;
        } else {
            assert (seg.fType == PathSegment.kQuad);

            float nearest = calcNearestPointForQuad(seg, xfX, xfY);
            float dist;
            if (betweenClosed(nearest, seg.fP0T_x, seg.fP2T_x, 0, false)) {
                double nx = nearest, ny = (double)nearest*nearest;
                dist = (float)distanceSquared(xfX, xfY, nx, ny);
            } else {
                float d0 = (float)distanceSquared(xfX, xfY, seg.fP0T_x, seg.fP0T_y);
                float d2 = (float)distanceSquared(xfX, xfY, seg.fP2T_x, seg.fP2T_y);
                dist = Math.min(d0, d2);
            }
            if (betweenClosedOpen(pointY, seg.fBB_top, seg.fBB_bottom, 0, false)) {
                scratch.outSide = calculateSideOfQuad(seg, pointX, pointY, xfX, xfY, scratch);
            } else {
                scratch.outSide = kNA_SegSide;
            }
            return (float)(dist * seg.fScalingFactorSqd);
        }
    }

    private static void calculateDistanceFieldData(Scratch scratch,
                                                   int width, int height) {
        int[] dfData = scratch.dfData;
        // for each segment
        for (PathSegment seg : scratch.segments) {
            float bbL = seg.fBB_left, bbT = seg.fBB_top,
                    bbR = seg.fBB_right, bbB = seg.fBB_bottom;
            // get the bounding box, outset by distance field pad, and clip to total bounds
            // Clip inside the distance field to avoid overflow
            int startCol = Math.max(0,     (int)(bbL - SK_DistanceFieldPad));
            int endCol   = Math.min(width,  (int)Math.ceil(bbR + SK_DistanceFieldPad));
            int startRow = Math.max(0,     (int)(bbT - SK_DistanceFieldPad));
            int endRow   = Math.min(height, (int)Math.ceil(bbB + SK_DistanceFieldPad));

            for (int row = startRow; row < endRow; row++) {
                int prevSide = kNA_SegSide;
                float pY = row + 0.5f;

                float plX = startCol, plY = pY;
                float prX = endCol,   prY = pY;

                if (betweenClosedOpen(pY, bbT, bbB, 0, false)) {
                    precomputationForRow(scratch, seg, plX, plY, prX, prY);
                } else {
                    scratch.rd_intersectionType = RD_NO_INTERSECTION;
                }

                for (int col = startCol; col < endCol; col++) {
                    int idx = row * width + col;
                    float distSq = dfDistSq(dfData, idx);

                    // Optimization for not calculating some points.
                    int dilation = distSq < 1.5f*1.5f ? 1 :
                                   distSq < 2.5f*2.5f ? 2 :
                                   distSq < 3.5f*3.5f ? 3 : SK_DistanceFieldPad;
                    if (dilation < SK_DistanceFieldPad) {
                        // roundOut of BB, then outset by dilation
                        int bbRL = (int)Math.floor(bbL) - dilation;
                        int bbRT = (int)Math.floor(bbT) - dilation;
                        int bbRR = (int)Math.ceil(bbR)  + dilation;
                        int bbRB = (int)Math.ceil(bbB)  + dilation;
                        if (col < bbRL || col >= bbRR || row < bbRT || row >= bbRB)
                            continue;
                    }

                    float pX = col + 0.5f;
                    float currDistSq = distanceToSegment(pX, pY, seg, scratch);
                    int side = scratch.outSide;

                    int deltaWinding = 0;
                    if (prevSide == kLeft_SegSide && side == kRight_SegSide) {
                        deltaWinding = -1;
                    } else if (prevSide == kRight_SegSide && side == kLeft_SegSide) {
                        deltaWinding = 1;
                    }
                    prevSide = side;

                    if (currDistSq < distSq) {
                        dfSetDistSq(dfData, idx, currDistSq);
                    }

                    dfAddDeltaWinding(dfData, idx, deltaWinding);
                }
            }
        }
    }

    private static void putDistanceFieldVal(float dist, int distanceMagnitude,
                                            byte @Nullable [] base, long address) {
        dist = MathUtil.pin(-dist, -distanceMagnitude, distanceMagnitude * 127.0f / 128.0f);
        dist += distanceMagnitude;
        byte packedVal = (byte)Math.round(dist / (2 * distanceMagnitude) * 256.0f);
        if (base != null) {
            base[(int)address] = packedVal;
        } else {
            MemoryUtil.memPutByte(address, packedVal);
        }
    }

    /**
     * At least ((num segments * 200) + (width * height * 9)) bytes are needed.
     * <p>
     * Dst pixel array is already padded. Src is already in device space.
     * <p>
     * By default, inner has positive distances, outer has negative distances.
     * Unless inverse is true.
     * <p>
     * NON_ZERO requires caller to use {@link java.awt.geom.Area} to resolve first, then use EVEN_ODD.
     *
     * @param distanceBase    dst heap array, if any
     * @param distanceAddress array offset if dst is heap, or memory address if dst is native
     * @param shape           device shape, at padded location
     * @param inverse         invert the sdf
     * @param width           padded width
     * @param height          padded height
     * @param rowBytes        row stride in bytes
     * @param scratch         reusable buffer for temp results
     */
    public static void generateDistanceFieldFromPath(byte @Nullable [] distanceBase, long distanceAddress,
                                                     java.awt.@NonNull Shape shape, boolean inverse,
                                                     int width, int height, int rowBytes,
                                                     @NonNull Scratch scratch) {
        assert distanceBase != null || distanceAddress != 0;
        assert width > 0 && height > 0 && rowBytes >= width;

        assert shape.getBounds().isEmpty() ||
                new java.awt.Rectangle(width, height).contains(shape.getBounds());

        // create initial distance data (init to "far away")
        initDistances(scratch, width, height);

        // polygonize path into line and quad segments
        PathIterator iter = shape.getPathIterator(null);
        int windingRule = iter.getWindingRule();
        float[] coords = scratch.pts;
        float lastX = 0, lastY = 0;
        float lastMoveX = 0, lastMoveY = 0;
        scratch.segments.clear();
        while (!iter.isDone()) {
            switch (iter.currentSegment(coords)) {
                case PathIterator.SEG_MOVETO -> {
                    lastMoveX = lastX = coords[0];
                    lastMoveY = lastY = coords[1];
                }
                case PathIterator.SEG_LINETO -> {
                    addLine(lastX, lastY, coords[0], coords[1], scratch);
                    lastX = coords[0];
                    lastY = coords[1];
                }
                case PathIterator.SEG_QUADTO -> {
                    addQuad(lastX, lastY, coords[0], coords[1], coords[2], coords[3], scratch);
                    lastX = coords[2];
                    lastY = coords[3];
                }
                case PathIterator.SEG_CUBICTO -> {
                    //TODO find inflection points and subdivide
                    lastX = coords[4];
                    lastY = coords[5];
                }
                case PathIterator.SEG_CLOSE -> {
                    addLine(lastX, lastY, lastMoveX, lastMoveY, scratch);
                }
            }
            iter.next();
        }

        // do all the work
        calculateDistanceFieldData(scratch, width, height);

        final int kInside  = -1;
        final int kOutside =  1;

        // adjust distance based on winding
        int[] dfData = scratch.dfData;
        for (int row = 0; row < height; row++) {
            int windingNumber = 0; // Winding number start from zero for each scanline
            for (int col = 0; col < width; col++) {
                int idx = row * width + col;
                windingNumber += dfDeltaWinding(dfData, idx);

                int dfSign;
                switch (windingRule) {
                    case PathIterator.WIND_NON_ZERO:
                        dfSign = (windingNumber != 0)     ^ inverse ? kInside : kOutside; // winding
                        break;
                    case PathIterator.WIND_EVEN_ODD:
                    default:
                        dfSign = (windingNumber % 2) != 0 ^ inverse ? kInside : kOutside; // evenOdd
                        break;
                }

                float minDist = (float)Math.sqrt(dfDistSq(dfData, idx));
                float dist     = dfSign * minDist;

                putDistanceFieldVal(dist, SK_DistanceFieldMagnitude,
                        distanceBase, distanceAddress + (long)row * rowBytes + col);
            }

            if (windingNumber != 0) {
                // fallback: re-derive sign via contains
                for (int col = 0; col < width; col++) {
                    int idx = row * width + col;
                    int dfSign = shape.contains(col + 0.5, row + 0.5) ^ inverse ? kInside : kOutside;
                    float minDist = (float)Math.sqrt(dfDistSq(dfData, idx));
                    float dist     = dfSign * minDist;

                    putDistanceFieldVal(dist, SK_DistanceFieldMagnitude,
                            distanceBase, distanceAddress + (long)row * rowBytes + col);
                }
            }
        }
    }
}
