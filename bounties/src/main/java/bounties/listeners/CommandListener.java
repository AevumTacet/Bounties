package bounties.listeners;

import bounties.DataManager;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import java.util.List;
import java.util.UUID;

public class CommandListener implements Listener {
    private final DataManager dataManager;

    public CommandListener(DataManager dataManager) {
        this.dataManager = dataManager;
    }

    @EventHandler
    public void onCommand(PlayerCommandPreprocessEvent event) {
        Player player = event.getPlayer();
        UUID uuid = player.getUniqueId();

        // 1. Verificar si el jugador es cautivo
        if (!dataManager.isCaptive(uuid)) return;

        // 2. Obtener comando base (sin "/" ni argumentos)
        String rawCommand = event.getMessage();
        String baseCommand = extractBaseCommand(rawCommand);

        // 3. Chequear lista de comandos restringidos
        List<String> restrictedCommands = dataManager.getPlugin().getConfig().getStringList("restricted-commands");
        
        if (restrictedCommands.contains(baseCommand.toLowerCase())) {
            // 4. Cancelar y notificar
            event.setCancelled(true);
            sendBlockedMessage(player);
        }
    }

    // Extrae el comando base (ej: "/tp @a" → "tp")
    private String extractBaseCommand(String fullCommand) {
        return fullCommand.substring(1)  // Remover "/"
                         .split(" ")[0]  // Ignorar argumentos
                         .toLowerCase(); // Case-insensitive
    }

    // Envía mensaje de comando bloqueado
    private void sendBlockedMessage(Player player) {
        Component message = MiniMessage.miniMessage().deserialize(
            dataManager.getPlugin().getConfig().getString(
                "messages.command-blocked", 
                "<red>¡Comando bloqueado durante tu condena!"
            )
        );
        player.sendMessage(message);
    }
}