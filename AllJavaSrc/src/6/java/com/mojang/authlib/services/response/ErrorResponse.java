package com.mojang.authlib.services.response;

import com.google.gson.annotations.SerializedName;
import java.util.Map;
import javax.annotation.Nullable;

public record ErrorResponse(
   @SerializedName("path") String path,
   @Nullable @SerializedName("error") String error,
   @Nullable @SerializedName("errorMessage") String errorMessage,
   @Nullable @SerializedName("details") Map<String, Object> details
) {
}
