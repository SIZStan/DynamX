package fr.dynamx.common;

import fr.dynamx.common.network.sync.PhysicsEntitySynchronizer;
import fr.dynamx.common.blocks.TEDynamXBlock;
import fr.dynamx.common.entities.PhysicsEntity;
import fr.dynamx.common.handlers.CommonEventHandler;
import fr.dynamx.common.network.sync.SPPhysicsEntitySynchronizer;
import fr.dynamx.common.physics.PhysicsTickHandler;
import fr.dynamx.common.physics.entities.AbstractEntityPhysicsHandler;
import fr.dynamx.common.physics.world.BuiltinPhysicsWorld;
import fr.dynamx.utils.DynamXConfig;
import net.minecraft.util.ResourceLocation;
import net.minecraft.world.World;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.common.FMLCommonHandler;
import net.minecraftforge.fml.common.registry.GameRegistry;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.server.FMLServerHandler;

import static fr.dynamx.utils.DynamXConstants.ID;

public abstract class CommonProxy {
    public void preInit() {
        GameRegistry.registerTileEntity(TEDynamXBlock.class, new ResourceLocation(ID + ":dynamxblock"));
    }

    public void init() {
        MinecraftForge.EVENT_BUS.register(new PhysicsTickHandler());
        MinecraftForge.EVENT_BUS.register(new CommonEventHandler());
    }

    public void completeInit(){}

    /**
     * @return The client world, if loader
     */
    public World getClientWorld() {
        return null;
    }

    /**
     * @return The server world, if loader
     */
    public World getServerWorld() {
        return FMLServerHandler.instance().getServer().getEntityWorld();
    }

    /**
     * @return True if the bullet physics engine should be used for the world. Always true except for client single player worlds
     */
    public boolean shouldUseBulletSimulation(World world) {
        if (world == null || world.provider == null) {
            if (DynamXConfig.enableSafeMode) {
                DynamXMain.log.warn("World or world provider is null, disabling physics");
            }
            return false;
        }
        
        int dimensionId = world.provider.getDimension();
        
        if (!DynamXConfig.isDimensionAllowed(dimensionId)) {
            if (DynamXConfig.enableDimensionWhitelist) {
                DynamXMain.log.debug("Dimension {} is not in whitelist, disabling DynamX physics", dimensionId);
            }
            return false;
        }
        
        String worldName = world.provider.getDimensionType().getName();
        if (DynamXConfig.isTemporaryWorld(worldName)) {
            DynamXMain.log.info("Detected temporary world '{}', disabling DynamX physics to prevent crashes", worldName);
            return false;
        }
        
        if (world.getSaveHandler() != null && world.getSaveHandler().getWorldDirectory() != null) {
            String worldDirName = world.getSaveHandler().getWorldDirectory().getName();
            if (DynamXConfig.isTemporaryWorld(worldDirName)) {
                DynamXMain.log.info("Detected temporary world directory '{}', disabling DynamX physics to prevent crashes", worldDirName);
                return false;
            }
        }
        
        return DynamXContext.getPhysicsWorldPerDimensionMap().containsKey(dimensionId);
    }

    /**
     * @return The {@link AbstractEntityPhysicsHandler} for the given entity, according to the side and game type (solo or multi)
     */
    public <T extends AbstractEntityPhysicsHandler<?, ?>> PhysicsEntitySynchronizer<? extends PhysicsEntity<T>> getNetHandlerForEntity(PhysicsEntity<T> tPhysicsEntity) {
        return new SPPhysicsEntitySynchronizer<>(tPhysicsEntity, Side.SERVER); //Does not work at all on dedicated servers or in lan games
    }

    /**
     * @return The minecraft server's tick counter
     */
    public int getTickTime() {
        return FMLCommonHandler.instance().getMinecraftServerInstance().getTickCounter();
    }

    /**
     * @param entity The entity to test
     * @return True if the current side is playing a simulation of this entity
     */
    public abstract boolean ownsSimulation(PhysicsEntity<?> entity);

    /**
     * Schedules the given task in the client or server threads, according to the given world's side
     */
    public abstract void scheduleTask(World mcWorld, Runnable task);

    /**
     * Creates the physics world
     */
    public void initPhysicsWorld(World world) {
        if (DynamXConfig.enableSafeMode) {
            if (world == null) {
                DynamXMain.log.error("Cannot initialize physics world: world is null");
                return;
            }
            if (world.provider == null) {
                DynamXMain.log.error("Cannot initialize physics world: world provider is null");
                return;
            }
        }
        
        int dimensionId = world.provider.getDimension();
        
        if (!DynamXConfig.isDimensionAllowed(dimensionId)) {
            DynamXMain.log.debug("Skipping physics world initialization for dimension {} (not in whitelist)", dimensionId);
            return;
        }
        
        String worldName = world.provider.getDimensionType().getName();
        if (DynamXConfig.isTemporaryWorld(worldName)) {
            DynamXMain.log.info("Skipping physics world initialization for temporary world '{}'", worldName);
            return;
        }
        
        if (DynamXContext.getPhysicsWorldPerDimensionMap().containsKey(dimensionId)) {
            DynamXMain.log.warn("Physics world of {} is already loaded ! Keeping the previously loaded world.", world);
            return;
        }
        
        try {
            DynamXMain.log.info("Initializing physics world for dimension {} (world: {})", dimensionId, worldName);
            DynamXContext.getPhysicsWorldPerDimensionMap().put(dimensionId, new BuiltinPhysicsWorld(world, false));
        } catch (Exception e) {
            DynamXMain.log.error("Failed to initialize physics world for dimension {}: {}", dimensionId, e.getMessage());
            if (DynamXConfig.enableSafeMode) {
                DynamXMain.log.error("Stack trace:", e);
                DynamXContext.getPhysicsWorldPerDimensionMap().remove(dimensionId);
            } else {
                throw e;
            }
        }
    }

    public abstract void schedulePacksInit();
}