package bounties;

import org.bukkit.Location;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import java.io.File;
import java.util.*;

public class DataManager {
    private final Main plugin;
    private FileConfiguration captivesData;
    private File dataFile;

    public DataManager(Main plugin) {
        this.plugin = plugin;
        this.dataFile = new File(plugin.getDataFolder(), "captives.yml");
        reloadData();
    }

    public void reloadData() {
        if (!dataFile.exists()) {
            plugin.saveResource("captives.yml", false); // Crea el archivo si no existe
        }
        captivesData = YamlConfiguration.loadConfiguration(dataFile);
    }

    public void saveData() {
        try {
            captivesData.save(dataFile);
        } catch (Exception e) {
            plugin.getLogger().severe("Error guardando captives.yml: " + e.getMessage());
        }
    }

    public boolean isCaptive(UUID uuid) {
        return captivesData.contains(uuid.toString());
    }

    // Cambiar el método addCaptive
    public void addCaptive(UUID uuid, int days, Location calabozo) {
        Map<String, Object> serializedLoc = calabozo.serialize();
        
        captivesData.set(uuid.toString() + ".days", Math.min(days, getMaxSentenceDays())); // Aplicar límite
        captivesData.set(uuid.toString() + ".totalTime", 0); // Tiempo acumulado en minutos
        captivesData.set(uuid.toString() + ".lastLogin", null); // Inicializar
        captivesData.set(uuid.toString() + ".location", serializedLoc);
        saveData();
    }

    // Método para obtener todos los UUIDs de cautivos
    public Set<UUID> getAllCaptives() {
        Set<UUID> uuids = new HashSet<>();
        for (String key : captivesData.getKeys(false)) {
            try {
                UUID uuid = UUID.fromString(key);
                uuids.add(uuid);
            } catch (IllegalArgumentException e) {
                plugin.getLogger().warning("UUID inválido en captives.yml: " + key);
            }
        }
        return uuids;
    }

    public void removeCaptive(UUID uuid) {
        captivesData.set(uuid.toString(), null);
        saveData();
    }

    public int getRemainingTime(UUID uuid) {
        int total = captivesData.getInt(uuid.toString() + ".totalTime", 0);
        long lastLogin = captivesData.getLong(uuid.toString() + ".lastLogin", 0);
        
        if (lastLogin > 0) {
            long elapsed = (System.currentTimeMillis() - lastLogin) / 60000; // Minutos desde última conexión
            total += elapsed;
        }
        return total;
    }


    // Método para actualizar tiempos al desconectar
    public void updateOnDisconnect(UUID uuid) {
        int current = getRemainingTime(uuid);
        captivesData.set(uuid.toString() + ".totalTime", current);
        captivesData.set(uuid.toString() + ".lastLogin", null);
        saveData();
    }

    // Método para registrar conexión
    public void updateOnLogin(UUID uuid) {
        captivesData.set(uuid.toString() + ".lastLogin", System.currentTimeMillis());
        saveData();
    }

    public int getMaxSentenceDays() {
        return plugin.getConfig().getInt("max-sentence-days", 10);
    }
        
    public int getDaysLeft(UUID uuid) {
        long startTime = captivesData.getLong(uuid.toString() + ".startTime");
        long elapsedMillis = System.currentTimeMillis() - startTime;
        int elapsedDays = (int) (elapsedMillis / 1200000); // Milisegundos en un día de Minecraft
        
        return captivesData.getInt(uuid.toString() + ".days", 0) - elapsedDays;
    }

    public Location getCalabozo(UUID uuid) {
        Map<String, Object> serializedLoc = (Map<String, Object>) captivesData.get(uuid.toString() + ".location");
        return serializedLoc != null ? Location.deserialize(serializedLoc) : null;
    }

    public Main getPlugin() {
        return plugin;
    }

}