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

package icyllis.arc3d.granite.test;

import icyllis.arc3d.core.ColorSpace;
import icyllis.arc3d.sketch.Matrix;
import org.lwjgl.glfw.GLFW;
import org.lwjgl.opengl.GL;
import org.lwjgl.opengl.GL11C;
import org.lwjgl.opengl.GL30C;
import org.lwjgl.opengl.GL33C;
import org.lwjgl.opengl.GL42C;
import org.lwjgl.opengl.GL43C;
import org.lwjgl.system.MemoryUtil;

import javax.imageio.ImageIO;

import java.io.File;
import java.io.IOException;
import java.util.Arrays;

import static icyllis.arc3d.granite.test.TestGraniteRenderer.*;
import static org.lwjgl.glfw.GLFW.*;

public class SimpleGLFW {

    public static void main(String[] args) {
        System.setProperty("java.awt.headless", "true");

        float[] c = {251/255f,191/255f,36/255f};
        System.out.println(Arrays.toString(c));
        ColorSpace.connect(
                ColorSpace.get(ColorSpace.Named.DISPLAY_P3), ColorSpace.get(ColorSpace.Named.SRGB)
        ).transformUnclamped(c);
        System.out.println(Arrays.toString(c));
        GLFW.glfwInit();
        GLFW.glfwDefaultWindowHints();
        glfwWindowHint(GLFW_CLIENT_API, GLFW_OPENGL_API);
        glfwWindowHint(GLFW_CONTEXT_CREATION_API, GLFW_NATIVE_CONTEXT_API);
        glfwWindowHint(GLFW_CONTEXT_VERSION_MAJOR, 3);
        glfwWindowHint(GLFW_CONTEXT_VERSION_MINOR, 3);
        glfwWindowHint(GLFW_OPENGL_PROFILE, GLFW_OPENGL_CORE_PROFILE);
        glfwWindowHint(GLFW_OPENGL_FORWARD_COMPAT, GLFW_TRUE);
        glfwWindowHint(GLFW_DEPTH_BITS, 0);
        glfwWindowHint(GLFW_STENCIL_BITS, 0);
        //GLFW.glfwWindowHint(GLFW.GLFW_VISIBLE, GLFW.GLFW_FALSE);
        long window = glfwCreateWindow(WINDOW_WIDTH, WINDOW_HEIGHT, "Test Window", 0, 0);
            if (window == 0) {
            throw new RuntimeException("0x" + Integer.toHexString(nglfwGetError(MemoryUtil.NULL)));
        }
        glfwMakeContextCurrent(window);
        glfwSwapInterval(1);

        GL.createCapabilities();

        int res = GL42C.glGetInternalformati(GL30C.GL_RENDERBUFFER, GL11C.GL_RGB8,
                GL43C.GL_INTERNALFORMAT_SUPPORTED);
        LOGGER.info("Renderbuffer supported: {}", res);

        while (!glfwWindowShouldClose(window)) {
            GL33C.glClearColor(1, 1, (System.currentTimeMillis() % 1000) / 1000f, 1);
            GL33C.glClear(GL33C.GL_COLOR_BUFFER_BIT);
            glfwSwapBuffers(window);

            glfwPollEvents();
        }

        glfwDestroyWindow(window);
        glfwTerminate();
    }
}
