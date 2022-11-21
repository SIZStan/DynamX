package fr.dynamx.api.network.sync.v3;

import com.google.common.collect.BiMap;
import com.google.common.collect.HashBiMap;
import fr.dynamx.api.network.sync.SyncTarget;
import fr.dynamx.common.DynamXMain;
import fr.dynamx.common.network.sync.v3.DynamXSynchronizedVariables;
import fr.dynamx.utils.optimization.HashMapPool;
import fr.dynamx.utils.optimization.PooledHashMap;
import lombok.Getter;
import net.minecraft.util.ResourceLocation;

import java.util.*;
import java.util.function.Predicate;

public class SynchronizedEntityVariableRegistry {
    private static final Map<ResourceLocation, SynchronizedVariableSerializer<?>> baseSyncVarRegistry = new HashMap<>();
    private static final BiMap<ResourceLocation, Integer> syncVarRegistry = HashBiMap.create();
    @Getter
    private static final Map<Integer, SynchronizedVariableSerializer<?>> serializerMap = new HashMap<>();

    //TODO ANNOTATION SYSTEM
    public static void addSyncVar(ResourceLocation name, SynchronizedVariableSerializer<?> serializer) {
        if (baseSyncVarRegistry.containsKey(name))
            throw new IllegalArgumentException("Duplicate SyncVar " + name);
        baseSyncVarRegistry.put(name, serializer);
    }

    private static int getIndex(String of, List<String> in) {
        for (int i = 0; i < in.size(); i++) {
            if (in.get(i).equals(of)) {
                return i;
            }
        }
        throw new IllegalArgumentException(of + " is not in input list " + in);
    }

    /**
     * Sorts variable ids in alphabetical order
     */
    public static void sortRegistry(Predicate<String> useMod) {
        DynamXMain.log.info("Sorting SynchronizedVariables registry ids...");
        Map<ResourceLocation, Integer> nw = new HashMap<>();
        List<String> buff = new ArrayList<>();
        for (ResourceLocation res : baseSyncVarRegistry.keySet()) {
            if (useMod.test(res.getNamespace())) {
                buff.add(res.toString());
            }
        }
        Collections.sort(buff); //Unique sorting
        for (ResourceLocation res : baseSyncVarRegistry.keySet()) {
            if (buff.contains(res.toString())) {
                int index = getIndex(res.toString(), buff);
                nw.put(res, index);
            }
        }
        fixIds(nw);
    }

    private static void fixIds(Map<ResourceLocation, Integer> newVarsRegistry) {
        DynamXMain.log.debug("Fixing SynchronizedVariables registry ids...");
        syncVarRegistry.clear();
        serializerMap.clear();
        newVarsRegistry.forEach((r, i) -> {
            DynamXMain.log.debug("Add : " + r + " = " + i);
            syncVarRegistry.put(r, i);
            serializerMap.put(i, baseSyncVarRegistry.get(r));
        });
    }

    public static Map<ResourceLocation, Integer> getSyncVarRegistry() {
        return syncVarRegistry;
    }

    /**
     * Internal variables, example to add your own variables
     */
    static {
        //TODO SIMPLIFY
        System.out.println("REGISTER");

        addSyncVar(DynamXSynchronizedVariables.POS, DynamXSynchronizedVariables.posSerializer);
        addSyncVar(DynamXSynchronizedVariables.CONTROLS, SynchronizedEntityVariableFactory.intSerializer);
        addSyncVar(DynamXSynchronizedVariables.SPEED_LIMIT, SynchronizedEntityVariableFactory.floatSerializer);
        addSyncVar(DynamXSynchronizedVariables.ENGINE_PROPERTIES, SynchronizedEntityVariableFactory.floatArraySerializer);
        addSyncVar(DynamXSynchronizedVariables.WHEEL_INFOS, DynamXSynchronizedVariables.wheelInfosSerializer);
        addSyncVar(DynamXSynchronizedVariables.WHEEL_STATES, DynamXSynchronizedVariables.wheelStatesSerializer);
        addSyncVar(DynamXSynchronizedVariables.WHEEL_PROPERTIES, SynchronizedEntityVariableFactory.floatArraySerializer);
        addSyncVar(DynamXSynchronizedVariables.WHEEL_VISUALS, SynchronizedEntityVariableFactory.floatArraySerializer);
        addSyncVar(DynamXSynchronizedVariables.MOVABLE_MOVER, SynchronizedEntityVariableFactory.playerSerializer);
        addSyncVar(DynamXSynchronizedVariables.MOVABLE_PICK_DISTANCE, SynchronizedEntityVariableFactory.floatSerializer);
        addSyncVar(DynamXSynchronizedVariables.MOVABLE_PICK_POSITION, SynchronizedEntityVariableFactory.vector3fSerializer);
        addSyncVar(DynamXSynchronizedVariables.MOVABLE_PICKER, SynchronizedEntityVariableFactory.playerSerializer);
        addSyncVar(DynamXSynchronizedVariables.MOVABLE_PICKED_ENTITY, SynchronizedEntityVariableFactory.physicsEntitySerializer);
        addSyncVar(DynamXSynchronizedVariables.MOVABLE_IS_PICKED, SynchronizedEntityVariableFactory.booleanSerializer);
        addSyncVar(DynamXSynchronizedVariables.DOORS_STATES, DynamXSynchronizedVariables.doorsStatesSerializer);
    }
}
