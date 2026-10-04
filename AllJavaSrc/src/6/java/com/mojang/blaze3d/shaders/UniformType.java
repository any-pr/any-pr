package com.mojang.blaze3d.shaders;

public enum UniformType {
   UNIFORM_BUFFER("ubo"),
   TEXEL_BUFFER("utb");

   final String name;

   UniformType(final String name) {
      this.name = name;
   }
}
