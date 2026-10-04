package com.mojang.blaze3d.vulkan;

import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.buffers.GpuBufferSlice;
import java.nio.ByteBuffer;
import java.nio.LongBuffer;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Supplier;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import org.jspecify.annotations.Nullable;
import org.lwjgl.PointerBuffer;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.system.MemoryUtil;
import org.lwjgl.util.vma.Vma;
import org.lwjgl.util.vma.VmaAllocationCreateInfo;
import org.lwjgl.vulkan.VK12;
import org.lwjgl.vulkan.VkBufferCreateInfo;
import org.lwjgl.vulkan.VkBufferViewCreateInfo;

@OnlyIn(Dist.CLIENT)
public abstract class VulkanGpuBuffer extends GpuBuffer implements Destroyable {
    private final long vkBuffer;

    public VulkanGpuBuffer(final long vkBuffer, final @GpuBuffer.Usage int usage, final long size) {
        super(usage, size);
        this.vkBuffer = vkBuffer;
    }

    public long vkBuffer() {
        return this.vkBuffer;
    }

    // 🔧 MCRe（C2ME 一档优化）：BufferView 缓存（TEXEL_BUFFER 描述符推送复用；buffer 销毁时同步清理）
    private final Map<BufferViewKey, Long> bufferViews = new HashMap<>();

    private record BufferViewKey(long offset, long range, int formatVk) {
    }

    /**
     * 🔧 MCRe：获取或创建 BufferView（同 buffer+offset+range+format 复用，不再每次推送新建+销毁排队）。
     * 视图生命周期跟随 buffer：销毁经延迟队列发生在飞行帧之后（引用它的命令早已执行完，安全）。
     */
    public long getOrCreateBufferView(final VulkanDevice device, final long offset, final long range, final int formatVk) {
        if (this.isClosed()) {
            throw new IllegalStateException("Attempt to create buffer view on closed buffer");
        }

        return this.bufferViews.computeIfAbsent(new BufferViewKey(offset, range, formatVk), key -> {
            try (MemoryStack stack = MemoryStack.stackPush()) {
                VkBufferViewCreateInfo info = VkBufferViewCreateInfo.calloc(stack).sType$Default();
                info.buffer(this.vkBuffer);
                info.offset(key.offset());
                info.range(key.range());
                info.format(key.formatVk());
                LongBuffer ptr = stack.callocLong(1);
                VulkanUtils.crashIfFailure(
                    device, VK12.vkCreateBufferView(device.vkDevice(), info, null, ptr), "Couldn't create buffer view for texel buffer"
                );
                return ptr.get(0);
            }
        });
    }

    // 🔧 MCRe：销毁全部缓存 BufferView（供子类 destroy 调用；方法调用不涉及字段访问检查）
    protected void destroyBufferViews(final VulkanDevice device) {
        for (Long viewHandle : this.bufferViews.values()) {
            VK12.vkDestroyBufferView(device.vkDevice(), viewHandle, null);
        }

        this.bufferViews.clear();
    }

    @OnlyIn(Dist.CLIENT)
    public static class Direct extends VulkanGpuBuffer {
        private boolean closed;
        protected final VulkanDevice device;
        private final long vmaAllocation;
        private int mappingRefCount;
        private final boolean isMappedPersistent;
        private final long mappedPointer; // 持久映射的指针，非持久则为 0

        public Direct(
            final VulkanDevice device,
            final @Nullable Supplier<String> label,
            final @GpuBuffer.Usage int usage,
            final long size,
            final boolean forceHostVisibleAllocation
        ) {
            this.device = device;

            int vmaUsage;
            int vmaFlags = 0;
            boolean persistentMapped = false;
            boolean needsHostAccess = (usage & (GpuBuffer.USAGE_MAP_READ | GpuBuffer.USAGE_MAP_WRITE)) != 0;
            
            if (needsHostAccess || forceHostVisibleAllocation) {
                vmaUsage = Vma.VMA_MEMORY_USAGE_CPU_TO_GPU;
                vmaFlags |= Vma.VMA_ALLOCATION_CREATE_MAPPED_BIT;
                persistentMapped = true;
            } else {
                vmaUsage = Vma.VMA_MEMORY_USAGE_GPU_ONLY;
                persistentMapped = false;
            }

            long vkBuffer;
            long vmaAlloc;
            long mappedPtr = 0L;
            try (MemoryStack stack = MemoryStack.stackPush()) {
                VkBufferCreateInfo bufferCreateInfo = VkBufferCreateInfo.calloc(stack).sType$Default();
                bufferCreateInfo.size(size);
                bufferCreateInfo.usage(VulkanConst.bufferUsageToVk(usage));
                // 🔧 MCRe（Vulkan 二期）：transfer 队列分离时 CONCURRENT 双家族（跨队列免 QFO barriers）；回退设备 EXCLUSIVE（原样）
                int[] sharingFamilies = device.sharingModeFamilies();
                bufferCreateInfo.sharingMode(sharingFamilies != null ? 2 : 0);
                bufferCreateInfo.pQueueFamilyIndices(sharingFamilies != null ? stack.ints(sharingFamilies) : null);
                
                VmaAllocationCreateInfo allocCreateInfo = VmaAllocationCreateInfo.calloc(stack);
                allocCreateInfo.usage(vmaUsage);
                allocCreateInfo.flags(vmaFlags);

                LongBuffer bufferPtr = stack.callocLong(1);
                PointerBuffer allocPtr = stack.callocPointer(1);
                int result = Vma.vmaCreateBuffer(device.vma(), bufferCreateInfo, allocCreateInfo, bufferPtr, allocPtr, null);
                VulkanUtils.crashIfFailure(device, result, "Failed to allocate VkBuffer");
                vkBuffer = bufferPtr.get(0);
                vmaAlloc = allocPtr.get(0);
                if (label != null) {
                    device.instance().debug().setObjectName(device.vkDevice(), 9, vkBuffer, label);
                }

                // 如果持久映射，立即映射获取指针
                if (persistentMapped) {
                    PointerBuffer mappedPtrBuf = stack.callocPointer(1);
                    result = Vma.vmaMapMemory(device.vma(), vmaAlloc, mappedPtrBuf);
                    VulkanUtils.crashIfFailure(device, result, "Failed to map persistent buffer");
                    mappedPtr = mappedPtrBuf.get(0);
                }
            }

            super(vkBuffer, usage, size);
            this.closed = false;
            this.vmaAllocation = vmaAlloc;
            this.mappingRefCount = 0;
            this.isMappedPersistent = persistentMapped;
            this.mappedPointer = mappedPtr;
        }

        @Override
        public void destroy() {
            // 如果是持久映射，需要 unmap
            if (this.isMappedPersistent && this.mappedPointer != 0L) {
                Vma.vmaUnmapMemory(this.device.vma(), this.vmaAllocation);
            }

            // 🔧 MCRe（C2ME 一档优化）：销毁缓存的 BufferView（经延迟队列，此时飞行帧早已完成，安全）
            this.destroyBufferViews(this.device);
            Vma.vmaDestroyBuffer(this.device.vma(), this.vkBuffer(), this.vmaAllocation);
        }

        @Override
        public boolean isClosed() {
            return this.closed;
        }

        @Override
        public void close() {
            if (!this.closed) {
                this.closed = true;
                if (this.mappingRefCount != 0) {
                    throw new IllegalStateException("Attempt to close a mapped buffer");
                }
                this.device.createCommandEncoder().queueForDestroy(this);
            }
        }

        @Override
        public GpuBufferSlice.MappedView map(final long offset, final long length, final boolean read, final boolean write) {
            if (this.isClosed()) {
                throw new IllegalStateException("Buffer already closed");
            }

            if (!read && !write) {
                throw new IllegalArgumentException("At least read or write must be true");
            }

            if (read && (this.usage() & 1) == 0) {
                throw new IllegalStateException("Buffer is not readable");
            }

            if (write && (this.usage() & 2) == 0) {
                throw new IllegalStateException("Buffer is not writable");
            }

            if (offset + length > this.size()) {
                throw new IllegalArgumentException(
                    "Cannot map more data than this buffer can hold (attempting to map "
                        + length
                        + " bytes at offset "
                        + offset
                        + " from "
                        + this.size()
                        + " size buffer)"
                );
            }

            if (length > 2147483647L) {
                throw new IllegalArgumentException("Mapping buffer slice larger than 2GB is not supported");
            }

            if (offset < 0L || length < 0L) {
                throw new IllegalArgumentException("Offset or length must be positive integer values");
            }

            this.mappingRefCount++;

            if (this.isMappedPersistent) {
                // 持久映射：直接使用已映射的指针切片
                ByteBuffer byteBuffer = MemoryUtil.memByteBuffer(this.mappedPointer + offset, (int)length);
                return new GpuBufferSlice.MappedView(
                    this.slice(offset, length),
                    byteBuffer,
                    () -> {
                        // 关闭时仅减少引用计数，不真正 unmap
                        this.mappingRefCount--;
                    }
                );
            } else {
                // 非持久映射：临时映射
                try (MemoryStack stack = MemoryStack.stackPush()) {
                    PointerBuffer pointer = stack.callocPointer(1);
                    int result = Vma.vmaMapMemory(this.device.vma(), this.vmaAllocation, pointer);
                    VulkanUtils.crashIfFailure(this.device, result, "Failed to map buffer");
                    long ptr = pointer.get(0) + offset;
                    ByteBuffer byteBuffer = MemoryUtil.memByteBuffer(ptr, (int)length);
                    return new GpuBufferSlice.MappedView(
                        this.slice(offset, length),
                        byteBuffer,
                        () -> {
                            this.mappingRefCount--;
                            if (this.mappingRefCount == 0) {
                                Vma.vmaUnmapMemory(this.device.vma(), this.vmaAllocation);
                            }
                        }
                    );
                }
            }
        }
    }
}