package net.minecraft.world.level.timers;

import com.google.common.collect.HashBasedTable;
import com.google.common.collect.Table;
import com.google.common.primitives.UnsignedLong;
import com.mojang.logging.LogUtils;
import com.mojang.serialization.Dynamic;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.PriorityQueue;
import java.util.Queue;
import java.util.Set;
import java.util.stream.Stream;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import org.slf4j.Logger;

public class TimerQueue<T> {
   private static final Logger LOGGER = LogUtils.getLogger();
   private static final String CALLBACK_DATA_TAG = "Callback";
   private static final String TIMER_NAME_TAG = "Name";
   private static final String TIMER_TRIGGER_TIME_TAG = "TriggerTime";
   private final TimerCallbacks<T> callbacksRegistry;
   private final Queue<TimerQueue.Event<T>> queue = new PriorityQueue<>(createComparator());
   private UnsignedLong sequentialId = UnsignedLong.ZERO;
   private final Table<String, Long, TimerQueue.Event<T>> events = HashBasedTable.create();

   private static <T> Comparator<TimerQueue.Event<T>> createComparator() {
      return Comparator.<TimerQueue.Event<T>>comparingLong(l -> l.triggerTime).thenComparing(l -> l.sequentialId);
   }

   public TimerQueue(final TimerCallbacks<T> callbacksRegistry, final Stream<? extends Dynamic<?>> eventData) {
      this(callbacksRegistry);
      this.queue.clear();
      this.events.clear();
      this.sequentialId = UnsignedLong.ZERO;
      eventData.forEach(input -> {
         Tag tag = (Tag)input.convert(NbtOps.INSTANCE).getValue();
         if (tag instanceof CompoundTag compoundTag) {
            this.loadEvent(compoundTag);
         } else {
            LOGGER.warn("Invalid format of events: {}", tag);
         }
      });
   }

   public TimerQueue(final TimerCallbacks<T> callbacksRegistry) {
      this.callbacksRegistry = callbacksRegistry;
   }

   public void tick(final T context, final long currentTick) {
      while (true) {
         TimerQueue.Event<T> event = this.queue.peek();
         if (event == null || event.triggerTime > currentTick) {
            return;
         }

         this.queue.remove();
         this.events.remove(event.id, currentTick);
         event.callback.handle(context, this, currentTick);
      }
   }

   public void schedule(final String id, final long time, final TimerCallback<T> callback) {
      if (!this.events.contains(id, time)) {
         this.sequentialId = this.sequentialId.plus(UnsignedLong.ONE);
         TimerQueue.Event<T> newEvent = new TimerQueue.Event<>(time, this.sequentialId, id, callback);
         this.events.put(id, time, newEvent);
         this.queue.add(newEvent);
      }
   }

   public int remove(final String id) {
      Collection<TimerQueue.Event<T>> eventsToRemove = this.events.row(id).values();
      eventsToRemove.forEach(this.queue::remove);
      int size = eventsToRemove.size();
      eventsToRemove.clear();
      return size;
   }

   public Set<String> getEventsIds() {
      return Collections.unmodifiableSet(this.events.rowKeySet());
   }

   private void loadEvent(final CompoundTag tag) {
      TimerCallback<T> callback = tag.<TimerCallback<T>>read("Callback", this.callbacksRegistry.codec()).orElse(null);
      if (callback != null) {
         String id = tag.getStringOr("Name", "");
         long time = tag.getLongOr("TriggerTime", 0L);
         this.schedule(id, time, callback);
      }
   }

   private CompoundTag storeEvent(final TimerQueue.Event<T> event) {
      CompoundTag result = new CompoundTag();
      result.putString("Name", event.id);
      result.putLong("TriggerTime", event.triggerTime);
      result.store("Callback", this.callbacksRegistry.codec(), event.callback);
      return result;
   }

   public ListTag store() {
      ListTag result = new ListTag();
      this.queue.stream().sorted(createComparator()).map(this::storeEvent).forEach(result::add);
      return result;
   }

   public static class Event<T> {
      public final long triggerTime;
      public final UnsignedLong sequentialId;
      public final String id;
      public final TimerCallback<T> callback;

      private Event(final long triggerTime, final UnsignedLong sequentialId, final String id, final TimerCallback<T> callback) {
         this.triggerTime = triggerTime;
         this.sequentialId = sequentialId;
         this.id = id;
         this.callback = callback;
      }
   }
}
