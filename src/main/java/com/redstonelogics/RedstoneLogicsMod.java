package com.redstonelogics;

import com.redstonelogics.command.RedstoneLogicsCommand;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Entry point. All it does is register the command; everything else is plain Java. */
public class RedstoneLogicsMod implements ModInitializer {

    public static final String MOD_ID = "redstonelogics";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    @Override
    public void onInitialize() {
        CommandRegistrationCallback.EVENT.register(
                (dispatcher, registry, environment) -> RedstoneLogicsCommand.register(dispatcher));
        LOGGER.info("RedstoneLogics ready; use /rlogic pos1, /rlogic pos2, /rlogic scan.");
    }
}
