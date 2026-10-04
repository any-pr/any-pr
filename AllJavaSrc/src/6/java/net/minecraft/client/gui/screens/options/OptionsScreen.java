package net.minecraft.client.gui.screens.options;

import java.util.function.Supplier;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Options;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.CycleButton;
import net.minecraft.client.gui.components.LockIconButton;
import net.minecraft.client.gui.components.StringWidget;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.layouts.EqualSpacingLayout;
import net.minecraft.client.gui.layouts.GridLayout;
import net.minecraft.client.gui.layouts.HeaderAndFooterLayout;
import net.minecraft.client.gui.layouts.LayoutElement;
import net.minecraft.client.gui.layouts.LayoutSettings;
import net.minecraft.client.gui.layouts.LinearLayout;
import net.minecraft.client.gui.screens.ConfirmScreen;
import net.minecraft.client.gui.screens.CreditsAndAttributionScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.options.controls.ControlsScreen;
import net.minecraft.client.gui.screens.packs.PackSelectionScreen;
import net.minecraft.client.gui.screens.telemetry.TelemetryInfoScreen;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ServerboundChangeDifficultyPacket;
import net.minecraft.network.protocol.game.ServerboundLockDifficultyPacket;
import net.minecraft.server.packs.repository.PackRepository;
import net.minecraft.world.Difficulty;
import org.jspecify.annotations.Nullable;

public class OptionsScreen extends Screen {
   private static final Component TITLE = Component.translatable("options.title");
   private static final Component SKIN_CUSTOMIZATION = Component.translatable("options.skinCustomisation");
   private static final Component SOUNDS = Component.translatable("options.sounds");
   private static final Component VIDEO = Component.translatable("options.video");
   public static final Component CONTROLS = Component.translatable("options.controls");
   private static final Component LANGUAGE = Component.translatable("options.language");
   private static final Component CHAT = Component.translatable("options.chat");
   private static final Component RESOURCEPACK = Component.translatable("options.resourcepack");
   private static final Component ACCESSIBILITY = Component.translatable("options.accessibility");
   private static final Component TELEMETRY = Component.translatable("options.telemetry");
   private static final Tooltip TELEMETRY_DISABLED_TOOLTIP = Tooltip.create(Component.translatable("options.telemetry.disabled"));
   private static final Component CREDITS_AND_ATTRIBUTION = Component.translatable("options.credits_and_attribution");
   private static final int COLUMNS = 2;
   private final HeaderAndFooterLayout layout = new HeaderAndFooterLayout(this, 61, 33);
   private final Screen lastScreen;
   private final Options options;
   private @Nullable CycleButton<Difficulty> difficultyButton;
   private @Nullable LockIconButton lockButton;

   public OptionsScreen(final Screen lastScreen, final Options options) {
      super(TITLE);
      this.lastScreen = lastScreen;
      this.options = options;
   }

   @Override
   protected void init() {
      LinearLayout header = this.layout.addToHeader(LinearLayout.vertical().spacing(8));
      header.addChild(new StringWidget(TITLE, this.font), LayoutSettings::alignHorizontallyCenter);
      LinearLayout subHeader = header.addChild(LinearLayout.horizontal()).spacing(8);
      subHeader.addChild(this.options.fov().createButton(this.minecraft.options));
      subHeader.addChild(this.createOnlineButton());
      GridLayout gridLayout = new GridLayout();
      gridLayout.defaultCellSetting().paddingHorizontal(4).paddingBottom(4).alignHorizontallyCenter();
      GridLayout.RowHelper helper = gridLayout.createRowHelper(2);
      helper.addChild(this.openScreenButton(SKIN_CUSTOMIZATION, () -> new SkinCustomizationScreen(this, this.options)));
      helper.addChild(this.openScreenButton(SOUNDS, () -> new SoundOptionsScreen(this, this.options)));
      helper.addChild(this.openScreenButton(VIDEO, () -> new VideoSettingsScreen(this, this.minecraft, this.options)));
      helper.addChild(this.openScreenButton(CONTROLS, () -> new ControlsScreen(this, this.options)));
      helper.addChild(this.openScreenButton(LANGUAGE, () -> new LanguageSelectScreen(this, this.options, this.minecraft.getLanguageManager())));
      helper.addChild(this.openScreenButton(CHAT, () -> new ChatOptionsScreen(this, this.options)));
      helper.addChild(
         this.openScreenButton(
            RESOURCEPACK,
            () -> new PackSelectionScreen(
               this.minecraft.getResourcePackRepository(),
               this::applyPacks,
               this.minecraft.getResourcePackDirectory(),
               Component.translatable("resourcePack.title")
            )
         )
      );
      helper.addChild(this.openScreenButton(ACCESSIBILITY, () -> new AccessibilityOptionsScreen(this, this.options)));
      Button telemetryButton = helper.addChild(this.openScreenButton(TELEMETRY, () -> new TelemetryInfoScreen(this, this.options)));
      if (!this.minecraft.allowsTelemetry()) {
         telemetryButton.active = false;
         telemetryButton.setTooltip(TELEMETRY_DISABLED_TOOLTIP);
      }

      helper.addChild(this.openScreenButton(CREDITS_AND_ATTRIBUTION, () -> new CreditsAndAttributionScreen(this)));
      this.layout.addToContents(gridLayout);
      this.layout.addToFooter(Button.builder(CommonComponents.GUI_DONE, button -> this.onClose()).width(200).build());
      this.layout.visitWidgets(x$0 -> {
         AbstractWidget var10000 = this.addRenderableWidget(x$0);
      });
      this.repositionElements();
   }

   @Override
   protected void repositionElements() {
      this.layout.arrangeElements();
   }

   @Override
   public void onClose() {
      this.minecraft.setScreen(this.lastScreen);
   }

   private void applyPacks(final PackRepository packRepository) {
      this.options.updateResourcePacks(packRepository);
      this.minecraft.setScreen(this);
   }

   private LayoutElement createOnlineButton() {
      if (this.minecraft.level != null && this.minecraft.hasSingleplayerServer()) {
         this.difficultyButton = createDifficultyButton(0, 0, "options.difficulty", this.minecraft);
         if (!this.minecraft.level.getLevelData().isHardcore()) {
            this.lockButton = new LockIconButton(
               0,
               0,
               button -> this.minecraft
                  .setScreen(
                     new ConfirmScreen(
                        this::lockCallback,
                        Component.translatable("difficulty.lock.title"),
                        Component.translatable("difficulty.lock.question", this.minecraft.level.getLevelData().getDifficulty().getDisplayName())
                     )
                  )
            );
            this.difficultyButton.setWidth(this.difficultyButton.getWidth() - this.lockButton.getWidth());
            this.lockButton.setLocked(this.minecraft.level.getLevelData().isDifficultyLocked());
            this.lockButton.active = !this.lockButton.isLocked();
            this.difficultyButton.active = !this.lockButton.isLocked();
            EqualSpacingLayout linearLayout = new EqualSpacingLayout(150, 0, EqualSpacingLayout.Orientation.HORIZONTAL);
            linearLayout.addChild(this.difficultyButton);
            linearLayout.addChild(this.lockButton);
            return linearLayout;
         } else {
            this.difficultyButton.active = false;
            return this.difficultyButton;
         }
      } else {
         return Button.builder(Component.translatable("options.online"), button -> this.minecraft.setScreen(new OnlineOptionsScreen(this, this.options)))
            .bounds(this.width / 2 + 5, this.height / 6 - 12 + 24, 150, 20)
            .build();
      }
   }

   public static CycleButton<Difficulty> createDifficultyButton(final int x, final int y, final String title, final Minecraft minecraft) {
      return CycleButton.builder(Difficulty::getDisplayName, minecraft.level.getDifficulty())
         .withValues(Difficulty.values())
         .create(x, y, 150, 20, Component.translatable(title), (button, value) -> minecraft.getConnection().send(new ServerboundChangeDifficultyPacket(value)));
   }

   private void lockCallback(final boolean result) {
      this.minecraft.setScreen(this);
      if (result && this.minecraft.level != null && this.lockButton != null && this.difficultyButton != null) {
         this.minecraft.getConnection().send(new ServerboundLockDifficultyPacket(true));
         this.lockButton.setLocked(true);
         this.lockButton.active = false;
         this.difficultyButton.active = false;
      }
   }

   @Override
   public void removed() {
      this.options.save();
   }

   private Button openScreenButton(final Component message, final Supplier<Screen> screenToScreen) {
      return Button.builder(message, button -> this.minecraft.setScreen(screenToScreen.get())).build();
   }
}
