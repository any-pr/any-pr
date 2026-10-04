package net.minecraft.data.tags;

import java.util.Arrays;
import java.util.Collection;
import java.util.function.Function;
import java.util.stream.Stream;
import net.minecraft.resources.ResourceKey;
import net.minecraft.tags.TagBuilder;
import net.minecraft.tags.TagKey;

public interface TagAppender<E, T> {
   TagAppender<E, T> add(E element);

   default TagAppender<E, T> add(final E... elements) {
      return this.addAll(Arrays.stream(elements));
   }

   default TagAppender<E, T> addAll(final Collection<E> elements) {
      elements.forEach(this::add);
      return this;
   }

   default TagAppender<E, T> addAll(final Stream<E> elements) {
      elements.forEach(this::add);
      return this;
   }

   TagAppender<E, T> addOptional(E element);

   TagAppender<E, T> addTag(TagKey<T> tag);

   TagAppender<E, T> addOptionalTag(TagKey<T> tag);

   static <T> TagAppender<ResourceKey<T>, T> forBuilder(final TagBuilder builder) {
      return new TagAppender<ResourceKey<T>, T>() {
         public TagAppender<ResourceKey<T>, T> add(final ResourceKey<T> element) {
            builder.addElement(element.identifier());
            return this;
         }

         public TagAppender<ResourceKey<T>, T> addOptional(final ResourceKey<T> element) {
            builder.addOptionalElement(element.identifier());
            return this;
         }

         @Override
         public TagAppender<ResourceKey<T>, T> addTag(final TagKey<T> tag) {
            builder.addTag(tag.location());
            return this;
         }

         @Override
         public TagAppender<ResourceKey<T>, T> addOptionalTag(final TagKey<T> tag) {
            builder.addOptionalTag(tag.location());
            return this;
         }
      };
   }

   default <U> TagAppender<U, T> map(final Function<U, E> converter) {
      final TagAppender<E, T> original = this;
      return new TagAppender<U, T>() {
         @Override
         public TagAppender<U, T> add(final U element) {
            original.add(converter.apply(element));
            return this;
         }

         @Override
         public TagAppender<U, T> addOptional(final U element) {
            original.add(converter.apply(element));
            return this;
         }

         @Override
         public TagAppender<U, T> addTag(final TagKey<T> tag) {
            original.addTag(tag);
            return this;
         }

         @Override
         public TagAppender<U, T> addOptionalTag(final TagKey<T> tag) {
            original.addOptionalTag(tag);
            return this;
         }
      };
   }
}
