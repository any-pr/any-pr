package com.mojang.serialization;

/** The subtype: inherits parse WITHOUT redeclaring it, the precondition MixinSubtypeOwnerRetarget.SAME_METHOD records. */
public interface Codec<A> extends Decoder<A> {
}
