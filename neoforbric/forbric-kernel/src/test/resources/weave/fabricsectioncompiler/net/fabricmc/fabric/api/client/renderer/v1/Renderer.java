package net.fabricmc.fabric.api.client.renderer.v1;

import java.util.function.Consumer;

import net.fabricmc.fabric.api.client.renderer.v1.mesh.QuadEmitter;
import net.fabricmc.fabric.api.client.renderer.v1.render.AltModelBlockRenderer;

/** Hand-written stand-in for fabric-renderer-api's renderer: the two things the chunk compile asks it for. */
public interface Renderer {
	static Renderer get() {
		return new Renderer() {
		};
	}

	default AltModelBlockRenderer altModelBlockRenderer() {
		return (emitter, x, y, z, level, pos, state, model, seed) -> emitter.emit("fabric:" + model.name());
	}

	default QuadEmitter quadEmitter(Consumer<String> sink) {
		return sink::accept;
	}
}
