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
import java.util.concurrent.ConcurrentHashMap;

public class Grappler extends JavaPlugin implements Listener {

    private File configFile;
    private FileConfiguration config;

private static final Component GRAPPLER_ITEM_NAME = Component.text("§6Grapple Ability (Right Click)");
    private final Map<UUID, Long> cooldowns = new ConcurrentHashMap<>();

    @Override
    public void onEnable() {
        createConfig();
        config = getConfig();
        Bukkit.getPluginManager().registerEvents(this, this);
    
        Objects.requireNonNull(getCommand("grapplerreload")).setExecutor((sender, command, label, args) -> {
            reloadPluginConfig();
            sender.sendMessage("§aGrappler config reloaded!");
            return true;
        });
    
        Objects.requireNonNull(getCommand("givegrappler")).setExecutor((sender, command, label, args) -> {
            if (sender instanceof Player player) {
                ItemStack grappler = createGrapplerItem();
                player.getInventory().addItem(grappler);
                player.sendMessage("§aGrappling ability item given!");
            }
            return true;
        });
    }

    private void createConfig() {
        configFile = new File(getDataFolder(), "config.yml");
        if (!configFile.exists()) {
            configFile.getParentFile().mkdirs();
            saveResource("config.yml", false);
        }
        config = YamlConfiguration.loadConfiguration(configFile);
    }

    public void reloadPluginConfig() {
        reloadConfig();
        config = getConfig();
        saveConfig();
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
        if (!event.getAction().isRightClick() || !hasGrappleAbilityItem(event.getPlayer())) {
            return;
        }
    
        Player player = event.getPlayer();
        long currentTime = System.currentTimeMillis();
        long cooldownTime = cooldowns.getOrDefault(player.getUniqueId(), 0L);
    
        if (currentTime < cooldownTime) {
            long timeLeft = (cooldownTime - currentTime) / 1000;
            player.sendActionBar(Component.text("§cYou must wait " + timeLeft + " seconds to use the grappler again."));
            return;
        }
    
        int maxDistance = config.getInt("max-distance", 50);
        Block targetBlock = player.getTargetBlockExact(maxDistance);
    
        if (targetBlock == null) {
            player.sendActionBar(Component.text("§cNo valid block found within range!"));
            return;
        }
    
        Location targetLocation = targetBlock.getLocation().add(0.5, 1, 0.5);
        double distance = player.getLocation().distance(targetLocation);
    
        if (distance > maxDistance) {
            player.sendActionBar(Component.text("§cTarget block is out of range!"));
            return;
        }
    
        int cooldownDuration = Math.max(0, config.getInt("cooldown", 5));
        cooldowns.put(player.getUniqueId(), currentTime + (cooldownDuration * 1000L));
    
        player.sendActionBar(Component.text("§aGrappling to target!"));
        grapplePlayer(player, targetLocation, distance);
    }
    
    private void grapplePlayer(Player player, Location targetLocation, double distance) {
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
                spawnParticles(world, playerLoc, targetLocation);
    
                distanceTraveled += 0.5;
            }
        }.runTaskTimer(this, 0, 1);
    }

private void spawnParticles(World world, Location start, Location end) {
    Vector direction = end.toVector().subtract(start.toVector()).normalize();
    double distance = start.distance(end);
    double step = 0.5;
    for (double i = 0; i < distance; i += step) {
        Location particleLoc = start.clone().add(direction.clone().multiply(i));
        world.spawnParticle(Particle.CRIT, particleLoc, 1, 0, 0, 0, 0);
    }
}
    private boolean hasGrappleAbilityItem(Player player) {
        return player.getInventory().getItemInMainHand().getType() == Material.PAPER &&
               player.getInventory().getItemInMainHand().getItemMeta() != null &&
               GRAPPLER_ITEM_NAME.equals(player.getInventory().getItemInMainHand().getItemMeta().displayName());
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
