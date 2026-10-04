package com.mojang.datafixers.optics;

import com.mojang.datafixers.FunctionType;
import com.mojang.datafixers.kinds.App;
import com.mojang.datafixers.kinds.App2;
import com.mojang.datafixers.kinds.K2;
import com.mojang.datafixers.optics.profunctors.Cocartesian;
import com.mojang.datafixers.util.Either;
import java.util.function.Function;

interface ReForgetE<R, A, B> extends App2<ReForgetE.Mu<R>, A, B> {
   static <R, A, B> ReForgetE<R, A, B> unbox(App2<ReForgetE.Mu<R>, A, B> box) {
      return (ReForgetE<R, A, B>)box;
   }

   B run(Either<A, R> var1);

   final class Instance<R> implements Cocartesian<ReForgetE.Mu<R>, ReForgetE.Instance.Mu<R>>, App<ReForgetE.Instance.Mu<R>, ReForgetE.Mu<R>> {
      @Override
      public <A, B, C, D> FunctionType<App2<ReForgetE.Mu<R>, A, B>, App2<ReForgetE.Mu<R>, C, D>> dimap(Function<C, A> g, Function<B, D> h) {
         return input -> (App2<ReForgetE.Mu<R>, C, D>)Optics.reForgetE("dimap", e -> {
            Either<A, R> either = e.mapLeft(g);
            B b = ReForgetE.<R, A, B>unbox(input).run(either);
            return (B)h.apply(b);
         });
      }

      @Override
      public <A, B, C> App2<ReForgetE.Mu<R>, Either<A, C>, Either<B, C>> left(App2<ReForgetE.Mu<R>, A, B> input) {
         ReForgetE<R, A, B> reForgetE = ReForgetE.unbox(input);
         return Optics.reForgetE(
            "left",
            e -> e.map(
               e2 -> e2.map(a -> (Either<B, C>)Either.left(reForgetE.run(Either.left((A)a))), Either::right),
               r -> (Either<B, C>)Either.left(reForgetE.run(Either.right((R)r)))
            )
         );
      }

      @Override
      public <A, B, C> App2<ReForgetE.Mu<R>, Either<C, A>, Either<C, B>> right(App2<ReForgetE.Mu<R>, A, B> input) {
         ReForgetE<R, A, B> reForgetE = ReForgetE.unbox(input);
         return Optics.reForgetE(
            "right",
            e -> e.map(e2 -> e2.map(Either::left, a -> Either.right(reForgetE.run(Either.left((A)a)))), r -> Either.right(reForgetE.run(Either.right((R)r))))
         );
      }

      static final class Mu<R> implements Cocartesian.Mu {
      }
   }

   final class Mu<R> implements K2 {
   }
}
