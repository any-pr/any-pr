package me.noisefarlands.mcbig.phys;

import me.noisefarlands.mcbig.core.BigVec3;
import me.noisefarlands.mcbig.Math.BigMath;
import me.noisefarlands.mcbig.util.DynamicNumber;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

public abstract class BigHitResult {

    protected final BigVec3 location;

    protected BigHitResult(BigVec3 location) {
        this.location = location;
    }

    public BigVec3 getBigLocation() {
        return this.location;
    }

    // ===== 使用完全限定名避免与内部类 Entity 冲突 =====
    public DynamicNumber bigDistanceTo(net.minecraft.world.entity.Entity entity) {
        BigVec3 entityPos = new BigVec3(entity.getX(), entity.getY(), entity.getZ());
        return BigMath.distanceSquared(
            this.location.bigX(), this.location.bigY(), this.location.bigZ(),
            entityPos.bigX(), entityPos.bigY(), entityPos.bigZ()
        );
    }

    public double distanceTo(net.minecraft.world.entity.Entity entity) {
        return bigDistanceTo(entity).doubleValue();
    }
    // ===== 修改结束 =====

    public Vec3 getLocation() {
        return this.location.toVec3();
    }

    public BigVec3 getLocationBig() {
        return this.location;
    }

    public abstract HitResult.Type getType();

    public static BigHitResult miss() {
        return new BigHitResult.Miss();
    }

    public static BigHitResult block(BigVec3 location) {
        return new BigHitResult.Block(location);
    }

    public static BigHitResult entity(BigVec3 location) {
        return new BigHitResult.Entity(location);
    }

    public static class Miss extends BigHitResult {
        public Miss() {
            super(BigVec3.ZERO);
        }

        @Override
        public HitResult.Type getType() {
            return HitResult.Type.MISS;
        }
    }

    public static class Block extends BigHitResult {
        public Block(BigVec3 location) {
            super(location);
        }

        @Override
        public HitResult.Type getType() {
            return HitResult.Type.BLOCK;
        }
    }

    public static class Entity extends BigHitResult {
        public Entity(BigVec3 location) {
            super(location);
        }

        @Override
        public HitResult.Type getType() {
            return HitResult.Type.ENTITY;
        }
    }
}