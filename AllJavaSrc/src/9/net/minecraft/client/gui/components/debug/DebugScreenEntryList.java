package net.minecraft.client.gui.components.debug;

import com.google.gson.JsonElement;
import com.google.gson.JsonSyntaxException;
import com.mojang.logging.LogUtils;
import com.mojang.serialization.Codec;
import com.mojang.serialization.Dynamic;
import com.mojang.serialization.JsonOps;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Map.Entry;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.Identifier;
import net.minecraft.util.StrictJsonParser;
import net.minecraft.util.datafix.DataFixTypes;
import org.apache.commons.io.FileUtils;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;

public class DebugScreenEntryList {
   private static final Logger LOGGER = LogUtils.getLogger();
   private static final int DEFAULT_DEBUG_PROFILE_VERSION = 4649;
   private Map<Identifier, DebugScreenEntryStatus> allStatuses;
   private final List<Identifier> currentlyEnabled = new ArrayList<>();
   private boolean isOverlayVisible = false;
   private @Nullable DebugScreenProfile profile;
   private final File debugProfileFile;
   private long currentlyEnabledVersion;
   private final Codec<DebugScreenEntryList.SerializedOptions> codec;

   public DebugScreenEntryList(final File workingDirectory) {
      this.debugProfileFile = new File(workingDirectory, "debug-profile.json");
      this.codec = DataFixTypes.DEBUG_PROFILE.wrapCodec(DebugScreenEntryList.SerializedOptions.CODEC, Minecraft.getInstance().getFixerUpper(), 4649);
      this.load();
   }

   public void load() {
      try {
         if (!this.debugProfileFile.isFile()) {
            this.loadDefaultProfile();
            this.rebuildCurrentList();
            return;
         }

         Dynamic<JsonElement> data = new Dynamic(
            JsonOps.INSTANCE, StrictJsonParser.parse(FileUtils.readFileToString(this.debugProfileFile, StandardCharsets.UTF_8))
         );
         DebugScreenEntryList.SerializedOptions serializedOptions = (DebugScreenEntryList.SerializedOptions)this.codec
            .parse(data)
            .getOrThrow(error -> new IOException("Could not parse debug profile JSON: " + error));
         if (serializedOptions.profile().isPresent()) {
            this.loadProfile(serializedOptions.profile().get());
         } else {
            this.allStatuses = new HashMap<>();
            if (serializedOptions.custom().isPresent()) {
               this.allStatuses.putAll(serializedOptions.custom().get());
            }

            this.profile = null;
         }
      } catch (IOException | JsonSyntaxException e) {
         LOGGER.error("Couldn't read debug profile file {}, resetting to default", this.debugProfileFile, e);
         this.loadDefaultProfile();
         this.save();
      }

      this.rebuildCurrentList();
   }

   public void loadProfile(final DebugScreenProfile profile) {
      this.profile = profile;
      Map<Identifier, DebugScreenEntryStatus> statuses = DebugScreenEntries.PROFILES.get(profile);
      this.allStatuses = new HashMap<>(statuses);
      this.rebuildCurrentList();
   }

   private void loadDefaultProfile() {
      this.profile = DebugScreenProfile.DEFAULT;
      this.allStatuses = new HashMap<>(DebugScreenEntries.PROFILES.get(DebugScreenProfile.DEFAULT));
   }

   public DebugScreenEntryStatus getStatus(final Identifier location) {
      DebugScreenEntryStatus status = this.allStatuses.get(location);
      return status == null ? DebugScreenEntryStatus.NEVER : status;
   }

   public boolean isCurrentlyEnabled(final Identifier location) {
      return this.currentlyEnabled.contains(location);
   }

   public void setStatus(final Identifier location, final DebugScreenEntryStatus status) {
      this.profile = null;
      this.allStatuses.put(location, status);
      this.rebuildCurrentList();
      this.save();
   }

   public boolean toggleStatus(final Identifier location) {
      DebugScreenEntryStatus status = this.allStatuses.get(location);
      switch (status) {
         case ALWAYS_ON:
            this.setStatus(location, DebugScreenEntryStatus.NEVER);
            return false;
         case IN_OVERLAY:
            if (this.isOverlayVisible) {
               this.setStatus(location, DebugScreenEntryStatus.NEVER);
               return false;
            }

            this.setStatus(location, DebugScreenEntryStatus.ALWAYS_ON);
            return true;
         case NEVER:
            if (this.isOverlayVisible) {
               this.setStatus(location, DebugScreenEntryStatus.IN_OVERLAY);
            } else {
               this.setStatus(location, DebugScreenEntryStatus.ALWAYS_ON);
            }

            return true;
         case null:
         default:
            this.setStatus(location, DebugScreenEntryStatus.ALWAYS_ON);
            return true;
      }
   }

   public Collection<Identifier> getCurrentlyEnabled() {
      return this.currentlyEnabled;
   }

   public void toggleDebugOverlay() {
      this.setOverlayVisible(!this.isOverlayVisible);
   }

   public void setOverlayVisible(final boolean visible) {
      if (this.isOverlayVisible != visible) {
         this.isOverlayVisible = visible;
         this.rebuildCurrentList();
      }
   }

   public boolean isOverlayVisible() {
      return this.isOverlayVisible;
   }

   public void rebuildCurrentList() {
      this.currentlyEnabled.clear();
      boolean isReducedDebugInfo = Minecraft.getInstance().showOnlyReducedInfo();

      for (Entry<Identifier, DebugScreenEntryStatus> entry : this.allStatuses.entrySet()) {
         if (entry.getValue() == DebugScreenEntryStatus.ALWAYS_ON || this.isOverlayVisible && entry.getValue() == DebugScreenEntryStatus.IN_OVERLAY) {
            DebugScreenEntry debug = DebugScreenEntries.getEntry(entry.getKey());
            if (debug != null && debug.isAllowed(isReducedDebugInfo)) {
               this.currentlyEnabled.add(entry.getKey());
            }
         }
      }

      this.currentlyEnabled.sort(Identifier::compareTo);
      this.currentlyEnabledVersion++;
   }

   public long getCurrentlyEnabledVersion() {
      return this.currentlyEnabledVersion;
   }

   public boolean isUsingProfile(final DebugScreenProfile profile) {
      return this.profile == profile;
   }

   public void save() {
      DebugScreenEntryList.SerializedOptions serializedOptions = new DebugScreenEntryList.SerializedOptions(
         Optional.ofNullable(this.profile), this.profile == null ? Optional.of(this.allStatuses) : Optional.empty()
      );

      try {
         FileUtils.writeStringToFile(
            this.debugProfileFile, ((JsonElement)this.codec.encodeStart(JsonOps.INSTANCE, serializedOptions).getOrThrow()).toString(), StandardCharsets.UTF_8
         );
      } catch (IOException e) {
         LOGGER.error("Failed to save debug profile file {}", this.debugProfileFile, e);
      }
   }

   record SerializedOptions(Optional<DebugScreenProfile> profile, Optional<Map<Identifier, DebugScreenEntryStatus>> custom) {
      private static final Codec<Map<Identifier, DebugScreenEntryStatus>> CUSTOM_ENTRIES_CODEC = Codec.unboundedMap(
         Identifier.CODEC, DebugScreenEntryStatus.CODEC
      );
      public static final Codec<DebugScreenEntryList.SerializedOptions> CODEC = RecordCodecBuilder.create(
         i -> i.group(
               DebugScreenProfile.CODEC.optionalFieldOf("profile").forGetter(DebugScreenEntryList.SerializedOptions::profile),
               CUSTOM_ENTRIES_CODEC.optionalFieldOf("custom").forGetter(DebugScreenEntryList.SerializedOptions::custom)
            )
            .apply(i, DebugScreenEntryList.SerializedOptions::new)
      );
   }
}
