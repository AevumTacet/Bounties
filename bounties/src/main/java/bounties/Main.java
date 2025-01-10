package bounties;

import org.bukkit.plugin.java.JavaPlugin;
import java.util.List;

public class Main extends JavaPlugin {

    private List<String> blockedCommands;

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
