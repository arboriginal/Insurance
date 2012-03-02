package me.arboriginal.Insurance;

import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.logging.FileHandler;
import java.util.logging.Formatter;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;

import org.bukkit.ChatColor;
import org.bukkit.CoalType;
import org.bukkit.DyeColor;
import org.bukkit.GrassSpecies;
import org.bukkit.Material;
import org.bukkit.TreeSpecies;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.material.MonsterEggs;
import org.bukkit.material.SmoothBrick;
import org.bukkit.material.Step;
import org.bukkit.plugin.java.JavaPlugin;

public class Insurance extends JavaPlugin implements Listener {
	protected FileConfiguration	config;

	// -----------------------------------------------------------------------------------------------
	// JavaPlugin related methods
	// -----------------------------------------------------------------------------------------------

	@Override
	public void onEnable() {
		initConfig();
		getServer().getPluginManager().registerEvents(this, this);
	}

	@Override
	public void reloadConfig() {
		super.reloadConfig();
		initConfig();
	}

	@Override
	public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
		if (command.getName().equals("insurance-calculate")) {
			return runCommandCalculate(sender, args);
		}

		if (command.getName().equals("insurance-reload")) {
			return runCommandReload(sender, args);
		}

		return false;
	}

	// -----------------------------------------------------------------------------------------------
	// Listener related methods
	// -----------------------------------------------------------------------------------------------

	@EventHandler
	public void onEntityDeath(PlayerDeathEvent event) {
		Player player = (Player) event.getEntity();

		if (player.hasPermission("Insurance.receivePrime")) {
			List<ItemStack> stuff = event.getDrops();

			if (stuff.size() > 0) {
				float prime = readStuff(stuff, player.getName());

				if (prime > 0) {
					payPlayer(player, prime);
				}
			}
		}
	}

	// -----------------------------------------------------------------------------------------------
	// Custom methods
	// -----------------------------------------------------------------------------------------------

	public float readStuff(List<ItemStack> stuff, String player) {
		boolean log = config.getBoolean("Insurance.log_prime");
		List<ItemStack> paid = new ArrayList<ItemStack>();
		String prime = player + "\n";
		float amount = 0;

		for (ItemStack stack : stuff) {
			float price = calculatePrice(stack);

			if (price > 0) {
				paid.add(stack);
				amount += price;

				if (log) {
					Material type = stack.getType();
					List<String> subMaterials = getSubMaterials(type);
					float condition = calculateCondition(stack);
					String details = "";

					if (subMaterials.size() > 0) {
						details += subMaterials.get(stack.getData().getData()) + " ";
					}

					if (condition < 1) {
						details += "condition: " + (100 * condition) + " %";
					}

					if (!details.equals("")) {
						details = " (" + details.trim() + ") ";
					}

					prime += stack.getAmount() + "x " + type + details + ": " + price + "\n";
				}
			}
		}

		stuff.removeAll(paid);

		if (log) {
			logPrime(prime + "-------------------------------------------------------------\nTotal: " + amount + "\n");
		}

		return amount;
	}

	private void logPrime(String prime) {
		final String dateFormat = config.getString("Insurance.log_dateFormat");
		Logger logger = Logger.getLogger("Insurance");
		FileHandler fh = null;

		try {
			fh = new FileHandler(getDataFolder() + "/primesLog.txt", config.getInt("Insurance.log_filesize"), 1, true);
			fh.setFormatter(new Formatter() {
				@Override
				public String format(LogRecord record) {
					return new SimpleDateFormat(dateFormat).format(new Date()) + " - " + formatMessage(record) + "\n\n";
				}
			});

			logger.addHandler(fh);
			logger.log(Level.OFF, prime);
			fh.close();
		}
		catch (SecurityException e) {
			e.printStackTrace();
		}
		catch (IOException e) {
			e.printStackTrace();
		}
	}

	private float calculatePrime(Player player) {
		return calculateContentPrime(player.getInventory().getContents())
		    + calculateContentPrime(player.getInventory().getArmorContents());
	}

	private float calculateContentPrime(ItemStack[] stuff) {
		float amount = 0;

		for (ItemStack stack : stuff) {
			amount += calculatePrice(stack);
		}

		return amount;
	}

	private float calculatePrice(ItemStack stack) {
		float price = getItemPrice(stack);

		if (price > 0) {
			price *= stack.getAmount();
		}

		return price;
	}

	private float getItemPrice(ItemStack stack) {
		float finalPrice = 0;

		if (stack != null) {
			String key = "Insurance.primes." + stack.getType();

			if (config.contains(key)) {
				Object price = config.get(key);

				if (!(price instanceof Double)) {
					key += "." + getSubMaterials(stack.getType()).get(stack.getData().getData());
					price = config.getDouble(key);
				}

				finalPrice += (Double) price * calculateCondition(stack);
			}
		}

		return finalPrice;
	}

	public void payPlayer(Player player, float amount) {
		player.sendMessage(renderString("pay_message", player.getName(), amount));

		getServer().dispatchCommand(getServer().getConsoleSender(), renderString("pay_command", player.getName(), amount));
	}

	private void initConfig() {
		config = getConfig();
		boolean edited = checkConfigValue("Insurance.log_prime", true);

		edited = checkConfigValue("Insurance.log_filesize", 10000) || edited;
		edited = checkConfigValue("Insurance.log_dateFormat", "yyyy-MM-dd HH:mm:ss") || edited;
		edited = checkConfigValue("Insurance.calc_message", "<player>, if you die now, you will receive <amount> dollars.")
		    || edited;
		edited = checkConfigValue("Insurance.pay_message",
		    "<player>, you will receive soon <amount> dollars for your lost stuff.") || edited;
		edited = checkConfigValue("Insurance.pay_command",
		    "tell <player> Your admin didn't make his job, you loose <amount> dollars :D") || edited;
		edited = checkConfigValue("Insurance.consider_condition", true) || edited;

		for (Material material : Material.values()) {
			Double defaultValue = 0.0;
			List<String> subMaterials = getSubMaterials(material);

			if (subMaterials.size() == 0) {
				edited = checkConfigValue("Insurance.primes." + material, defaultValue) || edited;
			}
			else {
				if (config.contains("Insurance.primes." + material)) {
					Object value = config.get("Insurance.primes." + material);

					if (value instanceof Double) {
						defaultValue += (Double) value;
					}
				}

				for (String sm : subMaterials) {
					edited = checkConfigValue("Insurance.primes." + material + "." + sm, defaultValue) || edited;
				}
			}
		}

		if (edited) {
			saveConfig();
			System.out.println("[Insurance] Omitted values has been added to your config file.");
		}
	}

	private List<String> getSubMaterials(Material material) {
		List<String> values = new ArrayList<String>();

		switch (material) {
			case SAPLING:
			case LOG:
			case LEAVES:
				return subMaterialsList(TreeSpecies.values());
			case LONG_GRASS:
				return subMaterialsList(GrassSpecies.values());
			case COAL:
				return subMaterialsList(CoalType.values());
			case WOOL:
				return subMaterialsList(DyeColor.values());
			case STEP:
			case DOUBLE_STEP:
				return subMaterialsList((new Step()).getTextures());
			case SMOOTH_BRICK:
				return subMaterialsList((new SmoothBrick()).getTextures());
			case MONSTER_EGGS:
				return subMaterialsList((new MonsterEggs()).getTextures());

			case INK_SACK:
				values = getSubMaterials(Material.WOOL);
				Collections.reverse(values);
				break;

			case HUGE_MUSHROOM_1:
			case HUGE_MUSHROOM_2:
				values.add(0, "Fleshy");
				values.add(1, "Top_north_west");
				values.add(2, "Top_north");
				values.add(3, "Top_north_east");
				values.add(4, "Top_west");
				values.add(5, "Top");
				values.add(6, "Top_east");
				values.add(7, "Top_south_west");
				values.add(8, "Top_south");
				values.add(9, "Top_south_east");
				values.add(10, "Stem");
				break;
		}

		return values;
	}

	private List<String> subMaterialsList(List<?> subMaterials) {
		return subMaterialsList(subMaterials.toArray());
	}

	private List<String> subMaterialsList(Object[] subMaterials) {
		List<String> values = new ArrayList<String>();

		for (Object subMaterial : subMaterials) {
			values.add(subMaterial.toString());
		}

		return values;
	}

	private float calculateCondition(ItemStack stack) {
		if (!config.getBoolean("Insurance.consider_condition")) {
			return 1;
		}

		int maxDurability = stack.getType().getMaxDurability();

		if (maxDurability == 0) {
			return 1;
		}
		
		return 1 - stack.getDurability() / (float) maxDurability;
	}

	private boolean checkConfigValue(String key, Object defaultValue) {
		if (!config.contains(key)) {
			config.set(key, defaultValue);

			return true;
		}

		return false;
	}

	private boolean runCommandCalculate(CommandSender sender, String[] args) {
		Player player = null;

		if (args.length == 1 && sender.hasPermission("Insurance.calculatePrime")) {
			player = getServer().getPlayer(args[0]);

			if (player == null) {
				sender.sendMessage(ChatColor.RED + "This player is not online.");

				return true;
			}
		}
		else if (args.length == 0 && sender.hasPermission("Insurance.calculateMyPrime")) {
			if (sender instanceof Player) {
				player = (Player) sender;
			}
			else {
				sender.sendMessage(ChatColor.RED + "You need to specify a playername.");

				return true;
			}
		}
		else {
			return false;
		}

		sender.sendMessage(ChatColor.GREEN + renderString("calc_message", player.getName(), calculatePrime(player)));

		return true;
	}

	private boolean runCommandReload(CommandSender sender, String[] args) {
		if (!sender.hasPermission("Insurance.reload")) {
			sender.sendMessage("Unknown command. Type \"help\" for help.");

			return true;
		}

		if (args.length == 0) {
			reloadConfig();
			sender.sendMessage(ChatColor.GREEN + "Insurance config has been reloaded!");

			return true;
		}

		return false;
	}

	private String renderString(String key, String player, float amount) {
		return config.getString("Insurance." + key).replace("<player>", player).replace("<amount>", "" + amount);
	}
}
