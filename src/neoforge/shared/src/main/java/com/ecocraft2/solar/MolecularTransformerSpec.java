package com.ecocraft2.solar;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.List;
import java.util.Optional;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * Version-independent data payload for {@code ecc2:molecular_transformer} JSON recipes.
 *
 * <p>Matches {@code assets-gen/generate_data.py}'s exact schema:
 * <pre>{@code
 * {
 *   "type": "ecera:molecular_transformer",
 *   "input": { "item": "..." } | { "tag": "..." },
 *   "input_count": 1,
 *   "output": { "id": "...", "count": 1 } | { "tag": "...", "count": 1 },
 *   "energy": 9000000
 * }
 * }</pre>
 * Gracefully handles optional external-mod tags or items that have no runtime match.
 */
public record MolecularTransformerSpec(
        EntryRef input, int inputCount, EntryRef output, long energy) {

    /** Reference to either an explicit item ID or an item tag (with optional count). */
    public record EntryRef(Optional<String> itemId, Optional<String> tagId, int count) {

        public static final Codec<EntryRef> INPUT_CODEC =
                RecordCodecBuilder.<EntryRef>create(
                                instance ->
                                        instance.group(
                                                        Codec.STRING
                                                                .optionalFieldOf("item")
                                                                .forGetter(EntryRef::itemId),
                                                        Codec.STRING
                                                                .optionalFieldOf("tag")
                                                                .forGetter(EntryRef::tagId),
                                                        Codec.INT
                                                                .optionalFieldOf("count", 1)
                                                                .forGetter(EntryRef::count))
                                                .apply(instance, EntryRef::new))
                        .validate(EntryRef::validate);

        public static final Codec<EntryRef> OUTPUT_CODEC =
                RecordCodecBuilder.<EntryRef>create(
                                instance ->
                                        instance.group(
                                                        Codec.STRING
                                                                .optionalFieldOf("id")
                                                                .forGetter(EntryRef::itemId),
                                                        Codec.STRING
                                                                .optionalFieldOf("item")
                                                                .forGetter(ignored -> Optional.empty()),
                                                        Codec.STRING
                                                                .optionalFieldOf("tag")
                                                                .forGetter(EntryRef::tagId),
                                                        Codec.INT
                                                                .optionalFieldOf("count", 1)
                                                                .forGetter(EntryRef::count))
                                                .apply(
                                                        instance,
                                                        (idOpt, itemOpt, tagOpt, cnt) ->
                                                                new EntryRef(
                                                                        idOpt.isPresent()
                                                                                ? idOpt
                                                                                : itemOpt,
                                                                        tagOpt,
                                                                        cnt)))
                        .validate(EntryRef::validate);

        public static final StreamCodec<RegistryFriendlyByteBuf, EntryRef> STREAM_CODEC =
                StreamCodec.of(
                        (buf, ref) -> {
                            buf.writeBoolean(ref.itemId().isPresent());
                            ref.itemId().ifPresent(buf::writeUtf);
                            buf.writeBoolean(ref.tagId().isPresent());
                            ref.tagId().ifPresent(buf::writeUtf);
                            buf.writeVarInt(ref.count());
                        },
                        buf -> {
                            Optional<String> item =
                                    buf.readBoolean() ? Optional.of(buf.readUtf()) : Optional.empty();
                            Optional<String> tag =
                                    buf.readBoolean() ? Optional.of(buf.readUtf()) : Optional.empty();
                            int count = buf.readVarInt();
                            return new EntryRef(item, tag, Math.max(1, count));
                        });

        private static DataResult<EntryRef> validate(EntryRef ref) {
            if (ref.itemId().isEmpty() && ref.tagId().isEmpty()) {
                return DataResult.error(
                        () -> "Molecular Transformer entry must specify 'item'/'id' or 'tag'");
            }
            if (ref.count() <= 0) {
                return DataResult.error(() -> "Molecular Transformer entry count must be > 0");
            }
            return DataResult.success(ref);
        }

        public boolean matches(ItemStack stack) {
            if (stack == null || stack.isEmpty()) {
                return false;
            }
            if (itemId.isPresent()) {
                Item target = SolarPlatform.findItem(itemId.get());
                return target != null && target != Items.AIR && stack.is(target);
            }
            return tagId.isPresent() && SolarPlatform.isInItemTag(stack, tagId.get());
        }

        public ItemStack resolveSingleStack(int overrideCount) {
            int qty = Math.max(1, overrideCount);
            if (itemId.isPresent()) {
                Item target = SolarPlatform.findItem(itemId.get());
                return target != null && target != Items.AIR
                        ? new ItemStack(target, qty)
                        : ItemStack.EMPTY;
            }
            if (tagId.isPresent()) {
                return SolarPlatform.firstItemInTag(tagId.get(), qty);
            }
            return ItemStack.EMPTY;
        }

        public List<ItemStack> resolveExamples(int overrideCount) {
            int qty = Math.max(1, overrideCount);
            if (itemId.isPresent()) {
                ItemStack stack = resolveSingleStack(qty);
                return stack.isEmpty() ? List.of() : List.of(stack);
            }
            if (tagId.isPresent()) {
                return SolarPlatform.allItemsInTag(tagId.get(), qty);
            }
            return List.of();
        }
    }

    public static final MapCodec<MolecularTransformerSpec> MAP_CODEC =
            RecordCodecBuilder.mapCodec(
                    instance ->
                            instance.group(
                                            EntryRef.INPUT_CODEC
                                                    .fieldOf("input")
                                                    .forGetter(MolecularTransformerSpec::input),
                                            Codec.INT
                                                    .optionalFieldOf("input_count", 1)
                                                    .forGetter(MolecularTransformerSpec::inputCount),
                                            EntryRef.OUTPUT_CODEC
                                                    .fieldOf("output")
                                                    .forGetter(MolecularTransformerSpec::output),
                                            Codec.LONG
                                                    .fieldOf("energy")
                                                    .forGetter(MolecularTransformerSpec::energy))
                                    .apply(instance, MolecularTransformerSpec::new));

    public static final StreamCodec<RegistryFriendlyByteBuf, MolecularTransformerSpec>
            STREAM_CODEC =
                    StreamCodec.composite(
                            EntryRef.STREAM_CODEC,
                            MolecularTransformerSpec::input,
                            net.minecraft.network.codec.ByteBufCodecs.VAR_INT,
                            MolecularTransformerSpec::inputCount,
                            EntryRef.STREAM_CODEC,
                            MolecularTransformerSpec::output,
                            net.minecraft.network.codec.ByteBufCodecs.VAR_LONG,
                            MolecularTransformerSpec::energy,
                            MolecularTransformerSpec::new);

    public MolecularTransformerSpec {
        inputCount = Math.max(1, inputCount);
    }

    /** Returns {@code true} if both input and output resolve to live items and {@code energy > 0}. */
    public boolean isAvailable() {
        return energy > 0L
                && !resolveOutput().isEmpty()
                && !resolveInputExamples().isEmpty();
    }

    /** Returns {@code true} if {@code stack} satisfies this recipe's input and count. */
    public boolean matchesStack(ItemStack stack) {
        return energy > 0L
                && stack != null
                && !stack.isEmpty()
                && stack.getCount() >= inputCount
                && input.matches(stack)
                && !resolveOutput().isEmpty();
    }

    /** Resolves the concrete output stack, or {@link ItemStack#EMPTY} if unavailable. */
    public ItemStack resolveOutput() {
        return output.resolveSingleStack(output.count());
    }

    /** Resolves all matching input example stacks for JEI/EMI recipe displays. */
    public List<ItemStack> resolveInputExamples() {
        return input.resolveExamples(inputCount);
    }
}
