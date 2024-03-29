/*
 * This file is part of Arc 3D.
 *
 * Copyright (C) 2022-2023 BloCamLimb <pocamelards@gmail.com>
 *
 * Arc 3D is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 3 of the License, or (at your option) any later version.
 *
 * Arc 3D is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with Arc 3D. If not, see <https://www.gnu.org/licenses/>.
 */

import java.io.*;
import java.util.StringTokenizer;

public class NUIST2023C {

    static final BufferedReader br = new BufferedReader(new InputStreamReader(System.in));
    static StringTokenizer st;

    static int ni() {
        while (st == null || !st.hasMoreElements())
            try {
                st = new StringTokenizer(br.readLine());
            } catch (IOException e) {
                e.printStackTrace();
            }
        return Integer.parseInt(st.nextToken());
    }

    public static void main(String[] args) {
        long a = ni(), r = (a << 2) - ni();
        if ((r & 1) == 0) {
            r >>= 1;
            if (r >= 0 && r <= a) {
                System.out.printf("%d %d\n", r, a - r);
                return;
            }
        }
        System.out.println("-1 -1");
    }
}
