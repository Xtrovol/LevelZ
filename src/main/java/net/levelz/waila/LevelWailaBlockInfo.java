package net.levelz.waila;

import java.util.List;

import mcp.mobius.waila.api.IBlockAccessor;
import mcp.mobius.waila.api.IBlockComponentProvider;
import mcp.mobius.waila.api.IPluginConfig;
import mcp.mobius.waila.api.IRegistrar;
import mcp.mobius.waila.api.TooltipPosition;
import net.levelz.access.PlayerStatsManagerAccess;
import net.levelz.stats.PlayerStatsManager;
import net.minecraft.block.Block;
import net.minecraft.text.Text;
import net.minecraft.text.TranslatableText;
import net.minecraft.util.Formatting;
import net.minecraft.util.Identifier;
import net.minecraft.util.registry.Registry;

public class LevelWailaBlockInfo extends LevelFeature implements IBlockComponentProvider {

    public static Identifier MINEABLE_INFO = new Identifier("levelz", "mineable_info");
    public static Identifier MINEABLE_LEVEL_INFO = new Identifier("levelz", "mineable_level_info");

    @Override
    public void initialize(IRegistrar registrar) {
        registrar.addConfig(MINEABLE_INFO, true);
        registrar.addConfig(MINEABLE_LEVEL_INFO, false);
        registrar.addComponent(this, TooltipPosition.BODY, Block.class);
    }

    @Override
    public void appendBody(List<Text> tooltip, IBlockAccessor accessor, IPluginConfig config) {
        if (config.get(MINEABLE_INFO)) {
            PlayerStatsManager playerStatsManager = ((PlayerStatsManagerAccess) accessor.getPlayer()).getPlayerStatsManager(accessor.getPlayer());
            if (playerStatsManager.lockedBlockIds.contains(Registry.BLOCK.getRawId(accessor.getBlock()))) {
                if (config.get(MINEABLE_LEVEL_INFO)) {
                    tooltip.add(new TranslatableText("block.levelz.locked_with_level.tooltip", playerStatsManager.getLevel("mining")).formatted(Formatting.RED));
                } else
                    tooltip.add(new TranslatableText("block.levelz.locked.tooltip"));
            }
        }
    }
}
