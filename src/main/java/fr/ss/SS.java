package fr.ss;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.*;

public class SS extends JavaPlugin implements Listener {

    // ---------- Modèles ----------
    static class Reason {
        final String name; final String duration; final Material icon;
        Reason(String name, String duration, Material icon) {
            this.name = name; this.duration = duration; this.icon = icon;
        }
    }

    static class Type {
        final String key, display, command; final boolean timed; final Material icon;
        final List<Reason> reasons = new ArrayList<Reason>();
        Type(String key, String display, String command, boolean timed, Material icon) {
            this.key = key; this.display = display; this.command = command;
            this.timed = timed; this.icon = icon;
        }
    }

    static class Pending {
        final String target; final Type type; final Reason reason;
        Pending(String target, Type type, Reason reason) {
            this.target = target; this.type = type; this.reason = reason;
        }
    }

    private static final String PREFIX = ChatColor.RED + "" + ChatColor.BOLD + "SS " + ChatColor.GRAY + "» ";
    private static final String T_MAIN = ChatColor.DARK_GRAY + "Sanction » ";
    private static final String T_REASON = ChatColor.DARK_GRAY + "Raison » ";
    private static final String T_CONFIRM = ChatColor.DARK_GRAY + "Confirmer » ";

    private final Map<String, Type> types = new LinkedHashMap<String, Type>();
    private final Map<UUID, String> targets = new HashMap<UUID, String>();
    private final Map<UUID, Pending> pending = new HashMap<UUID, Pending>();

    @Override
    public void onEnable() {
        // ===== MUTE (MaxBans : /tempmute <joueur> <durée> <raison>) =====
        Type mute = new Type("mute", ChatColor.GOLD + "Mute", "tempmute %p %t %r", true, Material.BOOK_AND_QUILL);
        mute.reasons.add(new Reason("Spam", "30m", Material.PAPER));
        mute.reasons.add(new Reason("Insultes", "1h", Material.BOOK));
        mute.reasons.add(new Reason("Provocation", "2h", Material.BLAZE_POWDER));
        mute.reasons.add(new Reason("Publicite", "1d", Material.SIGN));
        mute.reasons.add(new Reason("Racisme / Homophobie", "7d", Material.SKULL_ITEM));

        // ===== WARN (MaxBans : /warn <joueur> <raison>) =====
        Type warn = new Type("warn", ChatColor.YELLOW + "Warn", "warn %p %r", false, Material.PAPER);
        warn.reasons.add(new Reason("Langage inapproprie", "-", Material.BOOK));
        warn.reasons.add(new Reason("Spam", "-", Material.PAPER));
        warn.reasons.add(new Reason("Comportement toxique", "-", Material.BLAZE_POWDER));
        warn.reasons.add(new Reason("Publicite", "-", Material.SIGN));

        // ===== KICK (MaxBans : /kick <joueur> <raison>) =====
        Type kick = new Type("kick", ChatColor.RED + "Kick", "kick %p %r", false, Material.LEATHER_BOOTS);
        kick.reasons.add(new Reason("AFK", "-", Material.WATCH));
        kick.reasons.add(new Reason("Spam", "-", Material.PAPER));
        kick.reasons.add(new Reason("Insultes", "-", Material.BOOK));
        kick.reasons.add(new Reason("Comportement", "-", Material.BLAZE_POWDER));

        // ===== TEMPBAN (MaxBans : /tempban <joueur> <durée> <raison>) =====
        Type tempban = new Type("tempban", ChatColor.DARK_RED + "TempBan", "tempban %p %t %r", true, Material.IRON_SWORD);
        tempban.reasons.add(new Reason("Cheat (KillAura/Fly)", "30d", Material.DIAMOND_SWORD));
        tempban.reasons.add(new Reason("Insultes graves", "3d", Material.BOOK));
        tempban.reasons.add(new Reason("Bug abuse", "7d", Material.REDSTONE));
        tempban.reasons.add(new Reason("Team interdite", "1d", Material.LEATHER_CHESTPLATE));
        tempban.reasons.add(new Reason("Publicite", "7d", Material.SIGN));

        types.put("mute", mute);
        types.put("warn", warn);
        types.put("kick", kick);
        types.put("tempban", tempban);

        Bukkit.getPluginManager().registerEvents(this, this);
        getLogger().info("SS active (necessite MaxBans pour les commandes de sanction).");
    }

    // ---------- Commande ----------
    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage("Commande reservee aux joueurs.");
            return true;
        }
        Player p = (Player) sender;
        if (!p.hasPermission("ss.use")) {
            p.sendMessage(PREFIX + ChatColor.RED + "Permission refusee.");
            return true;
        }
        if (args.length != 1) {
            p.sendMessage(PREFIX + ChatColor.RED + "Usage : /ss <joueur>");
            return true;
        }
        targets.put(p.getUniqueId(), args[0]);
        openMain(p, args[0]);
        return true;
    }

    // ---------- Menus ----------
    private ItemStack item(Material m, int data, String name, String... lore) {
        ItemStack i = new ItemStack(m, 1, (short) data);
        ItemMeta meta = i.getItemMeta();
        meta.setDisplayName(name);
        if (lore.length > 0) meta.setLore(Arrays.asList(lore));
        i.setItemMeta(meta);
        return i;
    }

    private ItemStack head(String name) {
        ItemStack i = new ItemStack(Material.SKULL_ITEM, 1, (short) 3);
        SkullMeta m = (SkullMeta) i.getItemMeta();
        m.setOwner(name);
        m.setDisplayName(ChatColor.AQUA + name);
        m.setLore(Arrays.asList(ChatColor.GRAY + "Joueur a sanctionner"));
        i.setItemMeta(m);
        return i;
    }

    private void fill(Inventory inv) {
        ItemStack glass = item(Material.STAINED_GLASS_PANE, 15, " ");
        for (int s = 0; s < inv.getSize(); s++) inv.setItem(s, glass);
    }

    private void openMain(Player p, String target) {
        Inventory inv = Bukkit.createInventory(null, 27, T_MAIN + target);
        fill(inv);
        inv.setItem(4, head(target));
        int[] slots = {10, 12, 14, 16};
        int i = 0;
        for (Type t : types.values()) {
            inv.setItem(slots[i++], item(t.icon, 0, t.display,
                    ChatColor.GRAY + "Clique pour choisir une raison"));
        }
        p.openInventory(inv);
    }

    private void openReasons(Player p, String target, Type t) {
        Inventory inv = Bukkit.createInventory(null, 27, T_REASON + t.key + " " + target);
        fill(inv);
        inv.setItem(4, head(target));
        int[] slots = {10, 11, 12, 13, 14, 15, 16};
        int n = t.reasons.size();
        int start = (9 - n) / 2 + 9;
        for (int i = 0; i < n; i++) {
            Reason r = t.reasons.get(i);
            List<String> lore = new ArrayList<String>();
            lore.add(ChatColor.GRAY + "Sanction : " + t.display);
            if (t.timed) lore.add(ChatColor.GRAY + "Duree : " + ChatColor.WHITE + r.duration);
            lore.add("");
            lore.add(ChatColor.YELLOW + "Clique pour continuer");
            ItemStack it = item(r.icon, r.icon == Material.SKULL_ITEM ? 3 : 0, ChatColor.WHITE + r.name);
            ItemMeta m = it.getItemMeta();
            m.setLore(lore);
            it.setItemMeta(m);
            inv.setItem(start + i, it);
        }
        inv.setItem(22, item(Material.ARROW, 0, ChatColor.RED + "Retour"));
        p.openInventory(inv);
    }

    private void openConfirm(Player p, Pending pd) {
        Inventory inv = Bukkit.createInventory(null, 27, T_CONFIRM + pd.target);
        fill(inv);
        List<String> lore = new ArrayList<String>();
        lore.add(ChatColor.GRAY + "Joueur : " + ChatColor.WHITE + pd.target);
        lore.add(ChatColor.GRAY + "Sanction : " + pd.type.display);
        lore.add(ChatColor.GRAY + "Raison : " + ChatColor.WHITE + pd.reason.name);
        if (pd.type.timed) lore.add(ChatColor.GRAY + "Duree : " + ChatColor.WHITE + pd.reason.duration);
        ItemStack info = item(Material.PAPER, 0, ChatColor.AQUA + "Recapitulatif");
        ItemMeta m = info.getItemMeta();
        m.setLore(lore);
        info.setItemMeta(m);

        inv.setItem(13, info);
        for (int s : new int[]{9, 10, 11, 18, 19, 20})
            inv.setItem(s, item(Material.STAINED_GLASS_PANE, 5, ChatColor.GREEN + "" + ChatColor.BOLD + "CONFIRMER",
                    ChatColor.GRAY + "Appliquer la sanction"));
        for (int s : new int[]{15, 16, 17, 24, 25, 26})
            inv.setItem(s, item(Material.STAINED_GLASS_PANE, 14, ChatColor.RED + "" + ChatColor.BOLD + "ANNULER",
                    ChatColor.GRAY + "Annuler la sanction"));
        p.openInventory(inv);
    }

    // ---------- Clics ----------
    @EventHandler
    public void onClick(InventoryClickEvent e) {
        if (!(e.getWhoClicked() instanceof Player)) return;
        String title = e.getInventory().getTitle();
        if (title == null) return;
        boolean main = title.startsWith(T_MAIN);
        boolean reason = title.startsWith(T_REASON);
        boolean confirm = title.startsWith(T_CONFIRM);
        if (!main && !reason && !confirm) return;

        e.setCancelled(true);
        Player p = (Player) e.getWhoClicked();
        ItemStack it = e.getCurrentItem();
        if (it == null || it.getType() == Material.AIR || !it.hasItemMeta()) return;
        if (e.getClickedInventory() != e.getInventory()) return;

        String target = targets.get(p.getUniqueId());
        if (target == null) { p.closeInventory(); return; }

        if (main) {
            String clicked = ChatColor.stripColor(it.getItemMeta().getDisplayName()).toLowerCase();
            for (Type t : types.values()) {
                if (ChatColor.stripColor(t.display).toLowerCase().equals(clicked)) {
                    openReasons(p, target, t);
                    return;
                }
            }
        } else if (reason) {
            String key = title.substring(T_REASON.length()).split(" ")[0];
            Type t = types.get(key);
            if (t == null) return;
            String name = ChatColor.stripColor(it.getItemMeta().getDisplayName());
            if (it.getType() == Material.ARROW) { openMain(p, target); return; }
            for (Reason r : t.reasons) {
                if (r.name.equals(name)) {
                    Pending pd = new Pending(target, t, r);
                    pending.put(p.getUniqueId(), pd);
                    openConfirm(p, pd);
                    return;
                }
            }
        } else {
            if (it.getType() != Material.STAINED_GLASS_PANE) return;
            short data = it.getDurability();
            Pending pd = pending.get(p.getUniqueId());
            if (data == 5 && pd != null) {
                apply(p, pd);
                pending.remove(p.getUniqueId());
                p.closeInventory();
            } else if (data == 14) {
                pending.remove(p.getUniqueId());
                p.sendMessage(PREFIX + ChatColor.RED + "Sanction annulee.");
                openMain(p, target);
            }
        }
    }

    @EventHandler
    public void onClose(InventoryCloseEvent e) {
        // Nettoyage quand le staff ferme tous les menus
        final UUID id = e.getPlayer().getUniqueId();
        Bukkit.getScheduler().runTaskLater(this, new Runnable() {
            public void run() {
                Player p = Bukkit.getPlayer(id);
                if (p == null) { targets.remove(id); pending.remove(id); return; }
                String t = p.getOpenInventory().getTopInventory().getTitle();
                if (t == null || !(t.startsWith(T_MAIN) || t.startsWith(T_REASON) || t.startsWith(T_CONFIRM))) {
                    targets.remove(id);
                    pending.remove(id);
                }
            }
        }, 2L);
    }

    // ---------- Application via MaxBans ----------
    private void apply(Player staff, Pending pd) {
        String cmd = pd.type.command
                .replace("%p", pd.target)
                .replace("%t", pd.reason.duration)
                .replace("%r", pd.reason.name);
        // Execute en tant que staff pour que MaxBans enregistre le bon auteur
        boolean ok = Bukkit.dispatchCommand(staff, cmd);
        if (ok) {
            staff.sendMessage(PREFIX + ChatColor.GREEN + "Sanction appliquee : " + ChatColor.WHITE + "/" + cmd);
        } else {
            staff.sendMessage(PREFIX + ChatColor.RED + "Echec de la commande /" + cmd + " (MaxBans est-il installe ? permissions ?)");
        }
    }
}
