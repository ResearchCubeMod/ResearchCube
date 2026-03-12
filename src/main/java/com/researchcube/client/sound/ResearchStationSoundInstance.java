package com.researchcube.client.sound;

import com.researchcube.block.ResearchTableBlockEntity;
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;

/**
 * A looping ambient hum that plays at a Research Station while research is active.
 * Automatically stops when the block entity stops researching or is removed.
 */
public class ResearchStationSoundInstance extends AbstractTickableSoundInstance {

    private final ResearchTableBlockEntity blockEntity;

    public ResearchStationSoundInstance(ResearchTableBlockEntity be) {
        super(SoundEvents.BEACON_AMBIENT, SoundSource.BLOCKS, SoundInstance.createUnseededRandom());
        this.blockEntity = be;
        this.x = be.getBlockPos().getX() + 0.5;
        this.y = be.getBlockPos().getY() + 0.5;
        this.z = be.getBlockPos().getZ() + 0.5;
        this.looping = true;
        this.delay = 0;
        this.volume = 0.25f;
        this.pitch = 0.6f;
        this.relative = false;
    }

    @Override
    public void tick() {
        if (blockEntity.isRemoved() || !blockEntity.isResearching()) {
            this.stop();
        }
    }

    /**
     * Public wrapper for the protected stop() method.
     * Used by ClientSoundHandler to stop sounds externally.
     */
    public void stopSound() {
        this.stop();
    }
}
