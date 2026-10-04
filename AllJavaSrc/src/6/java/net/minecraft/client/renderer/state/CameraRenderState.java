package net.minecraft.client.renderer.state;

import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;

public class CameraRenderState {
   public BlockPos blockPos = BlockPos.ZERO;
   public Vec3 pos = new Vec3(0.0, 0.0, 0.0);
   public boolean initialized;
   public Vec3 entityPos = new Vec3(0.0, 0.0, 0.0);
   public Quaternionf orientation = new Quaternionf();
}
