package me.alpestrine.c.reward;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.context.CommandContext;
import me.alpestrine.c.reward.screen.CustomScreenHandler;
import me.alpestrine.c.reward.screen.screens.AbstractACScreen;
import me.alpestrine.c.reward.screen.screens.reward.AbstractRewardScreen;
import me.alpestrine.c.reward.screen.screens.reward.DailyScreen;
import me.alpestrine.c.reward.screen.screens.reward.PlaytimeScreen;
import me.alpestrine.c.reward.server.MainServer;
import net.fabricmc.api.ModInitializer;

import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.command.CommandRegistryAccess;
import net.minecraft.command.argument.EntityArgumentType;
import net.minecraft.entity.Entity;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.UUID;

public class MainMod implements ModInitializer {
	public static final String ID = "rewards";
	public static final Logger LOGGER = LoggerFactory.getLogger(ID);

	@Override
	public void onInitialize() {
		LOGGER.info("Rewards!");
		CommandRegistrationCallback.EVENT.register(this::register);
	}

	private void register(CommandDispatcher<ServerCommandSource> dispatcher, CommandRegistryAccess registryAccess,
			CommandManager.RegistrationEnvironment environment) {
		for (ConfigType ct : ConfigType.values()) {
			dispatcher
					.register(CommandManager.literal(String.format("rewards-reload-%s-config", ct.name().toLowerCase()))
							.executes(context -> MainServer.execute(() -> execCommand(context, environment, ct))));
		}

		String rewardScreenEntity = "rewards-screen-entity";
		dispatcher.register(CommandManager.literal(rewardScreenEntity)
				.then(CommandManager.literal("add")
						.then((CommandManager.argument("targets", EntityArgumentType.entities())
								.executes(context -> doScreenEntity(context, environment, ScreenEntityAction.Added,
										EntityArgumentType.getEntity(context, "targets")))))));

		dispatcher.register(CommandManager.literal(rewardScreenEntity)
				.then(CommandManager.literal("remove")
						.then(CommandManager.argument("targets", EntityArgumentType.entities())
								.executes(context -> doScreenEntity(context, environment, ScreenEntityAction.Removed,
										EntityArgumentType.getEntity(context, "targets"))))));
	}

	private int doScreenEntity(CommandContext<ServerCommandSource> context,
			CommandManager.RegistrationEnvironment environment, ScreenEntityAction action, Entity entity) {
		ServerCommandSource scs = context.getSource();
		UUID uuid;
		if (entity == null || (uuid = entity.getUuid()) == null) {
			String str = entity == null ? "Entity is null" : "Entity UUID is null";
			scs.sendError(Text.of(str));
			return 0;
		}
		if (scs.hasPermissionLevel(2) || environment.integrated) {
			boolean bl = switch (action) {
				case Added -> MainServer.screenEntities.add(uuid);
				case Removed -> MainServer.screenEntities.remove(uuid);
			};
			if (!bl) {
				scs.sendError(Text.of(switch (action) {
					case Added -> String.format("UUID %s already added to screen entities", uuid);
					case Removed -> String.format("UUID %s is not a screen entity", uuid);
				}));

			} else {
				scs.sendMessage(Text.of(String.format("Successfully %s UUID: %s", action.name(), uuid)));
			}

			// --- FIX: Force save of global config to disk when adding/removing NPCs ---
			MainServer.configHandlerThread.execute(MainServer.globalConfigHandler::writeCurrentValue);
			// --------------------------------------------------------------------------

		} else {
			scs.sendError(Text.of("No permission!"));
		}
		return 0;
	}

	public enum ScreenEntityAction {
		Added, Removed
	}

	private void execCommand(CommandContext<ServerCommandSource> context,
			CommandManager.RegistrationEnvironment environment, ConfigType type) {
		ServerCommandSource scs = context.getSource();
		if (scs.hasPermissionLevel(2) || environment.integrated) {
			try {
				scs.sendMessage(Text.of(String.format((switch (type) {
					case Daily -> DailyScreen.dailyHandler;
					case Playtime -> PlaytimeScreen.playtimeHandler;
					case PlayerData -> AbstractRewardScreen.dataHandler;
					case Global -> MainServer.globalConfigHandler;
				}).reload() ? "Successfully reloaded %s config!"
						: "Config file for %s didn't exist or was empty, default config was saved to disk.",
						type.name())));
				scs.getServer().getPlayerManager().getPlayerList().forEach(MainMod::refreshIfRewardScreen);
			} catch (Exception e) {
				scs.sendMessage(
						Text.of(String.format("Failed to reload %s config, check logs for error.", type.name())));
				e.printStackTrace(System.err);
			}
		} else {
			scs.sendError(Text.of("No permission!"));
		}
	}

	public static void refreshIfRewardScreen(ServerPlayerEntity spe) {
		if (spe.currentScreenHandler instanceof CustomScreenHandler csh
				&& csh.getInventory() instanceof AbstractACScreen aacs) {
			aacs.refresh(spe);
		}
	}

	public enum ConfigType {
		Daily, Playtime, Global, PlayerData
	}
}