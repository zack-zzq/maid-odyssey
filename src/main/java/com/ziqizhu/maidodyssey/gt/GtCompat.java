package com.ziqizhu.maidodyssey.gt;

import com.ziqizhu.maidodyssey.MaidOdyssey;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.items.IItemHandler;
import net.minecraftforge.registries.ForgeRegistries;
import org.jetbrains.annotations.Nullable;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Everything this mod needs from GregTech Modern, reached by reflection.
 * <p>
 * The GregTech Odyssey fork rebases on upstream often, ships a closed source {@code gtolib} and
 * mixins into both hatch classes from {@code GTOCore}. Binding to it at compile time would tie
 * this addon to one exact fork build, so instead every call goes through here and degrades into a
 * readable in-game message when a signature no longer matches.
 */
public final class GtCompat {
    public static final String GTCEU_ID = "gtceu";

    private static final String CLASS_META_MACHINE = "com.gregtechceu.gtceu.api.machine.MetaMachine";
    private static final String CLASS_META_MACHINE_BLOCK = "com.gregtechceu.gtceu.api.block.MetaMachineBlock";
    private static final String CLASS_TOOL_HELPER = "com.gregtechceu.gtceu.api.item.tool.ToolHelper";
    private static final String CLASS_TOOL_TYPE = "com.gregtechceu.gtceu.api.item.tool.GTToolType";
    private static final ResourceLocation DUCT_TAPE_ID = new ResourceLocation(GTCEU_ID, "duct_tape");

    private static final String CLASS_HEAT_CONTAINER = "com.gtolib.api.capability.IHeatContainer";
    private static final String CLASS_HEAT_HANDLER = "com.gtolib.api.machine.heat.HeatHandler";
    private static final Set<String> KNOWN_HEAT_BLOCK_PATHS = Set.of(
            "heater", "electric_heater", "mana_heater",
            "cooler", "boiler", "alchemy_cauldron",
            "heat_hatch", "advanced_heat_hatch"
    );

    private static final Map<String, Optional<Method>> METHOD_CACHE = new ConcurrentHashMap<>();
    private static final Map<String, Optional<Field>> FIELD_CACHE = new ConcurrentHashMap<>();
    private static final Map<Class<?>, Optional<Field>> HEAT_FIELD_CACHE = new ConcurrentHashMap<>();
    private static final Map<String, Boolean> TYPE_CACHE = new ConcurrentHashMap<>();

    private static volatile boolean bootstrapped;
    private static Class<?> metaMachineBlockClass;
    private static Method getMachineStatic;
    private static Object[] toolTypes;
    private static Method toolHelperIs;
    private static Method toolTypeIs;
    private static Method toolHelperCanUse;
    private static Method toolHelperDamage;
    private static Class<?> heatContainerClass;
    private static Class<?> heatHandlerClass;
    private static String bindingProblem;

    private GtCompat() {
    }

    public static boolean isLoaded() {
        return ModList.get().isLoaded(GTCEU_ID);
    }

    /** A human readable reason why the GregTech binding is incomplete. */
    public static String bindingProblem() {
        bootstrap();
        return bindingProblem == null ? "unknown reason, see the log" : bindingProblem;
    }

    private static void bootstrap() {
        if (bootstrapped) {
            return;
        }
        synchronized (GtCompat.class) {
            if (bootstrapped) {
                return;
            }
            bootstrapped = true;
            if (!isLoaded()) {
                bindingProblem = "GregTech Modern is not installed";
                return;
            }
            metaMachineBlockClass = findClass(CLASS_META_MACHINE_BLOCK);
            Class<?> metaMachineClass = findClass(CLASS_META_MACHINE);
            if (metaMachineClass != null) {
                getMachineStatic = staticMethod(metaMachineClass, "getMachine", BlockGetter.class, BlockPos.class);
            }

            // Optional: GTOCore heat lives in closed gtolib. Missing classes must not fail the
            // muffler / maintenance binding — those still work without GTOCore.
            heatContainerClass = findClass(CLASS_HEAT_CONTAINER);
            heatHandlerClass = findClass(CLASS_HEAT_HANDLER);

            Class<?> toolTypeClass = findClass(CLASS_TOOL_TYPE);
            Class<?> toolHelperClass = findClass(CLASS_TOOL_HELPER);
            if (toolTypeClass == null || toolHelperClass == null) {
                bindingProblem = "GregTech tool API not found (GTToolType / ToolHelper)";
                MaidOdyssey.LOGGER.info("GregTech binding ready (problem: {}, heat: {})",
                        bindingProblem,
                        heatContainerClass != null || heatHandlerClass != null ? "present" : "absent");
                return;
            }

            toolTypes = new Object[MaintenanceProblem.VALUES.length];
            StringBuilder missing = new StringBuilder();
            for (MaintenanceProblem problem : MaintenanceProblem.VALUES) {
                try {
                    Field field = toolTypeClass.getField(problem.gtToolTypeField());
                    toolTypes[problem.index()] = field.get(null);
                } catch (Throwable t) {
                    if (missing.length() > 0) {
                        missing.append(", ");
                    }
                    missing.append(problem.gtToolTypeField());
                }
            }

            toolHelperIs = staticMethod(toolHelperClass, "is", ItemStack.class, toolTypeClass);
            toolTypeIs = instanceMethod(toolTypeClass, "is", ItemStack.class);
            toolHelperCanUse = staticMethod(toolHelperClass, "canUse", ItemStack.class);
            toolHelperDamage = staticMethod(toolHelperClass, "damageItem", ItemStack.class, LivingEntity.class, int.class);

            if (missing.length() > 0) {
                bindingProblem = "GTToolType is missing " + missing + ", falling back to item tags";
            } else if (toolHelperIs == null && toolTypeIs == null) {
                bindingProblem = "ToolHelper#is not found, falling back to item tags";
            }

            MaidOdyssey.LOGGER.info("GregTech binding ready (problem: {}, heat: {})",
                    bindingProblem == null ? "none" : bindingProblem,
                    heatContainerClass != null || heatHandlerClass != null ? "present" : "absent");
        }
    }

    // ------------------------------------------------------------------ machines

    /**
     * @return the {@code MetaMachine} sitting at that position, or null when the block is not a
     * GregTech machine.
     */
    @Nullable
    public static Object getMachine(BlockGetter level, BlockPos pos) {
        if (!isLoaded()) {
            return null;
        }
        bootstrap();
        if (getMachineStatic != null) {
            Object machine = invoke(getMachineStatic, null, level, pos);
            if (machine != null) {
                return machine;
            }
        }
        BlockState state = level.getBlockState(pos);
        if (!state.hasBlockEntity()) {
            return null;
        }
        if (metaMachineBlockClass != null && !metaMachineBlockClass.isInstance(state.getBlock())) {
            return null;
        }
        BlockEntity blockEntity = level.getBlockEntity(pos);
        if (blockEntity == null) {
            return null;
        }
        Method getter = method(blockEntity.getClass(), "getMetaMachine");
        if (getter == null) {
            return null;
        }
        return invoke(getter, blockEntity);
    }

    /**
     * @return which of the wanted chores this machine currently needs, or null when it needs none.
     */
    @Nullable
    public static GtJob findJob(BlockGetter level, BlockPos pos, Set<GtJob> wanted) {
        Object machine = getMachine(level, pos);
        if (machine == null) {
            return null;
        }
        if (wanted.contains(GtJob.MUFFLER) && isMuffler(machine)) {
            IItemHandler inventory = getMufflerInventory(machine);
            if (inventory != null && hasAnyItem(inventory)) {
                return GtJob.MUFFLER;
            }
        }
        if (wanted.contains(GtJob.MAINTENANCE) && isMaintenance(machine)
                && !isFullAutoMaintenance(machine) && hasMaintenanceProblems(machine)) {
            return GtJob.MAINTENANCE;
        }
        return null;
    }

    // ------------------------------------------------------------------ muffler hatch

    public static boolean isMuffler(Object machine) {
        return hasType(machine.getClass(), "IMufflerMachine") || hasType(machine.getClass(), "MufflerPartMachine");
    }

    /** The ash inventory of a muffler hatch. GregTech's {@code CustomItemStackHandler} is an {@link IItemHandler}. */
    @Nullable
    public static IItemHandler getMufflerInventory(Object machine) {
        Method getter = method(machine.getClass(), "getInventory");
        if (getter == null) {
            noteProblem(machine.getClass().getSimpleName() + "#getInventory() is missing");
            return null;
        }
        Object inventory = invoke(getter, machine);
        if (inventory instanceof IItemHandler handler) {
            return handler;
        }
        noteProblem(machine.getClass().getSimpleName() + "#getInventory() is not an IItemHandler");
        return null;
    }

    public static boolean hasAnyItem(IItemHandler handler) {
        for (int slot = 0; slot < handler.getSlots(); slot++) {
            if (!handler.getStackInSlot(slot).isEmpty()) {
                return true;
            }
        }
        return false;
    }

    /**
     * Exhaust leaves through this face. Electric blast furnace mufflers usually face up.
     * Falls back to {@link Direction#UP} when the machine has no facing method.
     */
    public static Direction mufflerExhaustFacing(Object machine) {
        Direction facing = getFrontFacing(machine);
        return facing == null ? Direction.UP : facing;
    }

    @Nullable
    public static Direction getFrontFacing(Object machine) {
        Method getter = method(machine.getClass(), "getFrontFacing");
        if (getter == null) {
            return null;
        }
        Object value = invokeQuiet(getter, machine);
        return value instanceof Direction direction ? direction : null;
    }

    // ------------------------------------------------------------------ GTO heat

    /**
     * Cheap filter: only GregTech / GTOCore blocks can be mufflers or heat sources.
     */
    public static boolean isMaybeHazardBlock(BlockState state) {
        bootstrap();
        if (metaMachineBlockClass != null && metaMachineBlockClass.isInstance(state.getBlock())) {
            return true;
        }
        ResourceLocation id = ForgeRegistries.BLOCKS.getKey(state.getBlock());
        return id != null && (GTCEU_ID.equals(id.getNamespace()) || "gtocore".equals(id.getNamespace()));
    }

    public static boolean isKnownHeatMachineBlock(BlockState state) {
        ResourceLocation id = ForgeRegistries.BLOCKS.getKey(state.getBlock());
        return id != null && "gtocore".equals(id.getNamespace()) && KNOWN_HEAT_BLOCK_PATHS.contains(id.getPath());
    }

    public static boolean isHeaterLike(Object machine) {
        Class<?> type = machine.getClass();
        return hasType(type, "HeaterMachine")
                || hasType(type, "ElectricHeaterMachine")
                || hasType(type, "ManaHeaterMachine");
    }

    /**
     * The heat storage on this machine, or null when it has none / gtolib is missing.
     * Pipes implement the same interface but report temperature 0.
     */
    @Nullable
    public static Object findHeatContainer(@Nullable Object machine, @Nullable BlockEntity blockEntity) {
        bootstrap();
        Object found = findHeatContainerOn(machine);
        return found != null ? found : findHeatContainerOn(blockEntity);
    }

    @Nullable
    private static Object findHeatContainerOn(@Nullable Object owner) {
        if (owner == null) {
            return null;
        }
        if (isHeatContainer(owner)) {
            return owner;
        }
        Object viaGetter = invokeNamed(owner, "getHeatContainer");
        if (viaGetter == null) {
            viaGetter = invokeNamed(owner, "getHeatHandler");
        }
        if (isHeatContainer(viaGetter)) {
            return viaGetter;
        }
        Object viaField = readHeatField(owner);
        return isHeatContainer(viaField) ? viaField : null;
    }

    private static boolean isHeatContainer(@Nullable Object value) {
        if (value == null) {
            return false;
        }
        if (heatContainerClass != null && heatContainerClass.isInstance(value)) {
            return true;
        }
        if (heatHandlerClass != null && heatHandlerClass.isInstance(value)) {
            return true;
        }
        Class<?> type = value.getClass();
        return hasType(type, "IHeatContainer") || hasType(type, "HeatHandler");
    }

    /**
     * Kelvin. {@link Double#NaN} when the value cannot be read (caller should treat that as unknown,
     * not as ambient).
     */
    public static double getHeatTemperature(@Nullable Object container) {
        if (container == null) {
            return Double.NaN;
        }
        Method getter = method(container.getClass(), "getTemperature");
        if (getter == null) {
            return Double.NaN;
        }
        Object value = invokeQuiet(getter, container);
        return value instanceof Number number ? number.doubleValue() : Double.NaN;
    }

    // ------------------------------------------------------------------ maintenance hatch

    public static boolean isMaintenance(Object machine) {
        return hasType(machine.getClass(), "IMaintenanceMachine")
                || hasType(machine.getClass(), "MaintenanceHatchPartMachine");
    }

    public static boolean isFullAutoMaintenance(Object machine) {
        Method getter = method(machine.getClass(), "isFullAuto");
        return getter != null && Boolean.TRUE.equals(invoke(getter, machine));
    }

    public static boolean hasMaintenanceProblems(Object machine) {
        Method getter = method(machine.getClass(), "hasMaintenanceProblems");
        return getter != null && Boolean.TRUE.equals(invoke(getter, machine));
    }

    /** Six bit mask; a <em>set</em> bit means that problem is already fixed. */
    public static byte maintenanceMask(Object machine) {
        Method getter = method(machine.getClass(), "getMaintenanceProblems");
        Object value = getter == null ? null : invoke(getter, machine);
        return value instanceof Number number ? number.byteValue() : (byte) 0b111111;
    }

    public static boolean markProblemFixed(Object machine, int index) {
        Method setter = method(machine.getClass(), "setMaintenanceFixed", int.class);
        if (setter == null) {
            noteProblem(machine.getClass().getSimpleName() + "#setMaintenanceFixed(int) is missing");
            return false;
        }
        return invokeVoid(setter, machine, index);
    }

    public static boolean fixEverything(Object machine) {
        Method fixAll = method(machine.getClass(), "fixAllMaintenanceProblems");
        if (fixAll != null) {
            return invokeVoid(fixAll, machine);
        }
        boolean done = false;
        for (MaintenanceProblem problem : MaintenanceProblem.VALUES) {
            done |= markProblemFixed(machine, problem.index());
        }
        return done;
    }

    public static void setTaped(Object machine, boolean taped) {
        Method setter = method(machine.getClass(), "setTaped", boolean.class);
        if (setter != null) {
            invokeVoid(setter, machine, taped);
        }
    }

    public static void resetMaintenanceTimer(Object machine) {
        Method setter = method(machine.getClass(), "setTimeActive", int.class);
        if (setter != null) {
            invokeVoid(setter, machine, 0);
        }
    }

    // ------------------------------------------------------------------ tools

    /** @return true when the stack is a usable tool for that maintenance problem. */
    public static boolean isToolFor(ItemStack stack, MaintenanceProblem problem) {
        if (stack.isEmpty()) {
            return false;
        }
        bootstrap();
        Boolean matched = null;
        Object toolType = toolTypes == null ? null : toolTypes[problem.index()];
        if (toolType != null) {
            if (toolHelperIs != null) {
                Object result = invoke(toolHelperIs, null, stack, toolType);
                if (result instanceof Boolean bool) {
                    matched = bool;
                }
            }
            if (matched == null && toolTypeIs != null) {
                Object result = invoke(toolTypeIs, toolType, stack);
                if (result instanceof Boolean bool) {
                    matched = bool;
                }
            }
        }
        if (matched == null) {
            matched = matchesByTag(stack, problem);
        }
        return matched && isToolUsable(stack);
    }

    private static boolean matchesByTag(ItemStack stack, MaintenanceProblem problem) {
        return problem.fallbackTags().stream().anyMatch(stack::is);
    }

    private static boolean isToolUsable(ItemStack stack) {
        int reserve = com.ziqizhu.maidodyssey.MaidOdysseyConfig.toolDurabilityReserve();
        if (stack.isDamageableItem() && stack.getMaxDamage() - stack.getDamageValue() <= reserve) {
            return false;
        }
        if (toolHelperCanUse != null) {
            Object result = invoke(toolHelperCanUse, null, stack);
            if (result instanceof Boolean bool) {
                return bool;
            }
        }
        return true;
    }

    /** Applies one point of tool wear the same way GregTech does when a player repairs a hatch. */
    public static void damageTool(ItemStack stack, LivingEntity user) {
        bootstrap();
        if (toolHelperDamage != null && invokeVoid(toolHelperDamage, null, stack, user, 1)) {
            return;
        }
        stack.hurtAndBreak(1, user, broken -> broken.broadcastBreakEvent(net.minecraft.world.InteractionHand.MAIN_HAND));
    }

    public static boolean isDuctTape(ItemStack stack) {
        return !stack.isEmpty() && DUCT_TAPE_ID.equals(ForgeRegistries.ITEMS.getKey(stack.getItem()));
    }

    // ------------------------------------------------------------------ misc helpers

    /** First of the given item ids that actually exists, otherwise the fallback. */
    public static Item itemOrFallback(Item fallback, String... candidateIds) {
        for (String candidateId : candidateIds) {
            ResourceLocation id = ResourceLocation.tryParse(candidateId);
            if (id == null) {
                continue;
            }
            Item item = ForgeRegistries.ITEMS.getValue(id);
            if (item != null && item != net.minecraft.world.item.Items.AIR) {
                return item;
            }
        }
        return fallback;
    }

    // ------------------------------------------------------------------ reflection plumbing

    @Nullable
    private static Class<?> findClass(String name) {
        try {
            return Class.forName(name, false, GtCompat.class.getClassLoader());
        } catch (Throwable t) {
            return null;
        }
    }

    @Nullable
    private static Method staticMethod(Class<?> owner, String name, Class<?>... parameters) {
        Method found = method(owner, name, parameters);
        return found != null && java.lang.reflect.Modifier.isStatic(found.getModifiers()) ? found : null;
    }

    @Nullable
    private static Method instanceMethod(Class<?> owner, String name, Class<?>... parameters) {
        Method found = method(owner, name, parameters);
        return found != null && !java.lang.reflect.Modifier.isStatic(found.getModifiers()) ? found : null;
    }

    @Nullable
    private static Method method(Class<?> owner, String name, Class<?>... parameters) {
        StringBuilder key = new StringBuilder(owner.getName()).append('#').append(name);
        for (Class<?> parameter : parameters) {
            key.append(';').append(parameter.getName());
        }
        return METHOD_CACHE.computeIfAbsent(key.toString(), ignored -> {
            try {
                Method found = owner.getMethod(name, parameters);
                try {
                    found.setAccessible(true);
                } catch (Throwable ignoredAccess) {
                    // Public method on a public class, reflection will work anyway.
                }
                return Optional.of(found);
            } catch (Throwable t) {
                return Optional.empty();
            }
        }).orElse(null);
    }

    @Nullable
    private static Object invoke(Method method, @Nullable Object target, Object... arguments) {
        try {
            return method.invoke(target, arguments);
        } catch (Throwable t) {
            reportReflectionFailure(method, t);
            return null;
        }
    }

    /**
     * Same as {@link #invoke} but does not record a binding problem. Heat lookup is optional and
     * must not poison the muffler / maintenance error message.
     */
    @Nullable
    private static Object invokeQuiet(Method method, @Nullable Object target, Object... arguments) {
        try {
            return method.invoke(target, arguments);
        } catch (Throwable t) {
            return null;
        }
    }

    @Nullable
    private static Object invokeNamed(Object owner, String name) {
        Method getter = method(owner.getClass(), name);
        return getter == null ? null : invokeQuiet(getter, owner);
    }

    @Nullable
    private static Object readHeatField(Object owner) {
        Optional<Field> cached = HEAT_FIELD_CACHE.computeIfAbsent(owner.getClass(), GtCompat::findHeatField);
        if (cached.isEmpty()) {
            return null;
        }
        try {
            return cached.get().get(owner);
        } catch (Throwable t) {
            return null;
        }
    }

    private static Optional<Field> findHeatField(Class<?> type) {
        Field named = declaredField(type, "heatContainer");
        if (named == null) {
            named = declaredField(type, "heatHandler");
        }
        if (named != null && looksLikeHeatType(named.getType())) {
            return Optional.of(named);
        }
        for (Class<?> current = type; current != null && current != Object.class; current = current.getSuperclass()) {
            Field[] fields;
            try {
                fields = current.getDeclaredFields();
            } catch (Throwable t) {
                continue;
            }
            for (Field field : fields) {
                if (!looksLikeHeatType(field.getType())) {
                    continue;
                }
                try {
                    field.setAccessible(true);
                } catch (Throwable ignored) {
                    // continue anyway; get() may still work
                }
                return Optional.of(field);
            }
        }
        return Optional.empty();
    }

    private static boolean looksLikeHeatType(Class<?> fieldType) {
        if (heatContainerClass != null && heatContainerClass.isAssignableFrom(fieldType)) {
            return true;
        }
        if (heatHandlerClass != null && heatHandlerClass.isAssignableFrom(fieldType)) {
            return true;
        }
        String simple = fieldType.getSimpleName();
        return "IHeatContainer".equals(simple) || "HeatHandler".equals(simple);
    }

    @Nullable
    private static Field declaredField(Class<?> owner, String name) {
        String key = owner.getName() + '#' + name;
        return FIELD_CACHE.computeIfAbsent(key, ignored -> {
            for (Class<?> current = owner; current != null && current != Object.class; current = current.getSuperclass()) {
                try {
                    Field found = current.getDeclaredField(name);
                    try {
                        found.setAccessible(true);
                    } catch (Throwable ignoredAccess) {
                        // get() may still work on a public field
                    }
                    return Optional.of(found);
                } catch (Throwable t) {
                    // try superclass
                }
            }
            return Optional.empty();
        }).orElse(null);
    }

    private static boolean invokeVoid(Method method, @Nullable Object target, Object... arguments) {
        try {
            method.invoke(target, arguments);
            return true;
        } catch (Throwable t) {
            reportReflectionFailure(method, t);
            return false;
        }
    }

    private static void reportReflectionFailure(Method method, Throwable t) {
        String message = method.getDeclaringClass().getSimpleName() + '#' + method.getName() + " threw " + t;
        noteProblem(message);
        MaidOdyssey.LOGGER.error("{} failed", message, t);
    }

    private static void noteProblem(String message) {
        if (bindingProblem == null) {
            bindingProblem = message;
            MaidOdyssey.LOGGER.warn("GregTech binding problem: {}", message);
        }
    }

    private static boolean hasType(Class<?> type, String simpleName) {
        return TYPE_CACHE.computeIfAbsent(type.getName() + '@' + simpleName, ignored -> scanType(type, simpleName));
    }

    private static boolean scanType(@Nullable Class<?> type, String simpleName) {
        for (Class<?> current = type; current != null && current != Object.class; current = current.getSuperclass()) {
            if (simpleName.equals(current.getSimpleName()) || scanInterfaces(current, simpleName)) {
                return true;
            }
        }
        return false;
    }

    private static boolean scanInterfaces(Class<?> type, String simpleName) {
        for (Class<?> implemented : type.getInterfaces()) {
            if (simpleName.equals(implemented.getSimpleName()) || scanInterfaces(implemented, simpleName)) {
                return true;
            }
        }
        return false;
    }
}
