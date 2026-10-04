package net.minecraft.world.entity.ai;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import com.google.common.collect.UnmodifiableIterator;
import com.google.common.collect.ImmutableList.Builder;
import com.mojang.datafixers.util.Pair;
import com.mojang.logging.LogUtils;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.Dynamic;
import com.mojang.serialization.DynamicOps;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.MapLike;
import com.mojang.serialization.RecordBuilder;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.Map.Entry;
import java.util.function.Supplier;
import java.util.stream.Stream;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.VisibleForDebug;
import net.minecraft.world.attribute.EnvironmentAttribute;
import net.minecraft.world.attribute.EnvironmentAttributeSystem;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.behavior.Behavior;
import net.minecraft.world.entity.ai.behavior.BehaviorControl;
import net.minecraft.world.entity.ai.memory.ExpirableValue;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.memory.MemoryStatus;
import net.minecraft.world.entity.ai.sensing.Sensor;
import net.minecraft.world.entity.ai.sensing.SensorType;
import net.minecraft.world.entity.schedule.Activity;
import net.minecraft.world.phys.Vec3;
import org.apache.commons.lang3.mutable.MutableObject;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;

public class Brain<E extends LivingEntity> {
   private static final Logger LOGGER = LogUtils.getLogger();
   private final Supplier<Codec<Brain<E>>> codec;
   private static final int SCHEDULE_UPDATE_DELAY = 20;
   private final Map<MemoryModuleType<?>, Optional<? extends ExpirableValue<?>>> memories = Maps.newHashMap();
   private final Map<SensorType<? extends Sensor<? super E>>, Sensor<? super E>> sensors = Maps.newLinkedHashMap();
   private final Map<Integer, Map<Activity, Set<BehaviorControl<? super E>>>> availableBehaviorsByPriority = Maps.newTreeMap();
   private @Nullable EnvironmentAttribute<Activity> schedule;
   private final Map<Activity, Set<Pair<MemoryModuleType<?>, MemoryStatus>>> activityRequirements = Maps.newHashMap();
   private final Map<Activity, Set<MemoryModuleType<?>>> activityMemoriesToEraseWhenStopped = Maps.newHashMap();
   private Set<Activity> coreActivities = Sets.newHashSet();
   private final Set<Activity> activeActivities = Sets.newHashSet();
   private Activity defaultActivity = Activity.IDLE;
   private long lastScheduleUpdate = -9999L;

   public static <E extends LivingEntity> Brain.Provider<E> provider(
      final Collection<? extends MemoryModuleType<?>> memoryTypes, final Collection<? extends SensorType<? extends Sensor<? super E>>> sensorTypes
   ) {
      return new Brain.Provider<>(memoryTypes, sensorTypes);
   }

   public static <E extends LivingEntity> Codec<Brain<E>> codec(
      final Collection<? extends MemoryModuleType<?>> memoryTypes, final Collection<? extends SensorType<? extends Sensor<? super E>>> sensorTypes
   ) {
      final MutableObject<Codec<Brain<E>>> codecReference = new MutableObject();
      codecReference.setValue(
         (new MapCodec<Brain<E>>() {
               public <T> Stream<T> keys(final DynamicOps<T> ops) {
                  return memoryTypes.stream()
                     .flatMap(t -> t.getCodec().map(c -> BuiltInRegistries.MEMORY_MODULE_TYPE.getKey((MemoryModuleType<?>)t)).stream())
                     .map(l -> (T)ops.createString(l.toString()));
               }

               public <T> DataResult<Brain<E>> decode(final DynamicOps<T> ops, final MapLike<T> input) {
                  MutableObject<DataResult<Builder<Brain.MemoryValue<?>>>> result = new MutableObject(DataResult.success(ImmutableList.builder()));
                  input.entries().forEach(pair -> {
                     DataResult<MemoryModuleType<?>> typeResult = BuiltInRegistries.MEMORY_MODULE_TYPE.byNameCodec().parse(ops, pair.getFirst());
                     DataResult<? extends Brain.MemoryValue<?>> entryResult = typeResult.flatMap(type -> this.captureRead(type, ops, (T)pair.getSecond()));
                     result.setValue(((DataResult)result.get()).apply2(Builder::add, entryResult));
                  });
                  ImmutableList<Brain.MemoryValue<?>> memories = ((DataResult)result.get())
                     .resultOrPartial(Brain.LOGGER::error)
                     .<ImmutableList<Brain.MemoryValue<?>>>map(Builder::build)
                     .orElseGet(ImmutableList::of);
                  return DataResult.success(new Brain<>(memoryTypes, sensorTypes, memories, codecReference));
               }

               private <T, U> DataResult<Brain.MemoryValue<U>> captureRead(final MemoryModuleType<U> type, final DynamicOps<T> ops, final T input) {
                  return type.getCodec()
                     .<DataResult>map(DataResult::success)
                     .orElseGet(() -> DataResult.error(() -> "No codec for memory: " + type))
                     .flatMap(c -> c.parse(ops, input))
                     .map(v -> new Brain.MemoryValue<>(type, Optional.of(v)));
               }

               public <T> RecordBuilder<T> encode(final Brain<E> input, final DynamicOps<T> ops, final RecordBuilder<T> prefix) {
                  input.memories().forEach(m -> m.serialize(ops, prefix));
                  return prefix;
               }
            })
            .fieldOf("memories")
            .codec()
      );
      return (Codec<Brain<E>>)codecReference.get();
   }

   public Brain(
      final Collection<? extends MemoryModuleType<?>> memoryTypes,
      final Collection<? extends SensorType<? extends Sensor<? super E>>> sensorTypes,
      final ImmutableList<Brain.MemoryValue<?>> memories,
      final Supplier<Codec<Brain<E>>> codec
   ) {
      this.codec = codec;

      for (MemoryModuleType<?> memoryType : memoryTypes) {
         this.memories.put(memoryType, Optional.empty());
      }

      for (SensorType<? extends Sensor<? super E>> sensorType : sensorTypes) {
         this.sensors.put(sensorType, (Sensor<? super E>)sensorType.create());
      }

      for (Sensor<? super E> sensor : this.sensors.values()) {
         for (MemoryModuleType<?> type : sensor.requires()) {
            this.memories.put(type, Optional.empty());
         }
      }

      UnmodifiableIterator var11 = memories.iterator();

      while (var11.hasNext()) {
         Brain.MemoryValue<?> memory = (Brain.MemoryValue<?>)var11.next();
         memory.setMemoryInternal(this);
      }
   }

   public <T> DataResult<T> serializeStart(final DynamicOps<T> ops) {
      return this.codec.get().encodeStart(ops, this);
   }

   private Stream<Brain.MemoryValue<?>> memories() {
      return this.memories.entrySet().stream().map(e -> Brain.MemoryValue.createUnchecked(e.getKey(), e.getValue()));
   }

   public boolean hasMemoryValue(final MemoryModuleType<?> type) {
      return this.checkMemory(type, MemoryStatus.VALUE_PRESENT);
   }

   public void clearMemories() {
      this.memories.keySet().forEach(key -> this.memories.put((MemoryModuleType<?>)key, Optional.empty()));
   }

   public <U> void eraseMemory(final MemoryModuleType<U> type) {
      this.setMemory(type, Optional.empty());
   }

   public <U> void setMemory(final MemoryModuleType<U> type, final @Nullable U value) {
      this.setMemory(type, Optional.ofNullable(value));
   }

   public <U> void setMemoryWithExpiry(final MemoryModuleType<U> type, final U value, final long timeToLive) {
      this.setMemoryInternal(type, Optional.of(ExpirableValue.of(value, timeToLive)));
   }

   public <U> void setMemory(final MemoryModuleType<U> type, final Optional<? extends U> optionalValue) {
      this.setMemoryInternal(type, optionalValue.map(ExpirableValue::of));
   }

   private <U> void setMemoryInternal(final MemoryModuleType<U> type, final Optional<? extends ExpirableValue<?>> optionalExpirableValue) {
      if (this.memories.containsKey(type)) {
         if (optionalExpirableValue.isPresent() && this.isEmptyCollection(optionalExpirableValue.get().getValue())) {
            this.eraseMemory(type);
         } else {
            this.memories.put(type, optionalExpirableValue);
         }
      }
   }

   public <U> Optional<U> getMemory(final MemoryModuleType<U> type) {
      Optional<? extends ExpirableValue<?>> expirableValue = this.memories.get(type);
      if (expirableValue == null) {
         throw new IllegalStateException("Unregistered memory fetched: " + type);
      } else {
         return expirableValue.map(ExpirableValue::getValue);
      }
   }

   public <U> @Nullable Optional<U> getMemoryInternal(final MemoryModuleType<U> type) {
      Optional<? extends ExpirableValue<?>> expirableValue = this.memories.get(type);
      return expirableValue == null ? null : expirableValue.map(ExpirableValue::getValue);
   }

   public <U> long getTimeUntilExpiry(final MemoryModuleType<U> type) {
      Optional<? extends ExpirableValue<?>> memory = this.memories.get(type);
      return memory.map(ExpirableValue::getTimeToLive).orElse(0L);
   }

   @Deprecated
   @VisibleForDebug
   public Map<MemoryModuleType<?>, Optional<? extends ExpirableValue<?>>> getMemories() {
      return this.memories;
   }

   public <U> boolean isMemoryValue(final MemoryModuleType<U> memoryType, final U value) {
      return !this.hasMemoryValue(memoryType) ? false : this.getMemory(memoryType).filter(memory -> memory.equals(value)).isPresent();
   }

   public boolean checkMemory(final MemoryModuleType<?> type, final MemoryStatus status) {
      Optional<? extends ExpirableValue<?>> optionalExpirableValue = this.memories.get(type);
      return optionalExpirableValue == null
         ? false
         : status == MemoryStatus.REGISTERED
            || status == MemoryStatus.VALUE_PRESENT && optionalExpirableValue.isPresent()
            || status == MemoryStatus.VALUE_ABSENT && optionalExpirableValue.isEmpty();
   }

   public void setSchedule(final EnvironmentAttribute<Activity> schedule) {
      this.schedule = schedule;
   }

   public void setCoreActivities(final Set<Activity> activities) {
      this.coreActivities = activities;
   }

   @Deprecated
   @VisibleForDebug
   public Set<Activity> getActiveActivities() {
      return this.activeActivities;
   }

   @Deprecated
   @VisibleForDebug
   public List<BehaviorControl<? super E>> getRunningBehaviors() {
      List<BehaviorControl<? super E>> runningBehaviours = new ObjectArrayList();

      for (Map<Activity, Set<BehaviorControl<? super E>>> behavioursByActivities : this.availableBehaviorsByPriority.values()) {
         for (Set<BehaviorControl<? super E>> behaviors : behavioursByActivities.values()) {
            for (BehaviorControl<? super E> behavior : behaviors) {
               if (behavior.getStatus() == Behavior.Status.RUNNING) {
                  runningBehaviours.add(behavior);
               }
            }
         }
      }

      return runningBehaviours;
   }

   public void useDefaultActivity() {
      this.setActiveActivity(this.defaultActivity);
   }

   public Optional<Activity> getActiveNonCoreActivity() {
      for (Activity activity : this.activeActivities) {
         if (!this.coreActivities.contains(activity)) {
            return Optional.of(activity);
         }
      }

      return Optional.empty();
   }

   public void setActiveActivityIfPossible(final Activity activity) {
      if (this.activityRequirementsAreMet(activity)) {
         this.setActiveActivity(activity);
      } else {
         this.useDefaultActivity();
      }
   }

   private void setActiveActivity(final Activity activity) {
      if (!this.isActive(activity)) {
         this.eraseMemoriesForOtherActivitesThan(activity);
         this.activeActivities.clear();
         this.activeActivities.addAll(this.coreActivities);
         this.activeActivities.add(activity);
      }
   }

   private void eraseMemoriesForOtherActivitesThan(final Activity activity) {
      for (Activity oldActivity : this.activeActivities) {
         if (oldActivity != activity) {
            Set<MemoryModuleType<?>> memoryModuleTypes = this.activityMemoriesToEraseWhenStopped.get(oldActivity);
            if (memoryModuleTypes != null) {
               for (MemoryModuleType<?> memoryModuleType : memoryModuleTypes) {
                  this.eraseMemory(memoryModuleType);
               }
            }
         }
      }
   }

   public void updateActivityFromSchedule(final EnvironmentAttributeSystem environmentAttributes, final long gameTime, final Vec3 pos) {
      if (gameTime - this.lastScheduleUpdate > 20L) {
         this.lastScheduleUpdate = gameTime;
         Activity scheduledActivity = this.schedule != null ? environmentAttributes.getValue(this.schedule, pos) : Activity.IDLE;
         if (!this.activeActivities.contains(scheduledActivity)) {
            this.setActiveActivityIfPossible(scheduledActivity);
         }
      }
   }

   public void setActiveActivityToFirstValid(final List<Activity> activities) {
      for (Activity activity : activities) {
         if (this.activityRequirementsAreMet(activity)) {
            this.setActiveActivity(activity);
            break;
         }
      }
   }

   public void setDefaultActivity(final Activity activity) {
      this.defaultActivity = activity;
   }

   public void addActivity(final Activity activity, final int priorityOfFirstBehavior, final ImmutableList<? extends BehaviorControl<? super E>> behaviorList) {
      this.addActivity(activity, this.createPriorityPairs(priorityOfFirstBehavior, behaviorList));
   }

   public void addActivityAndRemoveMemoryWhenStopped(
      final Activity activity,
      final int priorityOfFirstBehavior,
      final ImmutableList<? extends BehaviorControl<? super E>> behaviorList,
      final MemoryModuleType<?> memoryThatMustHaveValueAndWillBeErasedAfter
   ) {
      Set<Pair<MemoryModuleType<?>, MemoryStatus>> conditions = ImmutableSet.of(
         Pair.of(memoryThatMustHaveValueAndWillBeErasedAfter, MemoryStatus.VALUE_PRESENT)
      );
      Set<MemoryModuleType<?>> memoriesToEraseWhenStopped = ImmutableSet.of(memoryThatMustHaveValueAndWillBeErasedAfter);
      this.addActivityAndRemoveMemoriesWhenStopped(
         activity, this.createPriorityPairs(priorityOfFirstBehavior, behaviorList), conditions, memoriesToEraseWhenStopped
      );
   }

   public void addActivity(final Activity activity, final ImmutableList<? extends Pair<Integer, ? extends BehaviorControl<? super E>>> behaviorPriorityPairs) {
      this.addActivityAndRemoveMemoriesWhenStopped(activity, behaviorPriorityPairs, ImmutableSet.of(), Sets.newHashSet());
   }

   public void addActivityWithConditions(
      final Activity activity,
      final int priorityOfFirstBehavior,
      final ImmutableList<? extends BehaviorControl<? super E>> behaviorList,
      final Set<Pair<MemoryModuleType<?>, MemoryStatus>> conditions
   ) {
      this.addActivityWithConditions(activity, this.createPriorityPairs(priorityOfFirstBehavior, behaviorList), conditions);
   }

   public void addActivityWithConditions(
      final Activity activity,
      final ImmutableList<? extends Pair<Integer, ? extends BehaviorControl<? super E>>> behaviorPriorityPairs,
      final Set<Pair<MemoryModuleType<?>, MemoryStatus>> conditions
   ) {
      this.addActivityAndRemoveMemoriesWhenStopped(activity, behaviorPriorityPairs, conditions, Sets.newHashSet());
   }

   public void addActivityAndRemoveMemoriesWhenStopped(
      final Activity activity,
      final ImmutableList<? extends Pair<Integer, ? extends BehaviorControl<? super E>>> behaviorPriorityPairs,
      final Set<Pair<MemoryModuleType<?>, MemoryStatus>> conditions,
      final Set<MemoryModuleType<?>> memoriesToEraseWhenStopped
   ) {
      this.activityRequirements.put(activity, conditions);
      if (!memoriesToEraseWhenStopped.isEmpty()) {
         this.activityMemoriesToEraseWhenStopped.put(activity, memoriesToEraseWhenStopped);
      }

      UnmodifiableIterator var5 = behaviorPriorityPairs.iterator();

      while (var5.hasNext()) {
         Pair<Integer, ? extends BehaviorControl<? super E>> pair = (Pair<Integer, ? extends BehaviorControl<? super E>>)var5.next();
         this.availableBehaviorsByPriority
            .computeIfAbsent((Integer)pair.getFirst(), key -> Maps.newHashMap())
            .computeIfAbsent(activity, key -> Sets.newLinkedHashSet())
            .add((BehaviorControl<? super E>)pair.getSecond());
      }
   }

   @VisibleForTesting
   public void removeAllBehaviors() {
      this.availableBehaviorsByPriority.clear();
   }

   public boolean isActive(final Activity activity) {
      return this.activeActivities.contains(activity);
   }

   public Brain<E> copyWithoutBehaviors() {
      Brain<E> brain = new Brain<>(this.memories.keySet(), this.sensors.keySet(), ImmutableList.of(), this.codec);

      for (Entry<MemoryModuleType<?>, Optional<? extends ExpirableValue<?>>> memoryEntry : this.memories.entrySet()) {
         MemoryModuleType<?> memoryModuleType = memoryEntry.getKey();
         if (memoryEntry.getValue().isPresent()) {
            brain.memories.put(memoryModuleType, memoryEntry.getValue());
         }
      }

      return brain;
   }

   public void tick(final ServerLevel level, final E body) {
      this.forgetOutdatedMemories();
      this.tickSensors(level, body);
      this.startEachNonRunningBehavior(level, body);
      this.tickEachRunningBehavior(level, body);
   }

   private void tickSensors(final ServerLevel level, final E body) {
      for (Sensor<? super E> sensor : this.sensors.values()) {
         sensor.tick(level, body);
      }
   }

   private void forgetOutdatedMemories() {
      for (Entry<MemoryModuleType<?>, Optional<? extends ExpirableValue<?>>> entry : this.memories.entrySet()) {
         if (entry.getValue().isPresent()) {
            ExpirableValue<?> memory = (ExpirableValue<?>)entry.getValue().get();
            if (memory.hasExpired()) {
               this.eraseMemory(entry.getKey());
            }

            memory.tick();
         }
      }
   }

   public void stopAll(final ServerLevel level, final E body) {
      long timestamp = body.level().getGameTime();

      for (BehaviorControl<? super E> behavior : this.getRunningBehaviors()) {
         behavior.doStop(level, body, timestamp);
      }
   }

   private void startEachNonRunningBehavior(final ServerLevel level, final E body) {
      long time = level.getGameTime();

      for (Map<Activity, Set<BehaviorControl<? super E>>> behavioursByActivities : this.availableBehaviorsByPriority.values()) {
         for (Entry<Activity, Set<BehaviorControl<? super E>>> behavioursForActivity : behavioursByActivities.entrySet()) {
            Activity activity = behavioursForActivity.getKey();
            if (this.activeActivities.contains(activity)) {
               for (BehaviorControl<? super E> behavior : behavioursForActivity.getValue()) {
                  if (behavior.getStatus() == Behavior.Status.STOPPED) {
                     behavior.tryStart(level, body, time);
                  }
               }
            }
         }
      }
   }

   private void tickEachRunningBehavior(final ServerLevel level, final E body) {
      long timestamp = level.getGameTime();

      for (BehaviorControl<? super E> behavior : this.getRunningBehaviors()) {
         behavior.tickOrStop(level, body, timestamp);
      }
   }

   private boolean activityRequirementsAreMet(final Activity activity) {
      if (!this.activityRequirements.containsKey(activity)) {
         return false;
      }

      for (Pair<MemoryModuleType<?>, MemoryStatus> memoryRequirement : this.activityRequirements.get(activity)) {
         MemoryModuleType<?> memoryType = (MemoryModuleType<?>)memoryRequirement.getFirst();
         MemoryStatus memoryStatus = (MemoryStatus)memoryRequirement.getSecond();
         if (!this.checkMemory(memoryType, memoryStatus)) {
            return false;
         }
      }

      return true;
   }

   private boolean isEmptyCollection(final Object object) {
      return object instanceof Collection && ((Collection)object).isEmpty();
   }

   ImmutableList<? extends Pair<Integer, ? extends BehaviorControl<? super E>>> createPriorityPairs(
      final int priorityOfFirstBehavior, final ImmutableList<? extends BehaviorControl<? super E>> behaviorList
   ) {
      int nextPrio = priorityOfFirstBehavior;
      Builder<Pair<Integer, ? extends BehaviorControl<? super E>>> listBuilder = ImmutableList.builder();
      UnmodifiableIterator var5 = behaviorList.iterator();

      while (var5.hasNext()) {
         BehaviorControl<? super E> behavior = (BehaviorControl<? super E>)var5.next();
         listBuilder.add(Pair.of(nextPrio++, behavior));
      }

      return listBuilder.build();
   }

   public boolean isBrainDead() {
      return this.memories.isEmpty() && this.sensors.isEmpty() && this.availableBehaviorsByPriority.isEmpty();
   }

   private static final class MemoryValue<U> {
      private final MemoryModuleType<U> type;
      private final Optional<? extends ExpirableValue<U>> value;

      private static <U> Brain.MemoryValue<U> createUnchecked(final MemoryModuleType<U> type, final Optional<? extends ExpirableValue<?>> value) {
         return new Brain.MemoryValue<>(type, (Optional<? extends ExpirableValue<U>>)value);
      }

      private MemoryValue(final MemoryModuleType<U> type, final Optional<? extends ExpirableValue<U>> value) {
         this.type = type;
         this.value = value;
      }

      private void setMemoryInternal(final Brain<?> brain) {
         brain.setMemoryInternal(this.type, this.value);
      }

      public <T> void serialize(final DynamicOps<T> ops, final RecordBuilder<T> builder) {
         this.type
            .getCodec()
            .ifPresent(
               codec -> this.value
                  .ifPresent(v -> builder.add(BuiltInRegistries.MEMORY_MODULE_TYPE.byNameCodec().encodeStart(ops, this.type), codec.encodeStart(ops, v)))
            );
      }
   }

   public static final class Provider<E extends LivingEntity> {
      private final Collection<? extends MemoryModuleType<?>> memoryTypes;
      private final Collection<? extends SensorType<? extends Sensor<? super E>>> sensorTypes;
      private final Codec<Brain<E>> codec;

      private Provider(
         final Collection<? extends MemoryModuleType<?>> memoryTypes, final Collection<? extends SensorType<? extends Sensor<? super E>>> sensorTypes
      ) {
         this.memoryTypes = memoryTypes;
         this.sensorTypes = sensorTypes;
         this.codec = Brain.codec(memoryTypes, sensorTypes);
      }

      public Brain<E> makeBrain(final Dynamic<?> input) {
         return this.codec
            .parse(input)
            .resultOrPartial(Brain.LOGGER::error)
            .orElseGet(() -> new Brain<>(this.memoryTypes, this.sensorTypes, ImmutableList.of(), () -> this.codec));
      }
   }
}
