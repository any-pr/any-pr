package net.minecraft.client.gui.screens;

import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.ActiveTextCollector;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.ChatComponent;
import net.minecraft.client.gui.components.CommandSuggestions;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.narration.NarratedElementType;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.multiplayer.chat.ChatListener;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Mth;
import net.minecraft.util.StringUtil;
import org.apache.commons.lang3.StringUtils;
import org.jspecify.annotations.Nullable;

public class ChatScreen extends Screen {
   public static final double MOUSE_SCROLL_SPEED = 7.0;
   private static final Component USAGE_TEXT = Component.translatable("chat_screen.usage");
   private String historyBuffer = "";
   private int historyPos = -1;
   protected EditBox input;
   protected String initial;
   protected boolean isDraft;
   protected ChatScreen.ExitReason exitReason = ChatScreen.ExitReason.INTERRUPTED;
   private CommandSuggestions commandSuggestions;

   public ChatScreen(final String initial, final boolean isDraft) {
      super(Component.translatable("chat_screen.title"));
      this.initial = initial;
      this.isDraft = isDraft;
   }

   @Override
   protected void init() {
      this.historyPos = this.minecraft.gui.getChat().getRecentChat().size();
      this.input = new EditBox(this.minecraft.fontFilterFishy, 4, this.height - 12, this.width - 4, 12, Component.translatable("chat.editBox")) {
         @Override
         protected MutableComponent createNarrationMessage() {
            return super.createNarrationMessage().append(ChatScreen.this.commandSuggestions.getNarrationMessage());
         }
      };
      this.input.setMaxLength(256);
      this.input.setBordered(false);
      this.input.setValue(this.initial);
      this.input.setResponder(this::onEdited);
      this.input.addFormatter(this::formatChat);
      this.input.setCanLoseFocus(false);
      this.addRenderableWidget(this.input);
      this.commandSuggestions = new CommandSuggestions(this.minecraft, this, this.input, this.font, false, false, 1, 10, true, -805306368);
      this.commandSuggestions.setAllowHiding(false);
      this.commandSuggestions.setAllowSuggestions(false);
      this.commandSuggestions.updateCommandInfo();
   }

   @Override
   protected void setInitialFocus() {
      this.setInitialFocus(this.input);
   }

   @Override
   public void resize(final int width, final int height) {
      this.initial = this.input.getValue();
      this.init(width, height);
   }

   @Override
   public void onClose() {
      this.exitReason = ChatScreen.ExitReason.INTENTIONAL;
      super.onClose();
   }

   @Override
   public void removed() {
      this.minecraft.gui.getChat().resetChatScroll();
      this.initial = this.input.getValue();
      if (this.shouldDiscardDraft() || StringUtils.isBlank(this.initial)) {
         this.minecraft.gui.getChat().discardDraft();
      } else if (!this.isDraft) {
         this.minecraft.gui.getChat().saveAsDraft(this.initial);
      }
   }

   protected boolean shouldDiscardDraft() {
      return this.exitReason != ChatScreen.ExitReason.INTERRUPTED
         && (this.exitReason != ChatScreen.ExitReason.INTENTIONAL || !this.minecraft.options.saveChatDrafts().get());
   }

   private void onEdited(final String value) {
      this.commandSuggestions.setAllowSuggestions(true);
      this.commandSuggestions.updateCommandInfo();
      this.isDraft = false;
   }

   @Override
   public boolean keyPressed(final KeyEvent event) {
      if (this.commandSuggestions.keyPressed(event)) {
         return true;
      }

      if (this.isDraft && event.key() == 259) {
         this.input.setValue("");
         this.isDraft = false;
         return true;
      }

      if (super.keyPressed(event)) {
         return true;
      }

      if (event.isConfirmation()) {
         this.handleChatInput(this.input.getValue(), true);
         this.exitReason = ChatScreen.ExitReason.DONE;
         this.minecraft.setScreen(null);
         return true;
      }

      switch (event.key()) {
         case 264:
            this.moveInHistory(1);
            break;
         case 265:
            this.moveInHistory(-1);
            break;
         case 266:
            this.minecraft.gui.getChat().scrollChat(this.minecraft.gui.getChat().getLinesPerPage() - 1);
            break;
         case 267:
            this.minecraft.gui.getChat().scrollChat(-this.minecraft.gui.getChat().getLinesPerPage() + 1);
            break;
         default:
            return false;
      }

      return true;
   }

   @Override
   public boolean mouseScrolled(final double x, final double y, final double scrollX, double scrollY) {
      scrollY = Mth.clamp(scrollY, -1.0, 1.0);
      if (this.commandSuggestions.mouseScrolled(scrollY)) {
         return true;
      }

      if (!this.minecraft.hasShiftDown()) {
         scrollY *= 7.0;
      }

      this.minecraft.gui.getChat().scrollChat((int)scrollY);
      return true;
   }

   @Override
   public boolean mouseClicked(final MouseButtonEvent event, final boolean doubleClick) {
      if (this.commandSuggestions.mouseClicked(event)) {
         return true;
      }

      if (event.button() == 0) {
         int screenHeight = this.minecraft.getWindow().getGuiScaledHeight();
         ActiveTextCollector.ClickableStyleFinder finder = new ActiveTextCollector.ClickableStyleFinder(this.getFont(), (int)event.x(), (int)event.y())
            .includeInsertions(this.insertionClickMode());
         this.minecraft.gui.getChat().captureClickableText(finder, screenHeight, this.minecraft.gui.getGuiTicks(), true);
         Style clicked = finder.result();
         if (clicked != null && this.handleComponentClicked(clicked, this.insertionClickMode())) {
            this.initial = this.input.getValue();
            return true;
         }
      }

      return super.mouseClicked(event, doubleClick);
   }

   private boolean insertionClickMode() {
      return this.minecraft.hasShiftDown();
   }

   private boolean handleComponentClicked(final Style clicked, final boolean allowInsertions) {
      ClickEvent event = clicked.getClickEvent();
      if (allowInsertions) {
         if (clicked.getInsertion() != null) {
            this.insertText(clicked.getInsertion(), false);
         }
      } else if (event != null) {
         if (event instanceof ClickEvent.Custom customEvent && customEvent.id().equals(ChatComponent.QUEUE_EXPAND_ID)) {
            ChatListener chatListener = this.minecraft.getChatListener();
            if (chatListener.queueSize() != 0L) {
               chatListener.acceptNextDelayedMessage();
            }
         } else {
            defaultHandleGameClickEvent(event, this.minecraft, this);
         }

         return true;
      }

      return false;
   }

   @Override
   public void insertText(final String text, final boolean replace) {
      if (replace) {
         this.input.setValue(text);
      } else {
         this.input.insertText(text);
      }
   }

   public void moveInHistory(final int dir) {
      int newPos = this.historyPos + dir;
      int max = this.minecraft.gui.getChat().getRecentChat().size();
      newPos = Mth.clamp(newPos, 0, max);
      if (newPos != this.historyPos) {
         if (newPos == max) {
            this.historyPos = max;
            this.input.setValue(this.historyBuffer);
         } else {
            if (this.historyPos == max) {
               this.historyBuffer = this.input.getValue();
            }

            this.input.setValue(this.minecraft.gui.getChat().getRecentChat().get(newPos));
            this.commandSuggestions.setAllowSuggestions(false);
            this.historyPos = newPos;
         }
      }
   }

   private @Nullable FormattedCharSequence formatChat(final String text, final int offset) {
      return this.isDraft ? FormattedCharSequence.forward(text, Style.EMPTY.withColor(ChatFormatting.GRAY).withItalic(true)) : null;
   }

   @Override
   public void render(final GuiGraphics graphics, final int mouseX, final int mouseY, final float a) {
      graphics.fill(2, this.height - 14, this.width - 2, this.height - 2, this.minecraft.options.getBackgroundColor(Integer.MIN_VALUE));
      this.minecraft.gui.getChat().render(graphics, this.font, this.minecraft.gui.getGuiTicks(), mouseX, mouseY, true, this.insertionClickMode());
      super.render(graphics, mouseX, mouseY, a);
      this.commandSuggestions.render(graphics, mouseX, mouseY);
   }

   @Override
   public void renderBackground(final GuiGraphics graphics, final int mouseX, final int mouseY, final float a) {
   }

   @Override
   public boolean isPauseScreen() {
      return false;
   }

   @Override
   public boolean isAllowedInPortal() {
      return true;
   }

   @Override
   protected void updateNarrationState(final NarrationElementOutput output) {
      output.add(NarratedElementType.TITLE, this.getTitle());
      output.add(NarratedElementType.USAGE, USAGE_TEXT);
      String value = this.input.getValue();
      if (!value.isEmpty()) {
         output.nest().add(NarratedElementType.TITLE, Component.translatable("chat_screen.message", value));
      }
   }

   public void handleChatInput(String msg, final boolean addToRecent) {
      msg = this.normalizeChatMessage(msg);
      if (!msg.isEmpty()) {
         if (addToRecent) {
            this.minecraft.gui.getChat().addRecentChat(msg);
         }

         if (msg.startsWith("/")) {
            this.minecraft.player.connection.sendCommand(msg.substring(1));
         } else {
            this.minecraft.player.connection.sendChat(msg);
         }
      }
   }

   public String normalizeChatMessage(final String message) {
      return StringUtil.trimChatMessage(StringUtils.normalizeSpace(message.trim()));
   }

   @FunctionalInterface
   public interface ChatConstructor<T extends ChatScreen> {
      T create(String initial, boolean isDraft);
   }

   protected enum ExitReason {
      INTENTIONAL,
      INTERRUPTED,
      DONE;
   }
}
