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

import icyllis.arc3d.core.ColorInfo;
import icyllis.arc3d.core.ColorSpace;
import icyllis.arc3d.core.ImageInfo;
import icyllis.arc3d.core.SharedPtr;
import icyllis.arc3d.engine.ContextOptions;
import icyllis.arc3d.engine.Engine;
import icyllis.arc3d.engine.ISurface;
import icyllis.arc3d.engine.Image;
import icyllis.arc3d.engine.ImmediateContext;
import icyllis.arc3d.granite.GraniteSurface;
import icyllis.arc3d.granite.GraniteUtil;
import icyllis.arc3d.granite.Recording;
import icyllis.arc3d.granite.RecordingContext;
import icyllis.arc3d.sketch.Surface;
import icyllis.arc3d.vulkan.VKUtil;
import icyllis.arc3d.vulkan.VulkanBackendImage;
import icyllis.arc3d.vulkan.VulkanBackendSemaphore;
import icyllis.arc3d.vulkan.VulkanCommandBuffer;
import icyllis.arc3d.vulkan.VulkanImageDesc;
import icyllis.arc3d.vulkan.VulkanImageMutableState;
import icyllis.arc3d.vulkan.VulkanQueueManager;
import icyllis.arc3d.vulkan.test.TestVulkanInit;
import org.lwjgl.glfw.Callbacks;
import org.lwjgl.glfw.GLFWVulkan;
import org.lwjgl.system.MemoryUtil;
import org.lwjgl.vulkan.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Arrays;

import static icyllis.arc3d.granite.test.TestGraniteRenderer.*;
import static org.lwjgl.glfw.GLFW.*;
import static org.lwjgl.system.MemoryStack.stackPush;
import static org.lwjgl.vulkan.KHRSurface.*;
import static org.lwjgl.vulkan.KHRSwapchain.*;
import static org.lwjgl.vulkan.VK10.*;

public class TestGraniteVulkan {

    public static final Logger LOGGER = LoggerFactory.getLogger("Arc3D");

    public static final boolean RENDER_TO_SWAPCHAIN_DIRECTLY = true;
    public static final boolean USE_EXTENDED_SRGB_LINEAR = true;
    public static final int sRGBWhitePointNits = 80;

    static class WindowContext {

        long[] swapchainImages = new long[3];
        Surface[] swapchainSurfaces = new Surface[3];
        boolean[] first = new boolean[swapchainImages.length];
        int minImageCount = 3;
        long swapchain;
        long vkSurface;
        long[] renderCompleteSemaphores = new long[swapchainImages.length];
        long window;

        WindowContext(TestVulkanInit init, RecordingContext recordingContext) {
            glfwDefaultWindowHints();
            glfwWindowHint(GLFW_CLIENT_API, GLFW_NO_API);
            window = glfwCreateWindow(WINDOW_WIDTH, WINDOW_HEIGHT, "Test Window", 0, 0);
            if (window == 0) {
                throw new RuntimeException("0x" + Integer.toHexString(nglfwGetError(MemoryUtil.NULL)));
            }
            TestGraniteRenderer.setKeyCallbacks(window);

            try (var stack = stackPush()) {
                var pSurface = stack.mallocLong(1);
                var result = GLFWVulkan.glfwCreateWindowSurface(init.getInstance(), window, null, pSurface);
                VKUtil._CHECK_(result);
                vkSurface = pSurface.get(0);

                var pSupportPresent = stack.mallocInt(1);
                result = vkGetPhysicalDeviceSurfaceSupportKHR(init.getPhysicalDevice(), init.getGraphicsQueueIndex(),
                        vkSurface, pSupportPresent);
                VKUtil._CHECK_(result);
                if (pSupportPresent.get(0) != VK_TRUE) {
                    throw new AssertionError("Presentation queue != graphics queue");
                }
                var presentModeCount = stack.mallocInt(1);
                vkGetPhysicalDeviceSurfacePresentModesKHR(init.getPhysicalDevice(), vkSurface, presentModeCount, null);
                var presentModes = stack.mallocInt(presentModeCount.get(0));
                vkGetPhysicalDeviceSurfacePresentModesKHR(init.getPhysicalDevice(), vkSurface, presentModeCount, presentModes);

                for (int i = 0; i < presentModes.capacity(); i++) {
                    LOGGER.info("PresentMode: {}", presentModes.get(i));
                }

                VkSurfaceCapabilitiesKHR surfCaps = VkSurfaceCapabilitiesKHR.calloc();
                result = vkGetPhysicalDeviceSurfaceCapabilitiesKHR(init.getPhysicalDevice(), vkSurface, surfCaps);
                VKUtil._CHECK_(result);

                LOGGER.info("MinImageCount: {}, MaxImageCount: {}, SupportedUsageFlags: {}",
                        surfCaps.minImageCount(), surfCaps.maxImageCount(),
                        Integer.toHexString(surfCaps.supportedUsageFlags()));

                var pFormatCount = pSupportPresent;
                result = vkGetPhysicalDeviceSurfaceFormatsKHR(init.getPhysicalDevice(), vkSurface, pFormatCount, null);
                VKUtil._CHECK_(result);
                int formatCount = pFormatCount.get(0);

                VkSurfaceFormatKHR.Buffer surfFormats = VkSurfaceFormatKHR.calloc(formatCount, stack);
                result = vkGetPhysicalDeviceSurfaceFormatsKHR(init.getPhysicalDevice(), vkSurface, pFormatCount, surfFormats);
                VKUtil._CHECK_(result);

                LOGGER.info("Surface format count: {}", surfFormats.limit());
                for (int i = 0; i < surfFormats.limit(); i++) {
                    surfFormats.position(i);
                    LOGGER.info("Surface format {}: format {} space {}", i,
                            VKUtil.vkFormatName(surfFormats.format()),
                            surfFormats.colorSpace());
                }

                int imageUsageFlags = VK_IMAGE_USAGE_COLOR_ATTACHMENT_BIT | VK_IMAGE_USAGE_TRANSFER_DST_BIT;

                int imageFormat = USE_EXTENDED_SRGB_LINEAR ? VK_FORMAT_R16G16B16A16_SFLOAT : VK_FORMAT_R8G8B8A8_UNORM;

                VkSwapchainCreateInfoKHR swapchainCI = VkSwapchainCreateInfoKHR.calloc(stack)
                        .sType$Default()
                        .surface(vkSurface)
                        .minImageCount(minImageCount)
                        .imageFormat(imageFormat)
                        .imageColorSpace(USE_EXTENDED_SRGB_LINEAR ? EXTSwapchainColorspace.VK_COLOR_SPACE_EXTENDED_SRGB_LINEAR_EXT : VK_COLOR_SPACE_SRGB_NONLINEAR_KHR)
                        .imageUsage(imageUsageFlags)
                        .preTransform(VK_SURFACE_TRANSFORM_IDENTITY_BIT_KHR)
                        .imageArrayLayers(1)
                        .imageSharingMode(VK_SHARING_MODE_EXCLUSIVE)
                        .presentMode(VK_PRESENT_MODE_FIFO_KHR)
                        .oldSwapchain(0)
                        .clipped(true)
                        .compositeAlpha(VK_COMPOSITE_ALPHA_OPAQUE_BIT_KHR);
                swapchainCI.imageExtent()
                        .width(WINDOW_WIDTH)
                        .height(WINDOW_HEIGHT);

                var pSwapchain = stack.mallocLong(1);
                result = vkCreateSwapchainKHR(init.getDevice(), swapchainCI, null, pSwapchain);
                VKUtil._CHECK_(result);

                swapchain = pSwapchain.get(0);

                vkGetSwapchainImagesKHR(init.getDevice(), swapchain, new int[]{swapchainImages.length}, swapchainImages);

                VulkanImageDesc imageDesc = new VulkanImageDesc(
                        0, VK_IMAGE_TYPE_2D, imageFormat,
                        VK_IMAGE_TILING_OPTIMAL, imageUsageFlags,
                        VK_SHARING_MODE_EXCLUSIVE, Engine.ImageType.k2D,
                        USE_EXTENDED_SRGB_LINEAR ? Engine.ImageFormat.kRGBA16F : Engine.ImageFormat.kRGBA8,
                        WINDOW_WIDTH, WINDOW_HEIGHT,
                        1, 1,
                        1, 1,
                        ISurface.FLAG_RENDERABLE
                );

                ColorSpace.Rgb origCS = (ColorSpace.Rgb) ColorSpace.get(ColorSpace.Named.LINEAR_EXTENDED_SRGB);
                float exposure = sRGBWhitePointNits / 80f;
                ColorSpace newCS = USE_EXTENDED_SRGB_LINEAR ?
                        new ColorSpace.Rgb("Generic scRGB",
                                origCS.getPrimaries(), origCS.getWhitePoint(), null,
                                d -> d * exposure, d -> d / exposure, -0.5f, 7.5f,
                                new ColorSpace.Rgb.TransferParameters(1/exposure,0,1/exposure,0,1))
                        : ColorSpace.get(ColorSpace.Named.SRGB);
                for (int i = 0; i < swapchainImages.length; i++) {
                    VulkanBackendImage backendImage = new VulkanBackendImage(
                            swapchainImages[i], imageDesc, new VulkanImageMutableState(VK_IMAGE_LAYOUT_UNDEFINED, init.getGraphicsQueueIndex()),
                            null
                    );
                    swapchainSurfaces[i] = GraniteSurface.wrapBackendImage(
                        recordingContext, backendImage, ImageInfo.make(
                                    WINDOW_WIDTH, WINDOW_HEIGHT, USE_EXTENDED_SRGB_LINEAR ? ColorInfo.CT_RGBA_F16 : ColorInfo.CT_RGBA_8888,
                                    ColorInfo.AT_PREMUL, newCS
                            ),
                            Engine.SurfaceOrigin.kUpperLeft,
                            null, "Swapchain" + i
                    );
                    if (swapchainSurfaces[i] == null) {
                        throw new UnsupportedOperationException();
                    }
                }

                surfCaps.free();
            }

            LOGGER.info("Swapchain images:");
            for (long image : swapchainImages) {
                LOGGER.info(Long.toHexString(image));
            }

            try (var stack = stackPush()) {
                VkSemaphoreCreateInfo semaphoreCreateInfo = VkSemaphoreCreateInfo.calloc(stack)
                        .sType$Default();
                var pSemaphore = stack.mallocLong(1);
                for (int i = 0; i < renderCompleteSemaphores.length; i++) {
                    vkCreateSemaphore(init.getDevice(),
                            semaphoreCreateInfo,
                            null,
                            pSemaphore);
                    renderCompleteSemaphores[i] = pSemaphore.get(0);
                }
            }

            Arrays.fill(first, true);
        }

        void destroy(TestVulkanInit init) {
            for (long semaphore : renderCompleteSemaphores) {
                vkDestroySemaphore(init.getDevice(), semaphore, null);
            }

            for (var surf : swapchainSurfaces) {
                surf.unref();
            }

            KHRSwapchain.vkDestroySwapchainKHR(init.getDevice(), swapchain, null);
            KHRSurface.vkDestroySurfaceKHR(init.getInstance(), vkSurface, null);
            Callbacks.glfwFreeCallbacks(window);
            glfwDestroyWindow(window);
        }
    }

    public static void main(String[] args) {
        System.setProperty("java.awt.headless", "true");
        /*TinyFileDialogs.tinyfd_messageBox(
                "Arc3D Test",
                "Arc3D starting with pid: " + ProcessHandle.current().pid(),
                "ok",
                "info",
                1
        );*/
        glfwInit();
        GLFWVulkan.setPath(VK.getFunctionProvider());
        try (var init = new TestVulkanInit(LOGGER)) {
            init.initialize();

            ContextOptions contextOptions = new ContextOptions();
            contextOptions.mLogger = LOGGER;
            contextOptions.mMaxCommandBuffersInflight = 2;
            @SharedPtr
            ImmediateContext immediateContext = init.createContext(contextOptions);
            if (immediateContext == null) {
                LOGGER.error("Failed to create Vulkan context");
                return;
            }
            if (!GraniteUtil.init(immediateContext)) {
                throw new RuntimeException();
            }

            var painter = new TestGraniteRenderer.Painter(immediateContext);

            WindowContext[] windows = new WindowContext[1];
            for (int i = 0; i < windows.length; i++) {
                windows[i] = new WindowContext(init, painter.mRC);
            }

            VkSemaphoreCreateInfo semaphoreCreateInfo = VkSemaphoreCreateInfo.calloc()
                    .sType$Default();


            for (;;) {
                boolean[] windowAlive = new boolean[windows.length];
                int windowCount = 0;
                for (int i = 0; i < windows.length; i++) {
                    windowAlive[i] = !glfwWindowShouldClose(windows[i].window);
                    if (windowAlive[i]) {
                        windowCount++;
                    }
                }
                if (windowCount == 0) {
                    break;
                }
                glfwPollEvents();

                long[] acquireSemaphores = new long[windows.length];
                int[] windowImageIndices = new int[windows.length];
                try (var stack = stackPush()) {
                    var pAcquireSemaphore = stack.mallocLong(1);
                    for (int i = 0; i < acquireSemaphores.length; i++) {
                        if (!windowAlive[i]) continue;
                        vkCreateSemaphore(init.getDevice(),
                                semaphoreCreateInfo, null, pAcquireSemaphore);
                        acquireSemaphores[i] = pAcquireSemaphore.get(0);
                    }
                    var pImageIndex = stack.mallocInt(1);
                    int result;
                    for (int i = 0; i < windowImageIndices.length; i++) {
                        if (!windowAlive[i]) continue;
                        result = vkAcquireNextImageKHR(init.getDevice(),
                                windows[i].swapchain, VKUtil.UINT64_MAX, acquireSemaphores[i],
                                VK_NULL_HANDLE, pImageIndex);
                        VKUtil._CHECK_(result);
                        windowImageIndices[i] = pImageIndex.get(0);
                        //LOGGER.info("Time after acquire returns: {}", String.format("%.2f", GLFW.glfwGetTime() * 1000));
                    }
                }

                Recording recording;
                if (!RENDER_TO_SWAPCHAIN_DIRECTLY) {
                    recording = painter.paint();
                } else {
                    Surface surface = windows[0].swapchainSurfaces[windowImageIndices[0]];
                    recording = painter.paint(surface);
                }

                var currentCmdBuffer = (VulkanCommandBuffer) immediateContext.getQueueManager().getCurrentCommandBuffer(true);
                for (int i = 0; i < windows.length; i++) {
                    if (windowAlive[i]) {
                        currentCmdBuffer.waitSemaphore(new VulkanBackendSemaphore(acquireSemaphores[i]));
                    }
                }

                if (!immediateContext.addTask(recording)) {
                    LOGGER.error("Failed to add recording: {}", recording);
                }
                if (recording != null) {
                    recording.close();
                }

                currentCmdBuffer.addFinishedCallback(
                        success -> {
                            for (int i = 0; i < windows.length; i++) {
                                if (windowAlive[i]) {
                                    vkDestroySemaphore(init.getDevice(), acquireSemaphores[i], null);
                                }
                            }
                        }
                );

                for (int i = 0; i < windows.length; i++) {
                    if (!windowAlive[i]) continue;
                    GraniteSurface backSurface = ((GraniteSurface) windows[i].swapchainSurfaces[windowImageIndices[i]]);
                    Image dstImage = backSurface.getBackingTarget().getImage();
                    if (!RENDER_TO_SWAPCHAIN_DIRECTLY) {
                        Image srcImage = ((GraniteSurface) painter.mSurface).getBackingTarget().getImage();
                        currentCmdBuffer.copyImage(
                                srcImage, 0, 0, WINDOW_WIDTH, WINDOW_HEIGHT,
                                dstImage,
                                0, 0, 0
                        );
                    }
                    currentCmdBuffer.prepareSurfaceForStateUpdate(
                            dstImage, null, true
                    );
                    /*Image srcImage = ((GraniteSurface) painter.mSurface).getBackingTarget().getImage();
                    currentCmdBuffer
                            .setupImageForPresent((VulkanImage) srcImage, WINDOW_WIDTH, WINDOW_HEIGHT,
                                    windows[i].swapchainImages[windowImageIndices[i]], windows[i].first[windowImageIndices[i]]);
                    windows[i].first[windowImageIndices[i]] = false;*/

                    currentCmdBuffer
                            .signalSemaphore(new VulkanBackendSemaphore(windows[i].renderCompleteSemaphores[windowImageIndices[i]]));
                }

                if (!immediateContext.submit()) {
                    LOGGER.error("Failed to submit queue");
                }

                /*try {
                    Thread.sleep(1);
                } catch (InterruptedException e) {
                    throw new RuntimeException(e);
                }*/

                try (var stack = stackPush()) {
                    var waitSem = stack.mallocLong(windowCount);
                    var indices = stack.mallocInt(windowCount);
                    var swapchains = stack.mallocLong(windowCount);
                    for (int i = 0; i < windows.length; i++) {
                        if (windowAlive[i]) {
                            waitSem.put(windows[i].renderCompleteSemaphores[windowImageIndices[i]]);
                            indices.put(windowImageIndices[i]);
                            swapchains.put(windows[i].swapchain);
                        }
                    }
                    waitSem.flip();
                    indices.flip();
                    swapchains.flip();
                    VkPresentInfoKHR present = VkPresentInfoKHR.calloc(stack)
                            .sType$Default()
                            .pWaitSemaphores(waitSem)
                            .swapchainCount(windowCount)
                            .pSwapchains(swapchains)
                            .pImageIndices(indices);

                    vkQueuePresentKHR(
                            ((VulkanQueueManager) immediateContext.getQueueManager()).getQueue(),
                            present);
                }
                //LOGGER.info("Time after present returns: {}", String.format("%.2f", GLFW.glfwGetTime() * 1000));
            }

            semaphoreCreateInfo.free();

            painter.close();

            LOGGER.info("Total command buffers: {}", immediateContext.getQueueManager().getCurrentCommandBufferCount());
            immediateContext.unref();

            // wait for presentation engine before destroying swapchains
            vkDeviceWaitIdle(init.getDevice());
            for (WindowContext window : windows) {
                window.destroy(init);
            }
        } finally {
            glfwTerminate();
        }
    }
}
