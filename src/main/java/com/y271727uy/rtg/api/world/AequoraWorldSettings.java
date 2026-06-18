package com.y271727uy.rtg.api.world;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

/**
 * Lightweight 1.20.1 replacement for RTGChunkGenSettings.
 */
public record AequoraWorldSettings(
        double riverBendMultiplier,
        double riverFrequency,
        double riverSizeMultiplier,
        float lakeFrequencyMultiplier,
        float lakeSizeMultiplier,
        float lakeShoreBend
) {
    public static AequoraWorldSettings defaults() {
        return new AequoraWorldSettings(1.0d, 1.0d, 1.0d, 1.0f, 1.0f, 1.0f);
    }

    public static AequoraWorldSettings fromJson(String json) {
        if (json == null || json.isBlank()) {
            return defaults();
        }

        try {
            JsonObject object = JsonParser.parseString(json).getAsJsonObject();
            AequoraWorldSettings defaults = defaults();
            return new AequoraWorldSettings(
                    getDouble(object, "riverBendMultiplier", defaults.riverBendMultiplier()),
                    getDouble(object, "riverFrequency", defaults.riverFrequency()),
                    getDouble(object, "riverSizeMultiplier", defaults.riverSizeMultiplier()),
                    getFloat(object, "lakeFrequencyMultiplier", defaults.lakeFrequencyMultiplier()),
                    getFloat(object, "lakeSizeMultiplier", defaults.lakeSizeMultiplier()),
                    getFloat(object, "lakeShoreBend", defaults.lakeShoreBend())
            );
        } catch (RuntimeException ignored) {
            return defaults();
        }
    }

    public String toJson() {
        JsonObject object = new JsonObject();
        object.addProperty("riverBendMultiplier", this.riverBendMultiplier);
        object.addProperty("riverFrequency", this.riverFrequency);
        object.addProperty("riverSizeMultiplier", this.riverSizeMultiplier);
        object.addProperty("lakeFrequencyMultiplier", this.lakeFrequencyMultiplier);
        object.addProperty("lakeSizeMultiplier", this.lakeSizeMultiplier);
        object.addProperty("lakeShoreBend", this.lakeShoreBend);
        return object.toString();
    }

    public AequoraWorldSettings withRiverBendMultiplier(double value) {
        return new AequoraWorldSettings(value, this.riverFrequency, this.riverSizeMultiplier, this.lakeFrequencyMultiplier, this.lakeSizeMultiplier, this.lakeShoreBend);
    }

    public AequoraWorldSettings withRiverFrequency(double value) {
        return new AequoraWorldSettings(this.riverBendMultiplier, value, this.riverSizeMultiplier, this.lakeFrequencyMultiplier, this.lakeSizeMultiplier, this.lakeShoreBend);
    }

    public AequoraWorldSettings withRiverSizeMultiplier(double value) {
        return new AequoraWorldSettings(this.riverBendMultiplier, this.riverFrequency, value, this.lakeFrequencyMultiplier, this.lakeSizeMultiplier, this.lakeShoreBend);
    }

    public AequoraWorldSettings withLakeFrequencyMultiplier(float value) {
        return new AequoraWorldSettings(this.riverBendMultiplier, this.riverFrequency, this.riverSizeMultiplier, value, this.lakeSizeMultiplier, this.lakeShoreBend);
    }

    public AequoraWorldSettings withLakeSizeMultiplier(float value) {
        return new AequoraWorldSettings(this.riverBendMultiplier, this.riverFrequency, this.riverSizeMultiplier, this.lakeFrequencyMultiplier, value, this.lakeShoreBend);
    }

    public AequoraWorldSettings withLakeShoreBend(float value) {
        return new AequoraWorldSettings(this.riverBendMultiplier, this.riverFrequency, this.riverSizeMultiplier, this.lakeFrequencyMultiplier, this.lakeSizeMultiplier, value);
    }

    private static double getDouble(JsonObject object, String name, double fallback) {
        return object.has(name) ? object.get(name).getAsDouble() : fallback;
    }

    private static float getFloat(JsonObject object, String name, float fallback) {
        return object.has(name) ? object.get(name).getAsFloat() : fallback;
    }
}
