package fr.dynamx.server.network;

import fr.dynamx.api.network.sync.SimulationHolder;
import fr.dynamx.api.network.sync.SyncTarget;
import fr.dynamx.api.network.sync.v3.*;
import fr.dynamx.common.DynamXMain;
import fr.dynamx.common.entities.PhysicsEntity;
import fr.dynamx.server.command.CmdNetworkConfig;
import fr.dynamx.utils.debug.Profiler;
import fr.dynamx.utils.optimization.PooledHashMap;
import fr.dynamx.utils.optimization.Vector3fPool;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.world.WorldServer;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

@SideOnly(Side.SERVER)
public class ServerPhysicsEntitySynchronizer<T extends PhysicsEntity<?>> extends MultiplayerPhysicsEntitySynchronizer<T> {
    private final Map<Integer, SyncTarget> varsToSync = new HashMap<>();
    private int updateCount = 0;

    public ServerPhysicsEntitySynchronizer(T entityIn) {
        super(entityIn);
    }

    @Override
    public void onPlayerStartControlling(EntityPlayer player, boolean addControllers) {
        if (entity.physicsHandler != null)
            entity.physicsHandler.setForceActivation(true);
        ServerPhysicsSyncManager.putTime(player, 0);
        setSimulationHolder(SimulationHolder.DRIVER);
    }

    @Override
    public void onPlayerStopControlling(EntityPlayer player, boolean removeControllers) {
        if (entity.physicsHandler != null)
            entity.physicsHandler.setForceActivation(false);
        setSimulationHolder(getDefaultSimulationHolder());
    }

    @Override
    public void onPrePhysicsTick(Profiler profiler) {
        readReceivedPackets();

        profiler.start(Profiler.Profiles.PHY1);
        Vector3fPool.openPool();
        entity.prePhysicsUpdateWrapper(profiler, true);
        Vector3fPool.closePool();
        profiler.end(Profiler.Profiles.PHY1);
        //entity.updatePos();

        if (entity.ticksExisted % entity.getSyncTickRate() == 0) //Don't send a packet each tick
        {
            varsToSync.clear();
            profiler.start(Profiler.Profiles.PKTSEND1);
            getDirtyVars(varsToSync, Side.CLIENT, updateCount);
            profiler.end(Profiler.Profiles.PKTSEND1);

            profiler.start(Profiler.Profiles.PKTSEND2);
            Set<? extends EntityPlayer> l = ((WorldServer) entity.world).getEntityTracker().getTrackingPlayers(entity);
            l.forEach(p -> sendSyncTo(p, SynchronizedEntityVariableRegistry.retainSyncVars(getSynchronizedVariables(), this.varsToSync, p == entity.getControllingPassenger() ? SyncTarget.DRIVER : SyncTarget.SPECTATORS)));
            profiler.end(Profiler.Profiles.PKTSEND2);
            updateCount++;

            //System.out.println("Send " + entity.ticksExisted);
        }

        //if(entity.getControllingPassenger() instanceof EntityPlayer)// && DynamXCommands.SERVER_NET_DEBUG)
        {
            if ((Math.abs(entity.motionX) > 0.05f || Math.abs(entity.motionY) > 0.05f || Math.abs(entity.motionZ) > 0.05f) && CmdNetworkConfig.SERVER_NET_DEBUG > 0) {
                DynamXMain.log.info("Entity " + entity.getEntityId() + " is moving motion " + entity.motionX + " " + entity.motionY + " " + entity.motionZ + " cli time " + ServerPhysicsSyncManager.toDebugString() + " ticks exist " + entity.ticksExisted);
            }
        }
    }

    @Override
    public void onPostPhysicsTick(Profiler profiler) {
        entity.postUpdatePhysicsWrapper(profiler, true);
    }

    private void sendSyncTo(EntityPlayer p, PooledHashMap<Integer, SynchronizedEntityVariable<?>> varsToSync) {
        //System.out.println("out " + varsToSync);
        if (!varsToSync.isEmpty())
            ServerPhysicsSyncManager.addEntitySync(p, entity, varsToSync);
    }

    @Override
    public void setSimulationTimeClient(int simulationTimeClient) {
        //Update stored driver's simulation time
        if (entity.getControllingPassenger() instanceof EntityPlayer) {
            ServerPhysicsSyncManager.putTime((EntityPlayer) entity.getControllingPassenger(), simulationTimeClient - 1);
        }
    }
}