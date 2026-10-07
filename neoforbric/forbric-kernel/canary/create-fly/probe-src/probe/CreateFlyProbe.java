/* Copyright 2026 The Forbric Project. Licensed under the Apache License, Version 2.0. */
package probe;
import net.fabricmc.api.ClientModInitializer;
import java.lang.reflect.*;import java.nio.file.*;import java.util.*;import java.util.concurrent.atomic.AtomicBoolean;
import org.spongepowered.asm.mixin.Mixins;import org.spongepowered.asm.mixin.extensibility.IMixinConfig;
public class CreateFlyProbe implements ClientModInitializer {
 public void onInitializeClient(){Thread t=new Thread(()->{try{ClassLoader cl=getClass().getClassLoader();Class<?> mc=Class.forName("net.minecraft.client.Minecraft",false,cl);Object minecraft=mc.getMethod("getInstance").invoke(null);AtomicBoolean done=new AtomicBoolean();for(int i=0;i<300&&!done.get();i++){mc.getMethod("execute",Runnable.class).invoke(minecraft,(Runnable)()->{try{Object player=mc.getField("player").get(minecraft);if(player==null||mc.getField("level").get(minecraft)==null)return;Field ticks=Class.forName("net.minecraft.world.entity.Entity",false,cl).getField("tickCount");if(ticks.getInt(player)<30)return;if(done.getAndSet(true))return;audit(minecraft,cl);}catch(Throwable e){done.set(true);fail(e);}});Thread.sleep(200);}}catch(Throwable e){fail(e);}},"Create injection coverage probe");t.setDaemon(true);t.start();}
 static Object field(Object owner,String name)throws Exception{Field f=owner.getClass().getDeclaredField(name);f.setAccessible(true);return f.get(owner);}
 static void audit(Object minecraft,ClassLoader cl)throws Exception{
  Set<String> targets=new TreeSet<>();
  for(var config:Mixins.getConfigs())if(config.getName().startsWith("create."))targets.addAll(config.getConfig().getTargets());
  if(targets.isEmpty()){
   Object transformer=net.forbric.kernel.mixin.ForbricMixinService.getTransformer();Object processor=field(transformer,"processor");
   for(Field f:processor.getClass().getDeclaredFields())if(java.util.Collection.class.isAssignableFrom(f.getType())){f.setAccessible(true);Object values=f.get(processor);if(values instanceof Collection<?> all)for(Object value:all)if(value instanceof IMixinConfig config&&config.getName().startsWith("create."))targets.addAll(config.getTargets());}
  }
  List<String> failures=new ArrayList<>();for(String name:targets)try{Class.forName(name.replace('/','.'),false,cl);}catch(Throwable e){failures.add(name+": "+e);}
  Object game=minecraft.getClass().getField("gameRenderer").get(minecraft);Object gui=field(game,"guiRenderer");Map<?,?> renderers=(Map<?,?>)field(gui,"pictureInPictureRenderers");int custom=0;List<String> names=new ArrayList<>();for(var row:renderers.entrySet())if(row.getKey()instanceof Class<?> type&&type.getName().startsWith("com.zurrtum.create.")){custom++;names.add(type.getName()+"="+row.getValue().getClass().getName());}
  Collections.sort(names);String result="passed="+(!targets.isEmpty()&&failures.isEmpty()&&custom==21)+"\ntargets="+targets.size()+"\ncustomRenderers="+custom+"\nfailures="+failures+"\n"+String.join("\n",names)+"\n";Files.writeString(Path.of("create-injection-probe.txt"),result);System.out.println("[CreateProbe] "+result);
 }
 static void fail(Throwable e){e.printStackTrace();try{Files.writeString(Path.of("create-injection-probe.txt"),"passed=false\nerror="+e+"\n");}catch(Exception ignored){}}
}
