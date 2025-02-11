package bounties.listeners;

import bounties.BountyManager;
import bounties.DataManager;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerRespawnEvent;

import java.util.UUID;

public class PlayerListener implements Listener {
    private final BountyManager bountyManager;
    private final DataManager dataManager;

    public PlayerListener(BountyManager bountyManager) {
        this.bountyManager = bountyManager;
        this.dataManager = bountyManager.getDataManager();
    }

    @EventHandler
    public void onPlayerDeath(PlayerDeathEvent event) {
        Player player = event.getEntity();
        UUID uuid = player.getUniqueId();

        if (dataManager.isCaptive(uuid)) {
            // Liberar al cautivo por muerte
            bountyManager.releaseCaptive(uuid, false);
            
            // Mensaje al jugador
            Component msg = MiniMessage.miniMessage().deserialize(
                bountyManager.getPlugin().getConfig().getString("messages.sentence-ended-death", 
                    "<red>¡Condena terminada por muerte!")
            );
            player.sendMessage(msg);
        }
    }

    @EventHandler
    public void onPlayerRespawn(PlayerRespawnEvent event) {
        Player player = event.getPlayer();
        UUID uuid = player.getUniqueId();

        if (dataManager.isCaptive(uuid)) {
            Location calabozo = dataManager.getCalabozo(uuid);
            
            if (calabozo != null && calabozo.getWorld() != null) {
                // Teletransportar al calabozo con offset Y+1
                event.setRespawnLocation(calabozo.clone().add(0, 1, 0));
            } else {
                // Notificar error y liberar
                dataManager.removeCaptive(uuid);
                Component errorMsg = MiniMessage.miniMessage().deserialize(
                    bountyManager.getPlugin().getConfig().getString("messages.invalid-calabozo",
                        "<red>¡Calabozo inválido! Has sido liberado.")
                );
                player.sendMessage(errorMsg);
            }
        }
    }
}