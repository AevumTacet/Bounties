package bounties;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.BookMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import java.util.List;
import java.util.UUID;

public class BountyManager {
    private final Main plugin;
    private final DataManager dataManager;
    private final NamespacedKey bountyKey;
    private final NamespacedKey validatedKey;

    public BountyManager(Main plugin, DataManager dataManager) {
        this.plugin = plugin;
        this.dataManager = dataManager;
        this.bountyKey = new NamespacedKey(plugin, "bounty_data");
        this.validatedKey = new NamespacedKey(plugin, "validated");
    }

    // ========== ACTIVACIÓN DE ÓRDENES DE CAPTURA ==========
    public boolean activateBounty(Player player, ItemStack book, Location calabozo) {
        // 1. Verificar que el libro esté firmado y tenga título "captura"
        if (!(book.getItemMeta() instanceof BookMeta meta) || 
            !meta.getTitle().equalsIgnoreCase("captura")) return false;

        // 2. Chequear autor y permisos
        if (!meta.getAuthor().equals(player.getName()) || 
            !player.hasPermission("bounties.use")) return false;

        // 3. Parsear contenido del libro (formato: "Jugador, días")
        List<String> pages = meta.getPages();
        if (pages.isEmpty()) return false;
        
        String[] parts = pages.get(0).split(",");
        if (parts.length != 2) return false;

        String targetName = parts[0].trim();
        int days;
        try {
            days = Integer.parseInt(parts[1].trim());
            if (days > dataManager.getMaxSentenceDays()) {
                player.sendMessage(MiniMessage.miniMessage().deserialize(
                    plugin.getConfig().getString("messages.sentence-too-long")
                ));
                return false;
            }
        } catch (NumberFormatException e) {
            return false;
        }

        // 4. Guardar datos NBT
        PersistentDataContainer pdc = book.getItemMeta().getPersistentDataContainer();
        pdc.set(bountyKey, PersistentDataType.STRING, 
            targetName + "|" + days + "|" + locationToString(calabozo));

        // 5. Notificar éxito
        player.sendActionBar(MiniMessage.miniMessage().deserialize(
            plugin.getConfig().getString("messages.bounty-activated", "")
                .replace("%x%", String.valueOf(calabozo.getBlockX()))
                .replace("%y%", String.valueOf(calabozo.getBlockY()))
                .replace("%z%", String.valueOf(calabozo.getBlockZ()))
        ));
        
        return true;
    }

    // ========== PROCESAMIENTO DE CAPTURA ==========
    public void processCapture(Player captor, Player captive) {
        // 1. Buscar libro válido en inventario
        for (ItemStack item : captor.getInventory()) {
            if (item == null || !item.hasItemMeta()) continue;
            
            PersistentDataContainer pdc = item.getItemMeta().getPersistentDataContainer();
            if (!pdc.has(bountyKey, PersistentDataType.STRING)) continue;

            // 2. Extraer datos NBT
            String[] data = pdc.get(bountyKey, PersistentDataType.STRING).split("\\|");
            if (data.length != 3) continue;
            
            String targetName = data[0];
            int days = Integer.parseInt(data[1]);
            Location calabozo = stringToLocation(data[2]);

            // 3. Verificar coincidencia de nombre
            if (!captive.getName().equalsIgnoreCase(targetName)) continue;

            // 4. Aplicar condena
            dataManager.addCaptive(captive.getUniqueId(), days, calabozo);
            
            // 5. Reemplazar libro por certificado
            ItemStack certificado = createCertificado(targetName, captor.getName(), days);
            item.setItemMeta(certificado.getItemMeta());

            // 6. Notificaciones
            notifyParties(captor, captive, calabozo);
            break;
        }
    }

    // ========== PROCESAMIENTO DE INDULTO ==========
    public boolean processIndulto(Player indultante, Player captive, ItemStack indulto) {
        // 1. Verificar NBT y permisos
        if (!indulto.hasItemMeta() || 
            !indultante.hasPermission("bounties.use")) return false;

        PersistentDataContainer pdc = indulto.getItemMeta().getPersistentDataContainer();
        if (!pdc.has(validatedKey, PersistentDataType.BYTE)) return false;

        // 2. Verificar nombre en libro
        BookMeta meta = (BookMeta) indulto.getItemMeta();
        if (meta.getPages().isEmpty() || 
            !meta.getPage(1).contains(captive.getName())) return false;

        // 3. Liberar cautivo
        dataManager.removeCaptive(captive.getUniqueId());
        
        // 4. Notificaciones
        Component msg = MiniMessage.miniMessage().deserialize(
            plugin.getConfig().getString("messages.pardon", "<green>¡%s ha sido indultado!")
                .replace("%s", captive.getName())
        );
        
        indultante.sendMessage(msg);
        captive.sendMessage(msg);
        
        return true;
    }

    // ========== MÉTODOS AUXILIARES ==========
    private String locationToString(Location loc) {
        return loc.getWorld().getName() + "," + 
               loc.getX() + "," + loc.getY() + "," + loc.getZ();
    }

    private Location stringToLocation(String str) {
        String[] parts = str.split(",");
        return new Location(
            plugin.getServer().getWorld(parts[0]),
            Double.parseDouble(parts[1]),
            Double.parseDouble(parts[2]),
            Double.parseDouble(parts[3])
        );
    }

    private ItemStack createCertificado(String cautivo, String captor, int days) {
        ItemStack libro = new ItemStack(org.bukkit.Material.WRITTEN_BOOK);
        BookMeta meta = (BookMeta) libro.getItemMeta();
        
        meta.setTitle("Certificado");
        meta.setAuthor("Sistema de Capturas");
        meta.pages(Component.text(
            "Cautivo: " + cautivo + "\n" +
            "Captor: " + captor + "\n" +
            "Días: " + days
        ));
        
        libro.setItemMeta(meta);
        return libro;
    }

    private void notifyParties(Player captor, Player captive, Location calabozo) {
        String coords = String.format("X: %d Y: %d Z: %d", 
            calabozo.getBlockX(), calabozo.getBlockY(), calabozo.getBlockZ());
        
        // Notificar al captor
        captor.sendMessage(MiniMessage.miniMessage().deserialize(
            plugin.getConfig().getString("messages.capture-success", "")
                .replace("%player%", captive.getName())
                .replace("%coords%", coords)
        ));

        // Notificar al cautivo
        captive.sendMessage(MiniMessage.miniMessage().deserialize(
            plugin.getConfig().getString("messages.captive-notify", "")
                .replace("%days%", String.valueOf(dataManager.getDaysLeft(captive.getUniqueId())))
        ));
    }
    public void startSentenceScheduler() {
        Bukkit.getScheduler().scheduleSyncRepeatingTask(plugin, () -> {
            for (UUID captiveId : dataManager.getAllCaptives()) {
                int totalMinutes = dataManager.getRemainingTime(captiveId);
                int daysServed = totalMinutes / 1440; // 1440 minutos = 1 día Minecraft
                int sentenceDays = dataManager.getDaysLeft(captiveId);
                
                if (daysServed >= sentenceDays) {
                    releaseCaptive(captiveId, true);
                }
            }
        }, 0L, 1200L); // Ejecutar cada minuto (1200 ticks = 60 seg)
    }

    public void releaseCaptive(UUID captiveId, boolean expired) {
        Player captive = Bukkit.getPlayer(captiveId);
        dataManager.removeCaptive(captiveId);
        
        if (captive != null) {
            String messageKey = expired ? "messages.sentence-expired" : "messages.pardon";
            Component msg = MiniMessage.miniMessage().deserialize(
                plugin.getConfig().getString(messageKey, "<green>¡Liberado!")
            );
            
            captive.sendMessage(msg);
        }
    }

    public DataManager getDataManager() {
        return dataManager;
    }

    public Main getPlugin() {
        return plugin;
    }

    public boolean validateIndulto(Player player, ItemStack indulto) {
        // 1. Verificar permisos
        if (!player.hasPermission("bounties.use")) return false;

        // 2. Verificar que sea un libro válido
        if (!(indulto.getItemMeta() instanceof BookMeta meta) || 
            !meta.getTitle().equalsIgnoreCase("indulto")) return false;

        // 3. Aplicar NBT de validación
        PersistentDataContainer pdc = meta.getPersistentDataContainer();
        pdc.set(validatedKey, PersistentDataType.BYTE, (byte) 1);
        indulto.setItemMeta(meta);

        return true;
    }

    // Método auxiliar para verificar si un indulto está validado
    public boolean isIndultoValidated(ItemStack indulto) {
        if (!indulto.hasItemMeta()) return false;
        PersistentDataContainer pdc = indulto.getItemMeta().getPersistentDataContainer();
        return pdc.has(validatedKey, PersistentDataType.BYTE);
    }

    // Método auxiliar para notificar al habilitador de la orden
    private void notifyBountyStarter(UUID captiveId) {
        // Implementar según tu sistema de tracking (requiere guardar el UUID del creador del bounty)
    }
}