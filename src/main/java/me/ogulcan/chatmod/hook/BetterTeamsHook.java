package me.ogulcan.chatmod.hook;

import com.booksaw.betterTeams.Team;
import com.booksaw.betterTeams.TeamPlayer;
import org.bukkit.Bukkit;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

/**
 * Utility methods for interacting with the Better Teams plugin.
 */
public class BetterTeamsHook {
    private static boolean checked;
    private static boolean enabled;
    private static String teamOrAllyToGlobal;
    private static String globalToTeam;
    private static String globalToAlly;

    private static void init() {
        if (checked) return;
        checked = true;
        Plugin bt = Bukkit.getPluginManager().getPlugin("BetterTeams");
        if (bt != null && bt.isEnabled()) {
            enabled = true;
            FileConfiguration cfg = bt.getConfig();
            teamOrAllyToGlobal = cfg.getString("chatPrefixes.teamOrAllyToGlobal", "!");
            globalToTeam = cfg.getString("chatPrefixes.globalToTeam", "!");
            globalToAlly = cfg.getString("chatPrefixes.globalToAlly", "?");
        }
    }

    /**
     * Determine if the given message is handled as team or ally chat by Better Teams.
     *
     * @param player the sender
     * @param message the chat message
     * @return true if the message is for team/ally chat, false otherwise
     */
    public static boolean isTeamChat(Player player, String message) {
        init();
        if (!enabled) return false;
        Team team = Team.getTeam(player);
        if (team == null) return false;
        TeamPlayer tp = team.getTeamPlayer(player);
        if (tp == null) return false;

        if (tp.isInTeamChat() || tp.isInAllyChat()) {
            if (teamOrAllyToGlobal != null && !teamOrAllyToGlobal.isEmpty()
                    && message.startsWith(teamOrAllyToGlobal)
                    && message.length() > teamOrAllyToGlobal.length()) {
                return false;
            }
            return true;
        }

        if (globalToTeam != null && !globalToTeam.isEmpty()
                && message.startsWith(globalToTeam)
                && message.length() > globalToTeam.length()) {
            return true;
        }
        if (globalToAlly != null && !globalToAlly.isEmpty()
                && message.startsWith(globalToAlly)
                && message.length() > globalToAlly.length()) {
            return true;
        }
        return false;
    }
}
