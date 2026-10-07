/* Copyright 2026 The Forbric Project. Licensed under the Apache License, Version 2.0. */
package probe;
import net.fabricmc.api.ClientModInitializer;
import java.lang.reflect.*;import java.nio.file.*;import java.util.*;import java.util.concurrent.atomic.AtomicBoolean;
import org.spongepowered.asm.mixin.Mixins;import org.spongepowered.asm.mixin.extensibility.IMixinConfig;
public class CreateAdaptedProbe implements ClientModInitializer {
 public void onInitializeClient(){Thread t=new Thread(()->{try{ClassLoader cl=getClass().getClassLoader();Class<?> mc=Class.forName("net.minecraft.client.Minecraft",false,cl);Object minecraft=mc.getMethod("getInstance").invoke(null);AtomicBoolean done=new AtomicBoolean();for(int i=0;i<300&&!done.get();i++){mc.getMethod("execute",Runnable.class).invoke(minecraft,(Runnable)()->{try{Object player=mc.getField("player").get(minecraft);if(player==null||mc.getField("level").get(minecraft)==null)return;if(done.getAndSet(true))return;audit(minecraft,cl);}catch(Throwable e){done.set(true);fail(e);}});Thread.sleep(200);}}catch(Throwable e){fail(e);}},"Create injection coverage probe");t.setDaemon(true);t.start();}
 static Object field(Object owner,String name)throws Exception{Field f=owner.getClass().getDeclaredField(name);f.setAccessible(true);return f.get(owner);}
 static void audit(Object minecraft,ClassLoader cl)throws Exception{
  Set<String> targets=new TreeSet<>();
  for(var config:Mixins.getConfigs())if((config.getName().contains("create")||config.getName().contains("flywheel")||config.getName().contains("ponder")||config.getName().contains("catnip")))targets.addAll(config.getConfig().getTargets());
  if(targets.isEmpty()){
   Object transformer=net.forbric.kernel.mixin.ForbricMixinService.getTransformer();Object processor=field(transformer,"processor");
   for(Field f:processor.getClass().getDeclaredFields())if(java.util.Collection.class.isAssignableFrom(f.getType())){f.setAccessible(true);Object values=f.get(processor);if(values instanceof Collection<?> all)for(Object value:all)if(value instanceof IMixinConfig config&&(config.getName().contains("create")||config.getName().contains("flywheel")||config.getName().contains("ponder")||config.getName().contains("catnip")))targets.addAll(config.getTargets());}
  }
  List<String> failures=new ArrayList<>();for(String name:targets)try{Class.forName(name.replace('/','.'),false,cl);}catch(Throwable e){failures.add(name+": "+e);}
  int bindings=checkKeys(minecraft,cl);
  String result="keyBindings="+bindings+"\npassed="+(!targets.isEmpty()&&failures.isEmpty())+"\ntargets="+targets.size()+"\nfailures="+failures+"\n";Files.writeString(Path.of("create-injection-probe.txt"),result);System.out.println("[CreateAdaptedProbe] "+result);
 }
 static int checkKeys(Object minecraft,ClassLoader cl)throws Exception {
  Class<?> keys=Class.forName("com.simibubi.create.AllKeys",true,cl);
  Object options=minecraft.getClass().getField("options").get(minecraft);
  List<Object> registered=Arrays.asList((Object[])options.getClass().getField("keyMappings").get(options));
  int count=0;
  for(Object key:(Object[])keys.getMethod("values").invoke(null)){
   Object binding=keys.getMethod("getKeybind").invoke(key);
   if(binding==null)throw new AssertionError("Uninitialized Create key: "+key);
   keys.getMethod("doesModifierAndCodeMatch",int.class).invoke(key,65);
   keys.getMethod("getBoundKey").invoke(key);
   keys.getMethod("isPressed").invoke(key);
   Field modifiable=keys.getDeclaredField("modifiable");modifiable.setAccessible(true);
   if(modifiable.getBoolean(key)&&!registered.contains(binding))throw new AssertionError("Missing Controls binding: "+key);
   if(modifiable.getBoolean(key))count++;
  }
  Class<?> input=Class.forName("net.minecraft.client.input.KeyEvent",true,cl);
  Class<?> event=Class.forName("net.neoforged.neoforge.client.event.InputEvent$Key",true,cl);
  Object bus=Class.forName("net.neoforged.neoforge.common.NeoForge",true,cl).getField("EVENT_BUS").get(null);
  Method post=Class.forName("net.neoforged.bus.api.IEventBus",true,cl).getMethod("post",Class.forName("net.neoforged.bus.api.Event",false,cl));
  for(int code:new int[]{65,342,341,340})for(int action:new int[]{1,0}){
   Object press=input.getConstructor(int.class,int.class,int.class).newInstance(code,0,0);
   post.invoke(bus,event.getConstructor(input,int.class).newInstance(press,action));
  }
  return count;
 }
 static void fail(Throwable e){e.printStackTrace();try{Files.writeString(Path.of("create-injection-probe.txt"),"passed=false\nerror="+e+"\n");}catch(Exception ignored){}}
}
