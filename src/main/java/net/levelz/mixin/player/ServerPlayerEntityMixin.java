package net.levelz.mixin.player;

import com.mojang.authlib.GameProfile;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.At;

import net.levelz.access.PlayerStatsManagerAccess;
import net.levelz.access.PlayerSyncAccess;
import net.levelz.init.ConfigInit;
import net.levelz.network.PlayerStatsServerPacket;
import net.levelz.stats.PlayerStatsManager;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;

@Mixin(ServerPlayerEntity.class)
public class ServerPlayerEntityMixin implements PlayerSyncAccess {
    PlayerStatsManager playerStatsManager = ((PlayerStatsManagerAccess) this).getPlayerStatsManager((PlayerEntity) (Object) this);
    private int syncedLevelExperience = -99999999;
    private boolean syncTeleportStats = false;
    private int tinySyncTicker = 0;

    @Inject(method = "<init>", at = @At(value = "TAIL"))
    private void initMixin(MinecraftServer server, ServerWorld world, GameProfile profile, CallbackInfo info) {
        ServerPlayerEntity serverPlayerEntity = (ServerPlayerEntity) (Object) this;
        serverPlayerEntity.getAttributeInstance(EntityAttributes.GENERIC_MAX_HEALTH)
                .setBaseValue(ConfigInit.CONFIG.healthBase + (double) playerStatsManager.getLevel("health") * ConfigInit.CONFIG.healthBonus);
        serverPlayerEntity.getAttributeInstance(EntityAttributes.GENERIC_MOVEMENT_SPEED)
                .setBaseValue(ConfigInit.CONFIG.movementBase + (double) playerStatsManager.getLevel("agility") * ConfigInit.CONFIG.movementBonus);
        serverPlayerEntity.getAttributeInstance(EntityAttributes.GENERIC_ATTACK_DAMAGE)
                .setBaseValue(ConfigInit.CONFIG.attackBase + (double) playerStatsManager.getLevel("strength") * ConfigInit.CONFIG.attackBonus);
        serverPlayerEntity.getAttributeInstance(EntityAttributes.GENERIC_ARMOR)
                .setBaseValue(ConfigInit.CONFIG.defenseBase + (double) playerStatsManager.getLevel("defense") * ConfigInit.CONFIG.defenseBonus);
        serverPlayerEntity.getAttributeInstance(EntityAttributes.GENERIC_LUCK).setBaseValue(ConfigInit.CONFIG.luckBase + (double) playerStatsManager.getLevel("luck") * ConfigInit.CONFIG.luckBonus);
        // Init stats - Can't send to client cause network hander is null -> onSpawn packet
    }

    @Inject(method = "addExperience", at = @At(value = "TAIL"))
    private void addExperienceMixin(int experience, CallbackInfo info) {
        this.syncedLevelExperience = -1;
    }

    @Inject(method = "playerTick", at = @At(value = "FIELD", target = "Lnet/minecraft/server/network/ServerPlayerEntity;totalExperience:I", ordinal = 0, shift = At.Shift.BEFORE))
    private void playerTickMixin(CallbackInfo info) {
        if (playerStatsManager.totalLevelExperience != this.syncedLevelExperience) {
            this.syncedLevelExperience = playerStatsManager.totalLevelExperience;
            PlayerStatsServerPacket.writeS2CXPPacket(playerStatsManager, ((ServerPlayerEntity) (Object) this));
            if (this.syncTeleportStats) {
                PlayerStatsServerPacket.writeS2CSkillPacket(playerStatsManager, (ServerPlayerEntity) (Object) this);
                this.syncTeleportStats = false;
            }
        }
        if (this.tinySyncTicker > 0) {
            this.tinySyncTicker--;
            if (this.tinySyncTicker < 1) {
                syncStats(false);
            }
        }

    }

    @Inject(method = "onSpawn", at = @At(value = "TAIL"))
    private void onSpawnMixin(CallbackInfo info) {
        PlayerStatsServerPacket.writeS2CSkillPacket(playerStatsManager, (ServerPlayerEntity) (Object) this);
    }

    @Inject(method = "copyFrom", at = @At(value = "FIELD", target = "Lnet/minecraft/server/network/ServerPlayerEntity;syncedExperience:I", ordinal = 0))
    private void copyFromMixin(ServerPlayerEntity oldPlayer, boolean alive, CallbackInfo info) {
        syncStats(false);
    }

    // tinySyncer is necessary due to client player issues
    // client player isn't readily loaded when S2C Packets roll out for any reason
    @Override
    public void syncStats(boolean syncDelay) {
        this.syncTeleportStats = true;
        this.syncedLevelExperience = -1;
        if (syncDelay) {
            this.tinySyncTicker = 5;
        }
    }

}