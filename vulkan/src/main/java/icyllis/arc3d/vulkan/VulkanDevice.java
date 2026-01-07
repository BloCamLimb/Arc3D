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

package icyllis.arc3d.vulkan;

import icyllis.arc3d.core.Rect2i;
import icyllis.arc3d.core.RefCnt;
import icyllis.arc3d.core.SharedPtr;
import icyllis.arc3d.engine.*;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.vulkan.*;

import java.util.HashMap;
import java.util.Set;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

import static org.lwjgl.vulkan.VK11.*;

public final class VulkanDevice extends Device {

    private final VkPhysicalDevice mPhysicalDevice;
    private final VkDevice mDevice;
    private final VulkanMemoryAllocator mMemoryAllocator;
    private boolean mProtectedContext;
    private final int mQueueIndex;

    private final ConcurrentLinkedQueue<Consumer<VulkanDevice>> mRenderCalls =
            new ConcurrentLinkedQueue<>();

    // executing thread only
    private final HashMap<VulkanRenderPassSet.CompatibleKey, @SharedPtr VulkanRenderPassSet> mRenderPassCompatibilityCache
            = new HashMap<>();
    private final VulkanRenderPassSet.CompatibleKey mLookupCompatibleRenderPassKey
            = new VulkanRenderPassSet.CompatibleKey();
    // multiple producers, one consumer
    private final AtomicBoolean mNeedsPurgeRenderPasses = new AtomicBoolean(false);

    // executing thread only
    private final HashMap<DescriptorSetLayout, @SharedPtr VulkanDescriptorSetManager> mDescriptorSetManagers
            = new HashMap<>();
    // executing thread only
    private boolean mNeedsPurgeDescriptorSets = false;

    public VulkanDevice(ContextOptions options, VulkanCaps caps,
                        VulkanBackendContext backendContext,
                        VulkanMemoryAllocator memoryAllocator) {
        super(Engine.BackendApi.kVulkan, options, caps);
        mMemoryAllocator = memoryAllocator;
        mPhysicalDevice = backendContext.mPhysicalDevice;
        mDevice = backendContext.mDevice;
        mQueueIndex = backendContext.mGraphicsQueueIndex;
    }

    @Nullable
    public static VulkanDevice make(@NonNull VulkanBackendContext context,
                                    ContextOptions options) {
        if (context.mInstance == null ||
                context.mPhysicalDevice == null ||
                context.mDevice == null ||
                context.mQueue == null) {
            return null;
        }

        VulkanCaps caps;
        try (var stack = MemoryStack.stackPush()) {
            final VkPhysicalDeviceProperties2 properties2 = VkPhysicalDeviceProperties2
                    .calloc(stack)
                    .sType$Default();
            vkGetPhysicalDeviceProperties2(context.mPhysicalDevice, properties2);
            final VkPhysicalDeviceProperties properties = properties2.properties();
            caps = new VulkanCaps(options,
                    context.mPhysicalDevice,
                    properties.apiVersion(),
                    context.mDeviceFeatures2,
                    context.mInstance.getCapabilities(),
                    context.mDevice.getCapabilities());
        }

        VulkanMemoryAllocator allocator = context.mMemoryAllocator;
        if (allocator == null) {
            return null;
        }

        return new VulkanDevice(options, caps,
                context, allocator);
    }

    public VkDevice vkDevice() {
        return mDevice;
    }

    public VkPhysicalDevice vkPhysicalDevice() {
        return mPhysicalDevice;
    }

    public int getQueueIndex() {
        return mQueueIndex;
    }

    public VulkanMemoryAllocator getMemoryAllocator() {
        return mMemoryAllocator;
    }

    public boolean checkResult(int result) {
        if (result == VK_SUCCESS || result > 0) {
            return true;
        }
        switch (result) {
            case VK_ERROR_DEVICE_LOST -> mDeviceIsLost = true;
            case VK_ERROR_OUT_OF_DEVICE_MEMORY,
                 VK_ERROR_OUT_OF_HOST_MEMORY -> mOutOfMemoryEncountered = true;
        }
        return false;
    }

    public boolean isProtectedContext() {
        return mProtectedContext;
    }

    /**
     * Execute the Vulkan command on ExecutingThread as soon as possible.
     */
    public void executeRenderCall(Consumer<VulkanDevice> renderCall) {
        if (isOnExecutingThread()) {
            renderCall.accept(this);
        } else {
            recordRenderCall(renderCall);
        }
    }

    public void recordRenderCall(Consumer<VulkanDevice> renderCall) {
        mRenderCalls.add(renderCall);
    }

    public void flushRenderCalls() {
        //noinspection UnnecessaryLocalVariable
        final var queue = mRenderCalls;
        Consumer<VulkanDevice> r;
        while ((r = queue.poll()) != null) r.accept(this);
    }

    @Override
    public void disconnect(boolean cleanup) {
        super.disconnect(cleanup);

        flushRenderCalls();

        for (VulkanRenderPassSet compatibleSet : mRenderPassCompatibilityCache.values()) {
            compatibleSet.unref();
        }
        mRenderPassCompatibilityCache.clear();
    }

    public void needsPurgeRenderPasses() {
        // memory_order_release
        mNeedsPurgeRenderPasses.setRelease(true);
    }

    public void needsPurgeDescriptorSets() {
        mNeedsPurgeDescriptorSets = true;
    }

    @Override
    protected void freeGpuResources() {
        super.freeGpuResources();
        // memory_order_relaxed
        mNeedsPurgeRenderPasses.setOpaque(false);
        for (var it = mRenderPassCompatibilityCache.values().iterator(); it.hasNext(); ) {
            var compatibleSet = it.next();
            if (compatibleSet.unique()) {
                compatibleSet.unref();
                it.remove();
            } else {
                compatibleSet.purgeAllFramebuffers();
            }
        }
        purgeStaleDescriptorSetManagers();
    }

    @Override
    protected void purgeResourcesNotUsedSince(long timeMillis) {
        super.purgeResourcesNotUsedSince(timeMillis);
        // memory_order_relaxed
        mNeedsPurgeRenderPasses.setOpaque(false);
        for (var it = mRenderPassCompatibilityCache.values().iterator(); it.hasNext(); ) {
            var compatibleSet = it.next();
            if (compatibleSet.unique()) {
                compatibleSet.unref();
                it.remove();
            } else {
                compatibleSet.purgeFramebuffersNotUsedSince(timeMillis);
            }
        }
        purgeStaleDescriptorSetManagers();
    }

    public void purgeStaleResourcesIfNeeded() {
        // compare_exchange_strong(acquire, relaxed)
        if (mNeedsPurgeRenderPasses.compareAndExchangeAcquire(true, false)) {
            for (var it = mRenderPassCompatibilityCache.values().iterator(); it.hasNext(); ) {
                var compatibleSet = it.next();
                if (compatibleSet.unique()) {
                    compatibleSet.unref();
                    it.remove();
                } else {
                    compatibleSet.purgeStaleFramebuffers();
                }
            }
        }
        if (mNeedsPurgeDescriptorSets) {
            purgeStaleDescriptorSetManagers();
        }
    }

    private void purgeStaleDescriptorSetManagers() {
        mNeedsPurgeDescriptorSets = false;
        for (var it = mDescriptorSetManagers.values().iterator(); it.hasNext(); ) {
            var manager = it.next();
            if (manager.unique()) {
                manager.unref();
                it.remove();
            }
        }
    }

    @Nullable
    @SharedPtr
    public VulkanRenderPassSet findOrCreateCompatibleRenderPassSet(@NonNull RenderPassDesc desc) {
        var key = mLookupCompatibleRenderPassKey.update(desc);
        VulkanRenderPassSet renderPassSet = mRenderPassCompatibilityCache.get(key);
        if (renderPassSet != null) {
            renderPassSet.ref();
            return renderPassSet;
        }

        renderPassSet = VulkanRenderPassSet.make(this, desc);
        if (renderPassSet == null) {
            return null;
        }
        mRenderPassCompatibilityCache.put(key.copy(), renderPassSet); // move
        renderPassSet.ref();
        return renderPassSet;
    }

    @Nullable
    @SharedPtr
    public VulkanRenderPass findOrCreateRenderPass(@NonNull RenderPassDesc desc) {
        @SharedPtr
        VulkanRenderPassSet renderPassSet = findOrCreateCompatibleRenderPassSet(desc);
        if (renderPassSet == null) {
            return null;
        }
        @SharedPtr
        VulkanRenderPass renderPass = renderPassSet.findOrCreateRenderPass(this, desc);
        renderPassSet.unref();
        return renderPass; // move
    }

    // Executing thread only, resource provider is always from ImmediateContext
    @Nullable
    @SharedPtr
    public VulkanDescriptorSetManager findOrCreateDescriptorSetManager(VulkanResourceProvider resourceProvider,
                                                                       @NonNull DescriptorSetLayout layoutInfo) {
        VulkanDescriptorSetManager setManager = mDescriptorSetManagers.get(layoutInfo);
        if (setManager != null) {
            setManager.ref();
            return setManager;
        }

        setManager = VulkanDescriptorSetManager.make(this, resourceProvider, layoutInfo);
        if (setManager == null) {
            return null;
        }

        mDescriptorSetManagers.put(layoutInfo, setManager);
        setManager.ref();
        return setManager;
    }

    @Override
    public ResourceProvider makeResourceProvider(Context context, long maxResourceBudget) {
        return new VulkanResourceProvider(this, context, maxResourceBudget);
    }

    @Nullable
    @Override
    protected GpuRenderTarget onCreateRenderTarget(int width, int height, int sampleCount, int numColorTargets, Image @Nullable[] colorTargets, Image @Nullable[] resolveTargets, int @Nullable[] mipLevels, @Nullable Image depthStencilTarget, int surfaceFlags) {
        return null;
    }

    @Nullable
    @Override
    protected GpuRenderTarget onWrapRenderableBackendTexture(BackendImage texture, int sampleCount, boolean ownership) {
        return null;
    }

    @Nullable
    @Override
    public GpuRenderTarget onWrapBackendRenderTarget(BackendRenderTarget backendRenderTarget) {
        return null;
    }

    @Override
    protected OpsRenderPass onGetOpsRenderPass(ImageProxyView writeView, Rect2i contentBounds, byte colorOps, byte stencilOps, float[] clearColor, Set<SurfaceProxy> sampledTextures, int pipelineFlags) {
        return null;
    }

    @Override
    protected void onResolveRenderTarget(GpuRenderTarget renderTarget, int resolveLeft, int resolveTop, int resolveRight, int resolveBottom) {

    }

    @Override
    public long insertFence() {
        return 0;
    }

    @Override
    public boolean checkFence(long fence) {
        return false;
    }

    @Override
    public void deleteFence(long fence) {

    }

    @Override
    public void addFinishedCallback(FlushInfo.FinishedCallback callback) {

    }

    @Override
    public void checkFinishedCallbacks() {

    }

    @Override
    public void waitForQueue() {

    }
}
