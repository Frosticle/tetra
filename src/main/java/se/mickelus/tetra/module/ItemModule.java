package se.mickelus.tetra.module;

import net.minecraft.item.ItemStack;

public abstract class ItemModule {
    public abstract int getIntegrity(ItemStack stack);
    public abstract int getDurability(ItemStack stack);
    public abstract String getName(ItemStack stack);
    public abstract void addModule(ItemStack targetStack, ItemStack[] materials);
    public abstract ItemStack[] removeModule(ItemStack targetStack, ItemStack[] tools);
}