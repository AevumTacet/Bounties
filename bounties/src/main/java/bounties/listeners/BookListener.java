package bounties.listeners;

import bounties.BountyManager;
import bounties.Main;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.BookMeta;

public class BookListener implements Listener {
    private final Main plugin;
    private final BountyManager bountyManager;

    public BookListener(Main plugin, BountyManager bountyManager) {
        this.plugin = plugin;
        this.bountyManager = bountyManager;
    }

    @EventHandler
    public void onSoulSandClick(PlayerInteractEvent event) {
        // 1. Verificar click derecho en bloque
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        
        // 2. Verificar bloque es Soul Sand
        Block block = event.getClickedBlock();
        if (block == null || block.getType() != Material.SOUL_SAND) return;
        
        // 3. Verificar ítem en mano es libro firmado
        ItemStack item = event.getItem();
        if (item == null || !isValidBountyBook(item)) return;
        
        // 4. Obtener ubicación del calabozo (Y+1 para evitar asfixia)
        Location calabozo = block.getLocation().add(0, 1, 0);
        
        // 5. Activar orden de captura
        if (bountyManager.activateBounty(event.getPlayer(), item, calabozo)) {
            event.setCancelled(true); // Prevenir apertura del libro
        }
    }

    @EventHandler
    public void onIndultoValidate(PlayerInteractEvent event) {
        // 1. Verificar click derecho al aire
        if (event.getAction() != Action.RIGHT_CLICK_AIR) return;
        
        // 2. Verificar ítem en mano es libro "indulto"
        ItemStack item = event.getItem();
        if (item == null || !isValidIndultoBook(item)) return;
        
        // 3. Validar y marcar indulto
        if (bountyManager.validateIndulto(event.getPlayer(), item)) {
            event.setCancelled(true);
            sendSuccessMessage(event.getPlayer(), "indulto-validated");
        }
    }

    // ========== MÉTODOS AUXILIARES ==========
    private boolean isValidBountyBook(ItemStack item) {
        if (item.getType() != Material.WRITTEN_BOOK) return false;
        BookMeta meta = (BookMeta) item.getItemMeta();
        
        return meta != null && 
               meta.getTitle().equalsIgnoreCase("captura") &&
               meta.getPageCount() >= 1 &&
               parseBountyTarget(meta.getPage(1)) != null;
    }

    private boolean isValidIndultoBook(ItemStack item) {
        if (item.getType() != Material.WRITTEN_BOOK) return false;
        BookMeta meta = (BookMeta) item.getItemMeta();
        
        return meta != null && 
               meta.getTitle().equalsIgnoreCase("indulto") &&
               !meta.getPages().isEmpty();
    }

    private String parseBountyTarget(String pageContent) {
        String[] parts = pageContent.split(",");
        if (parts.length != 2) return null;
        
        String target = parts[0].trim();
        try {
            Integer.parseInt(parts[1].trim());
            return target;
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private void sendSuccessMessage(Player player, String configKey) {
        Component msg = MiniMessage.miniMessage().deserialize(
            plugin.getConfig().getString("messages." + configKey, "✔️ Operación exitosa")
        );
        player.sendActionBar(msg);
    }
}