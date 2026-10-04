package net.minecraft.client;

import java.util.Arrays;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.Mth;
import net.minecraft.world.attribute.EnvironmentAttributeProbe;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.vehicle.minecart.Minecart;
import net.minecraft.world.entity.vehicle.minecart.NewMinecartBehavior;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.material.FogType;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.waypoints.TrackedWaypoint;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.joml.Vector3fc;

public class Camera implements TrackedWaypoint.Camera {
   private static final float DEFAULT_CAMERA_DISTANCE = 4.0F;
   private static final Vector3f FORWARDS = new Vector3f(0.0F, 0.0F, -1.0F);
   private static final Vector3f UP = new Vector3f(0.0F, 1.0F, 0.0F);
   private static final Vector3f LEFT = new Vector3f(-1.0F, 0.0F, 0.0F);
   private boolean initialized;
   private Level level;
   private Entity entity;
   private Vec3 position = Vec3.ZERO;
   private final BlockPos.MutableBlockPos blockPosition = new BlockPos.MutableBlockPos();
   private final Vector3f forwards = new Vector3f(FORWARDS);
   private final Vector3f up = new Vector3f(UP);
   private final Vector3f left = new Vector3f(LEFT);
   private float xRot;
   private float yRot;
   private final Quaternionf rotation = new Quaternionf();
   private boolean detached;
   private float eyeHeight;
   private float eyeHeightOld;
   private float partialTickTime;
   private final EnvironmentAttributeProbe attributeProbe = new EnvironmentAttributeProbe();

   public void setup(final Level level, final Entity entity, final boolean detached, final boolean mirror, final float a) {
      this.initialized = true;
      this.level = level;
      this.entity = entity;
      this.detached = detached;
      this.partialTickTime = a;
      if (entity.isPassenger()
         && entity.getVehicle() instanceof Minecart minecart
         && minecart.getBehavior() instanceof NewMinecartBehavior behavior
         && behavior.cartHasPosRotLerp()) {
         Vec3 positionOffset = minecart.getPassengerRidingPosition(entity)
            .subtract(minecart.position())
            .subtract(entity.getVehicleAttachmentPoint(minecart))
            .add(new Vec3(0.0, Mth.lerp(a, this.eyeHeightOld, this.eyeHeight), 0.0));
         this.setRotation(entity.getViewYRot(a), entity.getViewXRot(a));
         this.setPosition(behavior.getCartLerpPosition(a).add(positionOffset));
      } else {
         this.setRotation(entity.getViewYRot(a), entity.getViewXRot(a));
         this.setPosition(
            Mth.lerp(a, entity.xo, entity.getX()),
            Mth.lerp(a, entity.yo, entity.getY()) + Mth.lerp(a, this.eyeHeightOld, this.eyeHeight),
            Mth.lerp(a, entity.zo, entity.getZ())
         );
      }

      if (detached) {
         if (mirror) {
            this.setRotation(this.yRot + 180.0F, -this.xRot);
         }

         float cameraDistance = 4.0F;
         float cameraScale = 1.0F;
         if (entity instanceof LivingEntity living) {
            cameraScale = living.getScale();
            cameraDistance = (float)living.getAttributeValue(Attributes.CAMERA_DISTANCE);
         }

         float mountScale = cameraScale;
         float mountDistance = cameraDistance;
         if (entity.isPassenger() && entity.getVehicle() instanceof LivingEntity mount) {
            mountScale = mount.getScale();
            mountDistance = (float)mount.getAttributeValue(Attributes.CAMERA_DISTANCE);
         }

         this.move(-this.getMaxZoom(Math.max(cameraScale * cameraDistance, mountScale * mountDistance)), 0.0F, 0.0F);
      } else if (entity instanceof LivingEntity && ((LivingEntity)entity).isSleeping()) {
         Direction bedOrientation = ((LivingEntity)entity).getBedOrientation();
         this.setRotation(bedOrientation != null ? bedOrientation.toYRot() - 180.0F : 0.0F, 0.0F);
         this.move(0.0F, 0.3F, 0.0F);
      }
   }

   public void tick() {
      if (this.entity != null) {
         this.eyeHeightOld = this.eyeHeight;
         this.eyeHeight = this.eyeHeight + (this.entity.getEyeHeight() - this.eyeHeight) * 0.5F;
         this.attributeProbe.tick(this.level, this.position);
      }
   }

   private float getMaxZoom(float cameraDist) {
      float jitterScale = 0.1F;

      for (int i = 0; i < 8; i++) {
         float offsetX = (i & 1) * 2 - 1;
         float offsetY = (i >> 1 & 1) * 2 - 1;
         float offsetZ = (i >> 2 & 1) * 2 - 1;
         Vec3 from = this.position.add(offsetX * 0.1F, offsetY * 0.1F, offsetZ * 0.1F);
         Vec3 to = from.add(new Vec3(this.forwards).scale(-cameraDist));
         HitResult hitResult = this.level.clip(new ClipContext(from, to, ClipContext.Block.VISUAL, ClipContext.Fluid.NONE, this.entity));
         if (hitResult.getType() != HitResult.Type.MISS) {
            float distSq = (float)hitResult.getLocation().distanceToSqr(this.position);
            if (distSq < Mth.square(cameraDist)) {
               cameraDist = Mth.sqrt(distSq);
            }
         }
      }

      return cameraDist;
   }

   protected void move(final float forwards, final float up, final float right) {
      Vector3f offset = new Vector3f(right, up, -forwards).rotate(this.rotation);
      this.setPosition(new Vec3(this.position.x + offset.x, this.position.y + offset.y, this.position.z + offset.z));
   }

   protected void setRotation(final float yRot, final float xRot) {
      this.xRot = xRot;
      this.yRot = yRot;
      this.rotation.rotationYXZ((float) Math.PI - yRot * (float) (Math.PI / 180.0), -xRot * (float) (Math.PI / 180.0), 0.0F);
      FORWARDS.rotate(this.rotation, this.forwards);
      UP.rotate(this.rotation, this.up);
      LEFT.rotate(this.rotation, this.left);
   }

   protected void setPosition(final double x, final double y, final double z) {
      this.setPosition(new Vec3(x, y, z));
   }

   protected void setPosition(final Vec3 position) {
      this.position = position;
      this.blockPosition.set(position.x, position.y, position.z);
   }

   @Override
   public Vec3 position() {
      return this.position;
   }

   public BlockPos blockPosition() {
      return this.blockPosition;
   }

   public float xRot() {
      return this.xRot;
   }

   public float yRot() {
      return this.yRot;
   }

   @Override
   public float yaw() {
      return Mth.wrapDegrees(this.yRot());
   }

   public Quaternionf rotation() {
      return this.rotation;
   }

   public Entity entity() {
      return this.entity;
   }

   public boolean isInitialized() {
      return this.initialized;
   }

   public boolean isDetached() {
      return this.detached;
   }

   public EnvironmentAttributeProbe attributeProbe() {
      return this.attributeProbe;
   }

   public Camera.NearPlane getNearPlane() {
      Minecraft minecraft = Minecraft.getInstance();
      double aspectRatio = (double)minecraft.getWindow().getWidth() / minecraft.getWindow().getHeight();
      double planeHeight = Math.tan(minecraft.options.fov().get().intValue() * (float) (Math.PI / 180.0) / 2.0) * 0.05F;
      double planeWidth = planeHeight * aspectRatio;
      Vec3 forwardsVec3 = new Vec3(this.forwards).scale(0.05F);
      Vec3 leftVec3 = new Vec3(this.left).scale(planeWidth);
      Vec3 upVec3 = new Vec3(this.up).scale(planeHeight);
      return new Camera.NearPlane(forwardsVec3, leftVec3, upVec3);
   }

   public FogType getFluidInCamera() {
      if (!this.initialized) {
         return FogType.NONE;
      }

      FluidState fluidState1 = this.level.getFluidState(this.blockPosition);
      if (fluidState1.is(FluidTags.WATER) && this.position.y < this.blockPosition.getY() + fluidState1.getHeight(this.level, this.blockPosition)) {
         return FogType.WATER;
      }

      Camera.NearPlane plane = this.getNearPlane();

      for (Vec3 point : Arrays.asList(plane.forward, plane.getTopLeft(), plane.getTopRight(), plane.getBottomLeft(), plane.getBottomRight())) {
         Vec3 offsetPos = this.position.add(point);
         BlockPos checkPos = BlockPos.containing(offsetPos);
         FluidState fluidState = this.level.getFluidState(checkPos);
         if (fluidState.is(FluidTags.LAVA)) {
            if (offsetPos.y <= fluidState.getHeight(this.level, checkPos) + checkPos.getY()) {
               return FogType.LAVA;
            }
         } else {
            BlockState state = this.level.getBlockState(checkPos);
            if (state.is(Blocks.POWDER_SNOW)) {
               return FogType.POWDER_SNOW;
            }
         }
      }

      return FogType.NONE;
   }

   public Vector3fc forwardVector() {
      return this.forwards;
   }

   public Vector3fc upVector() {
      return this.up;
   }

   public Vector3fc leftVector() {
      return this.left;
   }

   public void reset() {
      this.level = null;
      this.entity = null;
      this.attributeProbe.reset();
      this.initialized = false;
   }

   public float getPartialTickTime() {
      return this.partialTickTime;
   }

   public static class NearPlane {
      private final Vec3 forward;
      private final Vec3 left;
      private final Vec3 up;

      private NearPlane(final Vec3 forward, final Vec3 left, final Vec3 up) {
         this.forward = forward;
         this.left = left;
         this.up = up;
      }

      public Vec3 getTopLeft() {
         return this.forward.add(this.up).add(this.left);
      }

      public Vec3 getTopRight() {
         return this.forward.add(this.up).subtract(this.left);
      }

      public Vec3 getBottomLeft() {
         return this.forward.subtract(this.up).add(this.left);
      }

      public Vec3 getBottomRight() {
         return this.forward.subtract(this.up).subtract(this.left);
      }

      public Vec3 getPointOnPlane(final float x, final float y) {
         return this.forward.add(this.up.scale(y)).subtract(this.left.scale(x));
      }
   }
}
