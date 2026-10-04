package net.minecraft.client.model.object.statue;

import net.minecraft.client.model.Model;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.core.Direction;

public class CopperGolemStatueModel extends Model<Direction> {
   public CopperGolemStatueModel(final ModelPart root) {
      super(root, RenderTypes::entityCutoutNoCull);
   }

   public void setupAnim(final Direction direction) {
      this.root.y = 0.0F;
      this.root.yRot = direction.getOpposite().toYRot() * (float) (Math.PI / 180.0);
      this.root.zRot = (float) Math.PI;
   }
}
