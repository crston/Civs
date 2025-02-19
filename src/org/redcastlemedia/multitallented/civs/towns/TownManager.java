package org.redcastlemedia.multitallented.civs.towns;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.inventory.ItemStack;
import org.redcastlemedia.multitallented.civs.Civs;
import org.redcastlemedia.multitallented.civs.ConfigManager;
import org.redcastlemedia.multitallented.civs.LocaleManager;
import org.redcastlemedia.multitallented.civs.alliances.Alliance;
import org.redcastlemedia.multitallented.civs.civilians.Civilian;
import org.redcastlemedia.multitallented.civs.civilians.CivilianManager;
import org.redcastlemedia.multitallented.civs.events.TownCreatedEvent;
import org.redcastlemedia.multitallented.civs.events.TownDestroyedEvent;
import org.redcastlemedia.multitallented.civs.events.TownDevolveEvent;
import org.redcastlemedia.multitallented.civs.events.TownEvolveEvent;
import org.redcastlemedia.multitallented.civs.items.CivItem;
import org.redcastlemedia.multitallented.civs.items.ItemManager;
import org.redcastlemedia.multitallented.civs.menus.RegionListMenu;
import org.redcastlemedia.multitallented.civs.menus.SelectGovTypeMenu;
import org.redcastlemedia.multitallented.civs.regions.Region;
import org.redcastlemedia.multitallented.civs.regions.RegionManager;
import org.redcastlemedia.multitallented.civs.regions.RegionType;
import org.redcastlemedia.multitallented.civs.regions.effects.HousingEffect;
import org.redcastlemedia.multitallented.civs.util.DebugLogger;
import org.redcastlemedia.multitallented.civs.util.Util;

import java.io.File;
import java.util.*;

public class TownManager {

    private static TownManager townManager = null;
    private HashMap<String, Town> towns = new HashMap<>();
    private List<Town> sortedTowns = new ArrayList<>();
    private HashMap<UUID, Town> invites = new HashMap<>();
    private ArrayList<Town> needsSaving = new ArrayList<>();

    public TownManager() {
        townManager = this;
    }

    public void reload() {
        towns.clear();
        sortedTowns.clear();
        invites.clear();
        loadAllTowns();
    }

    public void loadAllTowns() {
        File townFolder = new File(Civs.getInstance().getDataFolder(), "towns");
        if (!townFolder.exists()) {
            townFolder.mkdir();
        }
        try {
            for (File file : townFolder.listFiles()) {
                FileConfiguration config = new YamlConfiguration();
                try {
                    config.load(file);

                    loadTown(config);
                } catch (Exception e) {
                    Civs.logger.warning("Unable to read from towns/" + file.getName());
                    e.printStackTrace();
                }
            }
        } catch (NullPointerException npe) {
            Civs.logger.severe("Unable to read from town folder!");
        }
    }

    public List<Town> getTowns() { return sortedTowns; }
    public Town getTown(String name) {
        return towns.get(name);
    }

    public Town getTownAt(Location location) {
        ItemManager itemManager = ItemManager.getInstance();
        for (Town town : sortedTowns) {
            TownType townType = (TownType) itemManager.getItemType(town.getType());
            int radius = townType.getBuildRadius();
            int radiusY = townType.getBuildRadiusY();
            Location townLocation = town.getLocation();

            if (townLocation.getWorld() == null) {
                continue;
            }
            if (!townLocation.getWorld().equals(location.getWorld())) {
                continue;
            }

            if (townLocation.getX() - radius >= location.getX()) {
                break;
            }

            if (townLocation.getX() + radius >= location.getX() &&
                    townLocation.getZ() + radius >= location.getZ() &&
                    townLocation.getZ() - radius <= location.getZ() &&
                    townLocation.getY() - radiusY <= location.getY() &&
                    townLocation.getY() + radiusY >= location.getY()) {
                return town;
            }

        }
        return null;
    }

    public void checkCriticalRequirements(Region region) {
        Town town = getTownAt(region.getLocation());
        if (town == null) {
            return;
        }
        RegionType regionType = (RegionType) ItemManager.getInstance().getItemType(region.getType());
        RegionManager regionManager = RegionManager.getInstance();
        TownType townType = (TownType) ItemManager.getInstance().getItemType(town.getType());
        if (!townType.getCriticalReqs().contains(region.getType().toLowerCase())) {
            boolean containsReq = false;
            for (String currentReq : regionType.getGroups()) {
                if (townType.getCriticalReqs().contains(currentReq)) {
                    containsReq = true;
                    break;
                }
            }
            if (!containsReq) {
                return;
            }
        }
        boolean hasReq = false;
        outer: for (Region containedRegion :
                regionManager.getContainingRegions(town.getLocation(), townType.getBuildRadius())) {
            if (region.equals(containedRegion)) {
                continue;
            }
            if (containedRegion.getType().equalsIgnoreCase(region.getType())) {
                hasReq = true;
                break;
            }
            RegionType containedType = (RegionType) ItemManager.getInstance().getItemType(containedRegion.getType());
            for (String currentReq : containedType.getGroups()) {
                if (regionType.getGroups().contains(currentReq)) {
                    hasReq = true;
                    break outer;
                }
            }
        }
        if (!hasReq) {
            removeTown(town, true);
        }
    }

    public List<Town> checkIntersect(Location location, TownType townType) {
        Location[] locationCheck = new Location[9];
        locationCheck[0] = location;
        List<Town> towns = new ArrayList<>();
        locationCheck[1] = new Location(location.getWorld(),
                location.getX() + townType.getBuildRadius(),
                Math.min(location.getY() + townType.getBuildRadiusY(), location.getWorld().getMaxHeight()),
                location.getZ() + townType.getBuildRadius());
        locationCheck[2] = new Location(location.getWorld(),
                location.getX() - townType.getBuildRadius(),
                Math.min(location.getY() + townType.getBuildRadiusY(), location.getWorld().getMaxHeight()),
                location.getZ() + townType.getBuildRadius());
        locationCheck[3] = new Location(location.getWorld(),
                location.getX() + townType.getBuildRadius(),
                Math.min(location.getY() + townType.getBuildRadiusY(), location.getWorld().getMaxHeight()),
                location.getZ() - townType.getBuildRadius());
        locationCheck[4] = new Location(location.getWorld(),
                location.getX() - townType.getBuildRadius(),
                Math.min(location.getY() + townType.getBuildRadiusY(), location.getWorld().getMaxHeight()),
                location.getZ() - townType.getBuildRadius());
        locationCheck[5] = new Location(location.getWorld(),
                location.getX() + townType.getBuildRadius(),
                Math.max(location.getY() - townType.getBuildRadiusY(), 0),
                location.getZ() + townType.getBuildRadius());
        locationCheck[6] = new Location(location.getWorld(),
                location.getX() - townType.getBuildRadius(),
                Math.max(location.getY() - townType.getBuildRadiusY(), 0),
                location.getZ() + townType.getBuildRadius());
        locationCheck[7] = new Location(location.getWorld(),
                location.getX() + townType.getBuildRadius(),
                Math.max(location.getY() - townType.getBuildRadiusY(), 0),
                location.getZ() - townType.getBuildRadius());
        locationCheck[8] = new Location(location.getWorld(),
                location.getX() - townType.getBuildRadius(),
                Math.max(location.getY() - townType.getBuildRadiusY(), 0),
                location.getZ() - townType.getBuildRadius());
        for (Location currentLocation : locationCheck) {
            Town town = getTownAt(currentLocation);
            if (town != null && !towns.contains(town)) {
                towns.add(town);
            }
        }
        return towns;
    }

    private void loadTown(FileConfiguration config) {

        HashMap<UUID, String> people = new HashMap<>();
        for (String key : config.getConfigurationSection("people").getKeys(false)) {
            people.put(UUID.fromString(key), config.getString("people." + key));
        }
        int maxPower = config.getInt("max-power", 500);
        int power = config.getInt("power", maxPower);
        int housing = config.getInt("housing", 0);
        int villagers = config.getInt("villagers", 0);
        long lastDisable = config.getLong("last-disable", -1);
        String governmentType = config.getString("gov-type", GovernmentType.DICTATORSHIP.name());
        Town town = new Town(config.getString("name", "NameNotFound"),
                config.getString("type"),
                Region.idToLocation(config.getString("location")),
                people,
                power,
                maxPower,
                housing,
                villagers,
                lastDisable);
        town.setGovernmentType(governmentType);
        if (config.isSet("gov-type-changed-today")) {
            town.setGovTypeChangedToday(true);
        }
        if (config.isSet("last-active")) {
            town.setLastActive(config.getLong("last-active", -1));
        } else {
            town.setLastActive(-1);
        }
        if (config.isSet("revolt")) {
            loadRevolt(town, config.getStringList("revolt"));
        }
        if (config.isSet("last-vote")) {
            town.setLastVote(config.getLong("last-vote", 0));
        }
        if (config.isSet("votes")) {
            HashMap<UUID, HashMap<UUID, Integer>> votes = new HashMap<>();
            for (String cUuidString : config.getConfigurationSection("votes").getKeys(false)) {
                UUID cUuid = UUID.fromString(cUuidString);
                HashMap<UUID, Integer> theseVotes = new HashMap<>();
                for (String uuidString : config.getConfigurationSection("votes." + cUuidString).getKeys(false)) {
                    UUID uuid = UUID.fromString(uuidString);
                    theseVotes.put(uuid, config.getInt("votes." + cUuidString + "." + uuidString, 0));
                }
                votes.put(cUuid, theseVotes);
            }
            town.setVotes(votes);
        }
        if (config.isSet("bounties")) {
            town.setBounties(Util.readBountyList(config));
        }
        town.setBankAccount(config.getDouble("bank", 0));
        town.setTaxes(config.getDouble("taxes", 0));
        if (config.isSet("colonial-town")) {
            town.setColonialTown(config.getString("colonial-town"));
        }
        if (config.isSet("child-locations")) {
            List<Location> locationList = new ArrayList<>();
            for (String locationString : config.getStringList("child-locations")) {
                locationList.add(Region.idToLocation(locationString));
            }
            town.setChildLocations(locationList);
        }
        addTown(town);
    }

    private void loadRevolt(Town town, List<String> revoltList) {
        for (String uuidString : revoltList) {
            town.getRevolt().add(UUID.fromString(uuidString));
        }
    }

    public Set<Town> getOwnedTowns(Civilian civilian) {
        HashSet<Town> townSet = new HashSet();
        for (Town town : towns.values()) {
            if (!town.getRawPeople().containsKey(civilian.getUuid()) ||
                    !town.getRawPeople().get(civilian.getUuid()).contains("owner")) {
                continue;
            }
            townSet.add(town);
        }
        return townSet;
    }

    public void addTown(Town town) {
        towns.put(town.getName(), town);
        sortedTowns.add(town);
        if (sortedTowns.size() > 1) {
            Collections.sort(sortedTowns, new Comparator<Town>() {

                @Override
                public int compare(Town o1, Town o2) {
                    ItemManager itemManager = ItemManager.getInstance();
                    TownType townType1 = (TownType) itemManager.getItemType(o1.getType());
                    TownType townType2 = (TownType) itemManager.getItemType(o2.getType());
                    if (o1.getLocation().getX() - townType1.getBuildRadius() >
                            o2.getLocation().getX() - townType2.getBuildRadius()) {
                        return 1;
                    } else if (o1.getLocation().getX() - townType1.getBuildRadius() <
                            o2.getLocation().getX() - townType2.getBuildRadius()) {
                        return -1;
                    }
                    return 0;
                }
            });
        }
    }
    public void removeTown(Town town, boolean broadcast) {
        removeTown(town, broadcast, true);
    }
    public void removeTown(Town town, boolean broadcast, boolean destroyRing) {
        if (broadcast) {
            for (Player player : Bukkit.getOnlinePlayers()) {
                Civilian civ = CivilianManager.getInstance().getCivilian(player.getUniqueId());
                player.sendMessage(Civs.getPrefix() + LocaleManager.getInstance().getTranslation(civ.getLocale(),
                        "town-destroyed").replace("$1", town.getName()));
            }
        } else {
            TownType townType = (TownType) ItemManager.getInstance().getItemType(town.getType());
            TownDestroyedEvent townDestroyedEvent = new TownDestroyedEvent(town, townType);
            Bukkit.getPluginManager().callEvent(townDestroyedEvent);
        }
        towns.remove(town.getName());
        sortedTowns.remove(town);
        if (Civs.getInstance() == null) {
            return;
        }
        if (destroyRing && ConfigManager.getInstance().getTownRings()) {
            town.destroyRing(true, broadcast);
        }
        removeTownFile(town.getName());
    }

    public void setTownPower(Town town, int power) {
        if (power > town.getMaxPower()) {
            town.setPower(town.getMaxPower());
        } else {
            town.setPower(power);
        }
        TownType townType = (TownType) ItemManager.getInstance().getItemType(town.getType());
        if (town.getPower() < 1 && ConfigManager.getInstance().getDestroyTownsAtZero() &&
                townType.getChild() == null) {
            TownManager.getInstance().removeTown(town, true);
        } else {
            if (town.getPower() < 1) {
                if (townType.getChild() != null) {
                    devolveTown(town, townType);
                } else {
                    hasGrace(town, true);
                }
            } else {
                TownManager.getInstance().saveTown(town);
            }
        }
    }

    private void devolveTown(Town town, TownType townType) {
        if (townType.getChild() == null) {
            return;
        }
        TownDevolveEvent townDevolveEvent = new TownDevolveEvent(town, townType);
        Bukkit.getPluginManager().callEvent(townDevolveEvent);
        town.destroyRing(false, true);
        TownType childTownType = (TownType) ItemManager.getInstance().getItemType(townType.getChild());
        town.setType(childTownType.getProcessedName());
        town.setPower(childTownType.getPower());
        town.setMaxPower(childTownType.getMaxPower());
        TownManager.getInstance().saveTown(town);
        for (Player player : Bukkit.getOnlinePlayers()) {
            Civilian civilian = CivilianManager.getInstance().getCivilian(player.getUniqueId());
            player.sendMessage(ChatColor.RED + ChatColor.stripColor(Civs.getPrefix()) +
                    LocaleManager.getInstance().getTranslation(civilian.getLocale(), "devolve-town")
                    .replace("$1", town.getName())
                    .replace("$2", childTownType.getProcessedName()));
        }
    }

    private void removeTownFile(String townName) {
        File townFolder = new File(Civs.getInstance().getDataFolder(), "towns");
        if (!townFolder.exists()) {
            townFolder.mkdir();
        }
        File townFile = new File(townFolder, townName + ".yml");
        townFile.delete();
    }

    public boolean hasGrace(Town town, boolean disable) {
        long grace = getRemainingGracePeriod(town);
        if (grace < 0 && disable) {
            long lastDisable = (ConfigManager.getInstance().getTownGracePeriod() * 1000) + System.currentTimeMillis();
            town.setLastDisable(lastDisable);
            TownManager.getInstance().saveTown(town);
            return true;
        }
        if (!disable) {
            if (grace > -1) {
                town.setLastDisable(-1);
                TownManager.getInstance().saveTown(town);
            }
            return true;
        }
        return grace != 0;
    }

    public long getRemainingGracePeriod(Town town) {
        if (town == null) {
            return 0;
        }
        if (town.getLastDisable() < 1) {
            return -1;
        }
        return Math.max(0, town.getLastDisable() - System.currentTimeMillis());
    }

    public void addInvite(UUID uuid, Town town) {
        invites.put(uuid, town);
    }
    public void clearInvite(UUID uuid) {
        invites.remove(uuid);
    }
    public Town getInviteTown(UUID uuid) {
        return invites.get(uuid);
    }
    public boolean acceptInvite(UUID uuid) {
        if (!invites.containsKey(uuid)) {
            return false;
        }
        Town town = invites.get(uuid);
        town.setPeople(uuid, "member");
        saveTown(town);
        invites.remove(uuid);
        return true;
    }

    public Set<Region> getContainingRegions(String townName) {
        Town town = getTown(townName);
        if (town == null) {
            return new HashSet<>();
        }

        TownType townType = (TownType) ItemManager.getInstance().getItemType(town.getType());
        return RegionManager.getInstance().getRegionsXYZ(town.getLocation(), townType.getBuildRadius(),
                townType.getBuildRadiusY(), townType.getBuildRadius(), false);
    }

    public void saveTown(Town town) {
        needsSaving.add(town);
    }

    public void saveAllUnsavedTowns() {
        for (Town town : needsSaving) {
            saveTownNow(town);
        }
        needsSaving.clear();
    }

    public int getCountOfPendingSaves() {
        return needsSaving.size();
    }

    public void saveNextTown() {
        Town t = null;
        for (Town town : needsSaving) {
            t = town;
            saveTownNow(t);
            break;
        }
        if (t != null) {
            while (needsSaving.contains(t)) {
                needsSaving.remove(t);
            }
        }
    }

    private void saveTownNow(Town town) {
        if (Civs.getInstance() == null) {
            return;
        }
        if (ConfigManager.getInstance().isDebugLog()) {
            DebugLogger.saves++;
        }
        File townFolder = new File(Civs.getInstance().getDataFolder(), "towns");
        if (!townFolder.exists()) {
            townFolder.mkdir();
        }
        File townFile = new File(townFolder, town.getName() + ".yml");
        try {
            if (!townFile.exists()) {
                townFile.createNewFile();
            }
            FileConfiguration config = new YamlConfiguration();
            config.load(townFile);


            config.set("name", town.getName());
            config.set("type", town.getType());
            config.set("location", Region.locationToString(town.getLocation()));
            config.set("people", null);
            if (town.isGovTypeChangedToday()) {
                config.set("gov-type-changed-today", true);
            } else {
                config.set("gov-type-changed-today", null);
            }
            if (town.getLastActive() > -1) {
                config.set("last-active", town.getLastActive());
            } else {
                config.set("last-active", -1);
            }
            if (town.getRevolt().isEmpty()) {
                config.set("revolt", null);
            } else {
                saveRevolt(town, config);
            }
            for (UUID key : town.getRawPeople().keySet()) {
                if (town.getRawPeople().get(key).contains("ally")) {
                    continue;
                }
                config.set("people." + key, town.getPeople().get(key));
            }
            List<String> locationList = new ArrayList<>();
            for (Location lo : town.getChildLocations()) {
                locationList.add(Region.locationToString(lo));
            }
            config.set("child-locations", locationList);
            config.set("housing", town.getHousing());
            config.set("population", town.getPopulation());
            config.set("villagers", town.getVillagers());
            config.set("last-disable", town.getLastDisable());
            config.set("power", town.getPower());
            config.set("max-power", town.getMaxPower());
            config.set("gov-type", town.getGovernmentType());
            config.set("taxes", town.getTaxes());
            config.set("bank", town.getBankAccount());
            config.set("last-vote", town.getLastVote());
            config.set("votes", null);
            if (!town.getVotes().isEmpty()) {
                for (UUID uuid : town.getVotes().keySet()) {
                    for (UUID cUuid : town.getVotes().get(uuid).keySet()) {
                        config.set("votes." + uuid.toString() + "." + cUuid.toString(),
                                town.getVotes().get(uuid).get(cUuid));
                    }
                }
            }

            config.set("bounties", null);
            if (town.getBounties() != null && !town.getBounties().isEmpty()) {
                for (int i = 0; i < town.getBounties().size(); i++) {
                    if (town.getBounties().get(i).getIssuer() != null) {
                        config.set("bounties." + i + ".issuer", town.getBounties().get(i).getIssuer().toString());
                    }
                    config.set("bounties." + i + ".amount", town.getBounties().get(i).getAmount());
                }
            }
            if (town.getColonialTown() == null) {
                config.set("colonial-town", null);
            } else {
                config.set("colonial-town", town.getColonialTown());
            }

            //TODO save all town properties
            config.save(townFile);
        } catch (Exception e) {
            e.printStackTrace();
            Civs.logger.severe("Unable to save town " + town.getName() + ".yml");
        }
    }

    private void saveRevolt(Town town, FileConfiguration config) {
        ArrayList<String> uuidList = new ArrayList<>();
        for (UUID uuid : town.getRevolt()) {
            uuidList.add(uuid.toString());
        }
        config.set("revolt", uuidList);
    }

    public static TownManager getInstance() {
        if (townManager == null) {
            new TownManager();
        }
        return townManager;
    }

    public Set<Town> findCommonTowns(Civilian damagerCiv, Civilian dyingCiv) {
        HashSet<Town> commonTowns = new HashSet<>();
        for (Town town : sortedTowns) {
            if (town.getPeople().containsKey(damagerCiv.getUuid()) &&
                    town.getPeople().containsKey(dyingCiv.getUuid())) {
                commonTowns.add(town);
            }
        }
        return commonTowns;
    }

    public Town isOwnerOfATown(Civilian civilian) {
        for (Town town : sortedTowns) {
            if (!town.getRawPeople().containsKey(civilian.getUuid()) ||
                    !town.getRawPeople().get(civilian.getUuid()).contains("owner")) {
                continue;
            }
            return town;
        }
        return null;
    }

    public boolean townNameExists(String name) {
        for (String townName : towns.keySet()) {
            if (name.toLowerCase().equalsIgnoreCase(townName)) {
                return true;
            }
        }
        return false;
    }

    public void placeTown(Player player, String name, Town town) {
        LocaleManager localeManager = LocaleManager.getInstance();
        Civilian civilian = CivilianManager.getInstance().getCivilian(player.getUniqueId());

        if (TownManager.getInstance().townNameExists(name) && (town == null ||
                !town.getName().equalsIgnoreCase(name) ||
                !town.getRawPeople().containsKey(civilian.getUuid()))) {
            player.sendMessage(Civs.getPrefix() + localeManager.getTranslation(civilian.getLocale(),
                    "specify-town-name"));
            return;
        }

        ItemStack itemStack = player.getInventory().getItemInMainHand();
            if (itemStack == null || !CivItem.isCivsItem(itemStack)) {
            player.sendMessage(Civs.getPrefix() + localeManager.getTranslation(civilian.getLocale(),
                    "hold-town"));
            return;
        }
        CivItem civItem = CivItem.getFromItemStack(itemStack);

        if (civItem == null || !(civItem instanceof TownType)) {
            player.sendMessage(Civs.getPrefix() + localeManager.getTranslation(civilian.getLocale(),
                    "hold-town"));
            return;
        }
        TownType townType = (TownType) civItem;

        TownManager townManager = TownManager.getInstance();
        List<Town> intersectTowns = townManager.checkIntersect(player.getLocation(), townType);
        if (intersectTowns.size() > 1 ||
                    (townType.getChild() != null &&
                    !intersectTowns.isEmpty() &&
                    !townType.getChild().equals(intersectTowns.get(0).getType()))) {
            player.sendMessage(Civs.getPrefix() + localeManager.getTranslation(civilian.getLocale(),
                    "too-close-town").replace("$1", townType.getProcessedName()));
            return;
        }
        if (intersectTowns.isEmpty() && townType.getChild() != null) {
            player.sendMessage(Civs.getPrefix() + localeManager.getTranslation(civilian.getLocale(),
                    "must-be-built-on-top").replace("$1", townType.getProcessedName())
                    .replace("$2", townType.getChild()));
            return;
        }

        if (!townType.getReqs().isEmpty()) {
            HashMap<String, Integer> checkList = (HashMap<String, Integer>) townType.getReqs().clone();
            Set<Region> regions = RegionManager.getInstance().getRegionsXYZ(player.getLocation(), townType.getBuildRadius(),
                    townType.getBuildRadiusY(), townType.getBuildRadius(), false);
            regionCheck: for (Region region : regions) {
                RegionType regionType = (RegionType) ItemManager.getInstance().getItemType(region.getType());
                String regionTypeName = regionType.getProcessedName();
                if (checkList.containsKey(regionTypeName)) {
                    if (checkList.get(regionTypeName) < 2) {
                        checkList.remove(regionTypeName);
                    } else {
                        checkList.put(regionTypeName, checkList.get(regionTypeName) - 1);
                    }
                    continue;
                }
                for (String groupType : regionType.getGroups()) {
                    String groupName = groupType.toLowerCase();
                    if (checkList.containsKey(groupName)) {
                        if (checkList.get(groupName) < 2) {
                            checkList.remove(groupName);
                        } else {
                            checkList.put(groupName, checkList.get(groupName) - 1);
                        }
                        continue regionCheck;
                    }
                }
            }
            if (!checkList.isEmpty()) {
                player.sendMessage(Civs.getPrefix() + localeManager.getTranslation(civilian.getLocale(),
                        "missing-region-requirements").replace("$1", townType.getDisplayName()));
                player.openInventory(RegionListMenu.createMenu(civilian, checkList, 0));
                return;
            }
        }



        HashMap<UUID, String> people = new HashMap<>();
        people.put(player.getUniqueId(), "owner");
        Location newTownLocation = player.getLocation();
        List<Location> childLocations = new ArrayList<>();
        TownType childTownType = null;
        String governmentType = null;
        int villagerCount = 0;
        if (townType.getChild() != null) {
            Town intersectTown = intersectTowns.get(0);
            if (intersectTown.getPopulation() < townType.getChildPopulation()) {
                player.sendMessage(Civs.getPrefix() + localeManager.getTranslation(civilian.getLocale(), "population-req")
                        .replace("$1", intersectTown.getType())
                        .replace("$2", "" + townType.getChildPopulation()));
                return;
            }
            people = intersectTown.getPeople();
            newTownLocation = intersectTown.getLocation();
            childLocations.add(newTownLocation);
            name = intersectTown.getName();
            governmentType = intersectTown.getGovernmentType();
            childTownType = (TownType) ItemManager.getInstance().getItemType(intersectTown.getType());
            TownManager.getInstance().removeTown(intersectTown, false, false);
            // Don't destroy the ring on upgrade
    //            if (ConfigManager.getInstance().getTownRings()) {
    //                intersectTown.destroyRing(false);
    //            }
            villagerCount = intersectTown.getVillagers();
        }

        int housingCount = getHousingCount(newTownLocation, townType);
        Town newTown = new Town(name,
                townType.getProcessedName(),
                newTownLocation,
                people,
                townType.getPower(),
                townType.getMaxPower(), housingCount, villagerCount, -1);
            newTown.setChildLocations(childLocations);
        if (governmentType != null) {
            newTown.setGovernmentType(governmentType);
        } else {
            newTown.setGovernmentType(ConfigManager.getInstance().getDefaultGovernmentType());
        }
        Government government = GovernmentManager.getInstance().getGovernment(newTown.getGovernmentType());
        if (government != null) {
            for (GovTypeBuff buff : government.getBuffs()) {
                if (buff.getBuffType() != GovTypeBuff.BuffType.MAX_POWER) {
                    continue;
                }
                newTown.setMaxPower((int) Math.round((double) newTown.getMaxPower() * (1 + (double) buff.getAmount() / 100)));
                break;
            }
        } else {
            government = GovernmentManager.getInstance().getGovernment(ConfigManager.getInstance().getDefaultGovernmentType());
        }
        townManager.saveTown(newTown);
        townManager.addTown(newTown);
        player.getInventory().remove(itemStack);

        if (childTownType != null) {
            TownEvolveEvent townEvolveEvent = new TownEvolveEvent(newTown, childTownType, townType);
            Bukkit.getPluginManager().callEvent(townEvolveEvent);

            if (government.getGovernmentType() == GovernmentType.COOPERATIVE && Civs.econ != null &&
                    newTown.getBankAccount() > 0) {
                double price = townType.getPrice();
                price = Math.min(price, newTown.getBankAccount());
                Civs.econ.depositPlayer(player, price);
                newTown.setBankAccount(newTown.getBankAccount() - price);
                String priceString = Util.getNumberFormat(price, civilian.getLocale());
                player.sendMessage(Civs.getPrefix() + LocaleManager.getInstance().getTranslation(civilian.getLocale(),
                        "town-assist-price").replace("$1", priceString)
                        .replace("$2", townType.getDisplayName()));
            }

        } else {
            TownCreatedEvent townCreatedEvent = new TownCreatedEvent(newTown, townType);
            newTown.setLastVote(System.currentTimeMillis());
            Bukkit.getPluginManager().callEvent(townCreatedEvent);
        }

        player.sendMessage(Civs.getPrefix() + localeManager.getTranslation(civilian.getLocale(),
                "town-created").replace("$1", newTown.getName()));
        if (ConfigManager.getInstance().getTownRings()) {
            newTown.createRing();
        }
        if (childTownType == null && GovernmentManager.getInstance().getGovermentTypes().size() > 1) {
            player.openInventory(SelectGovTypeMenu.createMenu(civilian, newTown));
        }
        return;
    }

    int getHousingCount(Location newTownLocation, TownType townType) {
        int housingCount = 0;
        for (Region region : getRegionsInTown(newTownLocation, townType.getBuildRadius(), townType.getBuildRadiusY())) {
            RegionType regionType = (RegionType) ItemManager.getInstance().getItemType(region.getType());
            if (regionType.getEffects().containsKey(HousingEffect.KEY)) {
                housingCount += Integer.parseInt(regionType.getEffects().get(HousingEffect.KEY));
            }
        }
        return housingCount;
    }

    public Set<Region> getRegionsInTown(Town town) {
        TownType townType = (TownType) ItemManager.getInstance().getItemType(town.getType());
        return getRegionsInTown(town.getLocation(), townType.getBuildRadius(), townType.getBuildRadiusY());
    }

    private Set<Region> getRegionsInTown(Location location, int radius, int radiusY) {
        //TODO fix this to account for vertical radius being different
        return RegionManager.getInstance().getContainingRegions(location, radius);
    }
}
