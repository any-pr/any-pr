package net.minecraft.advancements.criterion;

import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Predicate;
import net.minecraft.advancements.CriterionTrigger;
import net.minecraft.advancements.CriterionTriggerInstance;
import net.minecraft.server.PlayerAdvancements;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.storage.loot.LootContext;

public abstract class SimpleCriterionTrigger<T extends SimpleCriterionTrigger.SimpleInstance> implements CriterionTrigger<T> {
   private final Map<PlayerAdvancements, Set<CriterionTrigger.Listener<T>>> players = Maps.newIdentityHashMap();

   @Override
   public final void addPlayerListener(final PlayerAdvancements player, final CriterionTrigger.Listener<T> listener) {
      this.players.computeIfAbsent(player, k -> Sets.newHashSet()).add(listener);
   }

   @Override
   public final void removePlayerListener(final PlayerAdvancements player, final CriterionTrigger.Listener<T> listener) {
      Set<CriterionTrigger.Listener<T>> listeners = this.players.get(player);
      if (listeners != null) {
         listeners.remove(listener);
         if (listeners.isEmpty()) {
            this.players.remove(player);
         }
      }
   }

   @Override
   public final void removePlayerListeners(final PlayerAdvancements player) {
      this.players.remove(player);
   }

   protected void trigger(final ServerPlayer player, final Predicate<T> matcher) {
      PlayerAdvancements advancements = player.getAdvancements();
      Set<CriterionTrigger.Listener<T>> allListeners = this.players.get(advancements);
      if (allListeners != null && !allListeners.isEmpty()) {
         LootContext playerContext = EntityPredicate.createContext(player, player);
         List<CriterionTrigger.Listener<T>> listeners = null;

         for (CriterionTrigger.Listener<T> listener : allListeners) {
            T triggerInstance = listener.trigger();
            if (matcher.test(triggerInstance)) {
               Optional<ContextAwarePredicate> predicate = triggerInstance.player();
               if (predicate.isEmpty() || predicate.get().matches(playerContext)) {
                  if (listeners == null) {
                     listeners = Lists.newArrayList();
                  }

                  listeners.add(listener);
               }
            }
         }

         if (listeners != null) {
            for (CriterionTrigger.Listener<T> listener : listeners) {
               listener.run(advancements);
            }
         }
      }
   }

   public interface SimpleInstance extends CriterionTriggerInstance {
      @Override
      default void validate(final CriterionValidator validator) {
         validator.validateEntity(this.player(), "player");
      }

      Optional<ContextAwarePredicate> player();
   }
}
