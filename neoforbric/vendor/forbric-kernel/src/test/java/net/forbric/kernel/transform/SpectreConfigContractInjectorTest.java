package net.forbric.kernel.transform;

import static org.junit.jupiter.api.Assertions.*;
import java.util.List;
import net.forbric.api.*;
import net.forbric.kernel.interop.SpectreConfigInitializer;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.parallel.ResourceLock;
import org.objectweb.asm.*;

@ExecutesInjector(SpectreConfigContractInjector.class)
@ResourceLock("mod-presence")
class SpectreConfigContractInjectorTest {
    @AfterEach void clear() { ModPresence.publishForgeFamily(List.of()); ModPresence.publishFabric(List.of()); }
    private void owner(Ecosystem ecosystem) {
        var mod=new DiscoveredMod(ecosystem,"spectrelib","0.22.0","SpectreLib",List.of(),List.of(),null,"spectrelib.jar");
        if(ecosystem==Ecosystem.FABRIC)ModPresence.publishFabric(List.of(mod));else ModPresence.publishForgeFamily(List.of(mod));
    }
    @Test void missingFabricContractIsReplacedByExecutableEquivalentOnlyForNeoOwner() throws Exception {
        ClassWriter w=new ClassWriter(0);String name="fixture/ConfigEntry";
        w.visit(Opcodes.V17,Opcodes.ACC_PUBLIC,name,null,"java/lang/Object",new String[]{SpectreConfigContractInjector.ORIGINAL});
        w.visitField(Opcodes.ACC_PUBLIC,"calls","I",null,null).visitEnd();
        var init=w.visitMethod(Opcodes.ACC_PUBLIC,"<init>","()V",null,null);init.visitCode();init.visitVarInsn(Opcodes.ALOAD,0);init.visitMethodInsn(Opcodes.INVOKESPECIAL,"java/lang/Object","<init>","()V",false);init.visitInsn(Opcodes.RETURN);init.visitMaxs(1,1);init.visitEnd();
        var m=w.visitMethod(Opcodes.ACC_PUBLIC,"onInitializeConfig","()V",null,null);m.visitCode();m.visitVarInsn(Opcodes.ALOAD,0);m.visitInsn(Opcodes.DUP);m.visitFieldInsn(Opcodes.GETFIELD,name,"calls","I");m.visitInsn(Opcodes.ICONST_1);m.visitInsn(Opcodes.IADD);m.visitFieldInsn(Opcodes.PUTFIELD,name,"calls","I");m.visitInsn(Opcodes.RETURN);m.visitMaxs(3,1);m.visitEnd();w.visitEnd();
        byte[] original=w.toByteArray();var injector=new SpectreConfigContractInjector();assertSame(original,injector.transform(name,original,null));
        owner(Ecosystem.FABRIC);assertSame(original,injector.transform(name,original,null));clear();owner(Ecosystem.NEOFORGE);
        byte[] repaired=injector.transform(name,original,null);assertNotSame(original,repaired);assertSame(repaired,injector.transform(name,repaired,null));
        class Loader extends ClassLoader { Class<?> define(byte[] b){return defineClass("fixture.ConfigEntry",b,0,b.length);} }
        Class<?> entry=new Loader().define(repaired);Object instance=entry.getConstructor().newInstance();
        ((SpectreConfigInitializer)instance).onInitializeConfig();assertEquals(1,entry.getField("calls").get(instance));
    }
}
