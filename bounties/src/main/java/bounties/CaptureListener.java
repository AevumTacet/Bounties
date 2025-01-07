package bounties;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractAtEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.BookMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class CaptureListener implements Listener {

    private final Main plugin;
    private final Map<UUID, Location> dungeonLocations = new HashMap<>();
    private final Map<UUID, Long> commandBlockedPlayers = new HashMap<>();

    public CaptureListener(Main plugin) {
        this.plugin = plugin;
    }

    @SuppressWarnings("deprecation")
    @EventHandler
    public void onPlayerInteract(PlayerInteractEvent event) {
        Player player = event.getPlayer();
        Action action = event.getAction();
        ItemStack item = player.getInventory().getItemInMainHand();
    
        // Verificar si el objeto en la mano es un libro escrito con el título "captura"
        if (item.getType() == Material.WRITTEN_BOOK) {
            BookMeta meta = (BookMeta) item.getItemMeta();
            if (meta != null && "captura".equalsIgnoreCase(meta.getTitle())) {
                
                // Verificar permisos solo si el jugador intenta usar un libro válido
                if (!player.hasPermission("bounties.use")) {
                    player.sendMessage(ChatColor.RED + "No tienes permiso para crear una orden de captura.");
                    event.setCancelled(true); // Cancelar la interacción con el libro
                    return;
                }
    
                int maxDays = plugin.getMaxSentenceDays(); // Límite máximo desde la configuración
    
                // Extraer el contenido del libro para verificar los días
                String content = String.join("\n", meta.getPages());
                String[] lines = content.split("\n");
                for (String line : lines) {
                    if (line.contains(",")) {
                        String[] parts = line.split(",");
                        if (parts.length == 2) {
                            try {
                                int days = Integer.parseInt(parts[1].trim());
                                if (days > maxDays) {
                                    player.sendMessage(ChatColor.RED + "El número de días en la sentencia es inválido. El máximo es de " + maxDays + " días.");
                                    return;
                                }
                            } catch (NumberFormatException e) {
                                player.sendMessage(ChatColor.RED + "Formato inválido en el libro. Asegúrate de que los días estén escritos como un número entero.");
                                return;
                            }
                        }
                    }
                }
    
                // Si pasa las validaciones, registrar coordenadas
                if (action == Action.RIGHT_CLICK_BLOCK && event.getClickedBlock() != null &&
                    event.getClickedBlock().getType() == Material.SOUL_SAND) {
    
                    Location blockLocation = event.getClickedBlock().getLocation();
                    Location dungeonLocation = new Location(
                            blockLocation.getWorld(),
                            blockLocation.getX(),
                            blockLocation.getY() + 1,
                            blockLocation.getZ()
                    );
    
                    PersistentDataContainer data = meta.getPersistentDataContainer();
                    data.set(new NamespacedKey(plugin, "dungeonLocation"), PersistentDataType.STRING,
                            dungeonLocation.getWorld().getName() + "," +
                            dungeonLocation.getBlockX() + "," +
                            dungeonLocation.getBlockY() + "," +
                            dungeonLocation.getBlockZ());
    
                    item.setItemMeta(meta);
                    player.sendMessage(ChatColor.GREEN + "Orden de captura registrada. Calabozo fijado en: "
                            + dungeonLocation.getBlockX() + ", " + dungeonLocation.getBlockY() + ", " + dungeonLocation.getBlockZ());
                }
            }
        }

        // Verificar si el objeto en la mano es un libro escrito con el título "gracia"
        if (item.getType() == Material.WRITTEN_BOOK) {
            BookMeta meta = (BookMeta) item.getItemMeta();
            if (meta != null && "gracia".equalsIgnoreCase(meta.getTitle())) {

                // Verificar que la interacción sea un clic derecho al aire
                if (action == Action.RIGHT_CLICK_AIR) {
                    if (!player.hasPermission("bounties.use")) {
                        player.sendMessage(ChatColor.RED + "No tienes permiso para emitir un perdón por gracia.");
                        return;
                    }

                    // Marcar el libro como validado usando un tag
                    PersistentDataContainer data = meta.getPersistentDataContainer();
                    data.set(new NamespacedKey(plugin, "validated"), PersistentDataType.BYTE, (byte) 1);
                    item.setItemMeta(meta);

                    player.sendMessage(ChatColor.GREEN + "El perdón por gracia ha sido emitido exitosamente.");
                }
            }
        }        


    }

    @SuppressWarnings("deprecation")
    @EventHandler
    public void onPlayerDeath(PlayerDeathEvent event) {
        Player victim = event.getEntity();
        Player killer = victim.getKiller();
        UUID victimId = victim.getUniqueId();

        if (killer != null) {
            ItemStack book = findCaptureBook(killer);

            if (book != null) {
                BookMeta meta = (BookMeta) book.getItemMeta();

                if (meta != null && meta.hasPages()) {
                    String[] bountyData = meta.getPage(1).split(",");
                    if (bountyData.length == 2) {
                        String targetName = bountyData[0].trim();
                        int days;

                        try {
                            days = Integer.parseInt(bountyData[1].trim());
                        } catch (NumberFormatException e) {
                            killer.sendMessage(ChatColor.RED + "El número de días en el libro es inválido.");
                            return;
                        }

                        if (victim.getName().equalsIgnoreCase(targetName)) {
                            String locationData = meta.getPersistentDataContainer().get(
                                    new NamespacedKey(plugin, "dungeonLocation"), PersistentDataType.STRING);

                            if (locationData != null) {
                                String[] parts = locationData.split(",");
                                Location dungeonLocation = new Location(
                                        Bukkit.getWorld(parts[0]),
                                        Double.parseDouble(parts[1]),
                                        Double.parseDouble(parts[2]),
                                        Double.parseDouble(parts[3])
                                );

                                dungeonLocations.put(victim.getUniqueId(), dungeonLocation);
                                commandBlockedPlayers.put(victim.getUniqueId(), System.currentTimeMillis() + (days * 86400000L));

                                killer.sendMessage(ChatColor.GREEN + "Has capturado a " + victim.getName() +
                                        ". Será transportado al calabozo y sus comandos estarán bloqueados por " + days + " días.");
                                plugin.getLogger().info(killer.getName() + " capturó a " + victim.getName() +
                                        ". Calabozo: " + dungeonLocation + ", Duración: " + days + " días.");
                            }
                        } else {
                            killer.sendMessage(ChatColor.RED + "El nombre en la orden de captura no coincide con el jugador asesinado.");
                        }
                    }
                }
            }
        }
            // Verificar si el jugador está en la lista de prohibición de comandos
        if (commandBlockedPlayers.containsKey(victimId)) {
        // Liberar al jugador de la prohibición
        commandBlockedPlayers.remove(victimId);
        victim.sendMessage(ChatColor.GREEN + "Tu sentencia termina con tu muerte. Ahora puedes usar comandos nuevamente.");
        plugin.getLogger().info("El jugador " + victim.getName() + " ha sido liberado de su sentencia tras su muerte.");
        }
    }

    @SuppressWarnings("deprecation")
    @EventHandler
    public void onPlayerRespawn(PlayerRespawnEvent event) {
        Player victim = event.getPlayer();
        UUID victimId = victim.getUniqueId();
    
        // 1. Teletransportar al jugador al calabozo si está registrado
        if (dungeonLocations.containsKey(victimId)) {
            Location dungeonLocation = dungeonLocations.get(victimId);
            event.setRespawnLocation(dungeonLocation); // Establecer el lugar de respawn
            dungeonLocations.remove(victimId); // Remover registro del calabozo
        }
    
        // 2. Manejar la prohibición de comandos
        if (commandBlockedPlayers.containsKey(victimId)) {
            long expiryTime = commandBlockedPlayers.get(victimId);
            if (System.currentTimeMillis() > expiryTime) {
                commandBlockedPlayers.remove(victimId); // Remover prohibición si expiró
            } else {
                Bukkit.getScheduler().runTaskLater(plugin, () -> {
                    if (System.currentTimeMillis() > expiryTime) {
                        commandBlockedPlayers.remove(victimId);
                    }
                }, (expiryTime - System.currentTimeMillis()) / 50L); // Programar remoción de bloqueo
            }
        }
    
    // Crear el Certificado tras remover el libro de captura
    if (plugin.getCapturedPlayers().containsKey(victimId)) {
        Player captor = Bukkit.getPlayer(plugin.getKillers().get(victimId));
        if (captor != null) {
            // Buscar y remover el libro de captura
            ItemStack captureBook = findCaptureBook(captor);
            if (captureBook != null) {
                captor.getInventory().removeItem(captureBook); // Remover el libro de captura

                // Crear el Certificado
                ItemStack certificateBook = new ItemStack(Material.WRITTEN_BOOK);
                BookMeta bookMeta = (BookMeta) certificateBook.getItemMeta();
                if (bookMeta != null) {
                    bookMeta.setTitle("Certificado");
                    bookMeta.setAuthor("Confirmado");

                    // Crear contenido del libro
                    Location respawnLocation = event.getRespawnLocation();
                    String pageContent = victim.getName() + " fue capturado por " + captor.getName() + ",\n"
                            + "y ha sido encarcelado en las coordenadas:\n"
                            + "X: " + respawnLocation.getBlockX() + "\n"
                            + "Y: " + respawnLocation.getBlockY() + "\n"
                            + "Z: " + respawnLocation.getBlockZ() + ".";

                    bookMeta.addPage(pageContent);
                    certificateBook.setItemMeta(bookMeta);

                    // Añadir el Certificado al inventario del captor
                    captor.getInventory().addItem(certificateBook);
                    captor.sendMessage(ChatColor.GREEN + "Has recibido un Certificado en tu inventario.");
                    }
            } else {
                 captor.sendMessage(ChatColor.RED + "No se encontró el libro de captura para eliminar.");
                }
            }

        }
    
    }

    @SuppressWarnings("deprecation")
    @EventHandler
    public void onPlayerInteractAtEntity(PlayerInteractAtEntityEvent event) {
        Player player = event.getPlayer();
        Entity clickedEntity = event.getRightClicked();
    
        // Verificar si el objetivo es un jugador
        if (clickedEntity instanceof Player) {
            Player targetPlayer = (Player) clickedEntity;
    
            // Verificar si el objeto en la mano es un libro escrito con el título "gracia"
            ItemStack item = player.getInventory().getItemInMainHand();
            if (item.getType() == Material.WRITTEN_BOOK) {
                BookMeta meta = (BookMeta) item.getItemMeta();
                if (meta != null && "gracia".equalsIgnoreCase(meta.getTitle())) {
    
                    // Verificar que el libro tiene el tag validated
                    PersistentDataContainer data = meta.getPersistentDataContainer();
                    if (!data.has(new NamespacedKey(plugin, "validated"), PersistentDataType.BYTE)) {
                        player.sendMessage(ChatColor.RED + "Este libro de gracia no ha sido validado.");
                        return;
                    }
    
                    // Verificar que el nombre del jugador en el libro coincide con el objetivo
                    String content = String.join("\n", meta.getPages()).trim();
                    if (!content.equalsIgnoreCase(targetPlayer.getName())) {
                        player.sendMessage(ChatColor.RED + "El nombre del jugador en el libro no coincide con el jugador cautivo.");
                        return;
                    }
    
                    // Liberar al jugador
                    UUID targetUUID = targetPlayer.getUniqueId();
                    if (plugin.getCapturedPlayers().containsKey(targetUUID)) {
                        plugin.getCapturedPlayers().remove(targetUUID);
                        player.sendMessage(ChatColor.GREEN + "Has liberado al jugador " + targetPlayer.getName() + ".");
                        targetPlayer.sendMessage(ChatColor.GREEN + "Has sido perdonado por la gracia de " + player.getName() + ".");
                    } else {
                        player.sendMessage(ChatColor.RED + "El jugador especificado no está capturado.");
                    }
                    // Consumir el libro de perdón
                    player.getInventory().remove(item);
                    player.sendMessage(ChatColor.YELLOW + "El libro de perdón ha sido consumido tras liberar al cautivo.");
                }
            }
        }
    }
    

    // Método auxiliar para buscar el libro de captura
    private ItemStack findCaptureBook(Player player) {
        for (ItemStack item : player.getInventory().getContents()) {
            if (item != null && item.getType() == Material.WRITTEN_BOOK) {
                BookMeta meta = (BookMeta) item.getItemMeta();
                if (meta != null && "captura".equalsIgnoreCase(meta.getTitle())) {
                    return item;
                }
            }
        }
        return null;
    }
}
