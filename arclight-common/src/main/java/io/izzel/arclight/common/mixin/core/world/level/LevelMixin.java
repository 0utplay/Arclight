package io.izzel.arclight.common.mixin.core.world.level;

import io.izzel.arclight.common.bridge.core.entity.EntityBridge;
import io.izzel.arclight.common.bridge.core.world.WorldBridge;
import io.izzel.arclight.common.bridge.core.world.border.WorldBorderBridge;
import io.izzel.arclight.common.bridge.core.world.server.ServerChunkProviderBridge;
import io.izzel.arclight.common.mod.ArclightMod;
import io.izzel.arclight.common.mod.server.ArclightServer;
import io.izzel.arclight.common.mod.server.world.WrappedWorlds;
import io.izzel.arclight.common.mod.util.ArclightCaptures;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Registry;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.LevelWriter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.border.WorldBorder;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.dimension.DimensionType;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.level.storage.LevelData;
import net.minecraft.world.level.storage.ServerLevelData;
import net.minecraft.world.level.storage.WritableLevelData;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.craftbukkit.v.CraftServer;
import org.bukkit.craftbukkit.v.CraftWorld;
import org.bukkit.craftbukkit.v.block.CraftBlock;
import org.bukkit.craftbukkit.v.block.data.CraftBlockData;
import org.bukkit.craftbukkit.v.event.CraftEventFactory;
import org.bukkit.craftbukkit.v.generator.CustomChunkGenerator;
import org.bukkit.craftbukkit.v.util.CraftNamespacedKey;
import org.bukkit.event.block.BlockPhysicsEvent;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.event.world.GenericGameEvent;
import org.bukkit.generator.ChunkGenerator;
import org.spigotmc.SpigotWorldConfig;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import org.spongepowered.asm.mixin.injection.callback.LocalCapture;

import javax.annotation.Nullable;
import java.lang.reflect.Field;
import java.util.Optional;
import java.util.function.Supplier;

@Mixin(Level.class)
public abstract class LevelMixin implements WorldBridge, LevelWriter {

    // @formatter:off
    @Shadow @Nullable public BlockEntity getBlockEntity(BlockPos pos) { return null; }
    @Shadow public abstract BlockState getBlockState(BlockPos pos);
    @Shadow public abstract WorldBorder getWorldBorder();
    @Shadow @Final private WorldBorder worldBorder;
    @Shadow public abstract long getDayTime();
    @Shadow public abstract MinecraftServer shadow$getServer();
    @Shadow @Final private DimensionType dimensionType;
    @Shadow public abstract LevelData getLevelData();
    @Shadow public abstract ResourceKey<Level> dimension();
    @Accessor("thread") public abstract Thread arclight$getMainThread();
    // @formatter:on

    private ResourceKey<DimensionType> typeKey;
    protected CraftWorld world;
    public boolean pvpMode;
    public boolean keepSpawnInMemory = true;
    public long ticksPerAnimalSpawns;
    public long ticksPerMonsterSpawns;
    public long ticksPerWaterSpawns;
    public long ticksPerWaterAmbientSpawns;
    public long ticksPerAmbientSpawns;
    public boolean populating;
    public org.bukkit.generator.ChunkGenerator generator;
    protected org.bukkit.World.Environment environment;
    public org.spigotmc.SpigotWorldConfig spigotConfig;
    @SuppressWarnings("unused") // Access transformed to public by ArclightMixinPlugin
    private static BlockPos lastPhysicsProblem; // Spigot
    public boolean preventPoiUpdated = false;

    public void arclight$constructor(WritableLevelData worldInfo, ResourceKey<Level> dimension, final DimensionType dimensionType, Supplier<ProfilerFiller> profiler, boolean isRemote, boolean isDebug, long seed) {
        throw new RuntimeException();
    }

    public void arclight$constructor(WritableLevelData worldInfo, ResourceKey<Level> dimension, final DimensionType dimensionType, Supplier<ProfilerFiller> profiler, boolean isRemote, boolean isDebug, long seed, org.bukkit.generator.ChunkGenerator gen, org.bukkit.World.Environment env) {
        arclight$constructor(worldInfo, dimension, dimensionType, profiler, isRemote, isDebug, seed);
        this.generator = gen;
        this.environment = env;
        bridge$getWorld();
    }

    @Inject(method = "<init>(Lnet/minecraft/world/level/storage/WritableLevelData;Lnet/minecraft/resources/ResourceKey;Lnet/minecraft/world/level/dimension/DimensionType;Ljava/util/function/Supplier;ZZJ)V", at = @At("RETURN"))
    private void arclight$init(WritableLevelData info, ResourceKey<Level> dimension, DimensionType dimType, Supplier<ProfilerFiller> profiler, boolean isRemote, boolean isDebug, long seed, CallbackInfo ci) {
        this.spigotConfig = new SpigotWorldConfig(((ServerLevelData) info).getLevelName());
        ((WorldBorderBridge) this.worldBorder).bridge$setWorld((Level) (Object) this);
        this.ticksPerAnimalSpawns = this.getCraftServer().getTicksPerAnimalSpawns();
        this.ticksPerMonsterSpawns = this.getCraftServer().getTicksPerMonsterSpawns();
        this.ticksPerWaterSpawns = this.getCraftServer().getTicksPerWaterSpawns();
        this.ticksPerWaterAmbientSpawns = this.getCraftServer().getTicksPerWaterAmbientSpawns();
        this.ticksPerAmbientSpawns = this.getCraftServer().getTicksPerAmbientSpawns();
        this.typeKey = this.getCraftServer().getHandle().getServer().registryAccess().registryOrThrow(Registry.DIMENSION_TYPE_REGISTRY)
            .getResourceKey(dimensionType)
            .orElseGet(() -> {
                Registry<DimensionType> registry = this.getCraftServer().getHandle().getServer().registryAccess().registryOrThrow(Registry.DIMENSION_TYPE_REGISTRY);
                ResourceKey<DimensionType> typeRegistryKey = ResourceKey.create(registry.key(), dimension.location());
                ArclightMod.LOGGER.warn("Assign {} to unknown dimension type {} as {}", typeRegistryKey, dimType);
                return typeRegistryKey;
            });
    }

    @Override
    public long bridge$ticksPerAnimalSpawns() {
        return ticksPerAnimalSpawns;
    }

    @Override
    public long bridge$ticksPerMonsterSpawns() {
        return ticksPerMonsterSpawns;
    }

    @Override
    public long bridge$ticksPerWaterSpawns() {
        return ticksPerWaterSpawns;
    }

    @Override
    public long bridge$ticksPerAmbientSpawns() {
        return ticksPerAmbientSpawns;
    }

    @Override
    public long bridge$ticksPerWaterAmbientSpawns() {
        return ticksPerWaterAmbientSpawns;
    }

    public ResourceKey<DimensionType> getTypeKey() {
        return this.typeKey;
    }

    @Override
    public ResourceKey<DimensionType> bridge$getTypeKey() {
        return getTypeKey();
    }

    @Override
    public SpigotWorldConfig bridge$spigotConfig() {
        return this.spigotConfig;
    }

    @Inject(method = "setBlock(Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;I)Z",
        at = @At("HEAD"), cancellable = true)
    private void arclight$hooks(BlockPos pos, BlockState newState, int flags, CallbackInfoReturnable<Boolean> cir) {
        if (!processCaptures(pos, newState, flags)) {
            cir.setReturnValue(false);
        }
    }

    private boolean processCaptures(BlockPos pos, BlockState newState, int flags) {
        Entity entityChangeBlock = ArclightCaptures.getEntityChangeBlock();
        if (entityChangeBlock != null) {
            if (CraftEventFactory.callEntityChangeBlockEvent(entityChangeBlock, pos, newState).isCancelled()) {
                return false;
            }
        }
        return true;
    }

    @Inject(method = "markAndNotifyBlock", cancellable = true, at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/block/state/BlockState;updateNeighbourShapes(Lnet/minecraft/world/level/LevelAccessor;Lnet/minecraft/core/BlockPos;II)V"))
    private void arclight$callBlockPhysics(BlockPos pos, LevelChunk chunk, BlockState blockstate, BlockState state, int flags, int recursionLeft, CallbackInfo ci) {
        try {
            if (this.world != null) {
                BlockPhysicsEvent event = new BlockPhysicsEvent(CraftBlock.at((LevelAccessor) this, pos), CraftBlockData.fromData(state));
                Bukkit.getPluginManager().callEvent(event);
                if (event.isCancelled()) {
                    ci.cancel();
                }
            }
        } catch (StackOverflowError e) {
            lastPhysicsProblem = pos;
        }
    }

    @Inject(method = "markAndNotifyBlock", cancellable = true, at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/Level;onBlockStateChange(Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/world/level/block/state/BlockState;)V"))
    private void arclight$preventPoiUpdate(BlockPos p_46605_, LevelChunk levelchunk, BlockState blockstate, BlockState p_46606_, int p_46607_, int p_46608_, CallbackInfo ci) {
        if (this.preventPoiUpdated) {
            ci.cancel();
        }
    }

    @Inject(method = "neighborChanged", cancellable = true,
        at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/block/state/BlockState;neighborChanged(Lnet/minecraft/world/level/Level;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/Block;Lnet/minecraft/core/BlockPos;Z)V"),
        locals = LocalCapture.CAPTURE_FAILHARD)
    private void arclight$callBlockPhysics2(BlockPos pos, Block blockIn, BlockPos fromPos, CallbackInfo ci, BlockState blockState) {
        try {
            if (this.world != null) {
                LevelAccessor iWorld = (LevelAccessor) this;
                BlockPhysicsEvent event = new BlockPhysicsEvent(CraftBlock.at(iWorld, pos), CraftBlockData.fromData(blockState), CraftBlock.at(iWorld, fromPos));
                Bukkit.getPluginManager().callEvent(event);
                if (event.isCancelled()) {
                    ci.cancel();
                }
            }
        } catch (StackOverflowError e) {
            lastPhysicsProblem = pos;
        }
    }

    @Inject(method = "postGameEventInRadius", cancellable = true, at = @At("HEAD"))
    private void arclight$gameEventEvent(Entity entity, GameEvent gameEvent, BlockPos pos, int i, CallbackInfo ci) {
        GenericGameEvent event = new GenericGameEvent(org.bukkit.GameEvent.getByKey(CraftNamespacedKey.fromMinecraft(Registry.GAME_EVENT.getKey(gameEvent))), new Location(this.getWorld(), pos.getX(),pos.getY(), pos.getZ()), (entity == null) ? null : ((EntityBridge) entity).bridge$getBukkitEntity(), i);
        Bukkit.getPluginManager().callEvent(event);
        if (event.isCancelled()) {
            ci.cancel();
        }
    }

    public CraftServer getCraftServer() {
        return (CraftServer) Bukkit.getServer();
    }

    public CraftWorld getWorld() {
        if (this.world == null) {
            Optional<Field> delegate = WrappedWorlds.getDelegate(this.getClass());
            if (delegate.isPresent()) {
                try {
                    return ((WorldBridge) delegate.get().get(this)).bridge$getWorld();
                } catch (IllegalAccessException e) {
                    throw new RuntimeException(e);
                }
            }
            if (generator == null) {
                generator = getCraftServer().getGenerator(((ServerLevelData) this.getLevelData()).getLevelName());
                if (generator != null && (Object) this instanceof ServerLevel serverWorld) {
                    CustomChunkGenerator gen = new CustomChunkGenerator(serverWorld, serverWorld.getChunkSource().getGenerator(), generator);
                    ((ServerChunkProviderBridge) serverWorld.getChunkSource()).bridge$setChunkGenerator(gen);
                }
            }
            if (environment == null) {
                environment = ArclightServer.getEnvironment(this.typeKey);
            }
            this.world = new CraftWorld((ServerLevel) (Object) this, generator, environment);
            getCraftServer().addWorld(this.world);
        }
        return this.world;
    }

    public BlockEntity getTileEntity(BlockPos pos, boolean validate) {
        return getBlockEntity(pos);
    }

    @Override
    public BlockEntity bridge$getTileEntity(BlockPos pos, boolean validate) {
        return getTileEntity(pos, validate);
    }

    @Override
    public CraftServer bridge$getServer() {
        return (CraftServer) Bukkit.getServer();
    }

    @Override
    public CraftWorld bridge$getWorld() {
        return this.getWorld();
    }

    @Override
    public boolean bridge$isPvpMode() {
        return this.pvpMode;
    }

    @Override
    public boolean bridge$isKeepSpawnInMemory() {
        return this.keepSpawnInMemory;
    }

    @Override
    public boolean bridge$isPopulating() {
        return this.populating;
    }

    @Override
    public void bridge$setPopulating(boolean populating) {
        this.populating = populating;
    }

    @Override
    public ChunkGenerator bridge$getGenerator() {
        return generator;
    }

    @Override
    public ServerLevel bridge$getMinecraftWorld() {
        return getWorld().getHandle();
    }

    @Override
    public boolean bridge$addEntity(Entity entity, CreatureSpawnEvent.SpawnReason reason) {
        if (getWorld().getHandle() != (Object) this) {
            return ((WorldBridge) getWorld().getHandle()).bridge$addEntity(entity, reason);
        } else {
            this.bridge$pushAddEntityReason(reason);
            return this.addFreshEntity(entity);
        }
    }

    @Override
    public void bridge$pushAddEntityReason(CreatureSpawnEvent.SpawnReason reason) {
        if (getWorld().getHandle() != (Object) this) {
            ((WorldBridge) getWorld().getHandle()).bridge$pushAddEntityReason(reason);
        }
    }

    @Override
    public CreatureSpawnEvent.SpawnReason bridge$getAddEntityReason() {
        if (getWorld().getHandle() != (Object) this) {
            return ((WorldBridge) getWorld().getHandle()).bridge$getAddEntityReason();
        }
        return null;
    }
}