package net.minecraft.client.renderer.block.dispatch;

import java.util.function.Function;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;

import net.neoforged.neoforge.client.model.block.BlockStateModelHooks;

/**
 * Hand-written stand-in, not game code: the merged {@code BlockStateModel.Unbaked}, whose two codec fields are built by
 * two {@code flatComapMap} calls on codecs NeoForge's hooks supply.
 */
public interface BlockStateModel {
	interface Unbaked {
		Codec<String> HARDCODED_WEIGHTED_CODEC = BlockStateModelHooks.weightedCodec().flatComapMap(Function.identity(), DataResult::success);
		Codec<String> CODEC = BlockStateModelHooks.modelCodec().flatComapMap(Function.identity(), DataResult::success);
	}
}
