package net.minecraft.network.chat;

import com.mojang.brigadier.ParseResults;
import com.mojang.brigadier.context.CommandContextBuilder;
import com.mojang.brigadier.context.ParsedArgument;
import com.mojang.brigadier.context.ParsedCommandNode;
import com.mojang.brigadier.tree.ArgumentCommandNode;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.commands.arguments.SignedArgument;
import org.jspecify.annotations.Nullable;

public record SignableCommand<S>(List<SignableCommand.Argument<S>> arguments) {
   public static <S> boolean hasSignableArguments(final ParseResults<S> command) {
      return !of(command).arguments().isEmpty();
   }

   public static <S> SignableCommand<S> of(final ParseResults<S> command) {
      String commandString = command.getReader().getString();
      CommandContextBuilder<S> rootContext = command.getContext();
      CommandContextBuilder<S> context = rootContext;
      List<SignableCommand.Argument<S>> arguments = collectArguments(commandString, context);

      CommandContextBuilder<S> child;
      while ((child = context.getChild()) != null && child.getRootNode() != rootContext.getRootNode()) {
         arguments.addAll(collectArguments(commandString, child));
         context = child;
      }

      return new SignableCommand<>(arguments);
   }

   private static <S> List<SignableCommand.Argument<S>> collectArguments(final String commandString, final CommandContextBuilder<S> context) {
      List<SignableCommand.Argument<S>> arguments = new ArrayList<>();

      for (ParsedCommandNode<S> node : context.getNodes()) {
         if (node.getNode() instanceof ArgumentCommandNode<S, ?> argument && argument.getType() instanceof SignedArgument) {
            ParsedArgument<S, ?> parsed = (ParsedArgument<S, ?>)context.getArguments().get(argument.getName());
            if (parsed != null) {
               String value = parsed.getRange().get(commandString);
               arguments.add(new SignableCommand.Argument<>(argument, value));
            }
         }
      }

      return arguments;
   }

   public SignableCommand.@Nullable Argument<S> getArgument(final String name) {
      for (SignableCommand.Argument<S> argument : this.arguments) {
         if (name.equals(argument.name())) {
            return argument;
         }
      }

      return null;
   }

   public record Argument<S>(ArgumentCommandNode<S, ?> node, String value) {
      public String name() {
         return this.node.getName();
      }
   }
}
