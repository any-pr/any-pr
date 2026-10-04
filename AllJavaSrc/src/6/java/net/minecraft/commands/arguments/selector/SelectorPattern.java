package net.minecraft.commands.arguments.selector;

import com.mojang.brigadier.StringReader;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;

public record SelectorPattern(String pattern, EntitySelector resolved) {
   public static final Codec<SelectorPattern> CODEC = Codec.STRING.comapFlatMap(SelectorPattern::parse, SelectorPattern::pattern);

   public static DataResult<SelectorPattern> parse(final String pattern) {
      try {
         EntitySelectorParser parser = new EntitySelectorParser(new StringReader(pattern), true);
         return DataResult.success(new SelectorPattern(pattern, parser.parse()));
      } catch (CommandSyntaxException ex) {
         return DataResult.error(() -> "Invalid selector component: " + pattern + ": " + ex.getMessage());
      }
   }

   @Override
   public boolean equals(final Object obj) {
      return obj instanceof SelectorPattern selector && this.pattern.equals(selector.pattern);
   }

   @Override
   public int hashCode() {
      return this.pattern.hashCode();
   }

   @Override
   public String toString() {
      return this.pattern;
   }
}
