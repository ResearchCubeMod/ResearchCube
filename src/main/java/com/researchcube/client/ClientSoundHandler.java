package com.researchcube.client;

import com.researchcube.ResearchCubeMod;
import com.researchcube.block.ResearchTableBlockEntity;
import com.researchcube.client.sound.ResearchStationSoundInstance;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.chunk.LevelChunk;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.LevelTickEvent;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

/**
 * Client-side tick handler that manages ambient sound instances for active Research Stations.
 * Scans nearby block entities periodically and starts/stops looping hum sounds.
 */
@EventBusSubscriber(modid = ResearchCubeMod.MOD_ID, value = Dist.CLIENT)
public class ClientSoundHandler {

    /** Active sound instances by block position. */
    private static final Map<BlockPos, ResearchStationSoundInstance> activeSounds = new HashMap<>();

    /** Counter for periodic scanning (not every tick). */
    private static int tickCounter = 0;

    private static final int SCAN_INTERVAL = 40; // ticks between scans
    private static final int SCAN_RADIUS_CHUNKS = 2; // chunk radius to scan

    @SubscribeEvent
    public static void onClientLevelTick(LevelTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) return;
        if (event.getLevel() != mc.level) return;

        tickCounter++;

        // Clean up sounds for removed/stopped entries every tick
        cleanupSounds();

        // Full scan only periodically
        if (tickCounter % SCAN_INTERVAL != 0) return;

        scanForResearchStations(mc);
    }

    private static void cleanupSounds() {
        Iterator<Map.Entry<BlockPos, ResearchStationSoundInstance>> it = activeSounds.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<BlockPos, ResearchStationSoundInstance> entry = it.next();
            if (entry.getValue().isStopped()) {
                it.remove();
            }
        }
    }

    private static void scanForResearchStations(Minecraft mc) {
        Level level = mc.level;
        if (level == null || mc.player == null) return;

        BlockPos playerPos = mc.player.blockPosition();
        int chunkX = playerPos.getX() >> 4;
        int chunkZ = playerPos.getZ() >> 4;

        for (int dx = -SCAN_RADIUS_CHUNKS; dx <= SCAN_RADIUS_CHUNKS; dx++) {
            for (int dz = -SCAN_RADIUS_CHUNKS; dz <= SCAN_RADIUS_CHUNKS; dz++) {
                if (!level.hasChunk(chunkX + dx, chunkZ + dz)) continue;
                LevelChunk chunk = level.getChunk(chunkX + dx, chunkZ + dz);

                for (Map.Entry<BlockPos, BlockEntity> entry : chunk.getBlockEntities().entrySet()) {
                    if (entry.getValue() instanceof ResearchTableBlockEntity rtbe) {
                        BlockPos pos = entry.getKey();
                        if (rtbe.isResearching() && !activeSounds.containsKey(pos)) {
                            // Start a new ambient sound for this researching station
                            ResearchStationSoundInstance sound = new ResearchStationSoundInstance(rtbe);
                            activeSounds.put(pos, sound);
                            mc.getSoundManager().play(sound);
                        }
                    }
                }
            }
        }
    }

    /**
     * Stop all active sounds. Called when the client disconnects, etc.
     */
    public static void stopAll() {
        for (ResearchStationSoundInstance sound : activeSounds.values()) {
            sound.stopSound();
        }
        activeSounds.clear();
    }
}
