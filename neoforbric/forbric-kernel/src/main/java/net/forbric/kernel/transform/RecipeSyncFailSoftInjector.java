/*
 * Copyright 2026 The Forbric Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package net.forbric.kernel.transform;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.InsnList;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.VarInsnNode;

import net.forbric.kernel.util.ForbricLog;

/**
 * Lets NeoForge's recipe sync leave out a recipe its own serializer cannot encode, instead of disconnecting the player.
 *
 * <p>NeoForge sends recipes to clients by TYPE, when a mod asks for that type in {@code OnDatapackSyncEvent}: Sophisticated
 * Core asks for crafting and stonecutting, so every crafting recipe of every mod goes through its serializer's stream
 * codec. Enchant Craft's serializer is {@code StreamCodec.unit(new ApplyEnchantRecipe())} beside
 * {@code MapCodec.unit(ApplyEnchantRecipe::new)}: the loaded recipe is a different object from the codec's, the unit
 * codec refuses it ("Can't encode ... expected ..."), the payload fails to encode and the integrated server drops the
 * player on join. That is an Enchant Craft bug, and NeoForge alone kicks the same way — but on Forbric the synced set
 * also carries Fabric and MinecraftForge mods' recipes, whose authors never had a reason to keep that codec working,
 * so one unencodable recipe anywhere is far likelier to lock everyone out.
 *
 * <p>So {@code CommonHooks.sendRecipes} passes the payload it built through {@code KernelRecipeSync.encodable} before
 * sending it: each recipe is encoded once, one that throws is left out with a warning naming it, and the rest go. The
 * filtering happens before the payload exists, so its count and entries always agree. Applied only where
 * {@code sendRecipes} makes exactly one {@code RecipeContentPayload.create} call. {@code -Dforbric.recipeSyncFailSoft=off}.
 */
public final class RecipeSyncFailSoftInjector implements ClassTransformer {
	public static final String PROPERTY = "forbric.recipeSyncFailSoft";
	static final String COMMON_HOOKS = "net.neoforged.neoforge.common.CommonHooks";
	static final String PAYLOAD = "net/neoforged/neoforge/network/payload/RecipeContentPayload";
	static final String PLAYER = "net/minecraft/server/level/ServerPlayer";
	static final String SEND_DESC = "(L" + PLAYER + ";Ljava/util/Set;Lnet/minecraft/world/item/crafting/RecipeMap;)V";
	static final String HELPER = "net/forbric/kernel/runtime/KernelRecipeSync";
	static final String HELPER_DESC = "(L" + PAYLOAD + ";L" + PLAYER + ";)L" + PAYLOAD + ";";

	public static boolean enabled() {
		return !"off".equalsIgnoreCase(System.getProperty(PROPERTY, "on"));
	}

	@Override
	public String name() {
		return "forbric-recipe-sync-fail-soft";
	}

	@Override
	public AnchorSet anchors() {
		if (!enabled()) return AnchorSet.scanned("switched off by -D" + PROPERTY);
		return AnchorSet.of(new AnchorSet.Anchor(COMMON_HOOKS, AnchorSet.Severity.REQUIRED,
				"one recipe whose serializer cannot network-encode it disconnects every player as they join, once any mod "
						+ "asks NeoForge to sync that recipe type"));
	}

	@Override
	public byte[] transform(String className, byte[] bytes, TransformContext context) {
		if (!enabled() || bytes == null || bytes.length == 0 || !COMMON_HOOKS.equals(className)) return bytes;
		ClassNode node = new ClassNode();
		new ClassReader(bytes).accept(node, 0);
		if (!repair(node)) return bytes;
		ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_MAXS);
		node.accept(writer);
		ForbricLog.info("[Forbric/RecipeSync] NeoForge's recipe sync leaves out a recipe its own serializer cannot encode, "
				+ "with a warning naming it, instead of disconnecting the player who joins");
		return writer.toByteArray();
	}

	static boolean repair(ClassNode hooks) {
		for (MethodNode method : hooks.methods) {
			if (!method.name.equals("sendRecipes") || !method.desc.equals(SEND_DESC)) continue;
			MethodInsnNode create = null;
			for (AbstractInsnNode insn : method.instructions) {
				if (!(insn instanceof MethodInsnNode call)) continue;
				if (call.owner.equals(HELPER)) return false;   // already applied
				if (call.getOpcode() == Opcodes.INVOKESTATIC && call.owner.equals(PAYLOAD) && call.name.equals("create")) {
					if (create != null) return false;
					create = call;
				}
			}
			if (create == null || !create.desc.endsWith(")L" + PAYLOAD + ";")) return false;
			InsnList filter = new InsnList();
			filter.add(new VarInsnNode(Opcodes.ALOAD, 0));
			filter.add(new MethodInsnNode(Opcodes.INVOKESTATIC, HELPER, "encodable", HELPER_DESC, false));
			method.instructions.insert(create, filter);
			return true;
		}
		return false;
	}
}
