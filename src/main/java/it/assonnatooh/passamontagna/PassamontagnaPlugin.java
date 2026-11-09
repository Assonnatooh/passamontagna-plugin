package it.assonnatooh.passamontagna;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.scoreboard.Team;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

public final class PassamontagnaPlugin extends JavaPlugin implements Listener, CommandExecutor {
    private static final int CUSTOM_MODEL_DATA = 64;
    private static final String STAFF_PERMISSION = "passamontagna.staff";

    private final Map<PlayerPair, TeamState> hiddenNameTags = new HashMap<>();

    @Override
    public void onEnable() {
        Objects.requireNonNull(getCommand("passamontagna"), "Command passamontagna missing in plugin.yml")
                .setExecutor(this);
        getServer().getPluginManager().registerEvents(this, this);
        getServer().getScheduler().runTaskTimer(this, this::refreshNameTags, 1L, 10L);
        getLogger().info("Passamontagna attivato.");
    }

    @Override
    public void onDisable() {
        for (Map.Entry<PlayerPair, TeamState> entry : Map.copyOf(hiddenNameTags).entrySet()) {
            restoreNameTag(entry.getKey(), entry.getValue());
        }
        hiddenNameTags.clear();
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(ChatColor.RED + "Solo un giocatore può usare questo comando.");
            return true;
        }
        if (args.length != 1 || !args[0].equalsIgnoreCase("get")) {
            player.sendMessage(ChatColor.YELLOW + "Usa: /passamontagna get");
            return true;
        }

        ItemStack item = createPassamontagna();
        PlayerInventory inventory = player.getInventory();
        if (inventory.firstEmpty() == -1) {
            player.getWorld().dropItemNaturally(player.getLocation(), item);
            player.sendMessage(ChatColor.GREEN + "Inventario pieno: il passamontagna è stato lasciato a terra.");
        } else {
            inventory.addItem(item);
            player.sendMessage(ChatColor.GREEN + "Hai ricevuto il passamontagna.");
        }
        return true;
    }

    private ItemStack createPassamontagna() {
        ItemStack item = new ItemStack(Material.STICK);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(ChatColor.DARK_GRAY + "Passamontagna");
            meta.setLore(java.util.List.of(
                    ChatColor.GRAY + "Indossalo nello slot della testa per restare anonimo.",
                    ChatColor.DARK_GRAY + "Custom Model Data: 64"
            ));
            meta.setCustomModelData(CUSTOM_MODEL_DATA);
            item.setItemMeta(meta);
        }
        return item;
    }

    private void refreshNameTags() {
        for (Player viewer : Bukkit.getOnlinePlayers()) {
            for (Player target : Bukkit.getOnlinePlayers()) {
                if (viewer.equals(target)) {
                    continue;
                }
                updateNameTag(viewer, target);
            }
        }
    }

    private void updateNameTag(Player viewer, Player target) {
        PlayerPair pair = new PlayerPair(viewer.getUniqueId(), target.getUniqueId());
        boolean shouldHide = wearsPassamontagna(target)
                && !viewer.isOp()
                && !viewer.hasPermission(STAFF_PERMISSION);
        Scoreboard scoreboard = viewer.getScoreboard();
        TeamState previous = hiddenNameTags.get(pair);

        if (!shouldHide) {
            if (previous != null) {
                restoreNameTag(pair, previous);
                hiddenNameTags.remove(pair);
            }
            return;
        }

        if (previous != null && previous.scoreboard() != scoreboard) {
            restoreNameTag(pair, previous);
            hiddenNameTags.remove(pair);
            previous = null;
        }

        String teamName = teamNameFor(target.getUniqueId());
        Team hiddenTeam = scoreboard.getTeam(teamName);
        if (hiddenTeam == null) {
            hiddenTeam = scoreboard.registerNewTeam(teamName);
            hiddenTeam.setOption(Team.Option.NAME_TAG_VISIBILITY, Team.OptionStatus.NEVER);
        }

        if (previous == null) {
            Team oldTeam = scoreboard.getEntryTeam(target.getName());
            String oldTeamName = oldTeam == null || oldTeam.getName().equals(teamName)
                    ? null
                    : oldTeam.getName();
            if (oldTeam != null && !oldTeam.getName().equals(teamName)) {
                oldTeam.removeEntry(target.getName());
            }
            hiddenNameTags.put(pair, new TeamState(scoreboard, teamName, oldTeamName));
        }
        if (!hiddenTeam.hasEntry(target.getName())) {
            hiddenTeam.addEntry(target.getName());
        }
    }

    private void restoreNameTag(PlayerPair pair, TeamState state) {
        String entryName = Bukkit.getOfflinePlayer(pair.targetId()).getName();
        if (entryName == null) {
            Player onlineTarget = Bukkit.getPlayer(pair.targetId());
            if (onlineTarget != null) {
                entryName = onlineTarget.getName();
            }
        }
        if (entryName == null) {
            return;
        }

        Team hiddenTeam = state.scoreboard().getTeam(state.hiddenTeamName());
        if (hiddenTeam != null) {
            hiddenTeam.removeEntry(entryName);
            if (hiddenTeam.getEntries().isEmpty()) {
                hiddenTeam.unregister();
            }
        }
        if (state.previousTeamName() != null) {
            Team previousTeam = state.scoreboard().getTeam(state.previousTeamName());
            if (previousTeam != null && state.scoreboard().getEntryTeam(entryName) == null) {
                previousTeam.addEntry(entryName);
            }
        }
    }

    private boolean wearsPassamontagna(Player player) {
        ItemStack helmet = player.getInventory().getHelmet();
        if (helmet == null || helmet.getType() != Material.STICK || !helmet.hasItemMeta()) {
            return false;
        }
        ItemMeta meta = helmet.getItemMeta();
        return meta != null && meta.hasCustomModelData() && meta.getCustomModelData() == CUSTOM_MODEL_DATA;
    }

    private String teamNameFor(UUID uuid) {
        return "pm" + uuid.toString().replace("-", "").substring(0, 14);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onChat(AsyncPlayerChatEvent event) {
        Player sender = event.getPlayer();
        if (!wearsPassamontagna(sender)) {
            return;
        }

        event.setCancelled(true);
        String format = event.getFormat();
        String message = event.getMessage();
        for (Player recipient : event.getRecipients()) {
            String visibleName = recipient.isOp() || recipient.hasPermission(STAFF_PERMISSION)
                    ? sender.getName()
                    : "Anonimo";
            try {
                recipient.sendMessage(String.format(Locale.ROOT, format, visibleName, message));
            } catch (RuntimeException exception) {
                recipient.sendMessage(visibleName + ": " + message);
            }
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        UUID leavingPlayer = event.getPlayer().getUniqueId();
        for (Map.Entry<PlayerPair, TeamState> entry : Map.copyOf(hiddenNameTags).entrySet()) {
            PlayerPair pair = entry.getKey();
            if (pair.viewerId().equals(leavingPlayer) || pair.targetId().equals(leavingPlayer)) {
                restoreNameTag(pair, entry.getValue());
                hiddenNameTags.remove(pair);
            }
        }
    }

    private record PlayerPair(UUID viewerId, UUID targetId) {
    }

    private record TeamState(Scoreboard scoreboard, String hiddenTeamName, String previousTeamName) {
    }
}
