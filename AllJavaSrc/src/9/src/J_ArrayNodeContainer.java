package net.minecraft.src;

class J_ArrayNodeContainer implements J_NodeContainer {
	final J_JsonArrayNodeBuilder nodeBuilder;
	final J_JsonListenerToJdomAdapter field_27293_b;

	J_ArrayNodeContainer(J_JsonListenerToJdomAdapter var1, J_JsonArrayNodeBuilder var2) {
		this.field_27293_b = var1;
		this.nodeBuilder = var2;
	}

	public void func_27290_a(J_JsonNodeBuilder var1) {
		this.nodeBuilder.withElement(var1);
	}

	public void func_27289_a(J_JsonFieldBuilder var1) {
		throw new RuntimeException("Coding failure in Argo:  Attempt to add a field to an array.");
	}
}
