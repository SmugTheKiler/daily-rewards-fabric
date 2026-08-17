package me.alpestrine.c.reward.server;

import me.alpestrine.c.reward.MainMod;
import me.alpestrine.c.reward.config.GlobalConfigHandler;
import me.alpestrine.c.reward.config.objects.JsonPlayerData;
import me.alpestrine.c.reward.screen.CustomScreenHandler;
import me.alpestrine.c.reward.screen.button.InventoryEvent;
import me.alpestrine.c.reward.screen.screens.AbstractACScreen;
import me.alpestrine.c.reward.screen.screens.reward.AbstractRewardScreen;
import me.alpestrine.c.reward.screen.screens.reward.DailyScreen;
import me.alpestrine.c.reward.screen.screens.reward.PlaytimeScreen;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.inventory.Inventory;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.UUID;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

public class MainServer {
    public static final Executor configHandlerThread = Executors.newSingleThreadExecutor();
    public static final GlobalConfigHandler globalConfigHandler = new GlobalConfigHandler();
    public static int millisecondsInDay;
    public static int ticksPerUpdate;
    public static HashSet<UUID> screenEntities = new HashSet<>();
    private static int lastSave;
    public static MinecraftServer server;

    public static void onInit(Object object) {
        server = (MinecraftServer) object;
        DailyScreen.dailyHandler.onInit();
        PlaytimeScreen.playtimeHandler.onInit();
        AbstractRewardScreen.dataHandler.onInit();
        globalConfigHandler.onInit();
    }

    public static int execute(Runnable task) {
        configHandlerThread.execute(task);
        return 0;
    }

    public static void handleClick(ScreenHandler sh, int slotIndex, int button, SlotActionType actionType,
            PlayerEntity player, CallbackInfo ci) {
        try {
            if (!(player instanceof ServerPlayerEntity spe)) {
                return;
            }

            // --- FIX: Prevent IndexOutOfBoundsException when clicking outside GUI (-1) ---
            if (slotIndex < 0) {
                return;
            }
            // ---------------------------------------------------------------------------

            if (sh instanceof CustomScreenHandler) {
                ci.cancel();
            }
            Inventory inventory = slotIndex == ScreenHandler.EMPTY_SPACE_SLOT_INDEX ? player.getInventory()
                    : sh.slots.get(slotIndex).inventory;
            InventoryEvent ie = new InventoryEvent(slotIndex, button, actionType, inventory, spe);
            if (inventory instanceof AbstractACScreen s) {
                s.onClick(ie);
            }
        } catch (Exception e) {
            System.err.println("Error handling click mixin");
            e.printStackTrace(System.err);
        }
    }

    public static void onTick() {
        int tect = TickExecutor.currentTick.get() + 1;
        TickExecutor.tickTasks.getOrDefault(tect, new ArrayList<>()).forEach(Runnable::run);
        TickExecutor.currentTick.set(tect);
        if (tect >= lastSave + ticksPerUpdate) {
            boolean toUpdate = false;
            for (ServerPlayerEntity spe : MainServer.server.getPlayerManager().getPlayerList()) {
                JsonPlayerData join = AbstractRewardScreen.dataHandler.getForUUID(spe.getUuid());
                if (join.currentStreak == 0 || join.lastRewardTime + millisecondsInDay < System.currentTimeMillis()) {// logged
                                                                                                                      // in
                                                                                                                      // after
                                                                                                                      // one
                                                                                                                      // day
                    join.currentStreak++;
                    join.lastRewardTime = System.currentTimeMillis();
                }
                join.playtimeSeconds += ticksPerUpdate / 20;
                join.lastJoin = System.currentTimeMillis();
                MainMod.refreshIfRewardScreen(spe);
                toUpdate = true;
            }

            lastSave = tect;
            if (toUpdate) {
                configHandlerThread.execute(AbstractRewardScreen.dataHandler::writeCurrentValue);
            }
        }
    }
}