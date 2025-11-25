/*
 * This file is part of Arc3D.
 *
 * Copyright (C) 2024 BloCamLimb <pocamelards@gmail.com>
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

package icyllis.arc3d.granite;

import icyllis.arc3d.core.RawPtr;
import icyllis.arc3d.core.SharedPtr;
import icyllis.arc3d.engine.ImageProxyView;
import icyllis.arc3d.engine.SamplerDesc;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;

import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.function.Function;

public class TextureDataGatherer implements AutoCloseable {

    private final IdentityHashMap<@RawPtr ImageProxyView, Integer> mTextureToIndex = new IdentityHashMap<>();
    private ObjectArrayList<@SharedPtr ImageProxyView> mIndexToTexture = new ObjectArrayList<>();
    private final Function<@RawPtr ImageProxyView, Integer> mTextureAccumulator = (@RawPtr ImageProxyView texture) -> {
        int index = mIndexToTexture.size();
        // persist the proxy ref, since ImageProxyView represents a single owner, just create new one
        mIndexToTexture.add(new ImageProxyView(texture));
        return index;
    };

    private final HashMap<SamplerDesc, Integer> mSamplerToIndex = new HashMap<>();
    private ObjectArrayList<SamplerDesc> mIndexToSampler = new ObjectArrayList<>();
    private final Function<SamplerDesc, Integer> mSamplerAccumulator = sampler -> {
        int index = mIndexToSampler.size();
        mIndexToSampler.add(sampler);
        return index;
    };

    final IntArrayList mTextureData = new IntArrayList();

    public void add(@RawPtr ImageProxyView textureView, SamplerDesc samplerDesc) {
        assert samplerDesc != null;
        int textureIndex = mTextureToIndex.computeIfAbsent(textureView, mTextureAccumulator);
        int samplerIndex = mSamplerToIndex.computeIfAbsent(samplerDesc, mSamplerAccumulator);
        mTextureData.add(textureIndex);
        mTextureData.add(samplerIndex);
    }

    public void reset() {
        mTextureData.clear();
    }

    /**
     * Returns a copied int array representing the texture binding.
     * Holds repeated texture index and sampler index.
     */
    public int[] finish() {
        return mTextureData.toIntArray();
    }

    ObjectArrayList<@SharedPtr ImageProxyView> detachTextureViews() {
        var res = mIndexToTexture;
        mIndexToTexture = null;
        return res;
    }

    ObjectArrayList<SamplerDesc> detachSamplerDescs() {
        var res = mIndexToSampler;
        mIndexToSampler = null;
        return res;
    }

    @Override
    public void close() {
        if (mIndexToTexture != null) {
            mIndexToTexture.forEach(ImageProxyView::close);
        }
        mIndexToTexture = null;
    }
}
