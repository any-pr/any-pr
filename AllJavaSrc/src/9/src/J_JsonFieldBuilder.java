package net.minecraft.src;

final class J_JsonFieldBuilder {
	private J_JsonNodeBuilder field_27306_a;
	private J_JsonNodeBuilder field_27305_b;

	static J_JsonFieldBuilder aJsonFieldBuilder() {
		return new J_JsonFieldBuilder();
	}

	J_JsonFieldBuilder func_27304_a(J_JsonNodeBuilder var1) {
		this.field_27306_a = var1;
		return this;
	}

	J_JsonFieldBuilder func_27300_b(J_JsonNodeBuilder var1) {
		this.field_27305_b = var1;
		return this;
	}

	J_JsonStringNode func_27303_b() {
		return (J_JsonStringNode)this.field_27306_a.buildNode();
	}

	J_JsonNode func_27302_c() {
		return this.field_27305_b.buildNode();
	}
}
