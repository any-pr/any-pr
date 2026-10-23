package fixture.fabricblockstatecodec;

import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import com.mojang.serialization.DataResult;
import com.mojang.serialization.DynamicOps;
import com.mojang.serialization.MapLike;

/** Ops over plain Java maps and lists, the shape a parsed blockstate file has. */
public final class MapOps implements DynamicOps<Object> {
	@Override
	public DataResult<Stream<Object>> getStream(Object input) {
		return input instanceof List<?> list ? DataResult.success(list.stream().map(Object.class::cast)) : DataResult.error("not a list");
	}

	@Override
	public DataResult<MapLike<Object>> getMap(Object input) {
		return input instanceof Map<?, ?> map ? DataResult.success(key -> map.get(key)) : DataResult.error("not a map");
	}
}
