package org.redcastlemedia.multitallented.civs.commands;

import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.redcastlemedia.multitallented.civs.Civs;
import org.redcastlemedia.multitallented.civs.LocaleManager;
import org.redcastlemedia.multitallented.civs.alliances.AllianceManager;
import org.redcastlemedia.multitallented.civs.civilians.Civilian;
import org.redcastlemedia.multitallented.civs.civilians.CivilianManager;
import org.redcastlemedia.multitallented.civs.items.ItemManager;
import org.redcastlemedia.multitallented.civs.regions.effects.HousingEffect;
import org.redcastlemedia.multitallented.civs.towns.Government;
import org.redcastlemedia.multitallented.civs.towns.GovernmentManager;
import org.redcastlemedia.multitallented.civs.towns.GovernmentType;
import org.redcastlemedia.multitallented.civs.towns.Town;
import org.redcastlemedia.multitallented.civs.towns.TownManager;
import org.redcastlemedia.multitallented.civs.towns.TownType;
import org.redcastlemedia.multitallented.civs.util.Util;

import net.md_5.bungee.api.ChatColor;
import net.md_5.bungee.api.chat.ClickEvent;
import net.md_5.bungee.api.chat.TextComponent;

public class InviteTownCommand implements CivCommand {

    public boolean runCommand(CommandSender commandSender, Command command, String s, String[] strings) {
        if (!(commandSender instanceof Player)) {
            commandSender.sendMessage(Civs.getPrefix() + "Unable to invite for non-players");
            return true;
        }
        Player player = (Player) commandSender;
        LocaleManager localeManager = LocaleManager.getInstance();

        Civilian civilian = CivilianManager.getInstance().getCivilian(player.getUniqueId());
        if (strings.length < 3) {
            player.sendMessage(Civs.getPrefix() + localeManager.getTranslation(civilian.getLocale(),
                    "specify-player-town"));
            return true;
        }

        //0 invite
        //1 player
        //2 townname
        String playerName = strings[1];
        String townName = strings[2];

        TownManager townManager = TownManager.getInstance();
        Town town = townManager.getTown(townName);
        if (town == null) {
            player.sendMessage(Civs.getPrefix() + localeManager.getTranslation(civilian.getLocale(),
                    "town-not-exist").replace("$1", townName));
            return true;
        }
        Government government = GovernmentManager.getInstance().getGovernment(town.getGovernmentType());
        boolean inviteAnyone = town.getRawPeople().containsKey(civilian.getUuid()) &&
                !town.getRawPeople().get(civilian.getUuid()).contains("foreign") &&
                (government.getGovernmentType() == GovernmentType.ANARCHY ||
                        government.getGovernmentType() == GovernmentType.LIBERTARIAN_SOCIALISM ||
                        government.getGovernmentType() == GovernmentType.LIBERTARIAN);
        if (Civs.perm != null && !Civs.perm.has(player, "civs.admin") &&
                !inviteAnyone) {
            if (!town.getPeople().containsKey(player.getUniqueId()) ||
                    (!town.getPeople().get(player.getUniqueId()).contains("owner") &&
                    !town.getPeople().get(player.getUniqueId()).contains("recruiter"))) {
                player.sendMessage(Civs.getPrefix() + localeManager.getTranslation(civilian.getLocale(),
                        "no-permission-invite").replace("$1", townName));
                return true;
            }
        }
        Player invitee = Bukkit.getPlayer(playerName);
        if (invitee == null) {
            player.sendMessage(Civs.getPrefix() + localeManager.getTranslation(civilian.getLocale(),
                    "player-not-online").replace("$1", playerName));
            return true;
        }
        if (town.getRawPeople().keySet().contains(invitee.getUniqueId()) &&
                !town.getRawPeople().get(invitee.getUniqueId()).contains("ally")) {
            player.sendMessage(Civs.getPrefix() + localeManager.getTranslation(civilian.getLocale(),
                    "already-member").replace("$1", player.getDisplayName())
                    .replace("$2", townName));
            return true;
        }
        TownType townType = (TownType) ItemManager.getInstance().getItemType(town.getType());
        boolean adminBypass = Civs.perm != null &&
                (Civs.perm.has(invitee, "civs.admin") ||
                Civs.perm.has(player, "civs.admin"));
        if (!townType.getEffects().containsKey(HousingEffect.HOUSING_EXCEPT) &&
                !adminBypass && town.getPopulation() >= town.getHousing()) {
            player.sendMessage(Civs.getPrefix() + localeManager.getTranslation(civilian.getLocale(),
                    "not-enough-housing"));
            return true;
        }

        for (Town otherTown : TownManager.getInstance().getTowns()) {
            if (otherTown.equals(town) ||
                    !otherTown.getRawPeople().containsKey(invitee.getUniqueId())) {
                continue;
            }
            Government otherGov = GovernmentManager.getInstance().getGovernment(otherTown.getGovernmentType());
            if ((government.getGovernmentType() == GovernmentType.TRIBALISM ||
                    otherGov.getGovernmentType() == GovernmentType.TRIBALISM) &&
                    !AllianceManager.getInstance().isAllied(town, otherTown)) {
                player.sendMessage(Civs.getPrefix() + LocaleManager.getInstance().getTranslation(
                        civilian.getLocale(),
                        "tribalism-no-invite").replace("$1", invitee.getDisplayName())
                        .replace("$2", otherTown.getName()));
                return true;
            }
        }

        Civilian inviteCiv = CivilianManager.getInstance().getCivilian(invitee.getUniqueId());

        player.sendMessage(Civs.getPrefix() + localeManager.getTranslation(civilian.getLocale(), "invite-sent"));
        String inviteMessage = Civs.getRawPrefix() + localeManager.getRawTranslation(inviteCiv.getLocale(),
                "invite-player").replace("$1", player.getDisplayName())
                .replace("$2", town.getType())
                .replace("$3", townName) + " ";
        TextComponent component = Util.parseColorsComponent(inviteMessage);

        TextComponent acceptComponent = new TextComponent("[✓]");
        acceptComponent.setColor(ChatColor.GREEN);
        acceptComponent.setUnderlined(true);
        acceptComponent.setClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/cv accept"));
        component.addExtra(acceptComponent);

        invitee.spigot().sendMessage(component);
        townManager.addInvite(invitee.getUniqueId(), town);
        return true;
    }
}
