package se.mickelus.tetra.items.modular;

import com.google.common.collect.ImmutableList;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.resources.I18n;
import net.minecraft.client.util.ITooltipFlag;
import net.minecraft.enchantment.Enchantment;
import net.minecraft.enchantment.EnchantmentHelper;
import net.minecraft.enchantment.UnbreakingEnchantment;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.ServerPlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.CompoundNBT;
import net.minecraft.util.SoundEvents;
import net.minecraft.util.text.*;
import net.minecraft.world.World;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.common.ToolType;
import net.minecraftforge.forgespi.Environment;
import org.apache.commons.lang3.ArrayUtils;
import org.apache.commons.lang3.EnumUtils;
import org.apache.commons.lang3.text.WordUtils;
import se.mickelus.tetra.ConfigHandler;
import se.mickelus.tetra.NBTHelper;
import se.mickelus.tetra.capabilities.Capability;
import se.mickelus.tetra.capabilities.ICapabilityProvider;
import se.mickelus.tetra.client.model.ModularModelLoader;
import se.mickelus.tetra.items.TetraItem;
import se.mickelus.tetra.module.ItemEffect;
import se.mickelus.tetra.module.ItemModule;
import se.mickelus.tetra.module.ItemModuleMajor;
import se.mickelus.tetra.module.ItemUpgradeRegistry;
import se.mickelus.tetra.module.data.EnchantmentMapping;
import se.mickelus.tetra.module.data.ImprovementData;
import se.mickelus.tetra.module.data.ModuleModel;
import se.mickelus.tetra.module.data.SynergyData;
import se.mickelus.tetra.module.improvement.DestabilizationEffect;
import se.mickelus.tetra.module.improvement.HonePacket;
import se.mickelus.tetra.module.schema.Material;
import se.mickelus.tetra.network.PacketHandler;
import se.mickelus.tetra.util.CastOptional;

import javax.annotation.Nullable;
import java.util.*;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public abstract class ItemModular extends TetraItem implements IItemModular, ICapabilityProvider {
    protected static final String identifierKey = "id";

    protected static final String repairCountKey = "repairCount";

    protected static final String cooledStrengthKey = "cooledStrength";

    private static final String honeProgressKey = "honing_progress";
    private static final String honeAvailableKey = "honing_available";
    private static final String honeCountKey = "honing_count";
    protected int honeBase = 450;
    protected int honeIntegrityMultiplier = 200;

    protected String[] majorModuleKeys;
    protected String[] minorModuleKeys;

    protected String[] requiredModules = new String[0];

    protected int baseDurability = 0;
    protected int baseIntegrity = 0;

    protected SynergyData[] synergies = new SynergyData[0];

    public ItemModular(Properties properties) {
        super(properties);
    }

    @Override
    public void clientInit() {
        ModularModelLoader.registerItem(this);
    }

    public String getModelCacheKey(ItemStack itemStack, LivingEntity entity) {
        return Optional.of(getIdentifier(itemStack))
                .filter(id -> !id.isEmpty())
                .orElseGet(() -> NBTHelper.getTag(itemStack).toString());
    }

    @Override
    public int getMaxDamage(ItemStack stack) {
        return (int) ((getAllModules(stack).stream()
                .map(itemModule -> itemModule.getDurability(stack))
                .reduce(0, Integer::sum) + baseDurability)
                * getDurabilityMultiplier(stack));
    }

    public float getDurabilityMultiplier(ItemStack itemStack) {
        return getAllModules(itemStack).stream()
                .map(itemModule -> itemModule.getDurabilityMultiplier(itemStack))
                .reduce(1f, (a, b) -> a * b);
    }

    public static int getIntegrityGain(ItemStack itemStack) {
        if (itemStack.getItem() instanceof ItemModular) {
            return ((ItemModular) itemStack.getItem()).getAllModules(itemStack).stream()
                    .map(module -> module.getIntegrityGain(itemStack))
                    .reduce(0, Integer::sum);
        }
        return 0;
    }

    public static int getIntegrityCost(ItemStack itemStack) {
        if (itemStack.getItem() instanceof ItemModular) {
            return ((ItemModular) itemStack.getItem()).getAllModules(itemStack).stream()
                    .map(module -> module.getIntegrityCost(itemStack))
                    .reduce(0, Integer::sum);
        }
        return 0;
    }

    protected Collection<ItemModule> getAllModules(ItemStack stack) {
        CompoundNBT stackTag = NBTHelper.getTag(stack);

        if (stackTag != null) {
            return Stream.concat(Arrays.stream(majorModuleKeys),Arrays.stream(minorModuleKeys))
                    .map(stackTag::getString)
                    .map(ItemUpgradeRegistry.instance::getModule)
                    .filter(Objects::nonNull)
                    .collect(Collectors.toList());
        }

        return Collections.emptyList();
    }

    @Override
    public ItemModuleMajor[] getMajorModules(ItemStack itemStack) {
        ItemModuleMajor[] modules = new ItemModuleMajor[majorModuleKeys.length];
        CompoundNBT stackTag = NBTHelper.getTag(itemStack);

        for (int i = 0; i < majorModuleKeys.length; i++) {
            String moduleName = stackTag.getString(majorModuleKeys[i]);
            ItemModule module = ItemUpgradeRegistry.instance.getModule(moduleName);
            if (module instanceof ItemModuleMajor) {
                modules[i] = (ItemModuleMajor) module;
            }
        }

        return modules;
    }

    @Override
    public ItemModule[] getMinorModules(ItemStack itemStack) {
        ItemModule[] modules = new ItemModule[minorModuleKeys.length];
        CompoundNBT stackTag = NBTHelper.getTag(itemStack);

        for (int i = 0; i < minorModuleKeys.length; i++) {
            String moduleName = stackTag.getString(minorModuleKeys[i]);
            ItemModule module = ItemUpgradeRegistry.instance.getModule(moduleName);
            modules[i] = module;
        }

        return modules;
    }

    @Override
    public boolean isModuleRequired(String moduleSlot) {
        return ArrayUtils.contains(requiredModules, moduleSlot);
    }

    @Override
    public int getNumMajorModules() {
        return majorModuleKeys.length;
    }

    @Override
    public String[] getMajorModuleKeys() {
        return majorModuleKeys;
    }

    @Override
    public String[] getMajorModuleNames() {
        return Arrays.stream(majorModuleKeys)
                .map(key -> I18n.format(key))
                .toArray(String[]::new);
    }

    @Override
    public int getNumMinorModules() {
        return minorModuleKeys.length;
    }

    @Override
    public String[] getMinorModuleKeys() {
        return minorModuleKeys;
    }

    @Override
    public String[] getMinorModuleNames() {
        return Arrays.stream(minorModuleKeys)
                .map(key -> I18n.format(key))
                .toArray(String[]::new);
    }

    @Override
    public ImmutableList<ModuleModel> getModels(ItemStack itemStack, @Nullable LivingEntity entity) {
        return getAllModules(itemStack).stream()
                .sorted(Comparator.comparing(ItemModule::getRenderLayer))
                .flatMap(itemModule -> Arrays.stream(itemModule.getModels(itemStack)))
                .filter(Objects::nonNull)
                .collect(Collectors.collectingAndThen(Collectors.toList(), ImmutableList::copyOf));
    }

    public boolean hasModule(ItemStack itemStack, ItemModule module) {
        return getAllModules(itemStack).stream()
            .anyMatch(module::equals);
    }

    /**
     * Helper for manually adding modules, to be used incases like creative tab items which are populated before modules exists. Use
     * with caution as this may break things if the module/variant doesn't actually end up existing.
     * @param itemStack
     * @param slot
     * @param module
     * @param moduleVariantKey
     * @param moduleVariant
     */
    public static void putModuleInSlot(ItemStack itemStack, String slot, String module, String moduleVariantKey, String moduleVariant) {
        CompoundNBT tag = NBTHelper.getTag(itemStack);
        tag.putString(slot, module);
        tag.putString(moduleVariantKey, moduleVariant);
    }

    public ItemModule getModuleFromSlot(ItemStack itemStack, String slot) {
        return ItemUpgradeRegistry.instance.getModule(NBTHelper.getTag(itemStack).getString(slot));
    }

    public void tickProgression(LivingEntity entity, ItemStack itemStack, int multiplier) {
        if (!ConfigHandler.moduleProgression.get()) {
            return;
        }

        tickHoningProgression(entity, itemStack, multiplier);

        for (ItemModuleMajor module: getMajorModules(itemStack)) {
            module.tickProgression(entity, itemStack, multiplier);
        }
    }

    public void tickHoningProgression(LivingEntity entity, ItemStack itemStack, int multiplier) {
        if (!ConfigHandler.moduleProgression.get()) {
            return;
        }

        // todo: store this in a separate data structure?
        CompoundNBT tag = NBTHelper.getTag(itemStack);
        if (!isHoneable(itemStack)) {
            int honingProgress;
            if (tag.contains(honeProgressKey)) {
                honingProgress = tag.getInt(honeProgressKey);
            } else {
                honingProgress = getHoningBase(itemStack);
            }

            honingProgress -= multiplier;
            tag.putInt(honeProgressKey, honingProgress);

            if (honingProgress <= 0 && !isHoneable(itemStack)) {
                tag.putBoolean(honeAvailableKey, true);

                if (entity instanceof ServerPlayerEntity) {
                    PacketHandler.sendTo(new HonePacket(itemStack), (ServerPlayerEntity) entity);
                }
            }
        }

    }

    public int getHoningProgress(ItemStack itemStack) {
        CompoundNBT tag = NBTHelper.getTag(itemStack);

        if (tag.contains(honeProgressKey)) {
            return tag.getInt(honeProgressKey);
        }

        return getHoningBase(itemStack);
    }

    public void setHoningProgress(ItemStack itemStack, int progress) {
        NBTHelper.getTag(itemStack).putInt(honeProgressKey, progress);
    }

    public int getHoningBase(ItemStack itemStack) {
        return Math.max(honeBase + honeIntegrityMultiplier * -getIntegrityCost(itemStack), 1);
    }

    public int getHonedCount(ItemStack itemStack) {
        return NBTHelper.getTag(itemStack).getInt(honeCountKey);
    }

    public static boolean isHoneable(ItemStack itemStack) {
        return NBTHelper.getTag(itemStack).contains(honeAvailableKey);
    }

    public static int getHoningSeed(ItemStack itemStack) {
        return NBTHelper.getTag(itemStack).getInt(honeCountKey) + 1;
    }

    public static void removeHoneable(ItemStack itemStack) {
        CompoundNBT tag = NBTHelper.getTag(itemStack);
        tag.remove(honeAvailableKey);
        tag.remove(honeProgressKey);
        tag.putInt(honeCountKey, tag.getInt(honeCountKey) + 1);
    }

    @Override
    public void setDamage(ItemStack itemStack, int damage) {
        super.setDamage(itemStack, Math.min(itemStack.getMaxDamage() - 1, damage));
    }

    @Override
    public <T extends LivingEntity> int damageItem(ItemStack stack, int amount, T entity, Consumer<T> onBroken) {
        return Math.min(stack.getMaxDamage() - stack.getDamage() - 1, amount);
    }

    public void applyDamage(int amount, ItemStack itemStack, LivingEntity responsibleEntity) {
        int damage = itemStack.getDamage();
        int maxDamage = itemStack.getMaxDamage();

        if (!isBroken(damage, maxDamage)) {
            int reducedAmount = getReducedDamage(amount, itemStack, responsibleEntity);
            itemStack.damageItem(reducedAmount, responsibleEntity, breaker -> breaker.sendBreakAnimation(breaker.getActiveHand()));

            if (isBroken(damage + reducedAmount, maxDamage) && !responsibleEntity.world.isRemote) {
                responsibleEntity.sendBreakAnimation(responsibleEntity.getActiveHand());
                responsibleEntity.playSound(SoundEvents.ITEM_SHIELD_BREAK, 1, 1);
            }
        }
    }

    private int getReducedDamage(int amount, ItemStack itemStack, LivingEntity responsibleEntity) {
        if (amount > 0) {
            int level = getEffectLevel(itemStack, ItemEffect.unbreaking);
            int reduction = 0;

            if (level > 0) {
                for (int i = 0; i < amount; i++) {
                    if (UnbreakingEnchantment.negateDamage(itemStack, level, responsibleEntity.world.rand)) {
                        reduction++;
                    }
                }
            }

            return amount - reduction;
        }
        return amount;
    }

    public boolean isBroken(ItemStack itemStack) {
        return isBroken(itemStack.getDamage(), itemStack.getMaxDamage());
    }

    private boolean isBroken(int damage, int maxDamage) {
        return maxDamage != 0 && damage >= maxDamage - 1;
    }

    @OnlyIn(Dist.CLIENT)
    @Override
    public void addInformation(ItemStack itemStack, @Nullable World world, List<ITextComponent> tooltip, ITooltipFlag advanced) {
        Style basicStyle = new Style().setColor(TextFormatting.GRAY);
        if (isBroken(itemStack)) {
            tooltip.add(new TranslationTextComponent("item.tetra.modular.broken")
                    .setStyle(new Style().setColor(TextFormatting.DARK_RED).setItalic(true)));
        }

        if (Screen.hasShiftDown()) {
            Arrays.stream(getMajorModules(itemStack))
                    .filter(Objects::nonNull)
                    .forEach(module -> {
                        tooltip.add(new StringTextComponent("\u00BB " + module.getName(itemStack)).setStyle(basicStyle));
                        Arrays.stream(module.getImprovements(itemStack))
                                .map(improvement -> String.format(" - %s", getImprovementTooltip(improvement.key, improvement.level, true)))
                                .map(StringTextComponent::new)
                                .map(textComponent -> textComponent.setStyle(new Style().setColor(TextFormatting.DARK_GRAY)))
                                .forEach(tooltip::add);
                    });
            Arrays.stream(getMinorModules(itemStack))
                    .filter(Objects::nonNull)
                    .map(module -> "* " + module.getName(itemStack))
                    .map(StringTextComponent::new)
                    .map(textComponent -> textComponent.setStyle(basicStyle))
                    .forEach(tooltip::add);

            // honing tooltip
            if (ConfigHandler.moduleProgression.get()) {
                if (isHoneable(itemStack)) {
                    tooltip.add(new StringTextComponent(" > ").setStyle(new Style().setColor(TextFormatting.AQUA))
                            .appendSibling(new TranslationTextComponent("hone.available")
                                    .setStyle(basicStyle)));
                } else {
                    int progress = getHoningProgress(itemStack);
                    int base = getHoningBase(itemStack);
                    String result = String.format("%.1f", 100f * (base - progress) / base);
                    tooltip.add(new StringTextComponent(" > ").setStyle(new Style().setColor(TextFormatting.DARK_AQUA))
                            .appendSibling(new TranslationTextComponent("hone.progress", result)
                                    .setStyle(basicStyle)));
                }
            }
        } else {
            Arrays.stream(getMajorModules(itemStack))
                    .filter(Objects::nonNull)
                    .flatMap(module -> Arrays.stream(module.getImprovements(itemStack)))
                    .filter(improvement -> improvement.enchantment)
                    .collect(Collectors.groupingBy(ImprovementData::getKey, Collectors.summingInt(ImprovementData::getLevel)))
                    .entrySet()
                    .stream()
                    .map(entry -> getImprovementTooltip(entry.getKey(), entry.getValue(), false))
                    .map(StringTextComponent::new)
                    .map(text -> text.setStyle(basicStyle))
                    .forEach(tooltip::add);
        }
    }

    private String getImprovementTooltip(String key, int level, boolean clearFormatting) {
        String tooltip = I18n.format(key + ".name");
        if (level > 0) {
            tooltip += " " + I18n.format("enchantment.level." + level);
        }

        if (clearFormatting) {
            return TextFormatting.getTextWithoutFormattingCodes(tooltip);
        }
        return tooltip;
    }

    /**
     * Returns an optional with the module that will be repaired in next repair attempt, the optional is empty if
     * there are no repairable modules in this item.
     * @param itemStack The itemstack for the modular item
     * @return An optional with the module that will be repaired in next repair attempt
     */
    private Optional<ItemModule> getRepairModule(ItemStack itemStack) {
        List<ItemModule> modules = getAllModules(itemStack).stream()
                .filter(itemModule -> itemModule.getRepairMaterial(itemStack) != null)
                .collect(Collectors.toList());

        if (modules.size() > 0) {
            int repairCount = getRepairCount(itemStack);
            return Optional.of(modules.get(repairCount % modules.size()));
        }
        return Optional.empty();
    }

    public String getRepairModuleName(ItemStack itemStack) {
        return getRepairModule(itemStack)
                .map(module -> module.getName(itemStack))
                .orElse(null);
    }

    public String getRepairSlot(ItemStack itemStack) {
        return getRepairModule(itemStack)
                .map(ItemModule::getSlot)
                .orElse(null);
    }

    /**
     * Returns an itemstack with the material required for the next repair attempt. Rotates between materials required
     * for different modules
     * @param itemStack The itemstack for the modular item
     * @return The material definition for the material required for the next repair attempt
     */
    public Material getRepairMaterial(ItemStack itemStack) {
        return getRepairModule(itemStack)
                .map(module -> module.getRepairMaterial(itemStack))
                .orElse(null);
    }

    /**
     * Returns the required size of the repair material itemstack for the next repair attempt.
     * @param itemStack The itemstack for the modular item
     * @return
     */
    public int getRepairMaterialCount(ItemStack itemStack) {
        return getRepairModule(itemStack)
                .map(module -> module.getRepairMaterial(itemStack).count)
                .orElse(0);
    }

    /**
     * Returns the amount of durability restored by the next repair attempt.
     * @param itemStack The itemstack for the modular item
     * @return
     */
    public int getRepairAmount(ItemStack itemStack) {
        return getMaxDamage(itemStack);
    }

    public Collection<Capability> getRepairRequiredCapabilities(ItemStack itemStack) {
        return getRepairModule(itemStack)
                .map(module -> module.getRepairRequiredCapabilities(itemStack))
                .orElse(Collections.emptyList());
    }

    public int getRepairRequiredCapabilityLevel(ItemStack itemStack, Capability capability) {
        return getRepairModule(itemStack)
                .filter(module -> module.getRepairRequiredCapabilities(itemStack).contains(capability))
                .map(module -> module.getRepairRequiredCapabilityLevel(itemStack, capability))
                .map(level -> Math.max(1, level))
                .orElse(0);
    }

    public int getRepairRequiredExperience(ItemStack itemStack) {
        return getRepairModule(itemStack)
                .map(module -> module.getMagicCapacity(itemStack))
                .map(capacity -> Math.max(0, -capacity))
                .orElse(0);
    }

    /**
     * Returns the number of times this item has been repaired.
     * @param itemStack The itemstack for the modular item
     * @return
     */
    private int getRepairCount(ItemStack itemStack) {
        return NBTHelper.getTag(itemStack).getInt(repairCountKey);
    }

    private void incrementRepairCount(ItemStack itemStack) {
        CompoundNBT tag = NBTHelper.getTag(itemStack);
        tag.putInt(repairCountKey, tag.getInt(repairCountKey) + 1);
    }

    public void repair(ItemStack itemStack) {
        setDamage(itemStack, getDamage(itemStack) - getRepairAmount(itemStack));

        incrementRepairCount(itemStack);
    }

    @Override
    public ItemStack getDefaultStack() {
        return new ItemStack(this);
    }

    public static void updateIdentifier(ItemStack itemStack) {
        NBTHelper.getTag(itemStack).putString(identifierKey, UUID.randomUUID().toString());
    }

    public String getIdentifier(ItemStack itemStack) {
        return NBTHelper.getTag(itemStack).getString(identifierKey);
    }

    @Override
    public void assemble(ItemStack itemStack, World world) {
        if (itemStack.getDamage() > itemStack.getMaxDamage()) {
            itemStack.setDamage(itemStack.getMaxDamage());
        }

        applyDestabilizationEffects(itemStack, world);

        CompoundNBT nbt = NBTHelper.getTag(itemStack);

        // this stops the tooltip renderer from showing enchantments
        nbt.putInt("HideFlags", 1);

        EnchantmentHelper.setEnchantments(getEnchantmentsFromImprovements(itemStack), itemStack);

        updateIdentifier(itemStack);
    }

    @Override
    public void onCreated(ItemStack itemStack, World world, PlayerEntity player) {
        updateIdentifier(itemStack);
    }

    public Map<Enchantment, Integer> getEnchantmentsFromImprovements(ItemStack itemStack) {
        Map<Enchantment, Integer> enchantments = new HashMap<>();
        CastOptional.cast(itemStack.getItem(), ItemModular.class)
                .map(item -> Arrays.stream(item.getMajorModules(itemStack)))
                .orElseGet(Stream::empty)
                .filter(Objects::nonNull)
                .flatMap(module -> Arrays.stream(module.getImprovements(itemStack)))
                .forEach(improvement -> {
                    for (EnchantmentMapping mapping : ItemUpgradeRegistry.instance.getEnchantmentMappings(improvement.key)) {
                        enchantments.merge(mapping.enchantment, (int) (improvement.level * mapping.multiplier), Integer::sum);
                    }
                });

        return enchantments;
    }

    public int getEnchantmentLevelFromImprovements(ItemStack itemStack, Enchantment enchantment) {
        return Arrays.stream(getMajorModules(itemStack))
                .filter(Objects::nonNull)
                .flatMap(module -> Arrays.stream(module.getImprovements(itemStack)))
                .mapToInt(improvement ->
                        (int) (Math.max(1, improvement.level) * Arrays.stream(ItemUpgradeRegistry.instance.getEnchantmentMappings(improvement.key))
                        .filter(mapping -> enchantment.equals(mapping.enchantment))
                        .map(mapping -> mapping.multiplier)
                        .reduce(0f, Float::sum))
                    )
                .sum();
    }

    public int getEnchantmentLevelFromImprovements(ItemStack itemStack, String slot, Enchantment enchantment) {
        return CastOptional.cast(getModuleFromSlot(itemStack, slot), ItemModuleMajor.class)
                .map(module -> Arrays.stream(module.getImprovements(itemStack)))
                .orElseGet(Stream::empty)
                .mapToInt(improvement ->
                        (int) (Math.max(1, improvement.level) * Arrays.stream(ItemUpgradeRegistry.instance.getEnchantmentMappings(improvement.key))
                        .filter(mapping -> enchantment.equals(mapping.enchantment))
                        .map(mapping -> mapping.multiplier)
                        .reduce(0f, Float::sum))
                )
                .sum();
    }

    public int getEnchantmentLevelFromImprovements(ItemStack itemStack, String slot, String improvementKey, Enchantment enchantment) {
        return CastOptional.cast(getModuleFromSlot(itemStack, slot), ItemModuleMajor.class)
                .map(module -> Arrays.stream(module.getImprovements(itemStack)))
                .orElseGet(Stream::empty)
                .filter(improvement -> improvementKey.equals(improvement.key))
                .mapToInt(improvement ->
                        (int) (Math.max(1, improvement.level) * Arrays.stream(ItemUpgradeRegistry.instance.getEnchantmentMappings(improvement.key))
                                .filter(mapping -> enchantment.equals(mapping.enchantment))
                                .map(mapping -> mapping.multiplier)
                                .reduce(0f, Float::sum))
                )
                .sum();
    }

    private void applyDestabilizationEffects(ItemStack itemStack, World world) {
        if (!world.isRemote) {
            Arrays.stream(getMajorModules(itemStack))
                    .filter(Objects::nonNull)
                    .forEach(module -> {
                        int instability = -module.getMagicCapacity(itemStack);

                        for (DestabilizationEffect effect:
                                DestabilizationEffect.getEffectsForImprovement(instability, module.getImprovements(itemStack))) {
                            int currentEffectLevel = module.getImprovementLevel(itemStack, effect.destabilizationKey);
                            int newLevel;

                            if (currentEffectLevel >= 0) {
                                newLevel = currentEffectLevel + 1;
                            } else if (effect.minLevel == effect.maxLevel) {
                                newLevel = effect.minLevel;
                            } else {
                                newLevel = effect.minLevel + world.rand.nextInt(effect.maxLevel - effect.minLevel);
                            }

                            if (module.acceptsImprovementLevel(effect.destabilizationKey, newLevel)
                                    && effect.probability * instability > world.rand.nextFloat()) {
                                module.addImprovement(itemStack, effect.destabilizationKey, newLevel);
                            }
                        }
                    });
        }
    }

    public void tweak(ItemStack itemStack, String slot, Map<String, Integer> tweaks) {
        ItemModule module = getModuleFromSlot(itemStack, slot);
        float durabilityFactor = 0;

        if (module == null || !module.isTweakable(itemStack)) {
            return;
        }

        if (itemStack.isDamageable()) {
            durabilityFactor = itemStack.getDamage() * 1f / itemStack.getMaxDamage();
        }

        tweaks.forEach((tweakKey, step) -> {
            if (module.hasTweak(itemStack, tweakKey)) {
                module.setTweakStep(itemStack, tweakKey, step);
            }
        });

        if (itemStack.isDamageable()) {
            itemStack.setDamage((int) (durabilityFactor * itemStack.getMaxDamage()
                    - (durabilityFactor * durabilityFactor * module.getDurability(itemStack))));
        }
    }

    public int getCapabilityLevel(ItemStack itemStack, ToolType toolType) {
        if (toolType != null) {
            return getCapabilityLevel(itemStack, toolType.getName());
        }
        return -1;
    }

    public int getCapabilityLevel(ItemStack itemStack, String capability) {
        if (EnumUtils.isValidEnum(Capability.class, capability)) {
            return getCapabilityLevel(itemStack, Capability.valueOf(capability));
        }
        return -1;
    }

    @Override
    public int getCapabilityLevel(ItemStack itemStack, Capability capability) {
        if (isBroken(itemStack)) {
            return -1;
        }

        int base = getAllModules(itemStack).stream()
                .map(module -> module.getCapabilityLevel(itemStack, capability))
                .max(Integer::compare)
                .orElse(-1);

        int synergyBonus = Arrays.stream(getSynergyData(itemStack))
                .map(synergyData -> synergyData.capabilities)
                .mapToInt(capabilityData -> capabilityData.getLevel(capability))
                .sum();

        return base + synergyBonus;
    }

    public float getCapabilityEfficiency(ItemStack itemStack, ToolType toolType) {
        if (toolType != null) {
            return getCapabilityEfficiency(itemStack, toolType.getName());
        }
        return -1;
    }

    public float getCapabilityEfficiency(ItemStack itemStack, String capability) {
        if (EnumUtils.isValidEnum(Capability.class, capability)) {
            return getCapabilityEfficiency(itemStack, Capability.valueOf(capability));
        }
        return -1;
    }

    @Override
    public float getCapabilityEfficiency(ItemStack itemStack, Capability capability) {
        if (isBroken(itemStack)) {
            return 0;
        }

        int highestLevel = getAllModules(itemStack).stream()
                .map(module -> module.getCapabilityLevel(itemStack, capability))
                .max(Integer::compare)
                .orElse(-1);

        // grabs the highest efficiency from modules that also provide a capability level (from the module(s) that have the highest capability level
        // adds the efficiency of all modules that have 0 capability level
        float efficiency = getAllModules(itemStack).stream()
                .filter(module -> module.getCapabilityLevel(itemStack, capability) >= highestLevel)
                .map(module -> module.getCapabilityEfficiency(itemStack, capability))
                .max(Float::compare)
                .orElse(1f)
                + (float) getAllModules(itemStack).stream()
                .filter(module -> module.getCapabilityLevel(itemStack, capability) == 0)
                .mapToDouble(module -> module.getCapabilityEfficiency(itemStack, capability))
                .sum();

        return Math.max(0, efficiency + (float) Arrays.stream(getSynergyData(itemStack))
                .map(synergyData -> synergyData.capabilities)
                .mapToDouble(capabilityData -> capabilityData.getEfficiency(capability))
                .sum());
    }

    @Override
    public Collection<Capability> getCapabilities(ItemStack itemStack) {
        if (isBroken(itemStack)) {
            return Collections.emptyList();
        }

        return getAllModules(itemStack).stream()
                .flatMap(module -> ((Collection<Capability>)module.getCapabilities(itemStack)).stream())
                .collect(Collectors.toSet());
    }

    /**
     * Apply special effects and possibly consume required resources after this item has been used to craft or upgrade
     * another item. This is called once for each capability used by the craft, which this item provide.
     * @param providerStack The providing stack, the itemstack for this item
     * @param targetStack The itemstack which is being upgraded/crafted/altered in some way
     * @param player The player performing the actions
     * @param capability The capability used
     * @param capabilityLevel The level of the used capability
     * @param consumeResources
     */
    public ItemStack onCraftConsumeCapability(ItemStack providerStack, ItemStack targetStack, PlayerEntity player,
            Capability capability, int capabilityLevel, boolean consumeResources) {
        ItemStack result = targetStack.copy();

        return result;
    }

    /**
     * Apply special effects and possibly consume required resources after this item has been used to perform a
     * workbench action.
     * @param providerStack The providing stack, the itemstack for this item
     * @param targetStack The itemstack which the action is performed upon
     * @param player The player performing the action
     * @param capability The capability used
     * @param capabilityLevel The level of the used capability
     * @param consumeResources
     */
    public ItemStack onActionConsumeCapability(ItemStack providerStack, ItemStack targetStack, PlayerEntity player,
            Capability capability, int capabilityLevel, boolean consumeResources) {
        ItemStack result = targetStack.copy();

        return result;
    }

    public int getEffectLevel(ItemStack itemStack, ItemEffect effect) {
        if (isBroken(itemStack)) {
            return -1;
        }

        return getAllModules(itemStack).stream()
                .mapToInt(module -> module.getEffectLevel(itemStack, effect))
                .sum();
    }

    public double getEffectEfficiency(ItemStack itemStack, ItemEffect effect) {
        if (isBroken(itemStack)) {
            return 0;
        }

        return getAllModules(itemStack).stream()
                .mapToDouble(module -> module.getEffectEfficiency(itemStack, effect))
                .sum();
    }

    public Collection<ItemEffect> getEffects(ItemStack itemStack) {
        if (isBroken(itemStack)) {
            return Collections.emptyList();
        }

        return getAllModules(itemStack).stream()
                .flatMap(module -> ((Collection<ItemEffect>)module.getEffects(itemStack)).stream())
                .distinct()
                .collect(Collectors.toSet());

    }

    @Override
    public boolean hasEffect(ItemStack itemStack) {
        return Arrays.stream(getImprovements(itemStack))
                .anyMatch(improvement -> improvement.enchantment);
    }

    @Override
    public boolean shouldCauseReequipAnimation(ItemStack oldStack, ItemStack newStack, boolean slotChanged) {
        return slotChanged;
    }

    public ImprovementData[] getImprovements(ItemStack itemStack) {
        return Arrays.stream(getMajorModules(itemStack))
                .filter(Objects::nonNull)
                .flatMap(module -> Arrays.stream(module.getImprovements(itemStack)))
                .toArray(ImprovementData[]::new);
    }

    protected String getDisplayNamePrefixes(ItemStack itemStack) {
        return Stream.concat(
                Arrays.stream(getImprovements(itemStack))
                        .map(improvement -> improvement.key + ".prefix")
                        .filter(I18n::hasKey)
                        .map(I18n::format),
                getAllModules(itemStack).stream()
                        .sorted(Comparator.comparing(module -> module.getItemPrefixPriority(itemStack)))
                        .map(module -> module.getItemPrefix(itemStack))
                        .filter(Objects::nonNull)
        )
                .limit(2)
                .reduce("", (result, prefix) -> result + prefix + " ");
    }

    @Override
    public ITextComponent getDisplayName(ItemStack itemStack) {
        // todo: since getItemStackDisplayName is called on the server we cannot use the I18n service
        if (Environment.get().getDist().isDedicatedServer()) {
            return new StringTextComponent("");
        }

        String name = Arrays.stream(getSynergyData(itemStack))
                .map(synergyData -> synergyData.name)
                .filter(Objects::nonNull)
                .filter(I18n::hasKey)
                .map(I18n::format)
                .findFirst()
                .orElse(null);

        if (name == null) {
            name = getAllModules(itemStack).stream()
                    .sorted(Comparator.comparing(module -> module.getItemNamePriority(itemStack)))
                    .map(module -> module.getItemName(itemStack))
                    .filter(Objects::nonNull)
                    .findFirst().orElse("");
        }

        String prefixes = getDisplayNamePrefixes(itemStack);
        return new StringTextComponent(WordUtils.capitalize(prefixes + name));
    }

    public SynergyData[] getAllSynergyData(ItemStack itemStack) {
        return synergies;
    }

    public SynergyData[] getSynergyData(ItemStack itemStack) {
        if (synergies.length > 0) {
            ArrayList<SynergyData> result = new ArrayList<>();
            String[] moduleKeys = getAllModules(itemStack).stream()
                    .map(ItemModule::getUnlocalizedName)
                    .sorted()
                    .toArray(String[]::new);
            String[] variantKeys = getAllModules(itemStack).stream()
                    .map(module -> module.getVariantData(itemStack))
                    .map(data -> data.key)
                    .sorted()
                    .toArray(String[]::new);

            for (SynergyData synergy : synergies) {
                int variantMatches = 0;
                int moduleMatches = 0;
                for (String variantKey : variantKeys) {
                    if (variantMatches == synergy.moduleVariants.length) {
                        break;
                    }

                    if (variantKey.equals(synergy.moduleVariants[variantMatches])) {
                        variantMatches++;
                    }
                }

                for (String moduleKey : moduleKeys) {
                    if (moduleMatches == synergy.modules.length) {
                        break;
                    }

                    if (moduleKey.equals(synergy.modules[moduleMatches])) {
                        moduleMatches++;
                    }
                }

                if (synergy.moduleVariants.length > 0 && variantMatches == synergy.moduleVariants.length
                        || synergy.modules.length > 0 && moduleMatches == synergy.modules.length) {
                    result.add(synergy);
                }
            }
            return result.toArray(new SynergyData[result.size()]);
        }
        return new SynergyData[0];
    }
}
