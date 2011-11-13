package me.arboriginal.Insurance;

import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.Iterator;
import java.util.List;
import java.util.logging.FileHandler;
import java.util.logging.Formatter;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;

import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.Event.Priority;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.PluginManager;
import org.bukkit.plugin.java.JavaPlugin;

public class Insurance extends JavaPlugin {
  protected FileConfiguration config;

  @Override
  public void onDisable() {
  }

  @Override
  public void onEnable() {
    initConfig();

    PluginManager pm = getServer().getPluginManager();
    InsuranceEntityListener el = new InsuranceEntityListener(this);

    pm.registerEvent(Event.Type.ENTITY_DEATH, el, Priority.Normal, this);
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

  public float readStuff(List<ItemStack> stuff, String player) {
    float amount = 0;
    boolean log = config.getBoolean("Insurance.log_prime");
    List<ItemStack> paid = new ArrayList<ItemStack>();
    String prime = null;

    if (log) {
      prime = player + "\n";
    }

    Iterator<ItemStack> i = stuff.iterator();

    while (i.hasNext()) {
      ItemStack stack = i.next();
      float price = calculatePrice(stack);

      if (price > 0) {
        paid.add(stack);
        amount += price;

        if (log) {
          prime += stack.getAmount() + "x " + stack.getType() + ": " + price + "\n";
        }
      }
    }

    stuff.removeAll(paid);

    if (log) {
      prime += "-------------------------------------------------------------\n";
      prime += "Total: " + amount + "\n";

      logPrime(prime);
    }

    return amount;
  }

  private void logPrime(String prime) {
    Logger logger = Logger.getLogger("Insurance");

    FileHandler fh = null;

    try {
      fh = new FileHandler(getDataFolder() + "/primesLog.txt", 10000, 1, true);
      fh.setFormatter(new InsuranceLogFormatter());

      logger.addHandler(fh);
      logger.log(Level.OFF, prime);

      if (fh != null) {
        fh.close();
      }
    }
    catch (SecurityException e) {
      e.printStackTrace();
    }
    catch (IOException e) {
      e.printStackTrace();
    }
  }

  private float calculatePrime(Player player) {
    float amount = 0;
    ItemStack[] stuff = player.getInventory().getContents();

    if (stuff.length > 0) {
      for (int i = 0; i < stuff.length; i++) {
        ItemStack stack = stuff[i];
        float prime = calculatePrice(stack);

        if (prime > 0) {
          amount += prime;
        }
      }
    }

    return amount;
  }

  public float calculatePrice(ItemStack stack) {
    float price = getItemPrice(stack);

    if (price > 0) {
      price *= stack.getAmount();
    }

    return price;
  }

  public float getItemPrice(ItemStack stack) {
    float price = 0;

    if (stack != null) {
      String key = "Insurance.primes." + stack.getType();

      if (config.contains(key)) {
        price += config.getDouble(key);
      }
    }

    return price;
  }

  public void payPlayer(Player player, float amount) {
    player.sendMessage(config.getString("Insurance.pay_message")
        .replace("<player>", player.getName()).replace("<amount>", "" + amount));

    getServer().dispatchCommand(
        getServer().getConsoleSender(),
        config.getString("Insurance.pay_command").replace("<player>", player.getName())
            .replace("<amount>", "" + amount));
  }

  private void initConfig() {
    config = getConfig();
    boolean edited = checkConfigValue("Insurance.log_prime", true);

    edited = checkConfigValue("Insurance.pay_message",
        "<player>, you will receive soon <amount> dollars for your lost stuff.") || edited;
    edited = checkConfigValue("Insurance.pay_command",
        "tell <player> Your admin didn't made his job, you loose <amount> dollars :D") || edited;

    Material[] values = Material.values();

    for (int i = 0; i < values.length; i++) {
      edited = checkConfigValue("Insurance.primes." + values[i], 0F) || edited;
    }

    if (edited) {
      saveConfig();
      System.out.println("[Insurance] Omitted values has been added to your config file.");
    }
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

    sender.sendMessage(ChatColor.GREEN + "Insurance prime: " + calculatePrime(player));

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

  // Internal class

  private class InsuranceLogFormatter extends Formatter {
    @Override
    public String format(LogRecord record) {
      return new SimpleDateFormat("dd/MM/yyyy, HH:mm:ss").format(new Date()) + " - "
          + formatMessage(record) + "\n\n";
    }

  }
}
