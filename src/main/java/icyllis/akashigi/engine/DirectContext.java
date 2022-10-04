/*
 * Akashi GI.
 * Copyright (C) 2022-2022 BloCamLimb. All rights reserved.
 *
 * Akashi GI is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 3 of the License, or (at your option) any later version.
 *
 * Akashi GI is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with Akashi GI. If not, see <https://www.gnu.org/licenses/>.
 */

package icyllis.akashigi.engine;

import icyllis.akashigi.opengl.GLServer;
import icyllis.akashigi.vulkan.VkBackendContext;
import org.jetbrains.annotations.ApiStatus;

import javax.annotation.Nullable;

/**
 * Represents a backend context of 3D graphics API (OpenGL or Vulkan) on the render thread.
 */
public final class DirectContext extends RecordingContext {

    private Server mServer;
    private ResourceCache mResourceCache;
    private ResourceProvider mResourceProvider;

    private DirectContext(int backend, ContextOptions options) {
        super(new ContextThreadSafeProxy(backend, options));
    }

    /**
     * Creates a DirectContext for a backend context, using default context options.
     *
     * @return context or null if failed to create
     * @see #makeOpenGL(ContextOptions)
     */
    @Nullable
    public static DirectContext makeOpenGL() {
        return makeOpenGL(new ContextOptions());
    }

    /**
     * Creates a DirectContext for a backend context, using specified context options.
     * <p>
     * Example with GLFW:
     * <pre>{@code
     *  public static void main(String[] args) {
     *      System.setProperty("java.awt.headless", Boolean.TRUE.toString());
     *      if (!glfwInit()) {
     *          throw new IllegalStateException();
     *      }
     *      // default hints use highest OpenGL version and native API
     *      glfwDefaultWindowHints();
     *      long window = glfwCreateWindow(1280, 720, "Example Window", NULL, NULL);
     *      if (window == NULL) {
     *          throw new IllegalStateException();
     *      }
     *      // you can make a thread a render thread
     *      glfwMakeContextCurrent(window);
     *      DirectContext direct = DirectContext.makeOpenGL();
     *      if (direct == null) {
     *          throw new IllegalStateException();
     *      }
     *      ...
     *      // destroy and close operations
     *      direct.drop();
     *      direct.close();
     *      glfwDestroyWindow(window);
     *      glfwTerminate();
     *  }
     * }</pre>
     *
     * @return context or null if failed to create
     */
    @Nullable
    public static DirectContext makeOpenGL(ContextOptions options) {
        var context = new DirectContext(Engine.OpenGL, options);
        context.mServer = GLServer.make(context, options);
        if (context.init()) {
            return context;
        }
        context.close();
        return null;
    }

    /**
     * Creates a DirectContext for a backend context, using default context options.
     *
     * @return context or null if failed to create
     * @see #makeVulkan(VkBackendContext, ContextOptions)
     */
    @Nullable
    public static DirectContext makeVulkan(VkBackendContext backendContext) {
        return makeVulkan(backendContext, new ContextOptions());
    }

    /**
     * Creates a DirectContext for a backend context, using specified context options.
     * <p>
     * The Vulkan context (VkQueue, VkDevice, VkInstance) must be kept alive until the returned
     * DirectContext is destroyed. This also means that any objects created with this
     * DirectContext (e.g. Surfaces, Images, etc.) must also be released as they may hold
     * refs on the DirectContext. Once all these objects and the DirectContext are released,
     * then it is safe to delete the Vulkan objects.
     *
     * @return context or null if failed to create
     */
    @Nullable
    public static DirectContext makeVulkan(VkBackendContext backendContext, ContextOptions options) {
        return null;
    }

    @ApiStatus.Internal
    public Server getServer() {
        return mServer;
    }

    @ApiStatus.Internal
    public ResourceCache getResourceCache() {
        return mResourceCache;
    }

    @ApiStatus.Internal
    public ResourceProvider getResourceProvider() {
        return mResourceProvider;
    }

    @Override
    protected boolean init() {
        if (mServer == null) {
            return false;
        }

        mThreadSafeProxy.init(mServer.mCaps, mServer.getPipelineBuilder());
        if (!super.init()) {
            return false;
        }

        assert getTextBlobCache() != null;
        assert getThreadSafeCache() != null;

        mResourceCache = new ResourceCache(getContextID());
        mResourceProvider = new ResourceProvider(mServer, mResourceCache);
        return true;
    }

    @Override
    protected void dispose() {
        super.dispose();
        if (mResourceCache != null) {
            mResourceCache.releaseAll();
        }
        mServer.disconnect(true);
    }
}