package smurfer.fastenchatn;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.FloatArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandManager;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.client.MinecraftClient;
import net.minecraft.command.CommandRegistryAccess;
import net.minecraft.text.Text;

import java.util.*;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class FastEnchantClient implements ClientModInitializer {
	private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();

	@Override
	public void onInitializeClient() {
		ClientCommandRegistrationCallback.EVENT.register(this::registerCommands);
	}

	private void registerCommands(CommandDispatcher<FabricClientCommandSource> dispatcher, CommandRegistryAccess registryAccess) {
		dispatcher.register(ClientCommandManager.literal("quickenchant")
				.then(ClientCommandManager.argument("item", StringArgumentType.word())
						.suggests((context, builder) -> {
							String[] items = new String[]{
									"boots", "leggings", "chestplate", "helmet", "sword", "axe", "pickaxe", "shovel", "hoe",
									"shear", "shears", "mace", "bow", "crossbow", "trident", "basic", "shield", "flint", "elytra"
							};
							for (String item : items) builder.suggest(item);
							return builder.buildFuture();
						})
						.then(ClientCommandManager.argument("delay", FloatArgumentType.floatArg(0))
								.then(ClientCommandManager.literal("noIncompatible")
										.executes(ctx -> {
											String item = StringArgumentType.getString(ctx, "item");
											float delay = FloatArgumentType.getFloat(ctx, "delay");
											return applyEnchantments(item, delay, false, true);
										}))
								.executes(ctx -> {
									String item = StringArgumentType.getString(ctx, "item");
									float delay = FloatArgumentType.getFloat(ctx, "delay");
									return applyEnchantments(item, delay, true, true);
								}))
						.executes(ctx -> {
							String item = StringArgumentType.getString(ctx, "item");
							return applyEnchantments(item, 0.75f, true, true);
						})
				)
		);

		dispatcher.register(ClientCommandManager.literal("fastenchant")
				.then(ClientCommandManager.argument("item", StringArgumentType.word())
						.suggests((context, builder) -> {
							String[] items = new String[]{
									"boots", "leggings", "chestplate", "helmet", "sword", "axe", "pickaxe", "shovel", "hoe",
									"shear", "shears", "mace", "bow", "crossbow", "trident", "basic", "shield", "flint", "elytra"
							};
							for (String item : items) builder.suggest(item);
							return builder.buildFuture();
						})
						.then(ClientCommandManager.argument("delay", FloatArgumentType.floatArg(0))
								.then(ClientCommandManager.literal("noIncompatible")
										.executes(ctx -> {
											String item = StringArgumentType.getString(ctx, "item");
											float delay = FloatArgumentType.getFloat(ctx, "delay");
											return applyEnchantments(item, delay, false, false);
										}))
								.executes(ctx -> {
									String item = StringArgumentType.getString(ctx, "item");
									float delay = FloatArgumentType.getFloat(ctx, "delay");
									return applyEnchantments(item, delay, true, false);
								}))
						.executes(ctx -> {
							String item = StringArgumentType.getString(ctx, "item");
							return applyEnchantments(item, 0.75f, true, false);
						})
				)
		);
	}

	private int applyEnchantments(String itemType, float delaySeconds, boolean allowIncompatible, boolean useSelector) {
		MinecraftClient client = MinecraftClient.getInstance();
		if (client.player == null || client.getNetworkHandler() == null) {
			return 0;
		}

		String[] baseCommands = getEnchantCommands(itemType.toLowerCase());
		if (!allowIncompatible) {
			baseCommands = filterIncompatible(baseCommands);
		}

		if (baseCommands.length == 0) {
			client.player.sendMessage(Text.literal("Invalid or empty enchant set for: " + itemType), false);
			return 0;
		}

		String[] commands = new String[baseCommands.length];
		for (int i = 0; i < baseCommands.length; i++) {
			if (useSelector) {
				commands[i] = baseCommands[i];
			} else {
				commands[i] = baseCommands[i].replace(" @s ", " ");
			}
		}

		for (int i = 0; i < commands.length; i++) {
			String cmd = commands[i];
			long delayMs = (long) (i * delaySeconds * 1000);
			scheduler.schedule(() -> {
				if (client.getNetworkHandler() != null) {
					client.getNetworkHandler().sendChatCommand(cmd);
				}
			}, delayMs, TimeUnit.MILLISECONDS);
		}

		// Send final message exactly after last command, no extra delay
		long finalDelay = (long) ((commands.length - 1) * delaySeconds * 1000);
		scheduler.schedule(() -> {
			if (client.player != null) {
				client.player.sendMessage(Text.literal("Enchanting complete for: " + itemType), false);
			}
		}, finalDelay, TimeUnit.MILLISECONDS);

		return 1;
	}

	private String[] filterIncompatible(String[] commands) {
		Set<String> seenGroups = new HashSet<>();
		List<String> result = new ArrayList<>();

		for (String cmd : commands) {
			String[] parts = cmd.split(" ");
			if (parts.length < 3) continue;
			String enchant = parts[2];

			String group = getIncompatibleGroup(enchant);
			if (group == null || seenGroups.add(group)) {
				result.add(cmd);
			}
		}
		return result.toArray(new String[0]);
	}

	private String getIncompatibleGroup(String enchant) {
		switch (enchant) {
			case "sharpness":
			case "smite":
			case "bane_of_arthropods":
				return "melee_damage";

			case "protection":
			case "fire_protection":
			case "projectile_protection":
			case "blast_protection":
				return "armor_protection";

			case "infinity":
			case "mending":
				return "bow_special";

			default:
				return null;
		}
	}

	private String[] getEnchantCommands(String itemType) {
		switch (itemType) {
			case "boots":
				return new String[]{
						"enchant @s protection 4",
						"enchant @s fire_protection 4",
						"enchant @s projectile_protection 4",
						"enchant @s feather_falling 4",
						"enchant @s unbreaking 3",
						"enchant @s mending 1",
						"enchant @s depth_strider 3",
						"enchant @s soul_speed 3"
				};
			case "leggings":
				return new String[]{
						"enchant @s protection 4",
						"enchant @s fire_protection 4",
						"enchant @s projectile_protection 4",
						"enchant @s swift_sneak 3",
						"enchant @s unbreaking 3",
						"enchant @s mending 1"
				};
			case "chestplate":
				return new String[]{
						"enchant @s protection 4",
						"enchant @s fire_protection 4",
						"enchant @s projectile_protection 4",
						"enchant @s unbreaking 3",
						"enchant @s mending 1"
				};
			case "helmet":
				return new String[]{
						"enchant @s protection 4",
						"enchant @s fire_protection 4",
						"enchant @s projectile_protection 4",
						"enchant @s respiration 3",
						"enchant @s aqua_affinity 1",
						"enchant @s unbreaking 3",
						"enchant @s mending 1"
				};
			case "sword":
				return new String[]{
						"enchant @s sharpness 5",
						"enchant @s smite 5",
						"enchant @s bane_of_arthropods 5",
						"enchant @s unbreaking 3",
						"enchant @s mending 1",
						"enchant @s fire_aspect 2",
						"enchant @s sweeping_edge 3"
				};
			case "axe":
				return new String[]{
						"enchant @s sharpness 5",
						"enchant @s smite 5",
						"enchant @s bane_of_arthropods 5",
						"enchant @s efficiency 5",
						"enchant @s unbreaking 3",
						"enchant @s mending 1",
						"enchant @s fortune 3"
				};
			case "pickaxe":
			case "shovel":
			case "hoe":
				return new String[]{
						"enchant @s efficiency 5",
						"enchant @s unbreaking 3",
						"enchant @s mending 1",
						"enchant @s fortune 3"
				};
			case "shear":
			case "shears":
				return new String[]{
						"enchant @s efficiency 5",
						"enchant @s unbreaking 3",
						"enchant @s mending 1"
				};
			case "mace":
				return new String[]{
						"enchant @s mending 1",
						"enchant @s unbreaking 3",
						"enchant @s wind_burst 1",
						"enchant @s fire_aspect 2",
						"enchant @s smite 5",
						"enchant @s bane_of_arthropods 5",
						"enchant @s density 5",
						"enchant @s breach 4"
				};
			case "bow":
				return new String[]{
						"enchant @s mending 1",
						"enchant @s infinity 1",
						"enchant @s power 5",
						"enchant @s unbreaking 3",
						"enchant @s flame 1",
						"enchant @s punch 2"
				};
			case "crossbow":
				return new String[]{
						"enchant @s mending 1",
						"enchant @s unbreaking 3",
						"enchant @s multishot 1",
						"enchant @s piercing 4"
				};
			case "trident":
				return new String[]{
						"enchant @s mending 1",
						"enchant @s unbreaking 3",
						"enchant @s impaling 5",
						"enchant @s loyalty 3",
						"enchant @s channeling 1"
				};
			case "basic":
			case "shield":
			case "flint":
			case "elytra":
				return new String[]{
						"enchant @s unbreaking 3",
						"enchant @s mending 1"
				};
			default:
				return new String[]{};
		}
	}
}
