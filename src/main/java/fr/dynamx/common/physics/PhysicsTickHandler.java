package fr.dynamx.common.physics;

import fr.dynamx.api.network.EnumPacketTarget;
import fr.dynamx.api.physics.IPhysicsWorld;
import fr.dynamx.common.DynamXContext;
import fr.dynamx.common.DynamXMain;
import fr.dynamx.utils.DynamXConfig;
import fr.dynamx.common.handlers.TaskScheduler;
import fr.dynamx.common.network.packets.MessageCollisionDebugDraw;
import fr.dynamx.server.command.CmdNetworkConfig;
import fr.dynamx.utils.DynamXLoadingTasks;
import fr.dynamx.utils.debug.DynamXDebugOptions;
import fr.dynamx.utils.debug.Profiler;
import fr.dynamx.utils.optimization.QuaternionPool;
import fr.dynamx.utils.optimization.SubClassPool;
import fr.dynamx.utils.optimization.Vector3fPool;
import net.minecraft.client.Minecraft;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.world.World;
import net.minecraft.world.WorldServer;
import net.minecraftforge.fml.common.FMLCommonHandler;
import net.minecraftforge.fml.common.eventhandler.EventPriority;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

import java.util.HashMap;
import java.util.Map;

public class PhysicsTickHandler {
    private static long lastTickTimeMs;
    public static final Map<EntityPlayer, Integer> requestedDebugInfo = new HashMap<>();

    @SideOnly(Side.CLIENT)
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public void tickClient(TickEvent.ClientTickEvent event) {
        if (event.phase == TickEvent.Phase.START) {
            try {
                Profiler.get().start(Profiler.Profiles.TICK);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        if (canTickClient(Minecraft.getMinecraft())) {
            tickWorldPhysics(event.phase, Minecraft.getMinecraft().world);
        }

        if (event.phase == TickEvent.Phase.START) {
            QuaternionPool.openPool(SubClassPool.TICK_CLIENT);
            Vector3fPool.openPool(SubClassPool.TICK_CLIENT);
            DynamXLoadingTasks.tick();
        } else {
            Profiler.get().end(Profiler.Profiles.TICK);
            if (Minecraft.getMinecraft().world != null) {
                boolean profiling = DynamXDebugOptions.PROFILING.isActive();
                if (profiling) {
                    if (DynamXMain.proxy.getTickTime() % 20 == 0) {
                        Profiler.get().printData("Client");
                    }
                }
                Profiler.setIsProfilingOn(profiling);
                Profiler.get().update();
            }

            if (!Minecraft.getMinecraft().isSingleplayer()) {//If not in solo
                TaskScheduler.tick();
            }
            Vector3fPool.closePool();
            QuaternionPool.closePool();
        }
    }

    @SideOnly(Side.CLIENT)
    private boolean canTickClient(Minecraft mc) {
        return mc.world != null && !mc.isGamePaused() && DynamXMain.proxy.shouldUseBulletSimulation(mc.world) && DynamXContext.getPhysicsWorld(mc.world) != null;
    }

    @SubscribeEvent
    public void tickServer(TickEvent.ServerTickEvent event) {
        if (event.phase == TickEvent.Phase.START) {
            QuaternionPool.openPool(SubClassPool.TICK_SERVER);
            Vector3fPool.openPool(SubClassPool.TICK_SERVER);
            try {
                Profiler.get().start(Profiler.Profiles.TICK);
            } catch (Exception e) {
                DynamXMain.log.throwing(e);
            }
        }
        for (WorldServer world : FMLCommonHandler.instance().getMinecraftServerInstance().worlds) {
            if (canTickServer(world)) {
                tickWorldPhysics(event.phase, world);
            }
        }

        if (event.phase == TickEvent.Phase.START) {
            if (FMLCommonHandler.instance().getSide().isServer()) {
                DynamXLoadingTasks.tick();
            }
        } else {
            Profiler.get().end(Profiler.Profiles.TICK);
            sendClientsDebug();
            Profiler.get().update();
            TaskScheduler.tick();
            Vector3fPool.closePool();
            QuaternionPool.closePool();
        }
    }

    private boolean canTickServer(World world) {
        // 基础安全检查
        if (world == null) {
            return false;
        }
        
        // 启用安全模式时的额外检查
        if (DynamXConfig.enableSafeMode) {
            if (world.provider == null) {
                DynamXMain.log.warn("World provider is null for world, skipping physics tick");
                return false;
            }
            
            // 检查是否为临时世界
            String worldName = world.provider.getDimensionType().getName();
            if (DynamXConfig.isTemporaryWorld(worldName)) {
                return false; // 静默跳过临时世界
            }
            
            // 检查维度是否在白名单中
            int dimensionId = world.provider.getDimension();
            if (!DynamXConfig.isDimensionAllowed(dimensionId)) {
                return false; // 静默跳过非白名单维度
            }
        }
        
        return world != null && DynamXMain.proxy.shouldUseBulletSimulation(world) && DynamXContext.getPhysicsWorld(world) != null;
    }

    private void tickWorldPhysics(TickEvent.Phase phase, World world) {
        try {
            IPhysicsWorld physicsWorld = DynamXContext.getPhysicsWorld(world);
            
            // 安全检查：确保物理世界存在
            if (physicsWorld == null) {
                if (DynamXConfig.enableSafeMode) {
                    DynamXMain.log.warn("Physics world is null for dimension {}, skipping tick", world.provider.getDimension());
                }
                return;
            }
            
            if (phase == TickEvent.Phase.END) {
                physicsWorld.tickEnd();
                return;
            }
            
            // START phase
            QuaternionPool.openPool(SubClassPool.TICK_PHYSICS_WORLD);
            Vector3fPool.openPool(SubClassPool.TICK_PHYSICS_WORLD);

            physicsWorld.tickStart();

            float deltaTimeSecond = getDeltaTimeMilliseconds() * 1.0E-3F;
            if (deltaTimeSecond > 0.5f) // game was paused ?
                deltaTimeSecond = 0.05f;

            Profiler.get().start(Profiler.Profiles.STEP_SIMULATION);
            
            // 安全模式：在关键操作前检查物理世界状态
            if (DynamXConfig.enableSafeMode) {
                if (physicsWorld.getDynamicsWorld() == null) {
                    DynamXMain.log.warn("Dynamics world is null for dimension {}, skipping simulation", world.provider.getDimension());
                    Profiler.get().end(Profiler.Profiles.STEP_SIMULATION);
                    Vector3fPool.closePool();
                    QuaternionPool.closePool();
                    return;
                }
            }
            
            physicsWorld.stepSimulation(deltaTimeSecond);
            Profiler.get().end(Profiler.Profiles.STEP_SIMULATION);

            if (physicsWorld.getDynamicsWorld() != null) {
                // 安全检查：确保joint列表存在
                if (DynamXConfig.enableSafeMode && physicsWorld.getDynamicsWorld().getJointList() == null) {
                    DynamXMain.log.warn("Joint list is null for dimension {}, skipping joint cleanup", world.provider.getDimension());
                } else {
                    physicsWorld.getDynamicsWorld().getJointList().forEach(joint -> {
                        if ((joint.getBodyA() != null && !physicsWorld.getDynamicsWorld().contains(joint.getBodyA()))
                                || (joint.getBodyB() != null && !physicsWorld.getDynamicsWorld().contains(joint.getBodyB()))) {
                            physicsWorld.removeJoint(joint);
                        }
                    });
                }
            }

            Vector3fPool.closePool();
            QuaternionPool.closePool();
            
        } catch (Exception e) {
            // 捕获并记录所有异常，防止崩溃
            if (DynamXConfig.enableSafeMode) {
                DynamXMain.log.error("Error during physics world tick for dimension {}: {}", world.provider.getDimension(), e.getMessage());
                DynamXMain.log.debug("Full stack trace:", e);
                
                // 清理资源池
                try {
                    Vector3fPool.closePool();
                    QuaternionPool.closePool();
                } catch (Exception cleanupException) {
                    DynamXMain.log.error("Error during cleanup: {}", cleanupException.getMessage());
                }
            } else {
                throw new RuntimeException("Physics tick error for dimension " + world.provider.getDimension(), e);
            }
        }
    }

    private void sendClientsDebug() {
        boolean profiling;
        if (DynamXMain.proxy.getServerWorld().getMinecraftServer().isDedicatedServer()) { //If integrated server, the vars are already shared
            profiling = false;
            boolean networkDebug = false, wheelData = false;
            for (Map.Entry<EntityPlayer, Integer> e : requestedDebugInfo.entrySet()) {
                //Don't spam of debug packets
                if (DynamXMain.proxy.getServerWorld().getMinecraftServer().getTickCounter() % 10 == 0 && (DynamXDebugOptions.BLOCK_BOXES.matchesNetMask(e.getValue()) || DynamXDebugOptions.SLOPE_BOXES.matchesNetMask(e.getValue()))) {
                    DynamXContext.getNetwork().sendToClient(new MessageCollisionDebugDraw(DynamXDebugOptions.BLOCK_BOXES.getDataIn(), DynamXDebugOptions.SLOPE_BOXES.getDataIn()), EnumPacketTarget.PLAYER, (EntityPlayerMP) e.getKey());
                }
                if (DynamXDebugOptions.PROFILING.matchesNetMask(e.getValue())) {
                    profiling = true;
                } else if (DynamXDebugOptions.FULL_NETWORK_DEBUG.matchesNetMask(e.getValue())) {
                    networkDebug = true;
                } else if (DynamXDebugOptions.WHEEL_ADVANCED_DATA.matchesNetMask(e.getValue())) {
                    wheelData = true;
                }
            }
            if (DynamXMain.proxy.getServerWorld().getMinecraftServer().getTickCounter() % 5 == 0) //requestedDebugInfo is sent all 5 ticks
                requestedDebugInfo.clear();
            if (networkDebug != DynamXDebugOptions.FULL_NETWORK_DEBUG.isActive()) {
                //System.out.println("Setting FULL_NETWORK_DEBUG active : " + networkDebug);
                if (networkDebug)
                    DynamXDebugOptions.FULL_NETWORK_DEBUG.enable();
                else
                    DynamXDebugOptions.FULL_NETWORK_DEBUG.disable();
            }
            if (wheelData != DynamXDebugOptions.WHEEL_ADVANCED_DATA.isActive()) {
                //System.out.println("Setting WHEEL_ADVANCED_DATA active : " + wheelData);
                if (wheelData)
                    DynamXDebugOptions.WHEEL_ADVANCED_DATA.enable();
                else
                    DynamXDebugOptions.WHEEL_ADVANCED_DATA.disable();
            }
        } else {
            profiling = DynamXDebugOptions.PROFILING.isActive();
        }
        if (profiling) {
            if (DynamXMain.proxy.getTickTime() % 20 == 0) {
                Profiler.get().printData("Server");
            }
        }
        Profiler.setIsProfilingOn(profiling);
    }

    private float getDeltaTimeMilliseconds() {
        long cur = System.currentTimeMillis();
        long dt = cur - lastTickTimeMs;
        lastTickTimeMs = cur;
        return dt;
    }
}
