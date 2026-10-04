package com.mojang.blaze3d.audio;

import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import org.lwjgl.openal.AL10;

@OnlyIn(Dist.CLIENT)
public class Listener {
    private ListenerTransform transform = ListenerTransform.INITIAL;

    public void setTransform(final ListenerTransform transform) {
        // 🔧 MCRe 修复（2026-10-02）：不再把监听器位置强制锁到原点！
        //
        // 【bug 现象】UI 声音（相对位置、无距离衰减）正常，但破坏方块 / 攻击生物 / 吃东西
        // 等世界音效离 (0,0) 一远就完全听不见。
        //
        // 【根因】此前为「避免大坐标下浮点溢出」把 position 换成 Vec3.ZERO：
        //   ListenerTransform fixedTransform = new ListenerTransform(Vec3.ZERO, ...);
        // 而 OpenAL 的距离衰减模型（AL_INVERSE_DISTANCE_CLAMPED）是按
        //   「监听器 ↔ 声源」的相对距离 计算增益的：
        //   gain = AL_REFERENCE_DISTANCE / (AL_REFERENCE_DISTANCE + AL_ROLLOFF_FACTOR × (d - AL_REFERENCE_DISTANCE))
        // 监听器钉在原点后，声源在玩家附近的 (x,y,z) 会被算出「到原点的距离」= |(x,y,z)|，
        // 玩家跑到远处（例：边境之地 X=1.25e7）→ d ≈ 1.25e7 → gain ≈ 3e-6 → 静音。
        //
        // 【修法】保留真实相机位置：监听器与声源处在同一坐标系，相对距离天然正确。
        // 大坐标下 float 精度退化只影响极近距离的定位精度（不至于整体静音），
        // 远好于「整段世界音效全哑」。
        this.transform = transform;
        Vec3 position = transform.position();
        Vec3 forward = transform.forward();
        Vec3 up = transform.up();
        AL10.alListener3f(4100, (float) position.x, (float) position.y, (float) position.z);
        AL10.alListenerfv(4111, new float[] {
            (float) forward.x, (float) forward.y, (float) forward.z,
            (float) up.x, (float) up.y, (float) up.z
        });
    }

    public void reset() {
        this.setTransform(ListenerTransform.INITIAL);
    }

    public ListenerTransform getTransform() {
        return this.transform;
    }
}