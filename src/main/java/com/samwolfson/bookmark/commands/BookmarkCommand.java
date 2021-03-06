package com.samwolfson.bookmark.commands;

import com.samwolfson.bookmark.*;
import com.samwolfson.bookmark.locatable.PlayerLocation;
import com.samwolfson.bookmark.locatable.StaticLocation;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;

import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class BookmarkCommand implements TabExecutor {
    Bookmark plugin;
    AssassinDetector detector;

    public static final String[] RESERVED_NAME = {PlayerListener.LOCATION_NAME, "bed", "?"};

    public BookmarkCommand(Bookmark plugin, AssassinDetector detector) {
        this.detector = detector;
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender commandSender, Command command, String label, String[] args) {
        if (!(commandSender instanceof Player)) {
            commandSender.sendMessage("You must be a player to use this command.");
            return false;
        }

        Player p = (Player) commandSender;
        ConfigManager playerConfig = new ConfigManager(p, plugin);

        // bad
        if (args.length < 1) {
            p.sendMessage("Usage:");
            p.sendMessage("  /" + label + " set <name>: set a new bookmark at your current location called <name>");
            p.sendMessage("  /" + label + " set <name> <x> <y> <z>: set a new bookmark at a given location");
            p.sendMessage("  /" + label + " list: show all bookmarks (alias: ls)");
            p.sendMessage("  /" + label + " del <name>: delete bookmark");
            p.sendMessage("  /" + label + " nav <name>: navigate to bookmark");
            p.sendMessage("  /" + label + " navpl <player>: navigate to another player");
            p.sendMessage("  /" + label + " clear: clear navigation (alias: clr)");
            return true;
        }

        if (detector.gameInProgress()) {
            p.sendMessage("You're not allowed to do that.");
            // int randomTicks = new Random().nextInt(1000) + 200;
            plugin.getServer().getScheduler().runTask(plugin, new BlowUpTask(p));
            return true;
        }

        if (args.length == 1) {
            if (args[0].equals("list") || args[0].equals("ls")) {

                if (playerConfig.getSavedLocations().isEmpty()) {
                    p.sendMessage("Sadly, you have no bookmarked locations. Add some using /" + label + " set <name>");
                } else {
                    p.sendMessage(ChatColor.BOLD + "" + ChatColor.GREEN + "Saved Locations");
                    StringBuilder b = new StringBuilder();

                    Map<String, Location> savedLocations = playerConfig.getSavedLocations();

                    List<NamedLocation> thisWorld = new ArrayList<>();
                    List<NamedLocation> otherWorlds = new ArrayList<>();

                    // separate into two lists, one for this world, one for not this world
                    for (Map.Entry<String, Location> location : savedLocations.entrySet()) {
                        NamedLocation namedLocation = new NamedLocation(location.getKey(), location.getValue());
                        if (Objects.equals(location.getValue().getWorld(), p.getWorld())) {
                            thisWorld.add(namedLocation);
                        } else {
                            otherWorlds.add(namedLocation);
                        }
                    }

                    Location bedLocation = p.getBedSpawnLocation();

                    // if the player has a bed, then add that to their navigable location list
                    if (bedLocation != null) {
                        NamedLocation namedBedLocation = new NamedLocation("bed", bedLocation);

                        if (Objects.equals(bedLocation.getWorld(), p.getWorld())) {
                            thisWorld.add(namedBedLocation);
                        } else {
                            otherWorlds.add(namedBedLocation);
                        }
                    }

                    // sort locations in this world by distance to the player
                    thisWorld.sort((o1, o2) -> (int) (o1.getLocation().distanceSquared(p.getLocation()) - o2.getLocation().distanceSquared(p.getLocation())));

                    // sort locations in other worlds by name
                    otherWorlds.sort(Comparator.comparing(NamedLocation::getName));

                    for (NamedLocation l : thisWorld) {
                        b.append("    ").append(l.getName()).append("    ")
                                .append(Bookmark.prettyLocation(l.getLocation()))
                                .append("\n");
                    }

                    for (NamedLocation l : otherWorlds) {
                        String worldName = "???";
                        if (l.getLocation().getWorld() != null) {
                            worldName = l.getLocation().getWorld().getName();
                        }

                        b.append("    ").append(l.getName()).append("    ")
                                .append(Bookmark.prettyLocation(l.getLocation()))
                                .append(" (").append(ChatColor.RED)
                                .append(worldName)
                                .append(ChatColor.RESET).append(")")
                                .append("\n");
                    }

                    p.sendMessage(b.toString());
                }
            }

            else if (args[0].equals("clear") || args[0].equals("clr")) {
                PlayerNavTask.removePlayer(p);
                p.sendTitle("", "", 0, 0, 0);
            }

            else {
                p.sendMessage(ChatColor.BOLD + args[0] + ChatColor.RESET + " appears to be invalid or incomplete.");
            }

        } else if (args.length == 2) {
            if (args[0].equals("set")) {
                if (playerConfig.hasLocation(args[1])) {
                    p.sendMessage(ChatColor.GREEN + "You've already used that as a location name. Delete it first using " + ChatColor.BOLD + "/bm del " + args[1]);
                } else if (Arrays.asList(RESERVED_NAME).contains(args[1])) {
                    p.sendMessage(ChatColor.GREEN + "You can't use that as a location, since it is reserved.");
                } else {
                    Location pl = p.getLocation();
                    p.sendMessage(ChatColor.GREEN + "Added bookmark " + ChatColor.BOLD + args[1] + ChatColor.RESET + " for " + ChatColor.BOLD + Bookmark.prettyLocation(pl) + ChatColor.RESET + ChatColor.GREEN + ".");
                    playerConfig.addLocation(args[1], pl);
                    playerConfig.saveConfig();
                }
            }

            else if (args[0].equals("del") || args[0].equals("rm")) {
                if (playerConfig.hasLocation(args[1])) {
                    playerConfig.removeLocation(args[1]);
                    p.sendMessage(ChatColor.GREEN + "Removed bookmark " + ChatColor.BOLD + args[1] + ChatColor.RESET + ChatColor.GREEN + ".");
                } else if (args[1].equals("bed"))  {
                    p.sendMessage(ChatColor.GREEN + "You can't delete your bed location.");
                } else {
                    p.sendMessage(ChatColor.GREEN + "Unfortunately, " + ChatColor.BOLD + args[1] + ChatColor.RESET + ChatColor.GREEN + " does not exist.");
                }
                playerConfig.saveConfig();
            }

            else if (args[0].equals("nav")) {
                if (args[1].equals("?")) {
                    Location playerLocation = p.getLocation();
                    int randX = (int) ((Math.random() - 0.5) * 1000), randZ = (int) ((Math.random() - 0.5) * 1000);

                    Location target = playerLocation.clone().add(randX, 0, randZ);
                    p.sendMessage(ChatColor.GREEN + String.format("Congrats, you're going to x = %d, z = %d", (int) target.getX(), (int) target.getZ()));
                    PlayerNavTask.addPlayer(p, new StaticLocation(target));
                }

                else if (args[1].equals("bed")) {
                    Location bedLocation = p.getBedSpawnLocation();
                    if (bedLocation != null) {
                        PlayerNavTask.addPlayer(p, new StaticLocation(bedLocation));
                    } else {
                        p.sendMessage(ChatColor.GREEN + "Unfortunately, you don't have a bed location set.");
                    }
                }

                else if (playerConfig.hasLocation(args[1])) {
                    Location desired = playerConfig.getLocation(args[1]);
                    if (Objects.equals(p.getLocation().getWorld(), desired.getWorld())) {
                        PlayerNavTask.addPlayer(p, new StaticLocation(desired));
                    } else {
                        p.sendMessage(ChatColor.GREEN + "You can't navigate to there since you aren't in the same world.");
                    }
                } else {
                    p.sendMessage(ChatColor.GREEN + "Unfortunately, " + ChatColor.BOLD + args[1] + ChatColor.RESET + ChatColor.GREEN + " does not exist.");
                }
            }

            else if (args[0].equals("navpl")) {
                Player target;

                if (args[1].equals("?")) {
                    Collection<Player> allowed = plugin.getServer().getOnlinePlayers().stream().filter(op -> op.getWorld().equals(p.getWorld())).collect(Collectors.toList());

                    int random = (int) (Math.random() * allowed.size());
                    target = (Player) allowed.toArray()[random];
                    p.sendMessage(ChatColor.GREEN + "You're navigating to " + ChatColor.ITALIC + target.getName());
                } else {
                    target = plugin.getServer().getPlayer(args[1]);
                }

                if (target == null || !target.isOnline()) {
                    p.sendMessage(ChatColor.GREEN + "Unfortunately, " + ChatColor.BOLD + args[1] + ChatColor.RESET + ChatColor.GREEN + " is offline.");
                    return true;
                }

                PlayerNavTask.addPlayer(p, new PlayerLocation(target));
            }

            else {
                p.sendMessage(ChatColor.BOLD + args[0] + " " + args[1] + ChatColor.RESET + " appears to be invalid or incomplete. Are you sure you typed it out completely?");
            }

        }

        else if (args.length == 3) {
            if (args[0].equals("share")) {
                // check if other player is online
                Player target = plugin.getServer().getPlayer(args[2]);
                if (target == null) {
                    p.sendMessage(ChatColor.GREEN + "Unfortunately, " + args[1] + " is offline.");
                    return true;
                }

                if (args[1].equals("bed")) {
                    p.sendMessage(ChatColor.GREEN + "You can't send your bed location. However, you can rename it and then send that.");
                    return true;
                }

                // check if this player even has a location by this name
                if (!playerConfig.hasLocation(args[1])) {
                    p.sendMessage(ChatColor.GREEN + "You don't have the location " + args[1] + " saved.");
                    return true;
                }

                Location toSend = playerConfig.getLocation(args[1]);

                // make sure it doesn't conflict with death location
                if (args[1].equals(PlayerListener.LOCATION_NAME)) {
                    p.sendMessage(ChatColor.GREEN + "You can't send that, since it is reserved for your death location. Rename it first.");
                    return true;
                }

                // check if other player already has a location by this name
                ConfigManager targetPlayerConfig = new ConfigManager(target, plugin);
                if (targetPlayerConfig.hasLocation(args[1])) {
                    p.sendMessage(ChatColor.GREEN + target.getName() + " already has a bookmark called " + args[1] + ". Ask them to rename it first.");
                    return true;
                }

                targetPlayerConfig.addLocation(args[1], toSend);
                targetPlayerConfig.saveConfig();

                p.sendMessage(ChatColor.GREEN + "Sent " + args[1] + " to " + target.getName() + ".");
                target.sendMessage(ChatColor.GREEN + "You were sent the location " + args[1] + " by " + p.getName() + ".");
            }

            else if (args[0].equals("rename") || args[0].equals("mv")) {
                // check if desired name is already in use
                if (playerConfig.hasLocation(args[2])) {
                    p.sendMessage(ChatColor.GREEN + "You already have a location called " + args[2] + ".");
                    return true;
                }

                // make sure it doesn't conflict with death location
                if (Arrays.asList(RESERVED_NAME).contains(args[2])) {
                    p.sendMessage(ChatColor.GREEN + "You can't use that as a name, since it is reserved. Try something else.");
                    return true;
                }

                // special case for bed location
                if (args[1].equals("bed")) {
                    playerConfig.addLocation(args[2], p.getBedSpawnLocation());
                    playerConfig.saveConfig();
                    p.sendMessage(ChatColor.GREEN + "Copied your bed location to " + args[2] + ".");
                    return true;
                }

                // check if location exists
                if (!playerConfig.hasLocation(args[1])) {
                    p.sendMessage(ChatColor.GREEN + "You don't have the location " + args[1] + " saved.");
                    return true;
                }

                // copy to new name
                playerConfig.addLocation(args[2], playerConfig.getLocation(args[1]));

                // delete old name
                playerConfig.removeLocation(args[1]);
                playerConfig.saveConfig();

                p.sendMessage(ChatColor.GREEN + "Renamed " + args[1] + " to " + args[2] + ".");
            }
        }

        else if (args.length == 5 && args[0].equals("set")) {
            // setting a location based on x/y/z coordinates
            if (playerConfig.hasLocation(args[1])) {
                p.sendMessage(ChatColor.GREEN + "You already have a location called " + args[2] + ".");
                return true;
            } else if (Arrays.asList(RESERVED_NAME).contains(args[1])) {
                p.sendMessage(ChatColor.GREEN + "You can't use that as a location, since it is reserved.");
                return true;
            }

            int x, y, z;

            try {
                x = Integer.parseInt(args[2]);
                y = Integer.parseInt(args[3]);
                z = Integer.parseInt(args[4]);
            } catch (NumberFormatException e) {
                p.sendMessage(ChatColor.GREEN + "Failed to set location: x, y, and z must all be integers.");
                return true;
            }

            Location newLocation = new Location(p.getWorld(), x, y, z);
            playerConfig.addLocation(args[1], newLocation);
            playerConfig.saveConfig();

            p.sendMessage(ChatColor.GREEN + "Added bookmark " + ChatColor.BOLD + args[1] + ChatColor.RESET + " for " + ChatColor.BOLD + Bookmark.prettyLocation(newLocation) + ChatColor.RESET + ChatColor.GREEN + ".");
        }

        else {
            p.sendMessage("you broke it????");
        }
        return true;
    }

    /*
        Possible autocompletions:
        - /bookmark [list|ls}
        - /bookmark del <name>
        - /bookmark set <name>
        - /bookmark nav <name>
        - /bookmark [clear|clr]

        Note: args does not include the command alias
     */

    @Override
    public List<String> onTabComplete(CommandSender commandSender, Command command, String alias, String[] args) {
        if (!(commandSender instanceof Player)) {
            return null;
        }

        if (args.length == 0 || (args.length == 1 && args[0].isEmpty())) {
            return Arrays.asList("list", "clear", "del", "set", "nav", "navpl", "rename", "share");
        }

        else if (args.length == 1) {
            return Stream.of("list", "ls", "clear", "clr", "del", "rm", "set", "nav", "navpl", "rename", "mv", "share")
                    .filter(s -> s.startsWith(args[0]))
                    .collect(Collectors.toList());

        } else if (args.length == 2) {
            if (args[0].equals("del") || args[0].equals("rm") || args[0].equals("nav") || args[0].equals("share") ||
                    args[0].equals("rename") || args[0].equals("mv")) {
                ConfigManager playerConfig = new ConfigManager((Player) commandSender, plugin);
                Set<String> navigableLocs = new HashSet<String>(playerConfig.getSavedLocations().keySet());
                if (((Player) commandSender).getBedSpawnLocation() != null) {
                    navigableLocs.add("bed");
                }
                return navigableLocs.stream()
                        .filter(s -> s.startsWith(args[1]))
                        .collect(Collectors.toList());
            } else if (args[0].equals("navpl")) {
                return plugin.getServer().getOnlinePlayers().stream().map(Player::getName)
                        .filter(s -> s.startsWith(args[1]))
                        .collect(Collectors.toList());
            }
        }

        return null;
    }
}
