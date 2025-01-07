package bounties;

import org.bukkit.Location;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class Main extends JavaPlugin {

    private List<String> blockedCommands;
    private final Map<UUID, Location> capturedPlayers = new HashMap<>();
    private final Map<UUID, UUID> killers = new HashMap<>();

    // Getter para capturedPlayers
    public Map<UUID, Location> getCapturedPlayers() {
        return capturedPlayers;
    }

    // Getter para killers
    public Map<UUID, UUID> getKillers() {
        return killers;
    }

    public int getMaxSentenceDays() {
        return getConfig().getInt("max-sentence-days", 10); // Valor predeterminado: 10 días
    }
    
    @Override
    public void onEnable() {
        saveDefaultConfig();
        blockedCommands = getConfig().getStringList("blocked-commands");
    
        // Este bloque no estaba correctamente cerrado
        getServer().getPluginManager().registerEvents(new CaptureListener(this), this);
        getLogger().info("Plugin de captura activado.");
    }

    @Override
    public void onDisable() {
        getLogger().info("Plugin de captura desactivado.");
    }

    public List<String> getBlockedCommands() {
        return blockedCommands;
    }
}
