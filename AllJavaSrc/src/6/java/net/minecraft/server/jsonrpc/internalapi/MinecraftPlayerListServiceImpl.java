package net.minecraft.server.jsonrpc.internalapi;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.server.dedicated.DedicatedServer;
import net.minecraft.server.jsonrpc.JsonRpcLogger;
import net.minecraft.server.jsonrpc.methods.ClientInfo;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.players.NameAndId;
import org.jspecify.annotations.Nullable;

public class MinecraftPlayerListServiceImpl implements MinecraftPlayerListService {
   private final JsonRpcLogger jsonRpcLogger;
   private final DedicatedServer server;

   public MinecraftPlayerListServiceImpl(final DedicatedServer server, final JsonRpcLogger jsonRpcLogger) {
      this.jsonRpcLogger = jsonRpcLogger;
      this.server = server;
   }

   @Override
   public List<ServerPlayer> getPlayers() {
      return this.server.getPlayerList().getPlayers();
   }

   @Override
   public @Nullable ServerPlayer getPlayer(final UUID uuid) {
      return this.server.getPlayerList().getPlayer(uuid);
   }

   @Override
   public Optional<NameAndId> fetchUserByName(final String name) {
      return this.server.services().nameToIdCache().get(name);
   }

   @Override
   public Optional<NameAndId> fetchUserById(final UUID id) {
      return Optional.ofNullable(this.server.services().sessionService().fetchProfile(id, true)).map(profile -> new NameAndId(profile.profile()));
   }

   @Override
   public Optional<NameAndId> getCachedUserById(final UUID id) {
      return this.server.services().nameToIdCache().get(id);
   }

   @Override
   public Optional<ServerPlayer> getPlayer(final Optional<UUID> id, final Optional<String> name) {
      if (id.isPresent()) {
         return Optional.ofNullable(this.server.getPlayerList().getPlayer(id.get()));
      } else {
         return name.isPresent() ? Optional.ofNullable(this.server.getPlayerList().getPlayerByName(name.get())) : Optional.empty();
      }
   }

   @Override
   public List<ServerPlayer> getPlayersWithAddress(final String ip) {
      return this.server.getPlayerList().getPlayersWithAddress(ip);
   }

   @Override
   public void remove(final ServerPlayer serverPlayer, final ClientInfo clientInfo) {
      this.server.getPlayerList().remove(serverPlayer);
      this.jsonRpcLogger.log(clientInfo, "Remove player '{}'", serverPlayer.getPlainTextName());
   }

   @Override
   public @Nullable ServerPlayer getPlayerByName(final String name) {
      return this.server.getPlayerList().getPlayerByName(name);
   }
}
