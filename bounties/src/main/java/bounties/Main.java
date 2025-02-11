package bounties;


import org.bukkit.plugin.java.JavaPlugin;

import bounties.listeners.BookListener;
import bounties.listeners.CommandListener;
import bounties.listeners.ConnectionListener;
import bounties.listeners.PlayerListener;

public class Main extends JavaPlugin {
    private DataManager dataManager;
    private BountyManager bountyManager;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        this.dataManager = new DataManager(this);
        this.bountyManager = new BountyManager(this, dataManager);
        bountyManager.startSentenceScheduler(); // Nueva línea
        
        
        // Registrar eventos
        getServer().getPluginManager().registerEvents(new BookListener(this, bountyManager), this);
        getServer().getPluginManager().registerEvents(new PlayerListener(bountyManager), this);
        getServer().getPluginManager().registerEvents(new CommandListener(dataManager), this);
        
        getLogger().info("Bounties activado!");
        getServer().getPluginManager().registerEvents(new ConnectionListener(dataManager), this);
    }

    @Override
    public void onDisable() {
        dataManager.saveData();
        getLogger().info("Bounties desactivado!");
    }
}