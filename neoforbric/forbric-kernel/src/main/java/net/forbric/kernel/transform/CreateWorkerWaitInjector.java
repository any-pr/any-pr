/* Copyright 2026 The Forbric Project. Licensed under the Apache License, Version 2.0. */
package net.forbric.kernel.transform;

import org.objectweb.asm.*;
import org.objectweb.asm.tree.*;

/** Preserve native task execution while making wait/notify atomic with its running/queued-work predicates. */
public final class CreateWorkerWaitInjector implements ClassTransformer {
	public static final String TARGET="com.zurrtum.create.client.flywheel.impl.task.ParallelTaskExecutor$WorkerThread";
	private static final String POOL="com/zurrtum/create/client/flywheel/impl/task/ParallelTaskExecutor";
	@Override public AnchorSet anchors(){return AnchorSet.of(new AnchorSet.Anchor(TARGET,AnchorSet.Severity.REQUIRED,
			"a Flywheel worker can miss a task or shutdown notification and wait forever"));}
	@Override public byte[] transform(String name,byte[] bytes,TransformContext context){
		if(!TARGET.equals(name))return bytes;
		ClassNode node=new ClassNode();new ClassReader(bytes).accept(node,0);
		if(node.fields.stream().noneMatch(f->f.name.equals("this$0")&&f.desc.equals("L"+POOL+";")))return bytes;
		MethodNode method=node.methods.stream().filter(m->m.name.equals("spinThenWait")&&m.desc.equals("()V")).findFirst().orElse(null);
		if(method==null)return bytes;
		java.util.List<MethodInsnNode> calls=new java.util.ArrayList<>();
		for(var instruction:method.instructions)if(instruction instanceof MethodInsnNode call&&call.getOpcode()==Opcodes.INVOKEVIRTUAL
				&&call.owner.equals("com/zurrtum/create/client/flywheel/impl/task/ThreadGroupNotifier")&&call.name.equals("awaitNotification")&&call.desc.equals("()V"))calls.add(call);
		if(calls.size()!=1)return bytes;
		MethodInsnNode call=calls.getFirst();InsnList receiver=new InsnList();receiver.add(new VarInsnNode(Opcodes.ALOAD,0));
		receiver.add(new FieldInsnNode(Opcodes.GETFIELD,node.name,"this$0","L"+POOL+";"));method.instructions.insertBefore(call,receiver);
		call.setOpcode(Opcodes.INVOKESTATIC);call.owner="net/forbric/kernel/interop/CreateTaskWait";call.name="awaitNotification";
		call.desc="(Ljava/lang/Object;Ljava/lang/Object;)V";call.itf=false;
		ClassWriter writer=new ClassWriter(ClassWriter.COMPUTE_MAXS);node.accept(writer);return writer.toByteArray();
	}
}
