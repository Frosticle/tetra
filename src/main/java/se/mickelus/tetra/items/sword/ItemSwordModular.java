package se.mickelus.tetra.items.sword;

import com.google.common.collect.Multimap;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.SharedMonsterAttributes;
import net.minecraft.entity.ai.attributes.AttributeModifier;
import net.minecraft.init.Blocks;
import net.minecraft.init.Items;
import net.minecraft.inventory.EntityEquipmentSlot;
import net.minecraft.item.IItemPropertyGetter;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.ItemSword;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.ResourceLocation;
import net.minecraft.world.World;
import net.minecraftforge.fml.common.registry.GameRegistry;
import se.mickelus.tetra.items.ItemModular;
import se.mickelus.tetra.items.TetraCreativeTabs;
import se.mickelus.tetra.module.ItemUpgradeRegistry;
import se.mickelus.tetra.network.PacketPipeline;

import javax.annotation.Nullable;
import java.util.Arrays;

public class ItemSwordModular extends ItemModular {

    public final static String bladeKey = "sword:blade";
    public final static String hiltKey = "sword:hilt";


    static final String unlocalizedName = "sword_modular";

    public ItemSwordModular() {
        setUnlocalizedName(unlocalizedName);
        setRegistryName(unlocalizedName);
        GameRegistry.register(this);
        setCreativeTab(TetraCreativeTabs.getInstance());

        majorModuleNames = new String[]{"Blade", "Hilt"};
        majorModuleKeys = new String[]{bladeKey, hiltKey};
        minorModuleNames = new String[]{"Guard", "Pommel", "Fuller"};
        minorModuleKeys = new String[]{"sword:guard", "sword:pommel", "sword:fuller"};
    }

    @Override
    public IItemPropertyGetter getPropertyGetter(ResourceLocation key) {
        return super.getPropertyGetter(key);
    }

    @Override
    public void init(PacketPipeline packetPipeline) {
        ItemUpgradeRegistry.instance.registerPlaceholder(this::replaceSword);
        new BladeModule();
        new HiltModule();

        new SimpleBladeSchema();
    }

    private ItemStack replaceSword(ItemStack originalStack) {
        Item originalItem = originalStack.getItem();

        if (!(originalItem instanceof ItemSword)) {
            return null;
        }

        return createItemStack(((ItemSword) originalItem).getToolMaterialName());
    }

    private ItemStack createItemStack(String material) {
        ItemStack itemStack = new ItemStack(this);
        itemStack.setTagCompound(new NBTTagCompound());

        ItemStack bladeMaterial;
        switch (material) {
            case "WOOD":
                bladeMaterial = new ItemStack(Blocks.PLANKS, 2);
                break;
            case "STONE":
                bladeMaterial = new ItemStack(Blocks.COBBLESTONE, 2);
                break;
            case "IRON":
                bladeMaterial = new ItemStack(Items.IRON_INGOT, 2);
                break;
            case "DIAMOND":
                bladeMaterial = new ItemStack(Items.DIAMOND, 2);
                break;
            default:
                bladeMaterial = new ItemStack(Blocks.PLANKS, 2);
                break;
        }

        BladeModule.instance.addModule(itemStack, new ItemStack[]{bladeMaterial});
        HiltModule.instance.addModule(itemStack, new ItemStack[] {new ItemStack(Items.STICK)});
        return itemStack;
    }

    public String getItemStackDisplayName(ItemStack stack) {
        NBTTagCompound tag = stack.getTagCompound();

        if (stack.hasTagCompound() && tag.hasKey(BladeModule.materialKey)) {
            return tag.getString(BladeModule.materialKey);
        }
        return "Wooden Blade";
    }

    public Multimap<String, AttributeModifier> getAttributeModifiers(EntityEquipmentSlot slot, ItemStack stack)
    {
        Multimap<String, AttributeModifier> multimap = super.getAttributeModifiers(slot, stack);

        if (slot == EntityEquipmentSlot.MAINHAND) {
            double damageModifier = Arrays.stream(getAllModules(stack)).map(itemModule -> itemModule.getDamageModifier(stack)).reduce(0d, Double::sum);
            damageModifier = Arrays.stream(getAllModules(stack)).map(itemModule -> itemModule.getDamageMultiplierModifier(stack)).reduce(damageModifier, (a, b) -> a*b);

            multimap.put(SharedMonsterAttributes.ATTACK_DAMAGE.getName(), new AttributeModifier(ATTACK_DAMAGE_MODIFIER, "Weapon modifier", damageModifier, 0));
            multimap.put(SharedMonsterAttributes.ATTACK_SPEED.getName(), new AttributeModifier(ATTACK_SPEED_MODIFIER, "Weapon modifier", -3.6D, 0));
        }

        return multimap;
    }

    @Nullable
    @Override
    public String getArmorTexture(ItemStack stack, Entity entity, EntityEquipmentSlot slot, String type) {
        return super.getArmorTexture(stack, entity, slot, type);
    }


}
