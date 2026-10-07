package fixture.overloadpin;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.List;
import java.util.Set;
import java.util.function.BiFunction;

import org.objectweb.asm.tree.ClassNode;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;

/**
 * Stands in for the one line of KernelBoot the weave harness does not run: installing the pre-Mixin transform chain
 * on the game loader. Here the chain is the production DuplicateLambdaPruneInjector alone, so the merged-looking
 * {@link SkyPass} is pruned — and its dropped shape recorded — exactly as a real boot would before any guest mixin is
 * read. MixinOverloadPin's only evidence is that record; without this it can never say anything.
 *
 * <p>Reflection, because a fixture is compiled against Mixin and ASM only. Mixin calls {@link #onLoad} while it
 * selects this config, which is before it reads a single mixin class through the kernel's service.
 */
public final class MergePrunePlugin implements IMixinConfigPlugin {
	@Override
	public void onLoad(String mixinPackage) {
		try {
			ClassLoader game = MergePrunePlugin.class.getClassLoader();
			Class<?> pruner = Class.forName("net.forbric.kernel.transform.DuplicateLambdaPruneInjector", true, game);
			Class<?> contextType = Class.forName("net.forbric.kernel.transform.TransformContext", true, game);
			Class<?> envType = Class.forName("net.fabricmc.api.EnvType", true, game);
			Constructor<?> newContext = contextType.getConstructor(envType, boolean.class, String.class);
			// KernelBoot's own context for a server boot.
			Object context = newContext.newInstance(enumConstant(envType, "SERVER"), false, "named");
			Object instance = pruner.getConstructor().newInstance();
			Method transform = pruner.getMethod("transform", String.class, byte[].class, contextType);

			BiFunction<String, byte[], byte[]> chain = (name, bytes) -> {
				try {
					return (byte[]) transform.invoke(instance, name, bytes, context);
				} catch (IllegalAccessException | InvocationTargetException e) {
					throw new IllegalStateException(e);
				}
			};
			game.getClass().getMethod("setTransformer", BiFunction.class).invoke(game, chain);
			System.out.println("[OverloadPinFixture] pre-Mixin chain installed: DuplicateLambdaPruneInjector");
		} catch (ReflectiveOperationException e) {
			throw new IllegalStateException("could not install the pre-Mixin chain", e);
		}
	}

	private static Object enumConstant(Class<?> type, String name) {
		for (Object constant : type.getEnumConstants()) {
			if (((Enum<?>) constant).name().equals(name)) return constant;
		}
		throw new IllegalStateException(type + " has no " + name);
	}

	@Override
	public String getRefMapperConfig() {
		return null;
	}

	@Override
	public boolean shouldApplyMixin(String targetClassName, String mixinClassName) {
		return true;
	}

	@Override
	public void acceptTargets(Set<String> myTargets, Set<String> otherTargets) {
	}

	@Override
	public List<String> getMixins() {
		return null;
	}

	@Override
	public void preApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {
	}

	@Override
	public void postApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {
	}
}
