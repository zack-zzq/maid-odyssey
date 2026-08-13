package com.ziqizhu.maidodyssey.gt;

import com.ziqizhu.maidodyssey.MaidOdyssey;
import net.minecraft.core.BlockPos;
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

    private static final Map<String, Optional<Method>> METHOD_CACHE = new ConcurrentHashMap<>();
    private static final Map<String, Boolean> TYPE_CACHE = new ConcurrentHashMap<>();

    private static volatile boolean bootstrapped;
    private static Class<?> metaMachineBlockClass;
    private static Method getMachineStatic;
    private static Object[] toolTypes;
    private static Method toolHelperIs;
    private static Method toolTypeIs;
    private static Method toolHelperCanUse;
    private static Method toolHelperDamage;
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

            Class<?> toolTypeClass = findClass(CLASS_TOOL_TYPE);
            Class<?> toolHelperClass = findClass(CLASS_TOOL_HELPER);
            if (toolTypeClass == null || toolHelperClass == null) {
                bindingProblem = "GregTech tool API not found (GTToolType / ToolHelper)";
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
            MaidOdyssey.LOGGER.info("GregTech binding ready (problem: {})", bindingProblem == null ? "none" : bindingProblem);
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
