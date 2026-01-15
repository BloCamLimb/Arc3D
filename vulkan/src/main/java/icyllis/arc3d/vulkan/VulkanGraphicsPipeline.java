/*
 * This file is part of Arc3D.
 *
 * Copyright (C) 2025 BloCamLimb <pocamelards@gmail.com>
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

import icyllis.arc3d.core.RawPtr;
import icyllis.arc3d.core.RefCnt;
import icyllis.arc3d.core.SharedPtr;
import icyllis.arc3d.engine.GraphicsPipeline;
import org.jspecify.annotations.NonNull;

import java.util.Arrays;
import java.util.concurrent.CompletableFuture;

import static org.lwjgl.vulkan.VK10.*;

public final class VulkanGraphicsPipeline extends GraphicsPipeline {

    // all plain fields rely on external synchronization

    private long mPipeline = VK_NULL_HANDLE;
    private long mPipelineLayout = VK_NULL_HANDLE;

    private CompletableFuture<VulkanGraphicsPipelineBuilder> mAsyncWork;

    @SharedPtr
    private VulkanDescriptorSetManager[] mDescriptorSetManagers;

    // this ref just keeps it alive, the same instance will be retrieved in beginRenderPass(),
    // if no pipeline needs the same VulkanRenderPassSet, it will be deleted ASAP
    @SharedPtr
    private VulkanRenderPassSet mRenderPassSet;

    private int mVertexBindingCount;

    VulkanGraphicsPipeline(VulkanDevice device,
                           CompletableFuture<VulkanGraphicsPipelineBuilder> asyncWork) {
        super(device);
        mAsyncWork = asyncWork;
    }

    void init(long pipeline,
              long pipelineLayout,
              @SharedPtr VulkanDescriptorSetManager[] descriptorSetManagers,
              @SharedPtr VulkanRenderPassSet renderPassSet,
              int vertexBindingCount) {
        mPipeline = pipeline;
        mPipelineLayout = pipelineLayout;
        mDescriptorSetManagers = descriptorSetManagers; // move
        mRenderPassSet = renderPassSet; // move
        mVertexBindingCount = vertexBindingCount;
    }

    @Override
    protected void deallocate() {
        ((VulkanDevice) getDevice()).executeRenderCall(dev -> {
            // AsyncWork is checked on ExecutingThread
            if (mAsyncWork != null) {
                mAsyncWork.whenComplete((res, ex) -> {
                    if (res != null) {
                        res.destroy();
                    } else {
                        // this would be impossible, however if it does occur, it should happen in
                        // PipelineDesc.create*PipelineInfo, so there should be no resource leak.
                        dev.getLogger().error("Fatal error encountered during pipeline compilation", ex);
                    }
                });
                mAsyncWork = null;
            }
            mRenderPassSet = RefCnt.move(mRenderPassSet);
            if (mDescriptorSetManagers != null) {
                for (VulkanDescriptorSetManager manager : mDescriptorSetManagers) {
                    if (manager != null) {
                        manager.unref();
                    }
                }
                Arrays.fill(mDescriptorSetManagers, null);
            }
            if (mPipelineLayout != VK_NULL_HANDLE) {
                vkDestroyPipelineLayout(dev.vkDevice(), mPipelineLayout, null);
                mPipelineLayout = VK_NULL_HANDLE;
            }
            if (mPipeline != VK_NULL_HANDLE) {
                vkDestroyPipeline(dev.vkDevice(), mPipeline, null);
                mPipeline = VK_NULL_HANDLE;
            }
            dev.needsPurgeRenderPasses();
            dev.needsPurgeDescriptorSets();
        });
    }

    private void checkAsyncWork(VulkanResourceProvider resourceProvider) {
        // pass the ImmediateContext's ResourceProvider to manage descriptor set pools
        boolean success = mAsyncWork.join().finish(this, resourceProvider);
        var stats = getDevice().getDeviceBoundCache().getStats();
        if (success) {
            stats.incNumCompilationSuccesses();
        } else {
            stats.incNumCompilationFailures();
        }
        mAsyncWork = null;
    }

    // ExecutingThread only
    public boolean bindPipeline(VulkanCommandBuffer commandBuffer) {
        if (mAsyncWork != null) {
            checkAsyncWork(commandBuffer.mResourceProvider);
        }
        if (mPipeline != VK_NULL_HANDLE) {
            vkCmdBindPipeline(
                    commandBuffer.mCommandBuffer,
                    VK_PIPELINE_BIND_POINT_GRAPHICS,
                    mPipeline
            );
            return true;
        }
        return false;
    }

    // call bindPipeline() first, can be NULL
    public long vkPipelineLayout() {
        return mPipelineLayout;
    }

    @NonNull
    @RawPtr
    public VulkanDescriptorSetManager getDescriptorSetManager(int setIndex) {
        return mDescriptorSetManagers[setIndex];
    }

    /**
     * @return number of descriptor set layouts
     */
    public int getSetCount() {
        return mDescriptorSetManagers.length;
    }

    public int getVertexBindingCount() {
        return mVertexBindingCount;
    }
}
