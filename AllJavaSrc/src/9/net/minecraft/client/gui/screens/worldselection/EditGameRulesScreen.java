package net.minecraft.client.gui.screens.worldselection;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import com.google.common.collect.ImmutableList.Builder;
import com.mojang.serialization.DataResult;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Consumer;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.ContainerObjectSelectionList;
import net.minecraft.client.gui.components.CycleButton;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.layouts.HeaderAndFooterLayout;
import net.minecraft.client.gui.layouts.LinearLayout;
import net.minecraft.client.gui.narration.NarratableEntry;
import net.minecraft.client.gui.narration.NarratedElementType;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.level.gamerules.GameRule;
import net.minecraft.world.level.gamerules.GameRuleCategory;
import net.minecraft.world.level.gamerules.GameRuleTypeVisitor;
import net.minecraft.world.level.gamerules.GameRules;
import org.jspecify.annotations.Nullable;

public class EditGameRulesScreen extends Screen {
   private static final Component TITLE = Component.translatable("editGamerule.title");
   private static final int SPACING = 8;
   private final HeaderAndFooterLayout layout = new HeaderAndFooterLayout(this);
   private final Consumer<Optional<GameRules>> exitCallback;
   private final Set<EditGameRulesScreen.RuleEntry> invalidEntries = Sets.newHashSet();
   private final GameRules gameRules;
   private EditGameRulesScreen.@Nullable RuleList ruleList;
   private @Nullable Button doneButton;

   public EditGameRulesScreen(final GameRules gameRules, final Consumer<Optional<GameRules>> exitCallback) {
      super(TITLE);
      this.gameRules = gameRules;
      this.exitCallback = exitCallback;
   }

   @Override
   protected void init() {
      this.layout.addTitleHeader(TITLE, this.font);
      this.ruleList = this.layout.addToContents(new EditGameRulesScreen.RuleList(this.gameRules));
      LinearLayout footer = this.layout.addToFooter(LinearLayout.horizontal().spacing(8));
      this.doneButton = footer.addChild(Button.builder(CommonComponents.GUI_DONE, button -> this.exitCallback.accept(Optional.of(this.gameRules))).build());
      footer.addChild(Button.builder(CommonComponents.GUI_CANCEL, button -> this.onClose()).build());
      this.layout.visitWidgets(x$0 -> {
         AbstractWidget var10000 = this.addRenderableWidget(x$0);
      });
      this.repositionElements();
   }

   @Override
   protected void repositionElements() {
      this.layout.arrangeElements();
      if (this.ruleList != null) {
         this.ruleList.updateSize(this.width, this.layout);
      }
   }

   @Override
   public void onClose() {
      this.exitCallback.accept(Optional.empty());
   }

   private void updateDoneButton() {
      if (this.doneButton != null) {
         this.doneButton.active = this.invalidEntries.isEmpty();
      }
   }

   private void markInvalid(final EditGameRulesScreen.RuleEntry invalidEntry) {
      this.invalidEntries.add(invalidEntry);
      this.updateDoneButton();
   }

   private void clearInvalid(final EditGameRulesScreen.RuleEntry invalidEntry) {
      this.invalidEntries.remove(invalidEntry);
      this.updateDoneButton();
   }

   public class BooleanRuleEntry extends EditGameRulesScreen.GameRuleEntry {
      private final CycleButton<Boolean> checkbox;

      public BooleanRuleEntry(final Component name, final List<FormattedCharSequence> tooltip, final String narration, final GameRule<Boolean> gameRule) {
         super(tooltip, name);
         this.checkbox = CycleButton.onOffBuilder(EditGameRulesScreen.this.gameRules.get(gameRule))
            .displayOnlyValue()
            .withCustomNarration(button -> button.createDefaultNarrationMessage().append("\n").append(narration))
            .create(10, 5, 44, 20, name, (button, newValue) -> EditGameRulesScreen.this.gameRules.set(gameRule, newValue, null));
         this.children.add(this.checkbox);
      }

      @Override
      public void renderContent(final GuiGraphics graphics, final int mouseX, final int mouseY, final boolean hovered, final float a) {
         this.renderLabel(graphics, this.getContentY(), this.getContentX());
         this.checkbox.setX(this.getContentRight() - 45);
         this.checkbox.setY(this.getContentY());
         this.checkbox.render(graphics, mouseX, mouseY, a);
      }
   }

   public class CategoryRuleEntry extends EditGameRulesScreen.RuleEntry {
      private final Component label;

      public CategoryRuleEntry(final Component label) {
         super(null);
         this.label = label;
      }

      @Override
      public void renderContent(final GuiGraphics graphics, final int mouseX, final int mouseY, final boolean hovered, final float a) {
         graphics.drawCenteredString(EditGameRulesScreen.this.minecraft.font, this.label, this.getContentXMiddle(), this.getContentY() + 5, -1);
      }

      @Override
      public List<? extends GuiEventListener> children() {
         return ImmutableList.of();
      }

      @Override
      public List<? extends NarratableEntry> narratables() {
         return ImmutableList.of(new NarratableEntry() {
            @Override
            public NarratableEntry.NarrationPriority narrationPriority() {
               return NarratableEntry.NarrationPriority.HOVERED;
            }

            @Override
            public void updateNarration(final NarrationElementOutput output) {
               output.add(NarratedElementType.TITLE, CategoryRuleEntry.this.label);
            }
         });
      }
   }

   @FunctionalInterface
   private interface EntryFactory<T> {
      EditGameRulesScreen.RuleEntry create(Component name, List<FormattedCharSequence> tooltip, String narration, GameRule<T> gameRule);
   }

   public abstract class GameRuleEntry extends EditGameRulesScreen.RuleEntry {
      private final List<FormattedCharSequence> label;
      protected final List<AbstractWidget> children = Lists.newArrayList();

      public GameRuleEntry(final @Nullable List<FormattedCharSequence> tooltip, final Component label) {
         super(tooltip);
         this.label = EditGameRulesScreen.this.minecraft.font.split(label, 175);
      }

      @Override
      public List<? extends GuiEventListener> children() {
         return this.children;
      }

      @Override
      public List<? extends NarratableEntry> narratables() {
         return this.children;
      }

      protected void renderLabel(final GuiGraphics graphics, final int rowTop, final int rowLeft) {
         if (this.label.size() == 1) {
            graphics.drawString(EditGameRulesScreen.this.minecraft.font, this.label.get(0), rowLeft, rowTop + 5, -1);
         } else if (this.label.size() >= 2) {
            graphics.drawString(EditGameRulesScreen.this.minecraft.font, this.label.get(0), rowLeft, rowTop, -1);
            graphics.drawString(EditGameRulesScreen.this.minecraft.font, this.label.get(1), rowLeft, rowTop + 10, -1);
         }
      }
   }

   public class IntegerRuleEntry extends EditGameRulesScreen.GameRuleEntry {
      private final EditBox input;

      public IntegerRuleEntry(final Component label, final List<FormattedCharSequence> tooltip, final String narration, final GameRule<Integer> gameRule) {
         super(tooltip, label);
         this.input = new EditBox(EditGameRulesScreen.this.minecraft.font, 10, 5, 44, 20, label.copy().append("\n").append(narration).append("\n"));
         this.input.setValue(EditGameRulesScreen.this.gameRules.getAsString(gameRule));
         this.input.setResponder(v -> {
            DataResult<Integer> value = gameRule.deserialize(v);
            if (value.isSuccess()) {
               this.input.setTextColor(-2039584);
               EditGameRulesScreen.this.clearInvalid(this);
               EditGameRulesScreen.this.gameRules.set(gameRule, (Integer)value.getOrThrow(), null);
            } else {
               this.input.setTextColor(-65536);
               EditGameRulesScreen.this.markInvalid(this);
            }
         });
         this.children.add(this.input);
      }

      @Override
      public void renderContent(final GuiGraphics graphics, final int mouseX, final int mouseY, final boolean hovered, final float a) {
         this.renderLabel(graphics, this.getContentY(), this.getContentX());
         this.input.setX(this.getContentRight() - 45);
         this.input.setY(this.getContentY());
         this.input.render(graphics, mouseX, mouseY, a);
      }
   }

   public abstract static class RuleEntry extends ContainerObjectSelectionList.Entry<EditGameRulesScreen.RuleEntry> {
      private final @Nullable List<FormattedCharSequence> tooltip;

      public RuleEntry(final @Nullable List<FormattedCharSequence> tooltip) {
         this.tooltip = tooltip;
      }
   }

   public class RuleList extends ContainerObjectSelectionList<EditGameRulesScreen.RuleEntry> {
      private static final int ITEM_HEIGHT = 24;

      public RuleList(final GameRules gameRules) {
         super(
            Minecraft.getInstance(),
            EditGameRulesScreen.this.width,
            EditGameRulesScreen.this.layout.getContentHeight(),
            EditGameRulesScreen.this.layout.getHeaderHeight(),
            24
         );
         final Map<GameRuleCategory, Map<GameRule<?>, EditGameRulesScreen.RuleEntry>> entries = Maps.newHashMap();
         gameRules.visitGameRuleTypes(
            new GameRuleTypeVisitor() {
               @Override
               public void visitBoolean(final GameRule<Boolean> gameRule) {
                  this.addEntry(gameRule, (x$0, x$1, x$2, x$3) -> EditGameRulesScreen.this.new BooleanRuleEntry(x$0, x$1, x$2, x$3));
               }

               @Override
               public void visitInteger(final GameRule<Integer> gameRule) {
                  this.addEntry(gameRule, (x$0, x$1, x$2, x$3) -> EditGameRulesScreen.this.new IntegerRuleEntry(x$0, x$1, x$2, x$3));
               }

               private <T> void addEntry(final GameRule<T> gameRule, final EditGameRulesScreen.EntryFactory<T> factory) {
                  Component readableName = Component.translatable(gameRule.getDescriptionId());
                  Component actualName = Component.literal(gameRule.id()).withStyle(ChatFormatting.YELLOW);
                  Component defaultValue = Component.translatable("editGamerule.default", Component.literal(gameRule.serialize(gameRule.defaultValue())))
                     .withStyle(ChatFormatting.GRAY);
                  String descriptionKey = gameRule.getDescriptionId() + ".description";
                  List<FormattedCharSequence> tooltip;
                  String narration;
                  if (I18n.exists(descriptionKey)) {
                     Builder<FormattedCharSequence> result = ImmutableList.builder().add(actualName.getVisualOrderText());
                     Component description = Component.translatable(descriptionKey);
                     EditGameRulesScreen.this.font.split(description, 150).forEach(result::add);
                     tooltip = result.add(defaultValue.getVisualOrderText()).build();
                     narration = description.getString() + "\n" + defaultValue.getString();
                  } else {
                     tooltip = ImmutableList.of(actualName.getVisualOrderText(), defaultValue.getVisualOrderText());
                     narration = defaultValue.getString();
                  }

                  entries.computeIfAbsent(gameRule.category(), k -> Maps.newHashMap())
                     .put(gameRule, factory.create(readableName, tooltip, narration, gameRule));
               }
            }
         );
         entries.entrySet()
            .stream()
            .sorted(Map.Entry.comparingByKey(Comparator.comparing(GameRuleCategory::getDescriptionId)))
            .forEach(
               e -> {
                  this.addEntry(EditGameRulesScreen.this.new CategoryRuleEntry(e.getKey().label().withStyle(ChatFormatting.BOLD, ChatFormatting.YELLOW)));
                  e.getValue()
                     .entrySet()
                     .stream()
                     .sorted(Map.Entry.comparingByKey(Comparator.comparing(GameRule::getDescriptionId)))
                     .forEach(v -> this.addEntry(v.getValue()));
               }
            );
      }

      @Override
      public void renderWidget(final GuiGraphics graphics, final int mouseX, final int mouseY, final float a) {
         super.renderWidget(graphics, mouseX, mouseY, a);
         EditGameRulesScreen.RuleEntry hovered = this.getHovered();
         if (hovered != null && hovered.tooltip != null) {
            graphics.setTooltipForNextFrame(hovered.tooltip, mouseX, mouseY);
         }
      }
   }
}
