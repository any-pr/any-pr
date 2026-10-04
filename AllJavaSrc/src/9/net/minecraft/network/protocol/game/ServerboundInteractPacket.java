package net.minecraft.network.protocol.game;

import java.util.function.Function;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.PacketType;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

public class ServerboundInteractPacket implements Packet<ServerGamePacketListener> {
   public static final StreamCodec<FriendlyByteBuf, ServerboundInteractPacket> STREAM_CODEC = Packet.codec(
      ServerboundInteractPacket::write, ServerboundInteractPacket::new
   );
   private final int entityId;
   private final ServerboundInteractPacket.Action action;
   private final boolean usingSecondaryAction;
   private static final ServerboundInteractPacket.Action ATTACK_ACTION = new ServerboundInteractPacket.Action() {
      @Override
      public ServerboundInteractPacket.ActionType getType() {
         return ServerboundInteractPacket.ActionType.ATTACK;
      }

      @Override
      public void dispatch(final ServerboundInteractPacket.Handler handler) {
         handler.onAttack();
      }

      @Override
      public void write(final FriendlyByteBuf output) {
      }
   };

   private ServerboundInteractPacket(final int entityId, final boolean usingSecondaryAction, final ServerboundInteractPacket.Action action) {
      this.entityId = entityId;
      this.action = action;
      this.usingSecondaryAction = usingSecondaryAction;
   }

   public static ServerboundInteractPacket createAttackPacket(final Entity entity, final boolean usingSecondaryAction) {
      return new ServerboundInteractPacket(entity.getId(), usingSecondaryAction, ATTACK_ACTION);
   }

   public static ServerboundInteractPacket createInteractionPacket(final Entity entity, final boolean usingSecondaryAction, final InteractionHand hand) {
      return new ServerboundInteractPacket(entity.getId(), usingSecondaryAction, new ServerboundInteractPacket.InteractionAction(hand));
   }

   public static ServerboundInteractPacket createInteractionPacket(
      final Entity entity, final boolean usingSecondaryAction, final InteractionHand hand, final Vec3 location
   ) {
      return new ServerboundInteractPacket(entity.getId(), usingSecondaryAction, new ServerboundInteractPacket.InteractionAtLocationAction(hand, location));
   }

   private ServerboundInteractPacket(final FriendlyByteBuf input) {
      this.entityId = input.readVarInt();
      ServerboundInteractPacket.ActionType type = input.readEnum(ServerboundInteractPacket.ActionType.class);
      this.action = type.reader.apply(input);
      this.usingSecondaryAction = input.readBoolean();
   }

   private void write(final FriendlyByteBuf output) {
      output.writeVarInt(this.entityId);
      output.writeEnum(this.action.getType());
      this.action.write(output);
      output.writeBoolean(this.usingSecondaryAction);
   }

   @Override
   public PacketType<ServerboundInteractPacket> type() {
      return GamePacketTypes.SERVERBOUND_INTERACT;
   }

   public void handle(final ServerGamePacketListener listener) {
      listener.handleInteract(this);
   }

   public @Nullable Entity getTarget(final ServerLevel level) {
      return level.getEntityOrPart(this.entityId);
   }

   public boolean isUsingSecondaryAction() {
      return this.usingSecondaryAction;
   }

   public boolean isWithinRange(final ServerPlayer player, final AABB aabb, final double buffer) {
      return this.action.getType() == ServerboundInteractPacket.ActionType.ATTACK
         ? player.isWithinAttackRange(aabb, buffer)
         : player.isWithinEntityInteractionRange(aabb, buffer);
   }

   public void dispatch(final ServerboundInteractPacket.Handler handler) {
      this.action.dispatch(handler);
   }

   private interface Action {
      ServerboundInteractPacket.ActionType getType();

      void dispatch(ServerboundInteractPacket.Handler handler);

      void write(FriendlyByteBuf output);
   }

   private enum ActionType {
      INTERACT(ServerboundInteractPacket.InteractionAction::new),
      ATTACK(input -> ServerboundInteractPacket.ATTACK_ACTION),
      INTERACT_AT(ServerboundInteractPacket.InteractionAtLocationAction::new);

      private final Function<FriendlyByteBuf, ServerboundInteractPacket.Action> reader;

      ActionType(final Function<FriendlyByteBuf, ServerboundInteractPacket.Action> reader) {
         this.reader = reader;
      }
   }

   public interface Handler {
      void onInteraction(final InteractionHand hand);

      void onInteraction(final InteractionHand hand, final Vec3 location);

      void onAttack();
   }

   private static class InteractionAction implements ServerboundInteractPacket.Action {
      private final InteractionHand hand;

      private InteractionAction(final InteractionHand hand) {
         this.hand = hand;
      }

      private InteractionAction(final FriendlyByteBuf input) {
         this.hand = input.readEnum(InteractionHand.class);
      }

      @Override
      public ServerboundInteractPacket.ActionType getType() {
         return ServerboundInteractPacket.ActionType.INTERACT;
      }

      @Override
      public void dispatch(final ServerboundInteractPacket.Handler handler) {
         handler.onInteraction(this.hand);
      }

      @Override
      public void write(final FriendlyByteBuf output) {
         output.writeEnum(this.hand);
      }
   }

   private static class InteractionAtLocationAction implements ServerboundInteractPacket.Action {
      private final InteractionHand hand;
      private final Vec3 location;

      private InteractionAtLocationAction(final InteractionHand hand, final Vec3 location) {
         this.hand = hand;
         this.location = location;
      }

      private InteractionAtLocationAction(final FriendlyByteBuf input) {
         this.location = new Vec3(input.readFloat(), input.readFloat(), input.readFloat());
         this.hand = input.readEnum(InteractionHand.class);
      }

      @Override
      public ServerboundInteractPacket.ActionType getType() {
         return ServerboundInteractPacket.ActionType.INTERACT_AT;
      }

      @Override
      public void dispatch(final ServerboundInteractPacket.Handler handler) {
         handler.onInteraction(this.hand, this.location);
      }

      @Override
      public void write(final FriendlyByteBuf output) {
         output.writeFloat((float)this.location.x);
         output.writeFloat((float)this.location.y);
         output.writeFloat((float)this.location.z);
         output.writeEnum(this.hand);
      }
   }
}
