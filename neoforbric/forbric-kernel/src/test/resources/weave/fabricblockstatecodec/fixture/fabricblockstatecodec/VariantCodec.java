package fixture.fabricblockstatecodec;

import com.mojang.datafixers.util.Pair;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.DynamicOps;
import com.mojang.serialization.MapLike;

/**
 * One ecosystem's reading of a blockstate variant: a custom model when the variant names this ecosystem's key, else
 * a plain variant of its {@code model} — empty when it has none, which is how the other ecosystem's custom model reads.
 */
public final class VariantCodec implements Codec<String> {
	private final String reader;
	private final String customKey;

	public VariantCodec(String reader, String customKey) {
		this.reader = reader;
		this.customKey = customKey;
	}

	@Override
	public <T> DataResult<Pair<String, T>> decode(DynamicOps<T> ops, T input) {
		MapLike<T> variant = ops.getMap(input).result().orElse(null);
		if (variant == null) return DataResult.error("not a variant");
		T custom = variant.get(customKey);
		T model = variant.get("model");
		String read = custom != null ? "custom " + custom : "plain " + (model == null ? "(empty)" : model);
		return DataResult.success(Pair.of(reader + ":" + read, input));
	}

	@Override
	public <T> DataResult<T> encode(String input, DynamicOps<T> ops, T prefix) {
		return DataResult.error("not encoded in this fixture");
	}
}
