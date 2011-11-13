package me.arboriginal.Insurance;

import java.util.List;

import org.bukkit.entity.Player;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.EntityListener;
import org.bukkit.inventory.ItemStack;

public class InsuranceEntityListener extends EntityListener {
  private final Insurance plugin;

  public InsuranceEntityListener(final Insurance plugin) {
    this.plugin = plugin;
  }

  @Override
  public void onEntityDeath(EntityDeathEvent event) {
    super.onEntityDeath(event);

    if (event.getEntity() instanceof Player) {
      Player player = (Player) event.getEntity();

      if (player.hasPermission("Insurance.receivePrime")) {
        List<ItemStack> stuff = event.getDrops();

        if (stuff.size() > 0) {
          float prime = plugin.readStuff(stuff, player.getName());

          if (prime > 0) {
            plugin.payPlayer(player, prime);
          }
        }
      }
    }
  }
}
