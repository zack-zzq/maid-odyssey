package com.ziqizhu.maidodyssey;

import com.mojang.logging.LogUtils;
import com.ziqizhu.maidodyssey.hazard.HazardTracker;
import com.ziqizhu.maidodyssey.maid.work.AshEating;
import com.ziqizhu.maidodyssey.report.MaidReporter;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.config.ModConfig;
import org.slf4j.Logger;

@Mod(MaidOdyssey.MOD_ID)
public final class MaidOdyssey {
    public static final String MOD_ID = "maid_odyssey";
    public static final Logger LOGGER = LogUtils.getLogger();

    public MaidOdyssey() {
        ModLoadingContext.get().registerConfig(ModConfig.Type.COMMON, MaidOdysseyConfig.SPEC);
        MinecraftForge.EVENT_BUS.register(MaidReporter.class);
        MinecraftForge.EVENT_BUS.register(HazardTracker.class);
        MinecraftForge.EVENT_BUS.register(AshEating.class);
    }

    public static ResourceLocation id(String path) {
        return new ResourceLocation(MOD_ID, path);
    }
}
