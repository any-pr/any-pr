package net.minecraft.world.entity.ai.goal;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.vehicle.boat.AbstractBoat;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

public class FollowBoatGoal extends Goal {
   private int timeToRecalcPath;
   private final PathfinderMob mob;
   private @Nullable Player following;
   private BoatGoals currentGoal;

   public FollowBoatGoal(final PathfinderMob mob) {
      this.mob = mob;
   }

   @Override
   public boolean canUse() {
      if (this.following != null && this.following.hasMovedHorizontallyRecently()) {
         return true;
      }

      for (AbstractBoat boat : this.mob.level().getEntitiesOfClass(AbstractBoat.class, this.mob.getBoundingBox().inflate(5.0))) {
         if (boat.getControllingPassenger() instanceof Player controllingPlayer && controllingPlayer.hasMovedHorizontallyRecently()) {
            return true;
         }
      }

      return false;
   }

   @Override
   public boolean isInterruptable() {
      return true;
   }

   @Override
   public boolean canContinueToUse() {
      return this.following != null && this.following.isPassenger() && this.following.hasMovedHorizontallyRecently();
   }

   @Override
   public void start() {
      for (AbstractBoat boat : this.mob.level().getEntitiesOfClass(AbstractBoat.class, this.mob.getBoundingBox().inflate(5.0))) {
         if (boat.getControllingPassenger() instanceof Player player) {
            this.following = player;
            break;
         }
      }

      this.timeToRecalcPath = 0;
      this.currentGoal = BoatGoals.GO_TO_BOAT;
   }

   @Override
   public void stop() {
      this.following = null;
   }

   @Override
   public void tick() {
      float speed = this.currentGoal == BoatGoals.GO_IN_BOAT_DIRECTION ? 0.01F : 0.015F;
      this.mob.moveRelative(speed, new Vec3(this.mob.xxa, this.mob.yya, this.mob.zza));
      this.mob.move(MoverType.SELF, this.mob.getDeltaMovement());
      if (--this.timeToRecalcPath <= 0) {
         this.timeToRecalcPath = this.adjustedTickDelay(10);
         if (this.currentGoal == BoatGoals.GO_TO_BOAT) {
            BlockPos behindBoatPos = this.following.blockPosition().relative(this.following.getDirection().getOpposite());
            behindBoatPos = behindBoatPos.offset(0, -1, 0);
            this.mob.getNavigation().moveTo(behindBoatPos.getX(), behindBoatPos.getY(), behindBoatPos.getZ(), 1.0);
            if (this.mob.distanceTo(this.following) < 4.0F) {
               this.timeToRecalcPath = 0;
               this.currentGoal = BoatGoals.GO_IN_BOAT_DIRECTION;
            }
         } else if (this.currentGoal == BoatGoals.GO_IN_BOAT_DIRECTION) {
            Direction direction = this.following.getMotionDirection();
            BlockPos goTo = this.following.blockPosition().relative(direction, 10);
            this.mob.getNavigation().moveTo(goTo.getX(), goTo.getY() - 1, goTo.getZ(), 1.0);
            if (this.mob.distanceTo(this.following) > 12.0F) {
               this.timeToRecalcPath = 0;
               this.currentGoal = BoatGoals.GO_TO_BOAT;
            }
         }
      }
   }
}
