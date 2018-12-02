//Copyright 2015 Ryan Hamshire
package me.ryanhamshire.AutomaticInventory;

import org.bukkit.ChatColor;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;

public class DataStore {
    private final static String dataLayerFolderPath = "plugins" + File.separator + "AutomaticInventory";
    final static String playerDataFolderPath = dataLayerFolderPath + File.separator + "PlayerData";
    private final static String messagesFilePath = dataLayerFolderPath + File.separator + "messages.yml";
    //in-memory cache for messages
    private HashMap<Messages, String> messages;

    public DataStore() {
        //ensure data folders exist
        File playerDataFolder = new File(playerDataFolderPath);
        if (!playerDataFolder.exists()) {
            playerDataFolder.mkdirs();
        }

        this.loadMessages();
    }

    private void loadMessages() {
        Messages[] messageIDs = Messages.values();
        this.messages = new HashMap<>();

        //initialize defaults
        //messages.put(Messages.NoManagedWorld, "The PopulationDensity plugin has not been properly configured.  Please update your config.yml to specify a world to manage.");
        messages.put(Messages.NoPermissionForFeature, "You don't have permission to use that feature.");
        messages.put(Messages.ChestSortEnabled, "Now auto-sorting any chests you use.");
        messages.put(Messages.ChestSortDisabled, "Stopped auto-sorting chests you use.");
        messages.put(Messages.InventorySortEnabled, "Now auto-sorting your personal inventory.");
        messages.put(Messages.InventorySortDisabled, "Stopped auto-sorting your personal inventory.");
        messages.put(Messages.AutoSortHelp, "Options are /AutoSort Chests and /AutoSort Inventory.");
        messages.put(Messages.AutoRefillEducation, "AutomaticInventory(AI) will auto-replace broken tools and depleted hotbar stacks from your inventory.");
        messages.put(Messages.InventorySortEducation, "AutomaticInventory(AI) will keep your inventory sorted.  Use /AutoSort to disable.");
        messages.put(Messages.ChestSortEducation3, "AutomaticInventory(AI) will sort the contents of chests you access.  Use /AutoSort to toggle.  TIP: Want some chests sorted but not others?  Chests with names including an asterisk (*) won't auto-sort.  You can rename any chest using an anvil.");
        messages.put(Messages.SuccessfulDeposit2, "Deposited {0} items.");
        messages.put(Messages.FailedDepositNoMatch, "No items deposited - none of your inventory items match items in that chest.");
        messages.put(Messages.QuickDepositAdvertisement3, "Want to deposit quickly from your hotbar?  Just pick a specific chest and sneak (hold shift) while hitting it.");
        messages.put(Messages.FailedDepositChestFull2, "That chest is full.");
        messages.put(Messages.SuccessfulDepositAll2, "Deposited {0} items into nearby chests.");
        messages.put(Messages.ChestLidBlocked, "That chest isn't accessible.");
        messages.put(Messages.DepositAllAdvertisement, "TIP: Instantly deposit all items from your inventory into all the right nearby boxes with /DepositAll!");

        //load the config file
        FileConfiguration config = YamlConfiguration.loadConfiguration(new File(messagesFilePath));
        FileConfiguration outConfig = new YamlConfiguration();

        //for each message ID
        for (Messages messageID : messageIDs) {
            //get default for this message
            String message = config.getString("messages." + messageID.name() + ".Text", messages.get(messageID));

            //read the message from the file, use default if necessary
            outConfig.set("messages." + messageID.name() + ".Text", message);

            //support formatting codes
            messages.put(messageID, ChatColor.translateAlternateColorCodes('&', message));
        }

        //save any changes
        try {
            outConfig.options().header("Use a YAML editor like NotepadPlusPlus to edit this file.  \nAfter editing, back up your changes before reloading the server in case you made a syntax error.  \nUse ampersands (&) for formatting codes, which are documented here: http://minecraft.gamepedia.com/Formatting_codes");
            outConfig.save(DataStore.messagesFilePath);
        } catch (IOException exception) {
            AutomaticInventory.logger.info("Unable to write to the configuration file at \"" + DataStore.messagesFilePath + "\"");
        }
    }

    public synchronized String getMessage(Messages messageID, String... args) {
        String message = messages.get(messageID);

        for (int i = 0; i < args.length; i++) {
            String param = args[i];
            message = message.replace("{" + i + "}", param);
        }

        return message;
    }
}
