package lv.theironminerlv.sidesurvivalportals.managers;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.sk89q.worldedit.bukkit.BukkitAdapter;
import com.sk89q.worldguard.WorldGuard;
import com.sk89q.worldguard.protection.ApplicableRegionSet;
import com.sk89q.worldguard.protection.flags.Flags;
import com.sk89q.worldguard.protection.flags.StateFlag;
import com.sk89q.worldguard.protection.managers.RegionManager;
import com.sk89q.worldguard.protection.regions.ProtectedCuboidRegion;
import com.sk89q.worldguard.protection.regions.ProtectedRegion;
import com.sk89q.worldguard.protection.regions.RegionContainer;

import org.bukkit.Axis;
import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.SoundCategory;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.Orientable;
import org.bukkit.block.data.type.GlassPane;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;

import lv.theironminerlv.sidesurvivalportals.SideSurvivalPortals;
import lv.theironminerlv.sidesurvivalportals.data.PortalData;
import lv.theironminerlv.sidesurvivalportals.objects.Portal;
import lv.theironminerlv.sidesurvivalportals.utils.BlockUtils;
import lv.theironminerlv.sidesurvivalportals.utils.ConvertUtils;
import lv.theironminerlv.sidesurvivalportals.utils.Messages;
import me.angeschossen.lands.api.integration.LandsIntegration;
import me.angeschossen.lands.api.land.Land;

public class PortalManager
{
    private SideSurvivalPortals plugin;
    private static LandsIntegration landsAPI;
    private static DataManager dataManager;
    public Map<UUID, BukkitTask> tasks = new HashMap<>();

    public PortalManager(SideSurvivalPortals plugin) {
        this.plugin = plugin;
        landsAPI = this.plugin.getLandsAPI();
        dataManager = this.plugin.getDataManager();
    }

    // Fully creates portal (region + blocks), but saving has to be done after
    // Will return true if everything is fine with creation
    public boolean create(Portal portal, boolean isNorthSouth) {
        if (portal == null)
            return false;

        Location pos1 = portal.getPos1();
        Location pos2 = portal.getPos2();
        World world = portal.getWorld();

        if ((pos1 == null) || (pos2 == null) || (world == null))
            return false;
        
        String id = generatePortalName(pos1, world);
        portal.setId(id);

        RegionContainer container = WorldGuard.getInstance().getPlatform().getRegionContainer();
        RegionManager regionManager = container.get(BukkitAdapter.adapt(world));
        if (regionManager == null)
            return false;

        ProtectedRegion region = new ProtectedCuboidRegion(id, ConvertUtils.toBlockVector3(pos1),
                ConvertUtils.toBlockVector3(pos2));
        region.setFlag(Flags.BUILD, StateFlag.State.DENY);
        region.setFlag(Flags.DENY_MESSAGE, "");
        regionManager.addRegion(region);

        setPortalGlass(pos1, pos2, isNorthSouth);

        Location safeLoc = getSafeTeleportLoc(portal);
        if (safeLoc != null)
            portal.setTpLoc(safeLoc);
        else
            portal.setTpLoc(pos1);

        PortalData.addPortal(portal, true);

        return true;
    }

    // Fully removes portal from world and database
    public void remove(Portal portal) {
        if (portal == null)
            return;
    
        ArrayList<Location> portalBlocks = BlockUtils.getBlocksBetween(portal.getPos1(), portal.getPos2());

        new BukkitRunnable(){
            public void run() {
                for (Location blockLoc : portalBlocks) {
                    blockLoc.getBlock().breakNaturally();
                }
            }
        }.runTask(plugin);

        PortalData.removePortal(portal);
        removeRegion(portal.getId(), portal.getWorld());
    }

    // Used to place purple stained glass pane blocks between positions,
    // IsNorthSouth controls how panes are going to connect to adjacent blocks
    private void setPortalGlass(Location pos1, Location pos2, boolean isNorthSouth) {
        ArrayList<Location> portalBlocks = BlockUtils.getBlocksBetween(pos1, pos2);
        Block loopBlock;

        for (Location blockLoc : portalBlocks) {
            loopBlock = blockLoc.getBlock();
            if (loopBlock.getType() != Material.OBSIDIAN) {
                loopBlock.setType(Material.PURPLE_STAINED_GLASS_PANE);
                BlockData data = loopBlock.getBlockData();
                if (isNorthSouth) {
                    ((GlassPane) data).setFace(BlockFace.NORTH, true);
                    ((GlassPane) data).setFace(BlockFace.SOUTH, true);
                } else {
                    ((GlassPane) data).setFace(BlockFace.EAST, true);
                    ((GlassPane) data).setFace(BlockFace.WEST, true);
                }

                loopBlock.setBlockData(data);
                loopBlock.getState().update(true);
            }
        }
    }

    private String generatePortalName(Location loc, World world) {
        return "portal_" + world.getName() + "_" + loc.getBlockX() + "_" + loc.getBlockY() + "_" + loc.getBlockZ();
    }

    public boolean isPortalAt(Location loc) {
        ProtectedRegion region = getRegionAt(loc);

        if (region == null)
            return false;

        if (!region.getId().contains("portal_"))
            return false;

        if (!PortalData.portalExists(region.getId()))
            return false;

        return true;
    }

    public Portal getPortalAt(Location loc) {
        ProtectedRegion region = getRegionAt(loc);
        if ((region != null) && (region.getId().contains("portal_"))) {

            return PortalData.CACHED_PORTALS.get(region.getId());
        }

        return null;
    }

    // Returns first WorldGuard region found at given location
    public ProtectedRegion getRegionAt(Location loc) {
        RegionContainer container = WorldGuard.getInstance().getPlatform().getRegionContainer();
        RegionManager regionManager = container.get(BukkitAdapter.adapt(loc.getWorld()));
        if (regionManager == null)
            return null;
            
        ApplicableRegionSet applicable = regionManager.getApplicableRegions(BukkitAdapter.asBlockVector(loc));
        if ((applicable == null) || (applicable.size() < 1))
            return null;

        ProtectedRegion region = applicable.iterator().next();
        if (region == null)
            return null;

        return region;
    }

    public void removeRegion(String id, World world) {
        RegionContainer container = WorldGuard.getInstance().getPlatform().getRegionContainer();
        RegionManager regionManager = container.get(BukkitAdapter.adapt(world));
        if (regionManager == null)
            return;

        regionManager.removeRegion(id);
    }

    // Removes any portals that are outside of Land's claims
    public void recheckPortals(Land land) {
        Map<String, Portal> portals = PortalData.getByLand(land);

        if (portals.size() > 0) {
            for (Portal portal : portals.values()) {
                if (!landsAPI.isClaimed(portal.getPos1())) {
                    remove(portal);
                }
            }
        }
    }

    public Location getSafeTeleportLoc(Portal portal) {
        Location pos1 = portal.getPos1().clone();
        Location pos2 = portal.getPos2().clone();
        Block checkBlock;
        ArrayList<Location> portalBlocks;

        pos2.setY(pos1.getY());
        if (portal.getNorthSouth()){
            portalBlocks = BlockUtils.getBlocksBetween(pos1.add(1.0, 0.0, 0.0), pos2.add(-1.0, 0.0, 0.0));
        } else {
            portalBlocks = BlockUtils.getBlocksBetween(pos1.add(0.0, 0.0, 1.0), pos2.add(0.0, 0.0, -1.0));
        }

        for (Location loc : portalBlocks) {
            if (loc.getBlock().isEmpty() && loc.getBlock().getRelative(BlockFace.UP).isEmpty()) {
                checkBlock = loc.getBlock().getRelative(BlockFace.DOWN);
                if ((!checkBlock.isEmpty() && !checkBlock.isLiquid() && !checkBlock.isPassable()) || checkBlock.getType() == Material.SNOW) {
                    loc.add(0.5, 0, 0.5);
                    return loc;
                } else if (checkBlock.isEmpty()) {
                    checkBlock = checkBlock.getRelative(BlockFace.DOWN);
                    if ((!checkBlock.isEmpty() && !checkBlock.isLiquid() && !checkBlock.isPassable()) || checkBlock.getType() == Material.SNOW) {
                        loc.add(0.5, 0, 0.5);
                        return loc;
                    }
                }

            }
        }

        return null;
    }
    
    public void removeLandAccess(Portal portal, Land land) {
        if (!PortalData.portalExists(portal))
            return;

        List<Integer> allowedLands = portal.getAllowedLands();
        
        allowedLands.remove((Object)land.getId());
        portal.setAllowedlands(allowedLands);
        dataManager.save(portal);
    }

    public void removeLandAccess(Portal portal, int landId) {
        if (!PortalData.portalExists(portal))
            return;

        List<Integer> allowedLands = portal.getAllowedLands();
        
        allowedLands.remove((Object)landId);
        portal.setAllowedlands(allowedLands);
        dataManager.save(portal);
    }

    public void removePlayerAccess(Portal portal, UUID uuid) {
        if (!PortalData.portalExists(portal))
            return;

        List<UUID> allowedPlayers = portal.getAllowedPlayers();
        
        allowedPlayers.remove(uuid);
        portal.setAllowedPlayers(allowedPlayers);
        dataManager.save(portal);
    }

    public void fakePortalBlocks(Player player, Portal portal, boolean enable) {
        if (!PortalData.portalExists(portal))
            return;
        
        ArrayList<Location> portalBlocks = BlockUtils.getBlocksBetween(portal.getPos1(), portal.getPos2());
        Orientable fakePortal = (Orientable) Material.NETHER_PORTAL.createBlockData();

        if (portal.getNorthSouth())
            fakePortal.setAxis(Axis.Z);
        
        if (enable) {
            for (Location loc : portalBlocks) {
                player.sendBlockChange(loc, fakePortal);
            }
        } else {
            for (Location loc : portalBlocks) {
                player.sendBlockChange(loc, loc.getBlock().getBlockData());
            }
        }
    }
    
    public void teleportTo(Player player, Portal portal, boolean cooldown) {
        plugin.handleClose.remove(player);
        player.closeInventory();

        if (!PortalData.portalExists(portal))
            return;

        Location loc = portal.getTpLoc().clone();

        if (!loc.getBlock().isEmpty() || !loc.getBlock().getRelative(BlockFace.UP).isEmpty()) {
            loc = null;
        }

        Block checkBlock = portal.getTpLoc().getBlock().getRelative(BlockFace.DOWN);
        if (checkBlock.isEmpty() || checkBlock.isLiquid() || checkBlock.isPassable()) {

            if (checkBlock.isEmpty()) {
                checkBlock = checkBlock.getRelative(BlockFace.DOWN);
                if (checkBlock.isEmpty() || checkBlock.isLiquid() || checkBlock.isPassable())
                    loc = null;
            } else
                loc = null;
        }

        if (loc == null) {
            loc = getSafeTeleportLoc(portal);
            
            if (loc == null) {
                player.sendMessage(Messages.get("chat.teleport-not-safe"));
                return;
            }
            
            portal.setTpLoc(loc);
            dataManager.save(portal);
        }
  
        loc.setPitch(player.getLocation().getPitch());
        loc.setYaw(player.getLocation().getYaw());

        if (cooldown) {
            if (player.hasPermission("sidesurvivalportals.tp.bypass")) {
                player.teleport(loc);
                return;
            }
        
            if (!tasks.containsKey(player.getUniqueId())) {
                // player.addPotionEffect(new PotionEffect(PotionEffectType.CONFUSION, 150, 0, true, false, false));
                player.playSound(player.getLocation(), Sound.BLOCK_PORTAL_TRAVEL, SoundCategory.BLOCKS, 1.0f, 1.0f);
                if (isPortalAt(player.getLocation()))
                    fakePortalBlocks(player, getPortalAt(player.getLocation()), true);

                final String tpTitle = Messages.get("chat.teleport-title");

                tasks.put(player.getUniqueId(), new BukkitRunnable() {
                    int n = 0;
                    String dots;
                    
                    @Override
                    public void run() {
                        if (n >= 5) {
                            if (isPortalAt(player.getLocation()))
                                fakePortalBlocks(player, portal, false);
                            teleportTo(player, portal, false);
                            tasks.remove(player.getUniqueId());
                            this.cancel();
                            return;
                        }

                        dots = "";
                        for (int i = 0; i < n; i++) {
                            if (i > 0)
                                dots += " ●";
                            else
                                dots += "&d&l●";
                        }
                        dots += "&5&l";
        
                        for (int i = n; i < 5; i++) {
                            if (i > 0)
                                dots += " ●";
                            else
                                dots += "●";
                        }
                        player.sendTitle(tpTitle, ConvertUtils.color(dots), 0, 20, 5);

                        n++;
                    }
                }.runTaskTimer(plugin, 0, 15));
            }
            return;
        }

        player.teleport(loc);
    }

    public void teleportToSpawn(Player player, boolean isNether) {
        player.closeInventory();

        if (player.hasPermission("sidesurvivalportals.tp.bypass")) {
            if (isNether)
                player.teleport(PortalData.getNetherSpawnLocation());
            else
                player.teleport(PortalData.getSpawnLocation());
            return;
        }
    
        if (!tasks.containsKey(player.getUniqueId())) {
            // player.addPotionEffect(new PotionEffect(PotionEffectType.CONFUSION, 150, 0, true, false, false));
            player.playSound(player.getLocation(), Sound.BLOCK_PORTAL_TRAVEL, SoundCategory.BLOCKS, 1.0f, 1.0f);
            if (isPortalAt(player.getLocation()))
                fakePortalBlocks(player, getPortalAt(player.getLocation()), true);

            final String tpTitle = Messages.get("chat.teleport-title");

            tasks.put(player.getUniqueId(), new BukkitRunnable() {
                int n = 0;
                String dots;
                
                @Override
                public void run() {
                    if (n >= 5) {
                        if (isPortalAt(player.getLocation()))
                            fakePortalBlocks(player, getPortalAt(player.getLocation()), false);

                        if (isNether)
                            player.teleport(PortalData.getNetherSpawnLocation());
                        else
                            player.teleport(PortalData.getSpawnLocation());
                        tasks.remove(player.getUniqueId());
                        this.cancel();
                        return;
                    }

                    dots = "";
                    for (int i = 0; i < n; i++) {
                        if (i > 0)
                            dots += " ●";
                        else
                            dots += "&d&l●";
                    }
                    dots += "&5&l";
    
                    for (int i = n; i < 5; i++) {
                        if (i > 0)
                            dots += " ●";
                        else
                            dots += "●";
                    }
                    player.sendTitle(tpTitle, ConvertUtils.color(dots), 0, 20, 5);

                    n++;
                }
            }.runTaskTimer(plugin, 0, 15));
        }
    }
}
