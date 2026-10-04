package com.mojang.blocklist;

import java.util.function.Predicate;
import javax.annotation.Nullable;

public interface BlockListSupplier {
   @Nullable
   Predicate<String> createBlockList();
}
