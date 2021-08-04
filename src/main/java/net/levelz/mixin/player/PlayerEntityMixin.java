package net.levelz.mixin.player;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.At.Shift;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import org.spongepowered.asm.mixin.injection.At;

import net.levelz.access.PlayerStatsManagerAccess;
import net.levelz.data.LevelLists;
import net.levelz.init.ConfigInit;
import net.levelz.stats.PlayerStatsManager;
import net.levelz.network.PlayerStatsServerPacket;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.player.HungerManager;
import net.minecraft.item.SwordItem;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.math.MathHelper;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.server.network.ServerPlayerEntity;

@Mixin(PlayerEntity.class)
public class PlayerEntityMixin implements PlayerStatsManagerAccess {

    private final PlayerStatsManager playerStatsManager = new PlayerStatsManager();
    private final PlayerEntity playerEntity = (PlayerEntity) (Object) this;
    private boolean isCrit;

    @Inject(method = "readCustomDataFromNbt", at = @At(value = "TAIL"))
    public void readCustomDataFromNbtMixin(NbtCompound tag, CallbackInfo info) {
        this.playerStatsManager.readNbt(tag);
    }

    @Inject(method = "writeCustomDataToNbt", at = @At(value = "TAIL"))
    public void writeCustomDataToNbtMixin(NbtCompound tag, CallbackInfo info) {
        this.playerStatsManager.writeNbt(tag);
    }

    @Inject(method = "addExperience", at = @At(value = "TAIL"))
    public void addExperienceMixin(int experience, CallbackInfo info) {
        playerStatsManager.levelProgress += (float) experience / (float) playerStatsManager.getNextLevelExperience();
        playerStatsManager.totalLevelExperience = MathHelper.clamp(playerStatsManager.totalLevelExperience + experience, 0, Integer.MAX_VALUE);
        while (playerStatsManager.levelProgress >= 1.0F) {
            playerStatsManager.levelProgress = (playerStatsManager.levelProgress - 1.0F) * (float) playerStatsManager.getNextLevelExperience();
            playerStatsManager.addExperienceLevels(1);
            playerStatsManager.levelProgress /= (float) playerStatsManager.getNextLevelExperience();
            if (playerEntity instanceof ServerPlayerEntity) {
                PlayerStatsServerPacket.writeS2CSkillPacket(playerStatsManager, (ServerPlayerEntity) playerEntity);
            }
            if (playerStatsManager.overallLevel > 0 && playerStatsManager.overallLevel % 5 == 0) {
                float f = playerStatsManager.overallLevel > 30 ? 1.0F : (float) playerStatsManager.overallLevel / 30.0F;
                playerEntity.world.playSound((PlayerEntity) null, playerEntity.getX(), playerEntity.getY(), playerEntity.getZ(), SoundEvents.ENTITY_PLAYER_LEVELUP, playerEntity.getSoundCategory(),
                        f * 0.75F, 1.0F);
            }
        }
    }

    @Redirect(method = "addExhaustion", at = @At(value = "INVOKE", target = "Lnet/minecraft/entity/player/HungerManager;addExhaustion(F)V"))
    private void addExhaustion(HungerManager hungerManager, float exhaustion) {
        exhaustion *= ConfigInit.CONFIG.staminaBase - ((float) playerStatsManager.getLevel("stamina") * ConfigInit.CONFIG.staminaBonus);
        hungerManager.addExhaustion(exhaustion);
    }

    @ModifyVariable(method = "attack", at = @At(value = "INVOKE", target = "Lnet/minecraft/item/ItemStack;getItem()Lnet/minecraft/item/Item;"), ordinal = 2)
    private boolean attackMixin(boolean original) {
        if (playerEntity.world.random.nextFloat() < (float) playerStatsManager.getLevel("luck") * ConfigInit.CONFIG.luckCritBonus) {
            isCrit = true;
            return true;
        } else
            isCrit = false;
        return original;
    }

    @ModifyVariable(method = "attack", at = @At(value = "INVOKE", target = "Lnet/minecraft/item/ItemStack;getItem()Lnet/minecraft/item/Item;", shift = At.Shift.AFTER), ordinal = 0)
    private float attackMixinTwo(float original) {
        if (playerStatsManager.getLevel("strength") == ConfigInit.CONFIG.maxLevel && ConfigInit.CONFIG.attackDoubleDamageChance > playerEntity.world.random.nextFloat()) {
            return original * 2F;
        } else
            return isCrit ? original * ConfigInit.CONFIG.critDmgBonus : original;
    }

    @ModifyVariable(method = "attack", at = @At(value = "INVOKE_ASSIGN", target = "Lnet/minecraft/entity/player/PlayerEntity;getAttackCooldownProgress(F)F"), ordinal = 0)
    private float attackMixinThree(float original) {
        if (!playerEntity.isCreative() && playerEntity.getMainHandStack().getItem() instanceof SwordItem && !PlayerStatsManager.playerLevelisHighEnough(playerEntity, LevelLists.swordList,
                ((SwordItem) playerEntity.getMainHandStack().getItem()).getMaterial().toString().toLowerCase(), true)) {
            return original - ((SwordItem) playerEntity.getMainHandStack().getItem()).getAttackDamage();
        }
        return original;
    }

    @ModifyVariable(method = "attack", at = @At(value = "INVOKE_ASSIGN", target = "Lnet/minecraft/entity/player/PlayerEntity;getAttackCooldownProgress(F)F"), ordinal = 1)
    private float attackMixinFour(float original) {
        if (!playerEntity.isCreative() && playerEntity.getMainHandStack().getItem() instanceof SwordItem && !PlayerStatsManager.playerLevelisHighEnough(playerEntity, LevelLists.swordList,
                ((SwordItem) playerEntity.getMainHandStack().getItem()).getMaterial().toString().toLowerCase(), true)) {
            return 0;
        }
        return original;
    }

    @Inject(method = "damage", at = @At(value = "INVOKE", target = "Lnet/minecraft/entity/player/PlayerEntity;dropShoulderEntities()V", shift = Shift.AFTER), cancellable = true)
    private void damageMixin(DamageSource source, float amount, CallbackInfoReturnable<Boolean> info) {
        if (playerStatsManager.getLevel("defense") == ConfigInit.CONFIG.maxLevel && source.getAttacker() != null && playerEntity.world.random.nextFloat() <= ConfigInit.CONFIG.defenseReflectChance) {
            source.getAttacker().damage(source, amount);
        }
        if (playerStatsManager.getLevel("agility") == ConfigInit.CONFIG.maxLevel && playerEntity.world.random.nextFloat() <= ConfigInit.CONFIG.movementMissChance) {
            info.setReturnValue(false);
        }
    }

    @Override
    public PlayerStatsManager getPlayerStatsManager(PlayerEntity player) {
        return this.playerStatsManager;
    }

}