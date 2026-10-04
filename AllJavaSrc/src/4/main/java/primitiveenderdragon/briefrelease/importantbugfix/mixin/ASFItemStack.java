package primitiveenderdragon.briefrelease.importantbugfix.mixin;

import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import java.util.Objects;

@Mixin(ItemStack.class)
public class ASFItemStack {

    /**
     * @author 判断物品类型只看物品名字，导致附魔不同的同名物品被认为是相同
     * @reason 某些代码误用此段，而非isSameItemSameComponents
     */
    @Overwrite
    public static boolean isSameItem(final ItemStack a, final ItemStack b) {
        if (!a.is(b.getItem())) {
            return false;
        } else {
            return a.isEmpty() && b.isEmpty() || Objects.equals(a.getEnchantments(), b.getEnchantments());
        }
    }

    /**
     * @author 该段代码内容未修改，但简化了语句
     * @reason 现在同一个物品不但名称，而且耐久、附魔等组件也要完全相同
     */
    @Overwrite
    public static boolean isSameItemSameComponents(final ItemStack a, final ItemStack b) {
        if (!a.is(b.getItem())) {
            return false;
        } else {
            return a.isEmpty() && b.isEmpty() || Objects.equals(a.getComponents(), b.getComponents());
        }
    }
}
