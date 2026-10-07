package probe;

import java.lang.reflect.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePiecesBuilder;
import net.minecraft.world.level.levelgen.structure.structures.NetherFortressPieces;
import net.minecraft.world.level.levelgen.structure.structures.OceanMonumentPieces;
import net.neoforged.fml.common.Mod;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.client.event.ViewportEvent;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import nl.enjarai.doabarrelroll.api.RollEntity;
import nl.enjarai.doabarrelroll.api.RollCamera;
import org.joml.Quaternionf;
import org.joml.Matrix4f;

@Mod("c2merollprobe")
public final class Probe {
	private static final AtomicBoolean started=new AtomicBoolean();
	private static final AtomicInteger cameraSamples=new AtomicInteger();
	private static volatile boolean forced;
	public Probe(IEventBus bus) {
		NeoForge.EVENT_BUS.addListener(ServerStartedEvent.class,event -> {
			if(started.compareAndSet(false,true)) {Thread t=new Thread(Probe::structures,"c2me-structure-probe");t.setDaemon(true);t.start();}
		});
		NeoForge.EVENT_BUS.addListener(ViewportEvent.ComputeCameraAngles.class,event -> {
			if(event.getCamera().entity() instanceof RollEntity entity && entity.doABarrelRoll$isRolling()) event.setRoll(15f);
		});
	}
	public static void tick() {
		Minecraft mc=Minecraft.getInstance();
		if(mc.player!=null && mc.player.isFallFlying() && ((RollEntity)mc.player).doABarrelRoll$isRolling()) {
			((RollEntity)mc.player).doABarrelRoll$setRoll(45f);forced=true;
		}
	}
	public static void camera(Camera camera,CameraRenderState state,float partial) {
		if(!forced || camera.entity()!=Minecraft.getInstance().player || !((RollEntity)camera.entity()).doABarrelRoll$isRolling())return;
		float roll=((RollCamera)camera).doABarrelRoll$getRoll();
		// What DABR's ordinary-view hook stores: the entity's roll at this frame's partial tick, negated. On the first
		// frames of a flight that interpolates from 0, so those say nothing either way and are skipped.
		float entityRoll=-((RollEntity)camera.entity()).doABarrelRoll$getRoll(partial);
		if(Math.abs(entityRoll)<1f)return;
		if(Math.abs(roll)<1f)throw new AssertionError("DABR camera roll remains zero while rolling (entity roll "+entityRoll+")");
		if(Math.abs(roll-entityRoll)>0.01f)throw new AssertionError("DABR camera roll "+roll+" is not the entity roll "+entityRoll);
		if(Math.abs(camera.getRoll()-15f)>0.001f)throw new AssertionError("carrier camera event roll was overwritten: "+camera.getRoll());
		float z=-(15f*((float)Math.PI/180f))+(float)(roll*(Math.PI/180d));
		Quaternionf expected=new Quaternionf().rotationYXZ((float)Math.PI-state.yRot*((float)Math.PI/180f),-state.xRot*((float)Math.PI/180f),z);
		if(Math.abs(expected.dot(state.orientation))<0.99999f)throw new AssertionError("render orientation did not include both rolls "+state.orientation+" expected "+expected);
		Matrix4f expectedView=new Matrix4f().rotation(expected).transpose();
		if(!expectedView.equals(state.viewRotationMatrix,0.0001f))throw new AssertionError("render view matrix lost roll");
		int samples=cameraSamples.incrementAndGet();
		if(samples==10)System.out.println("[C2MERollProbe] CAMERA_PASS samples="+samples+" dabrRoll="+roll+" carrierRoll="+camera.getRoll()+" orientation="+state.orientation);
	}
	private static void structures() {
		ExecutorService pool=Executors.newFixedThreadPool(8);
		try {
			Class<?> room=Class.forName("net.minecraft.world.level.levelgen.structure.structures.OceanMonumentPieces$RoomDefinition");
			for(String name:List.of("claimed","isSource","scanIndex"))if(!Modifier.isVolatile(room.getDeclaredField(name).getModifiers()))throw new AssertionError("C2ME volatile guard missing: "+name);
			List<Future<Integer>> tasks=new ArrayList<>();
			for(int index=0;index<64;index++) {final int seed=index;tasks.add(pool.submit(() -> {
				RandomSource random=RandomSource.create(8035262L+seed);
				NetherFortressPieces.StartPiece start=new NetherFortressPieces.StartPiece(random,seed*512,seed*512);
				for(String name:List.of("availableBridgePieces","availableCastlePieces")) {Field field=start.getClass().getDeclaredField(name);field.setAccessible(true);if(!field.get(start).getClass().getName().contains("Synchronized"))throw new AssertionError("C2ME synchronized list missing: "+name);}
				StructurePiecesBuilder pieces=new StructurePiecesBuilder();pieces.addPiece(start);start.addChildren(start,pieces,random);int count=0;
				while(!start.pendingChildren.isEmpty()) {if(++count>10000)throw new AssertionError("fortress generation did not terminate");int n=random.nextInt(start.pendingChildren.size());start.pendingChildren.remove(n).addChildren(start,pieces,random);}
				new OceanMonumentPieces.MonumentBuilding(RandomSource.create(9035262L+seed),seed*128,seed*128,Direction.NORTH);
				return pieces.build().pieces().size();
			}));}
			int pieces=0;for(Future<Integer> task:tasks)pieces+=task.get(60,TimeUnit.SECONDS);
			System.out.println("[C2MERollProbe] STRUCTURES_PASS fortresses=64 monuments=64 workers=8 pieces="+pieces);
		} catch(Throwable error) {error.printStackTrace();System.out.println("[C2MERollProbe] STRUCTURES_FAIL "+error);}
		finally {pool.shutdownNow();}
	}
}
