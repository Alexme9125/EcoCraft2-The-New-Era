package com.ecocraft2.compat;

import net.minecraft.nbt.CompoundTag;

/** NeoForge 26.x adapter exposing a {@link CompoundTag} through {@link CompatTelemetry.TagView}. */
final class CompatTagView {

    private CompatTagView() {}

    static CompatTelemetry.TagView of(CompoundTag tag) {
        return new View26(tag);
    }

    private record View26(CompoundTag tag) implements CompatTelemetry.TagView {

        @Override
        public boolean contains(String key) {
            return tag.contains(key);
        }

        @Override
        public int getInt(String key, int fallback) {
            return tag.getIntOr(key, fallback);
        }

        @Override
        public long getLong(String key, long fallback) {
            return tag.getLongOr(key, fallback);
        }

        @Override
        public double getDouble(String key, double fallback) {
            return tag.getDoubleOr(key, fallback);
        }

        @Override
        public boolean getBoolean(String key, boolean fallback) {
            return tag.getBooleanOr(key, fallback);
        }

        @Override
        public String getString(String key, String fallback) {
            return tag.getStringOr(key, fallback);
        }
    }
}
