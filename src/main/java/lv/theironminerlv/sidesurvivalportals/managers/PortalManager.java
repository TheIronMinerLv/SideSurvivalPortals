package lv.theironminerlv.sidesurvivalportals.managers;

import java.util.ArrayList;

import com.sk89q.worldedit.bukkit.BukkitAdapter;
import com.sk89q.worldguard.WorldGuard;
import com.sk89q.worldguard.protection.ApplicableRegionSet;
import com.sk89q.worldguard.protection.flags.Flags;
import com.sk89q.worldguard.protection.flags.StateFlag;
import com.sk89q.worldguard.protection.managers.RegionManager;
import com.sk89q.worldguard.protection.regions.ProtectedCuboidRegion;
import com.sk89q.worldguard.protection.regions.ProtectedRegion;
import com.sk89q.worldguard.protection.regions.RegionContainer;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.type.GlassPane;

import lv.theironminerlv.sidesurvivalportals.SideSurvivalPortals;
import lv.theironminerlv.sidesurvivalportals.data.PortalData;
import lv.theironminerlv.sidesurvivalportals.objects.Portal;
import lv.theironminerlv.sidesurvivalportals.utils.BlockUtils;
import lv.theironminerlv.sidesurvivalportals.utils.ConvertUtils;

public class PortalManager
{
    private SideSurvivalPortals plugin;
    private static PortalData portalData;

    public PortalManager(SideSurvivalPortals plugin) {
        this.plugin = plugin;
        portalData = this.plugin.getPortalData();
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
        regionManager.addRegion(region);

        portalData.addPortal(portal);

        setPortalGlass(pos1, pos2, isNorthSouth);

        return true;
    }

    // Fully removes portal from world and database
    public void remove(Portal portal) {
        if (portal == null)
            return;
    
        ArrayList<Location> portalBlocks = BlockUtils.getBlocksBetween(portal.getPos1(), portal.getPos2());

        for (Location blockLoc : portalBlocks) {
            blockLoc.getBlock().breakNaturally();
        }

        portalData.removePortal(portal);
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
        if ((region != null) && (region.getId().contains("portal_"))) {
            return true;
        }

        return false;
    }

    public Portal getPortalAt(Location loc) {
        ProtectedRegion region = getRegionAt(loc);
        if ((region != null) && (region.getId().contains("portal_"))) {

            return portalData.CACHED_PORTALS.get(region.getId());
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
}