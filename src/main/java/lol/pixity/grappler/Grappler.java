package lol.pixity.grappler;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import net.kyori.adventure.text.Component;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;
import org.bukkit.Particle;
import org.bukkit.World;
import java.io.File;
import java.io.IOException;
import java.util.*;

public class Grappler extends JavaPlugin implements Listener {

    private File configFile;
    private FileConfiguration config;

    private final HashMap<UUID, Long> cooldowns = new HashMap<>();

    @Override
    public void onEnable() {
        createConfig();
        Bukkit.getPluginManager().registerEvents(this, this);
        if (getCommand("grapplerreload") != null) {
            Objects.requireNonNull(getCommand("grapplerreload")).setExecutor((sender, command, label, args) -> {
                reloadConfig();
                sender.sendMessage("§aGrappler config reloaded!");
                return true;
            });
        }
        if (getCommand("givegrappler") != null) {
            Objects.requireNonNull(getCommand("givegrappler")).setExecutor((sender, command, label, args) -> {
                if (sender instanceof Player player) {
                    ItemStack grappler = createGrapplerItem();
                    player.getInventory().addItem(grappler);
                    player.sendMessage("§aGrappling ability item given!");
                }
                return true;
            });
        }
    }

    private void createConfig() {
        configFile = new File(getDataFolder(), "config.yml");
        if (!configFile.exists()) {
            configFile.getParentFile().mkdirs();
            saveResource("config.yml", false);
        }
        config = YamlConfiguration.loadConfiguration(configFile);
    }

    @Override
    public void reloadConfig() {
        config = YamlConfiguration.loadConfiguration(configFile);
    }

    private ItemStack createGrapplerItem() {
        ItemStack grappler = new ItemStack(Material.PAPER);
        ItemMeta meta = grappler.getItemMeta();
        if (meta != null) {
            meta.displayName(Component.text("§6Grapple Ability (Right Click)"));
            meta.lore(List.of(
                    Component.text("§7Can be used from empty hand"),
                    Component.text("§7or by holding ability item")
            ));
            grappler.setItemMeta(meta);
        }
        return grappler;
    }

    @EventHandler
    public void onPlayerInteract(PlayerInteractEvent event) {
        Player player = event.getPlayer();
    
            if (!hasGrappleAbilityItem(player)) {
                return;
            }

            if (cooldowns.containsKey(player.getUniqueId())) {
                long timeLeft = (cooldowns.get(player.getUniqueId()) - System.currentTimeMillis()) / 1000;
                if (timeLeft > 0) {
                    player.sendActionBar(Component.text("§cYou must wait " + timeLeft + " seconds to use the grappler again."));
                    return;
                }
            }

            if (event.getAction().isRightClick()) {
                int maxDistance = config.getInt("max-distance", 50);
                Block targetBlock = player.getTargetBlockExact(maxDistance);

                if (targetBlock == null) {
                    player.sendActionBar(Component.text("§cNo valid block found within range!"));
                    getLogger().info("No target block found for player " + player.getName());
                    return;
                }

                getLogger().info("Target block found: " + targetBlock.getType() + " at " + targetBlock.getLocation());

                Location targetLocation = targetBlock.getLocation().add(0.5, 1, 0.5);
                double distance = player.getLocation().distance(targetLocation);

                if (distance <= maxDistance) {
                    int cooldownTime = Math.max(0, config.getInt("cooldown", 5));
                    cooldowns.put(player.getUniqueId(), System.currentTimeMillis() + (cooldownTime * 1000L));

                    player.sendActionBar(Component.text("§aGrappling to target!"));
                    getLogger().info("Player " + player.getName() + " grappling to " + targetLocation);

                    new BukkitRunnable() {
                        final World world = player.getWorld();
                        final Location startLoc = player.getLocation().add(0, 1, 0);
                        final Vector direction = targetLocation.toVector().subtract(startLoc.toVector()).normalize();
                        double distanceTraveled = 0;

                        @Override
                        public void run() {
                            if (distanceTraveled >= distance || !player.isOnline()) {
                                cancel();
                                return;
                            }

                            Vector movement = direction.clone().multiply(0.5);
                            player.setVelocity(movement);

                            Location playerLoc = player.getLocation().add(0, 1, 0);
                            Vector leashVector = targetLocation.toVector().subtract(playerLoc.toVector());
                            double leashLength = leashVector.length();
                            Vector leashDirection = leashVector.normalize();

                            for (double i = 0; i < leashLength; i += 0.5) {
                                Location particleLoc = playerLoc.clone().add(leashDirection.clone().multiply(i));
                                world.spawnParticle(Particle.CRIT, particleLoc, 1, 0, 0, 0, 0);
                            }

                            distanceTraveled += 0.5;
                        }
                    }.runTaskTimer(this, 0, 1);
                } else {
                    player.sendActionBar(Component.text("§cTarget block is out of range!"));
                    getLogger().info("Target block out of range for player " + player.getName());
            }
        }
    }

    private boolean hasGrappleAbilityItem(Player player) {
        for (ItemStack item : player.getInventory()) {
            if (item != null && item.getType() == Material.PAPER && item.hasItemMeta()) {
                ItemMeta meta = item.getItemMeta();
                if (meta != null && Component.text("§6Grapple Ability (Right Click)").equals(meta.displayName())) {
                    return true;
                }
            }
        }
        return false;
    }

    @Override
    public void saveConfig() {
        try {
            config.save(configFile);
        } catch (IOException e) {
            getLogger().severe("Failed to save config file: " + e.getMessage());
        }
    }

    @Override
    public void onDisable() {
        cooldowns.clear();
    }
}
