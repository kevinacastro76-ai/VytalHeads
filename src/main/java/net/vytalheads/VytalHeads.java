package net.vytalheads;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.command.*;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.*;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.stream.Collectors;

public final class VytalHeads extends JavaPlugin implements Listener, TabExecutor {
    private final Map<String, HeadEntry> heads = new LinkedHashMap<>();
    private NamespacedKey headKey;
    private NamespacedKey categoryKey;
    private File database;

    @Override public void onEnable() {
        saveDefaultConfig();
        headKey = new NamespacedKey(this, "head_id");
        categoryKey = new NamespacedKey(this, "category");
        database = new File(getDataFolder(), getConfig().getString("database.file", "heads.json"));
        if (!getDataFolder().exists()) getDataFolder().mkdirs();
        if (!database.exists()) {
            try (InputStream in = getResource("heads.json")) {
                if (in != null) Files.copy(in, database.toPath());
                else Files.writeString(database.toPath(), "{\"heads\":[]}", StandardCharsets.UTF_8);
            } catch (IOException e) { getLogger().warning("No se pudo crear la base inicial: " + e.getMessage()); }
        }
        loadDatabase();
        Objects.requireNonNull(getCommand("heads")).setExecutor(this);
        Objects.requireNonNull(getCommand("heads")).setTabCompleter(this);
        Bukkit.getPluginManager().registerEvents(this, this);
        getLogger().info("VytalHeads habilitado. Cabezas cargadas: " + heads.size());
    }

    private void loadDatabase() {
        heads.clear();
        if (!database.exists()) return;
        try {
            String raw = Files.readString(database.toPath(), StandardCharsets.UTF_8);
            // Parser JSON pequeño para el esquema controlado de heads.json.
            java.util.regex.Matcher m = java.util.regex.Pattern.compile(
                "\\{\\s*\"id\"\\s*:\\s*\"((?:\\\\.|[^\"\\\\])*)\"\\s*,\\s*\"name\"\\s*:\\s*\"((?:\\\\.|[^\"\\\\])*)\"\\s*,\\s*\"category\"\\s*:\\s*\"((?:\\\\.|[^\"\\\\])*)\"\\s*,\\s*\"material\"\\s*:\\s*\"((?:\\\\.|[^\"\\\\])*)\"\\s*,\\s*\"texture\"\\s*:\\s*\"((?:\\\\.|[^\"\\\\])*)\"\\s*\\}"
            ).matcher(raw);
            while (m.find()) {
                String id = unescape(m.group(1));
                heads.put(id.toLowerCase(Locale.ROOT), new HeadEntry(id, unescape(m.group(2)),
                    unescape(m.group(3)).toUpperCase(Locale.ROOT), unescape(m.group(4)).toUpperCase(Locale.ROOT),
                    unescape(m.group(5))));
            }
        } catch (IOException e) { getLogger().severe("Error leyendo heads.json: " + e.getMessage()); }
    }

    private String unescape(String s) {
        return s.replace("\\\"", "\"").replace("\\n", "\n").replace("\\\\", "\\");
    }
    private String escape(String s) {
        return s.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n");
    }
    private void saveDatabase() {
        StringBuilder out = new StringBuilder("{\n  \"heads\": [\n");
        int i = 0;
        for (HeadEntry h : heads.values()) {
            if (i++ > 0) out.append(",\n");
            out.append("    {\"id\":\"").append(escape(h.id)).append("\",\"name\":\"")
                .append(escape(h.name)).append("\",\"category\":\"").append(escape(h.category))
                .append("\",\"material\":\"").append(escape(h.material)).append("\",\"texture\":\"")
                .append(escape(h.texture)).append("\"}");
        }
        out.append("\n  ]\n}\n");
        try { Files.writeString(database.toPath(), out, StandardCharsets.UTF_8); }
        catch (IOException e) { getLogger().severe("No se pudo guardar la base: " + e.getMessage()); }
    }

    @Override public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0) {
            if (!(sender instanceof Player p)) { sender.sendMessage("Este comando requiere un jugador."); return true; }
            if (!p.hasPermission("vytalheads.use")) { p.sendMessage(color("&cNo tienes permiso.")); return true; }
            openCategories(p); return true;
        }
        String sub = args[0].toLowerCase(Locale.ROOT);
        if (sub.equals("reload")) {
            if (!sender.hasPermission("vytalheads.admin")) { sender.sendMessage(color("&cNo tienes permiso.")); return true; }
            loadDatabase(); reloadConfig(); sender.sendMessage(color("&aBase recargada. Cabezas: &f" + heads.size())); return true;
        }
        if (sub.equals("search")) {
            if (!(sender instanceof Player p)) { sender.sendMessage("Este comando requiere un jugador."); return true; }
            if (args.length < 2) { p.sendMessage(color("&eUso: /heads search <texto>")); return true; }
            String query = String.join(" ", Arrays.copyOfRange(args, 1, args.length)).toLowerCase(Locale.ROOT);
            List<HeadEntry> found = heads.values().stream().filter(h -> h.name.toLowerCase(Locale.ROOT).contains(query)
                || h.id.toLowerCase(Locale.ROOT).contains(query) || h.category.toLowerCase(Locale.ROOT).contains(query)).toList();
            openHeads(p, found, 0, "Búsqueda: " + query); return true;
        }
        if (sub.equals("give")) {
            if (!sender.hasPermission("vytalheads.give")) { sender.sendMessage(color("&cNo tienes permiso.")); return true; }
            if (args.length < 3) { sender.sendMessage(color("&eUso: /heads give <jugador> <id>")); return true; }
            Player target = Bukkit.getPlayerExact(args[1]);
            HeadEntry h = heads.get(args[2].toLowerCase(Locale.ROOT));
            if (target == null || h == null) { sender.sendMessage(color("&cJugador desconectado o ID desconocido.")); return true; }
            target.getInventory().addItem(createItem(h));
            sender.sendMessage(color("&aCabeza entregada a " + target.getName())); return true;
        }
        if (sub.equals("add")) {
            if (!sender.hasPermission("vytalheads.admin")) { sender.sendMessage(color("&cNo tienes permiso.")); return true; }
            if (!(sender instanceof Player p)) { sender.sendMessage("Este comando requiere un jugador."); return true; }
            if (args.length < 4) { p.sendMessage(color("&eUso: /heads add <id> <categoria> <nombre>")); return true; }
            ItemStack held = p.getInventory().getItemInMainHand();
            if (held.getType().isAir()) { p.sendMessage(color("&cSostén una cabeza en la mano.")); return true; }
            String id = args[1].toLowerCase(Locale.ROOT);
            String category = args[2].toUpperCase(Locale.ROOT);
            String name = String.join(" ", Arrays.copyOfRange(args, 3, args.length));
            String encoded = Base64.getEncoder().encodeToString(held.serializeAsBytes());
            heads.put(id, new HeadEntry(id, name, category, held.getType().name(), encoded));
            saveDatabase(); p.sendMessage(color("&aCabeza guardada con ID: &f" + id)); return true;
        }
        if (args.length == 1) {
            String cat = args[0].toUpperCase(Locale.ROOT);
            if (!(sender instanceof Player p)) { sender.sendMessage("Este comando requiere un jugador."); return true; }
            List<HeadEntry> found = heads.values().stream().filter(h -> h.category.equals(cat)).toList();
            if (found.isEmpty()) { p.sendMessage(color("&cCategoría vacía o desconocida.")); return true; }
            openHeads(p, found, 0, cat); return true;
        }
        sender.sendMessage(color("&e/heads | /heads <categoria> | /heads search <texto> | /heads give <jugador> <id> | /heads add <id> <categoria> <nombre> | /heads reload"));
        return true;
    }

    private void openCategories(Player p) {
        Inventory inv = Bukkit.createInventory(null, 27, color(getConfig().getString("settings.menu-title", "&5VytalHeads - Categorías")));
        Map<String, Material> icons = Map.of("JUGADORES", Material.PLAYER_HEAD, "ANIMALES", Material.PIGLIN_HEAD,
            "MOBS", Material.CREEPER_HEAD, "COMIDA", Material.CAKE, "BLOQUES", Material.BRICKS,
            "DECORACION", Material.FLOWER_POT, "ESPECIALES", Material.NETHER_STAR, "TODAS", Material.CHEST);
        int[] slots = {10,11,12,13,14,15,16,22};
        int i = 0;
        for (var entry : icons.entrySet()) {
            ItemStack item = new ItemStack(entry.getValue());
            ItemMeta meta = item.getItemMeta();
            meta.setDisplayName(color("&d" + entry.getKey()));
            long count = entry.getKey().equals("TODAS") ? heads.size() : heads.values().stream().filter(h -> h.category.equals(entry.getKey())).count();
            meta.setLore(List.of(color("&7Cabezas: &f" + count), color("&eHaz clic para abrir")));
            meta.getPersistentDataContainer().set(categoryKey, PersistentDataType.STRING, entry.getKey());
            item.setItemMeta(meta); inv.setItem(slots[i++], item);
        }
        p.openInventory(inv);
    }

    private void openHeads(Player p, List<HeadEntry> entries, int page, String title) {
        int pageSize = 45;
        int maxPage = Math.max(0, (entries.size() - 1) / pageSize);
        page = Math.max(0, Math.min(page, maxPage));
        Inventory inv = Bukkit.createInventory(null, 54, color("&5VytalHeads &8| &7" + title + " &8(" + (page+1) + "/" + (maxPage+1) + ")"));
        int start = page * pageSize;
        for (int i = start; i < Math.min(start + pageSize, entries.size()); i++) {
            HeadEntry h = entries.get(i);
            ItemStack item = createItem(h);
            ItemMeta meta = item.getItemMeta();
            meta.getPersistentDataContainer().set(headKey, PersistentDataType.STRING, h.id);
            item.setItemMeta(meta); inv.setItem(i-start, item);
        }
        ItemStack back = new ItemStack(Material.ARROW); ItemMeta bm = back.getItemMeta();
        bm.setDisplayName(color("&cVolver a categorías")); back.setItemMeta(bm); inv.setItem(45, back);
        ItemStack previous = new ItemStack(Material.PAPER); ItemMeta pm = previous.getItemMeta();
        pm.setDisplayName(color("&ePágina anterior")); pm.getPersistentDataContainer().set(categoryKey, PersistentDataType.STRING, "PAGE:" + (page-1) + ":" + title);
        previous.setItemMeta(pm); inv.setItem(48, previous);
        ItemStack next = new ItemStack(Material.PAPER); ItemMeta nm = next.getItemMeta();
        nm.setDisplayName(color("&aPágina siguiente")); nm.getPersistentDataContainer().set(categoryKey, PersistentDataType.STRING, "PAGE:" + (page+1) + ":" + title);
        next.setItemMeta(nm); inv.setItem(50, next);
        p.openInventory(inv);
    }

    private ItemStack createItem(HeadEntry h) {
        try {
            if (h.texture != null && !h.texture.isBlank()) {
                byte[] data = Base64.getDecoder().decode(h.texture);
                ItemStack item = ItemStack.deserializeBytes(data);
                if (item != null && !item.getType().isAir()) return item;
            }
        } catch (Exception ignored) {}
        Material material = Material.matchMaterial(h.material);
        if (material == null || !material.isItem()) material = Material.PLAYER_HEAD;
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(color("&d" + h.name));
        meta.setLore(List.of(color("&7ID: &f" + h.id), color("&8Categoría: " + h.category)));
        meta.getPersistentDataContainer().set(headKey, PersistentDataType.STRING, h.id);
        item.setItemMeta(meta); return item;
    }

    @EventHandler public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player p)) return;
        String title = ChatColor.stripColor(event.getView().getTitle());
        if (title == null || !title.contains("VytalHeads")) return;
        event.setCancelled(true);
        ItemStack clicked = event.getCurrentItem();
        if (clicked == null || clicked.getType().isAir() || !clicked.hasItemMeta()) return;
        ItemMeta meta = clicked.getItemMeta();
        String category = meta.getPersistentDataContainer().get(categoryKey, PersistentDataType.STRING);
        if (category != null) {
            if (category.equals("TODAS")) { openHeads(p, new ArrayList<>(heads.values()), 0, "Todas"); return; }
            if (category.startsWith("PAGE:")) {
                String[] parts = category.split(":", 3);
                int page; try { page = Integer.parseInt(parts[1]); } catch (Exception ex) { return; }
                String cat = parts.length > 2 ? parts[2] : "Todas";
                List<HeadEntry> list = cat.equalsIgnoreCase("Todas") ? new ArrayList<>(heads.values()) :
                    heads.values().stream().filter(h -> h.category.equalsIgnoreCase(cat)).toList();
                openHeads(p, list, page, cat); return;
            }
            if (category.matches("[A-Z_]+")) {
                List<HeadEntry> list = heads.values().stream().filter(h -> h.category.equals(category)).toList();
                openHeads(p, list, 0, category); return;
            }
        }
        String id = meta.getPersistentDataContainer().get(headKey, PersistentDataType.STRING);
        if (id != null) {
            HeadEntry h = heads.get(id.toLowerCase(Locale.ROOT));
            if (h != null) {
                HashMap<Integer, ItemStack> leftovers = p.getInventory().addItem(createItem(h));
                leftovers.values().forEach(it -> p.getWorld().dropItemNaturally(p.getLocation(), it));
                p.sendMessage(color("&aHas recibido: &f" + h.name));
            }
            return;
        }
        if (clicked.getType() == Material.ARROW) openCategories(p);
    }

    private String color(String s) { return ChatColor.translateAlternateColorCodes('&', s == null ? "" : s); }

    @Override public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) return Arrays.asList("JUGADORES","ANIMALES","MOBS","COMIDA","BLOQUES","DECORACION","ESPECIALES","search","give","add","reload")
            .stream().filter(s -> s.toLowerCase(Locale.ROOT).startsWith(args[0].toLowerCase(Locale.ROOT))).collect(Collectors.toList());
        return Collections.emptyList();
    }
    private record HeadEntry(String id, String name, String category, String material, String texture) {}
                         }
                
