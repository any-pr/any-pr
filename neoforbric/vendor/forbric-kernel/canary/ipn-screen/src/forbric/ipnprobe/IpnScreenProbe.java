package forbric.ipnprobe;

import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import com.google.gson.GsonBuilder;
import net.minecraft.client.Minecraft;
import net.minecraft.client.KeyboardHandler;
import net.minecraft.client.MouseHandler;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonInfo;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.client.event.ClientTickEvent;

/** Exercises the installed IPN through real screen initialization and KeyboardHandler, without calling IPN's actions. */
@Mod("forbric_ipn_probe")
public final class IpnScreenProbe {
 private int ticks;
 private boolean failed, finished;
 private List<String> before;
 private final Map<String,Object> result = new LinkedHashMap<>();
 public IpnScreenProbe() {
  NeoForge.EVENT_BUS.addListener(ClientTickEvent.Post.class, e -> tick(Minecraft.getInstance()));
 }
 private void tick(Minecraft mc) {
  if (mc.player == null || mc.level == null || finished) return;
  ticks++;
  try {
   if (ticks == 30) {
    var server = Objects.requireNonNull(mc.getSingleplayerServer());
    var uuid = mc.player.getUUID();
    server.execute(() -> {
     var player = Objects.requireNonNull(server.getPlayerList().getPlayer(uuid));
     player.setGameMode(GameType.SURVIVAL);
     Inventory inv = player.getInventory();
     for (int i=0;i<36;i++) inv.setItem(i,ItemStack.EMPTY);
     inv.setItem(9,new ItemStack(Items.DIAMOND,3));
     inv.setItem(10,new ItemStack(Items.COBBLESTONE,5));
     inv.setItem(11,new ItemStack(Items.APPLE,2));
     inv.setItem(12,new ItemStack(Items.DIAMOND,4));
     player.inventoryMenu.broadcastChanges();
    });
   }
   if (ticks == 70) mc.gui.setScreen(new InventoryScreen(mc.player));
   if (ticks == 90) {
    before = snapshot(mc.player.getInventory());
    result.put("before", before);
    Object handler = singleton("org.anti_ad.mc.ipnext.gui.inject.ContainerScreenEventHandler");
    Object widgets = handler.getClass().getMethod("getCurrentWidgets").invoke(handler);
    List<String> tree = new ArrayList<>();
    if (widgets instanceof List<?> list) for(Object w:list) tree(w,tree,0);
    result.put("widgets",tree);
    result.put("buttonsPresent",tree.stream().anyMatch(s -> s.contains("SortingButtonCollectionWidget")));
    result.put("screen",mc.gui.screen().getClass().getName());
    log("buttons="+result.get("buttonsPresent")+" before="+before+" widgets="+tree);
    Screenshot.grab(mc,false);
   }
   if(ticks == 100) key(mc,1);
   if(ticks == 101) key(mc,0);
   if(ticks == 180) {
    var after=snapshot(mc.player.getInventory());
    result.put("after",after);
    result.put("rSorted",!before.equals(after) && totals(before).equals(totals(after))
      && after.stream().filter(s -> s.contains("minecraft:diamond=")).count()==1);
    log("R sorted="+result.get("rSorted")+" after="+after);
    var server=mc.getSingleplayerServer(); var uuid=mc.player.getUUID();
    result.put("serverAfter",server.submit(() -> snapshot(server.getPlayerList().getPlayer(uuid).getInventory())).join());
    Screenshot.grab(mc,false);
   }
   if(ticks == 200) {
    var server=mc.getSingleplayerServer();var uuid=mc.player.getUUID();
    server.execute(() -> {
     var player=server.getPlayerList().getPlayer(uuid);var inv=player.getInventory();
     for(int i=9;i<36;i++)inv.setItem(i,ItemStack.EMPTY);
     inv.setItem(9,new ItemStack(Items.DIAMOND,3));inv.setItem(10,new ItemStack(Items.COBBLESTONE,5));
     inv.setItem(11,new ItemStack(Items.APPLE,2));inv.setItem(12,new ItemStack(Items.DIAMOND,4));
     player.inventoryMenu.broadcastChanges();
    });
   }
   if(ticks == 230) result.put("buttonBefore",snapshot(mc.player.getInventory()));
   if(ticks == 240) hoverSortButton(mc);
   if(ticks == 250) mouse(mc,1);
   if(ticks == 251) mouse(mc,0);
   if(ticks == 300) {
    var after=snapshot(mc.player.getInventory());result.put("buttonAfter",after);
    result.put("buttonSorted",after.equals(result.get("after")) && !after.equals(result.get("buttonBefore")));
    var server=mc.getSingleplayerServer();var uuid=mc.player.getUUID();
    result.put("serverButtonAfter",server.submit(() -> snapshot(server.getPlayerList().getPlayer(uuid).getInventory())).join());
    log("Button sorted="+result.get("buttonSorted")+" after="+after);Screenshot.grab(mc,false);
   }
   if(ticks == 320) {
    result.put("pass",Boolean.TRUE.equals(result.get("buttonsPresent")) && Boolean.TRUE.equals(result.get("rSorted"))
      && Objects.equals(result.get("after"),result.get("serverAfter")) && Boolean.TRUE.equals(result.get("buttonSorted"))
      && Objects.equals(result.get("buttonAfter"),result.get("serverButtonAfter")));
    finish();
    mc.gui.setScreen(null);
   }
  } catch(Throwable e) {
   if(!failed) { failed=true; result.put("error",e.toString()); result.put("pass",false); e.printStackTrace(); finish(); }
  }
 }
 private void finish() {
  finished=true;
  try { Files.writeString(Path.of("ipn-screen-result.json"),new GsonBuilder().setPrettyPrinting().create().toJson(result)); }
  catch(Exception e) { throw new RuntimeException(e); }
  log("RESULT "+result.get("pass"));
 }
 private static void key(Minecraft mc,int action) throws Exception {
  Method m=KeyboardHandler.class.getDeclaredMethod("keyPress",long.class,int.class,KeyEvent.class);
  m.setAccessible(true);
  m.invoke(mc.keyboardHandler,mc.getWindow().handle(),action,new KeyEvent(82,0,0));
  log("KeyboardHandler R action="+action);
 }
 private static Object singleton(String name) throws Exception {
  return Class.forName(name).getField("INSTANCE").get(null);
 }
 private static Object sortButton(Object widget)throws Exception {
  Class<?> c=widget.getClass();
  if(c.getName().endsWith("$SortButton") && Boolean.TRUE.equals(c.getMethod("getVisible").invoke(widget)))return widget;
  for(Object child:(List<?>)c.getMethod("getChildren").invoke(widget)) {
   Object found=sortButton(child);if(found!=null)return found;
  }
  return null;
 }
 private void hoverSortButton(Minecraft mc)throws Exception {
  Object handler=singleton("org.anti_ad.mc.ipnext.gui.inject.ContainerScreenEventHandler");
  Object widgets=handler.getClass().getMethod("getCurrentWidgets").invoke(handler);
  Object button=null;
  if(widgets instanceof List<?> list)for(Object widget:list){button=sortButton(widget);if(button!=null)break;}
  if(button==null){result.put("buttonMissing",true);return;}
  Class<?> c=button.getClass();
  double x=((Number)c.getMethod("getScreenX").invoke(button)).doubleValue()+((Number)c.getMethod("getWidth").invoke(button)).doubleValue()/2;
  double y=((Number)c.getMethod("getScreenY").invoke(button)).doubleValue()+((Number)c.getMethod("getHeight").invoke(button)).doubleValue()/2;
  var window=mc.getWindow();
  Method move=MouseHandler.class.getDeclaredMethod("onMove",long.class,double.class,double.class);move.setAccessible(true);
  move.invoke(mc.mouseHandler,window.handle(),x*window.getScreenWidth()/window.getGuiScaledWidth(),y*window.getScreenHeight()/window.getGuiScaledHeight());
  log("Hover sort button at gui "+x+","+y);
 }
 private void mouse(Minecraft mc,int action)throws Exception {
  if(Boolean.TRUE.equals(result.get("buttonMissing")))return;
  Method button=MouseHandler.class.getDeclaredMethod("onButton",long.class,MouseButtonInfo.class,int.class);button.setAccessible(true);
  button.invoke(mc.mouseHandler,mc.getWindow().handle(),new MouseButtonInfo(0,0),action);
  log("MouseHandler sort-button action="+action);
 }
 private static void tree(Object w,List<String> out,int depth)throws Exception {
  if(depth>8)return;
  Class<?> c=w.getClass();
  out.add(c.getSimpleName()+" text="+c.getMethod("getText").invoke(w)+" x="+c.getMethod("getScreenX").invoke(w)
    +" y="+c.getMethod("getScreenY").invoke(w)+" visible="+c.getMethod("getVisible").invoke(w));
  for(Object child:(List<?>)c.getMethod("getChildren").invoke(w)) tree(child,out,depth+1);
 }
 private static List<String> snapshot(Inventory inv) {
  List<String> out=new ArrayList<>();
  for(int i=9;i<36;i++) { ItemStack s=inv.getItem(i); out.add(i+":"+BuiltInRegistries.ITEM.getKey(s.getItem())+"="+s.getCount()); }
  return out;
 }
 private static Map<String,Integer> totals(List<String> slots) {
  Map<String,Integer> out=new TreeMap<>();
  for(String s:slots) {String[] parts=s.substring(s.indexOf(':')+1).split("=");if(Integer.parseInt(parts[1])>0)out.merge(parts[0],Integer.parseInt(parts[1]),Integer::sum);}
  return out;
 }
 private static void log(String s){System.out.println("[IPNProbe] "+s);}
}
