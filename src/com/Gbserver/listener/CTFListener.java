package com.Gbserver.listener;

import com.Gbserver.Main;
import com.Gbserver.Utilities;
import com.Gbserver.commands.CTF;
import com.Gbserver.commands.Team;
import com.Gbserver.variables.ChatWriter;
import com.Gbserver.variables.ChatWriterType;
import org.bukkit.*;
import org.bukkit.entity.Player;
import org.bukkit.entity.Sheep;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.LinkedList;
import java.util.List;

public class CTFListener implements Listener {
    public static List<Player> inSafety = new LinkedList<>();

    @EventHandler
    public void onPlayerInteractEntity(PlayerInteractEntityEvent pie) {

        if (CTF.allPlayers().contains(pie.getPlayer()) && CTF.isRunning) {
            pie.setCancelled(true);
            // Valid.
            //

            if (pie.getPlayer().getItemInHand().getType() == Material.SHEARS
                    && pie.getRightClicked() instanceof Sheep) {
                // Make the flag ride on this player. On death, it teleports
                // back to the original location.
                // On passing boundary, wins!
                Sheep s = (Sheep) pie.getRightClicked();
                if (s.getColor() == DyeColor.BLUE && CTF.getOriginatedTeam(pie.getPlayer()) == Team.RED) {
                    Bukkit.getScheduler().cancelTask(CTF.frozenblue);
                    pie.getPlayer().setPassenger(pie.getRightClicked());
                    pie.getPlayer().sendMessage(ChatWriter.getMessage(ChatWriterType.GAME,
                            "You are now carrying the flag. Cross the team boundary to win!"));
                    return;
                }

                if (s.getColor() == DyeColor.RED && CTF.getOriginatedTeam(pie.getPlayer()) == Team.BLUE) {
                    Bukkit.getScheduler().cancelTask(CTF.frozenred);
                    pie.getPlayer().setPassenger(pie.getRightClicked());
                    pie.getPlayer().sendMessage(ChatWriter.getMessage(ChatWriterType.GAME,
                            "You are now carrying the flag. Cross the team boundary to win!"));
                }
            }
        }
    }

    @EventHandler
    public void onPlayerDeath(final PlayerDeathEvent pde) {
        if (CTF.allPlayers().contains(pde.getEntity()) && CTF.isRunning) {
            pde.setKeepInventory(true);
            if (pde.getEntity().getPassenger() == CTF.blueFlag) {
                // Blue flag needs to be in its original location.
                pde.getEntity().eject();
                CTF.blueFlag.teleport(CTF.blueFlagLocation);
                CTF.frozenblue = Utilities.setFrozen(CTF.blueFlag);

            }

            if (pde.getEntity().getPassenger() == CTF.redFlag) {
                // Red flag needs to be in its original location.
                pde.getEntity().eject();
                CTF.redFlag.teleport(CTF.redFlagLocation);
                CTF.frozenred = Utilities.setFrozen(CTF.redFlag);
            }
            // Respawn after 10 seconds
            pde.setDeathMessage(ChatWriter.getMessage(ChatWriterType.DEATH, ChatColor.YELLOW + pde.getEntity().getName()
                    + ChatColor.DARK_RED + " has been killed while trying to obtain the flag."));
            ChatWriter.writeTo(pde.getEntity(), ChatWriterType.GAME, "Respawning in 10 seconds...");
            pde.getEntity().setHealth(20);
            pde.getEntity().setGameMode(GameMode.SPECTATOR);
            pde.getEntity().teleport(CTF.spectate);
            Bukkit.getScheduler().scheduleSyncDelayedTask(JavaPlugin.getPlugin(Main.class), new Runnable() {
                public void run() {
                    pde.getEntity().teleport(CTF.getSpawn(CTF.getOriginatedTeam(pde.getEntity())));
                    pde.getEntity().setGameMode(GameMode.SURVIVAL);
                    ChatWriter.writeTo(pde.getEntity(), ChatWriterType.GAME, "You have been respawned.");
                }
            }, 200L);
        }

    }

    @EventHandler
    public void onEntityDamageByEntity(EntityDamageByEntityEvent edbee) {
        if(!(edbee.getDamager() instanceof Player)) return;
        Player damager = (Player) edbee.getDamager();
        if (damager.getItemInHand().getType() == Material.IRON_SWORD
                && edbee.getEntity() instanceof Player) {
            // Deduct health by 20/3.
            Player p = (Player) edbee.getEntity();
            if (CTF.getOriginatedTeam(p) != CTF.getLocationTeam(p)) {
                p.damage(20 / 3);
            }
            return;
        }
        if (edbee.getEntity() == CTF.blueFlag || edbee.getEntity() == CTF.redFlag) {
            edbee.setCancelled(true);
        }
        if (CTF.isRunning && inSafety.contains(edbee.getEntity())) {
            edbee.setCancelled(true);
        }
    }

    @EventHandler
    public void onPlayerMove(final PlayerMoveEvent pme) {
        if (CTF.allPlayers().contains(pme.getPlayer()) && CTF.isRunning) {
            if (pme.getPlayer().getLocation().getY() < 0 && CTF.isRunning
                    && CTF.allPlayers().contains(pme.getPlayer())) {
                pme.getPlayer().teleport(CTF.getSpawn(CTF.getOriginatedTeam(pme.getPlayer())));
            }
            if (pme.getPlayer().getLocation().getY() > 90 && pme.getPlayer().getLocation().getBlockZ() == 0) {
                // This is where the game ends.
                if (pme.getPlayer().getPassenger() == CTF.blueFlag || pme.getPlayer().getPassenger() == CTF.redFlag) {

                    ChatWriter.write(ChatWriterType.GAME, Team.toString(CTF.getOriginatedTeam(pme.getPlayer()))
                            + ChatColor.YELLOW + " has won the game!");
                    CTF.stopGame();
                } else {
                    ChatWriter.writeTo(pme.getPlayer(), ChatWriterType.GAME, "You are crossing the team boundary.");
                    return;
                }

            }
            // 5 sec limit near flag.
            if (pme.getPlayer().getLocation().distance(
                    CTF.getFlagByTeam(Team.opposite(CTF.getOriginatedTeam(pme.getPlayer())))) < 5) {
                if (!inSafety.contains(pme.getPlayer())) {
                    inSafety.add(pme.getPlayer());
                    ChatWriter.writeTo(pme.getPlayer(), ChatWriterType.GAME, "You have entered the safety circle! "
                            + ChatColor.YELLOW + "You will be killed if you don't leave the circle after 5 seconds!");
                    Bukkit.getScheduler().scheduleSyncDelayedTask(Utilities.getInstance(), new Runnable() {
                        public void run() {
                            if (inSafety.contains(pme.getPlayer())) {
                                pme.getPlayer().damage(pme.getPlayer().getHealth());
                                inSafety.remove(pme.getPlayer());
                            }
                        }
                    }, 100L);

                }
                return;
            }
            inSafety.remove(pme.getPlayer());
        }
    }

}
