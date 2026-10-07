package net.forbric.kernel.mixin;
import static org.junit.jupiter.api.Assertions.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.objectweb.asm.tree.*;

class OptionalMixinDependenciesTest {
	private ClassNode mixin(){
		ClassNode node=new ClassNode();node.name="net/diebuddies/mixins/immediatelyfast/MixinSignText";
		MethodNode method=new MethodNode();method.name="physicsmod$fixSignTextAfterReload";AnnotationNode inject=new AnnotationNode("Lorg/spongepowered/asm/mixin/injection/Inject;");inject.values=new ArrayList<>(List.of("method",List.of("immediatelyFast$shouldCache")));method.visibleAnnotations=new ArrayList<>(List.of(inject));node.methods.add(method);return node;
	}
	@Test void absentCompatPartnerIsNotARequiredFeatureFailure(){assertEquals("immediatelyfast",OptionalMixinDependencies.absent(mixin(),id->false));}
	@Test void installedPartnerAndAdditionalFeaturesStillReportRealFailures(){
		assertNull(OptionalMixinDependencies.absent(mixin(),id->true));
		ClassNode changed=mixin();changed.methods.add(changed.methods.getFirst());assertNull(OptionalMixinDependencies.absent(changed,id->false));
		changed=mixin();changed.name="some/OtherMixin";assertNull(OptionalMixinDependencies.absent(changed,id->false));
	}
}
