package net.minecraft.advancements.criterion;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;
import net.minecraft.core.HolderGetter;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.TagKey;
import net.minecraft.util.ExtraCodecs;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.storage.loot.LootContext;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.level.storage.loot.predicates.LootItemCondition;
import net.minecraft.world.level.storage.loot.predicates.LootItemEntityPropertyCondition;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.scores.Team;
import org.jspecify.annotations.Nullable;

public record EntityPredicate(
   Optional<EntityTypePredicate> entityType,
   Optional<DistancePredicate> distanceToPlayer,
   Optional<MovementPredicate> movement,
   EntityPredicate.LocationWrapper location,
   Optional<MobEffectsPredicate> effects,
   Optional<NbtPredicate> nbt,
   Optional<EntityFlagsPredicate> flags,
   Optional<EntityEquipmentPredicate> equipment,
   Optional<EntitySubPredicate> subPredicate,
   Optional<Integer> periodicTick,
   Optional<EntityPredicate> vehicle,
   Optional<EntityPredicate> passenger,
   Optional<EntityPredicate> targetedEntity,
   Optional<String> team,
   Optional<SlotsPredicate> slots,
   DataComponentMatchers components
) {
   public static final Codec<EntityPredicate> CODEC = Codec.recursive(
      "EntityPredicate",
      subCodec -> RecordCodecBuilder.create(
         i -> i.group(
               EntityTypePredicate.CODEC.optionalFieldOf("type").forGetter(EntityPredicate::entityType),
               DistancePredicate.CODEC.optionalFieldOf("distance").forGetter(EntityPredicate::distanceToPlayer),
               MovementPredicate.CODEC.optionalFieldOf("movement").forGetter(EntityPredicate::movement),
               EntityPredicate.LocationWrapper.CODEC.forGetter(EntityPredicate::location),
               MobEffectsPredicate.CODEC.optionalFieldOf("effects").forGetter(EntityPredicate::effects),
               NbtPredicate.CODEC.optionalFieldOf("nbt").forGetter(EntityPredicate::nbt),
               EntityFlagsPredicate.CODEC.optionalFieldOf("flags").forGetter(EntityPredicate::flags),
               EntityEquipmentPredicate.CODEC.optionalFieldOf("equipment").forGetter(EntityPredicate::equipment),
               EntitySubPredicate.CODEC.optionalFieldOf("type_specific").forGetter(EntityPredicate::subPredicate),
               ExtraCodecs.POSITIVE_INT.optionalFieldOf("periodic_tick").forGetter(EntityPredicate::periodicTick),
               subCodec.optionalFieldOf("vehicle").forGetter(EntityPredicate::vehicle),
               subCodec.optionalFieldOf("passenger").forGetter(EntityPredicate::passenger),
               subCodec.optionalFieldOf("targeted_entity").forGetter(EntityPredicate::targetedEntity),
               Codec.STRING.optionalFieldOf("team").forGetter(EntityPredicate::team),
               SlotsPredicate.CODEC.optionalFieldOf("slots").forGetter(EntityPredicate::slots),
               DataComponentMatchers.CODEC.forGetter(EntityPredicate::components)
            )
            .apply(i, EntityPredicate::new)
      )
   );
   public static final Codec<ContextAwarePredicate> ADVANCEMENT_CODEC = Codec.withAlternative(ContextAwarePredicate.CODEC, CODEC, EntityPredicate::wrap);

   public static ContextAwarePredicate wrap(final EntityPredicate.Builder singlePredicate) {
      return wrap(singlePredicate.build());
   }

   public static Optional<ContextAwarePredicate> wrap(final Optional<EntityPredicate> singlePredicate) {
      return singlePredicate.map(EntityPredicate::wrap);
   }

   public static List<ContextAwarePredicate> wrap(final EntityPredicate.Builder... predicates) {
      return Stream.of(predicates).map(EntityPredicate::wrap).toList();
   }

   public static ContextAwarePredicate wrap(final EntityPredicate singlePredicate) {
      LootItemCondition asCondition = LootItemEntityPropertyCondition.hasProperties(LootContext.EntityTarget.THIS, singlePredicate).build();
      return new ContextAwarePredicate(List.of(asCondition));
   }

   public boolean matches(final ServerPlayer player, final @Nullable Entity entity) {
      return this.matches(player.level(), player.position(), entity);
   }

   public boolean matches(final ServerLevel level, final @Nullable Vec3 position, final @Nullable Entity entity) {
      if (entity == null) {
         return false;
      }

      if (this.entityType.isPresent() && !this.entityType.get().matches(entity.getType())) {
         return false;
      }

      if (position == null) {
         if (this.distanceToPlayer.isPresent()) {
            return false;
         }
      } else if (this.distanceToPlayer.isPresent()
         && !this.distanceToPlayer.get().matches(position.x, position.y, position.z, entity.getX(), entity.getY(), entity.getZ())) {
         return false;
      }

      if (this.movement.isPresent()) {
         Vec3 knownMovement = entity.getKnownMovement();
         Vec3 velocity = knownMovement.scale(20.0);
         if (!this.movement.get().matches(velocity.x, velocity.y, velocity.z, entity.fallDistance)) {
            return false;
         }
      }

      if (this.location.located.isPresent() && !this.location.located.get().matches(level, entity.getX(), entity.getY(), entity.getZ())) {
         return false;
      }

      if (this.location.steppingOn.isPresent()) {
         Vec3 onPos = Vec3.atCenterOf(entity.getOnPos());
         if (!entity.onGround() || !this.location.steppingOn.get().matches(level, onPos.x(), onPos.y(), onPos.z())) {
            return false;
         }
      }

      if (this.location.affectsMovement.isPresent()) {
         Vec3 onPos = Vec3.atCenterOf(entity.getBlockPosBelowThatAffectsMyMovement());
         if (!this.location.affectsMovement.get().matches(level, onPos.x(), onPos.y(), onPos.z())) {
            return false;
         }
      }

      if (this.effects.isPresent() && !this.effects.get().matches(entity)) {
         return false;
      }

      if (this.flags.isPresent() && !this.flags.get().matches(entity)) {
         return false;
      }

      if (this.equipment.isPresent() && !this.equipment.get().matches(entity)) {
         return false;
      }

      if (this.subPredicate.isPresent() && !this.subPredicate.get().matches(entity, level, position)) {
         return false;
      }

      if (this.vehicle.isPresent() && !this.vehicle.get().matches(level, position, entity.getVehicle())) {
         return false;
      }

      if (this.passenger.isPresent() && entity.getPassengers().stream().noneMatch(p -> this.passenger.get().matches(level, position, p))) {
         return false;
      }

      if (this.targetedEntity.isPresent() && !this.targetedEntity.get().matches(level, position, entity instanceof Mob ? ((Mob)entity).getTarget() : null)) {
         return false;
      }

      if (this.periodicTick.isPresent() && entity.tickCount % this.periodicTick.get() != 0) {
         return false;
      }

      if (this.team.isPresent()) {
         Team team = entity.getTeam();
         if (team == null || !this.team.get().equals(team.getName())) {
            return false;
         }
      }

      if (this.slots.isPresent() && !this.slots.get().matches(entity)) {
         return false;
      } else {
         return !this.components.test(entity) ? false : this.nbt.isEmpty() || this.nbt.get().matches(entity);
      }
   }

   public static LootContext createContext(final ServerPlayer player, final Entity entity) {
      LootParams lootParams = new LootParams.Builder(player.level())
         .withParameter(LootContextParams.THIS_ENTITY, entity)
         .withParameter(LootContextParams.ORIGIN, player.position())
         .create(LootContextParamSets.ADVANCEMENT_ENTITY);
      return new LootContext.Builder(lootParams).create(Optional.empty());
   }

   public static class Builder {
      private Optional<EntityTypePredicate> entityType = Optional.empty();
      private Optional<DistancePredicate> distanceToPlayer = Optional.empty();
      private Optional<MovementPredicate> movement = Optional.empty();
      private Optional<LocationPredicate> located = Optional.empty();
      private Optional<LocationPredicate> steppingOnLocation = Optional.empty();
      private Optional<LocationPredicate> movementAffectedBy = Optional.empty();
      private Optional<MobEffectsPredicate> effects = Optional.empty();
      private Optional<NbtPredicate> nbt = Optional.empty();
      private Optional<EntityFlagsPredicate> flags = Optional.empty();
      private Optional<EntityEquipmentPredicate> equipment = Optional.empty();
      private Optional<EntitySubPredicate> subPredicate = Optional.empty();
      private Optional<Integer> periodicTick = Optional.empty();
      private Optional<EntityPredicate> vehicle = Optional.empty();
      private Optional<EntityPredicate> passenger = Optional.empty();
      private Optional<EntityPredicate> targetedEntity = Optional.empty();
      private Optional<String> team = Optional.empty();
      private Optional<SlotsPredicate> slots = Optional.empty();
      private DataComponentMatchers components = DataComponentMatchers.ANY;

      public static EntityPredicate.Builder entity() {
         return new EntityPredicate.Builder();
      }

      public EntityPredicate.Builder of(final HolderGetter<EntityType<?>> lookup, final EntityType<?> entityType) {
         this.entityType = Optional.of(EntityTypePredicate.of(lookup, entityType));
         return this;
      }

      public EntityPredicate.Builder of(final HolderGetter<EntityType<?>> lookup, final TagKey<EntityType<?>> entityTypeTag) {
         this.entityType = Optional.of(EntityTypePredicate.of(lookup, entityTypeTag));
         return this;
      }

      public EntityPredicate.Builder entityType(final EntityTypePredicate entityType) {
         this.entityType = Optional.of(entityType);
         return this;
      }

      public EntityPredicate.Builder distance(final DistancePredicate distanceToPlayer) {
         this.distanceToPlayer = Optional.of(distanceToPlayer);
         return this;
      }

      public EntityPredicate.Builder moving(final MovementPredicate movement) {
         this.movement = Optional.of(movement);
         return this;
      }

      public EntityPredicate.Builder located(final LocationPredicate.Builder location) {
         this.located = Optional.of(location.build());
         return this;
      }

      public EntityPredicate.Builder steppingOn(final LocationPredicate.Builder location) {
         this.steppingOnLocation = Optional.of(location.build());
         return this;
      }

      public EntityPredicate.Builder movementAffectedBy(final LocationPredicate.Builder location) {
         this.movementAffectedBy = Optional.of(location.build());
         return this;
      }

      public EntityPredicate.Builder effects(final MobEffectsPredicate.Builder effects) {
         this.effects = effects.build();
         return this;
      }

      public EntityPredicate.Builder nbt(final NbtPredicate nbt) {
         this.nbt = Optional.of(nbt);
         return this;
      }

      public EntityPredicate.Builder flags(final EntityFlagsPredicate.Builder flags) {
         this.flags = Optional.of(flags.build());
         return this;
      }

      public EntityPredicate.Builder equipment(final EntityEquipmentPredicate.Builder equipment) {
         this.equipment = Optional.of(equipment.build());
         return this;
      }

      public EntityPredicate.Builder equipment(final EntityEquipmentPredicate equipment) {
         this.equipment = Optional.of(equipment);
         return this;
      }

      public EntityPredicate.Builder subPredicate(final EntitySubPredicate subPredicate) {
         this.subPredicate = Optional.of(subPredicate);
         return this;
      }

      public EntityPredicate.Builder periodicTick(final int period) {
         this.periodicTick = Optional.of(period);
         return this;
      }

      public EntityPredicate.Builder vehicle(final EntityPredicate.Builder vehicle) {
         this.vehicle = Optional.of(vehicle.build());
         return this;
      }

      public EntityPredicate.Builder passenger(final EntityPredicate.Builder passenger) {
         this.passenger = Optional.of(passenger.build());
         return this;
      }

      public EntityPredicate.Builder targetedEntity(final EntityPredicate.Builder targetedEntity) {
         this.targetedEntity = Optional.of(targetedEntity.build());
         return this;
      }

      public EntityPredicate.Builder team(final String team) {
         this.team = Optional.of(team);
         return this;
      }

      public EntityPredicate.Builder slots(final SlotsPredicate slots) {
         this.slots = Optional.of(slots);
         return this;
      }

      public EntityPredicate.Builder components(final DataComponentMatchers components) {
         this.components = components;
         return this;
      }

      public EntityPredicate build() {
         return new EntityPredicate(
            this.entityType,
            this.distanceToPlayer,
            this.movement,
            new EntityPredicate.LocationWrapper(this.located, this.steppingOnLocation, this.movementAffectedBy),
            this.effects,
            this.nbt,
            this.flags,
            this.equipment,
            this.subPredicate,
            this.periodicTick,
            this.vehicle,
            this.passenger,
            this.targetedEntity,
            this.team,
            this.slots,
            this.components
         );
      }
   }

   public record LocationWrapper(Optional<LocationPredicate> located, Optional<LocationPredicate> steppingOn, Optional<LocationPredicate> affectsMovement) {
      public static final MapCodec<EntityPredicate.LocationWrapper> CODEC = RecordCodecBuilder.mapCodec(
         i -> i.group(
               LocationPredicate.CODEC.optionalFieldOf("location").forGetter(EntityPredicate.LocationWrapper::located),
               LocationPredicate.CODEC.optionalFieldOf("stepping_on").forGetter(EntityPredicate.LocationWrapper::steppingOn),
               LocationPredicate.CODEC.optionalFieldOf("movement_affected_by").forGetter(EntityPredicate.LocationWrapper::affectsMovement)
            )
            .apply(i, EntityPredicate.LocationWrapper::new)
      );
   }
}
