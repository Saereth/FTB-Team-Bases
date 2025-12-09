package dev.ftb.mods.ftbteambases.config;

import dev.ftb.mods.ftblibrary.snbt.SNBTCompoundTag;
import dev.ftb.mods.ftblibrary.snbt.config.BaseValue;
import dev.ftb.mods.ftblibrary.snbt.config.SNBTConfig;
import dev.ftb.mods.ftbteambases.FTBTeamBases;
import net.minecraft.ResourceLocationException;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.List;

public class ResourceLocationListValue extends BaseValue<List<ResourceLocation>> {
    private final String comment;

    public ResourceLocationListValue(SNBTConfig parent, String key, List<ResourceLocation> defaultValue, String comment) {
        super(parent, key, defaultValue);
        this.comment = comment;
    }

    @Override
    public void write(SNBTCompoundTag tag) {
        ListTag list = new ListTag();
        for (ResourceLocation rl : get()) {
            list.add(StringTag.valueOf(rl.toString()));
        }
        tag.comment(key, comment);
        tag.put(key, list);
    }

    @Override
    public void read(SNBTCompoundTag tag) {
        if (!tag.contains(key)) {
            set(defaultValue);
            return;
        }
        ListTag listTag = tag.getList(key, Tag.TAG_STRING);
        List<ResourceLocation> result = new ArrayList<>();
        for (Tag t : listTag) {
            String value = t.getAsString();
            if (value != null && !value.isBlank()) {
                try {
                    result.add(ResourceLocation.parse(value));
                } catch (ResourceLocationException e) {
                    FTBTeamBases.LOGGER.error("Invalid resource location in '{}': {}", key, value);
                }
            }
        }
        set(List.copyOf(result));
    }
}
