package primitiveenderdragon.briefrelease.importantbugfix.mixin;

import java.util.Map;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(LivingEntity.class)
public interface ASFLivingEntityAccessor {

	@Invoker("collectEquipmentChanges")
	Map<EquipmentSlot, ItemStack> invokeCollectEquipmentChanges();
}
