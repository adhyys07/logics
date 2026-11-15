package com.redstonelogics.client;

import com.mojang.blaze3d.platform.InputConstants;
import com.redstonelogics.client.gui.CircuitScreen;
import com.redstonelogics.scanner.FloodFillSelector;
import com.redstonelogics.scanner.WorldScanner;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;

public class RedstoneLogicsClient implements ClientModInitializer {

    public static final String MOD_ID = "redstonelogics";

    private static final KeyMapping.Category CATEGORY =
            KeyMapping.Category.register(Identifier.fromNamespaceAndPath(MOD_ID, "main"));

    private static KeyMapping openKey;
    private static KeyMapping pickKey;

    @Override
    public void onInitializeClient() {
        openKey = KeyMappingHelper.registerKeyMapping(new KeyMapping(
                "key.redstonelogics.open",
                InputConstants.Type.KEYBOARD,
                InputConstants.KEY_K,
                CATEGORY));

        pickKey = KeyMappingHelper.registerKeyMapping(new KeyMapping(
                "key.redstonelogics.pick",
                InputConstants.Type.KEYBOARD,
                InputConstants.KEY_V,
                CATEGORY));

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            while (openKey.consumeClick()) {
                client.setScreenAndShow(new CircuitScreen());
            }
            while (pickKey.consumeClick()) {
                pickAndScan(client);
            }
        });

        HudElementRegistry.addLast(
                Identifier.fromNamespaceAndPath(MOD_ID, "selection_hud"),
                (graphics, delta) -> SelectionHud.render(graphics));

        WorldHighlightRenderer.register();
    }

    /**
     * The whole flow in one keypress: look at any part of a build, press the
     * pick key, and the selection, scan and analysis all happen without a
     * command or a corner.
     */
    public static void pickAndScan(Minecraft client) {
        if (client.level == null || client.player == null) {
            return;
        }
        HitResult hit = client.hitResult;
        if (!(hit instanceof BlockHitResult block) || hit.getType() != HitResult.Type.BLOCK) {
            message(client, "Look at a redstone component first.");
            return;
        }

        BlockPos target = block.getBlockPos();
        FloodFillSelector.Region region = FloodFillSelector.from(client.level, target);
        if (region == null) {
            message(client, "That block is not part of a redstone build.");
            return;
        }
        if (region.volume() > WorldScanner.MAX_VOLUME) {
            message(client, "Build spans " + region.volume() + " blocks; the limit is "
                    + WorldScanner.MAX_VOLUME + ".");
            return;
        }

        ClientSelection.set(region.min(), region.max());
        ClientAnalysis.rescan();
        message(client, "Picked " + region.components()
                + " component(s); press K for the diagram.");
    }

    private static void message(Minecraft client, String text) {
        client.gui.hud.setOverlayMessage(Component.literal("[rlogic] " + text), false);
    }
}
