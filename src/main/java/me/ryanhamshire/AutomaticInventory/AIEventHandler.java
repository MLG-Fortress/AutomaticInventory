//Copyright 2015 Ryan Hamshire

package me.ryanhamshire.AutomaticInventory;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.block.*;
import org.bukkit.entity.Entity;
import org.bukkit.entity.HumanEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.minecart.StorageMinecart;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockDamageEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.entity.ProjectileLaunchEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.event.player.*;
import org.bukkit.inventory.*;
import org.bukkit.inventory.meta.Damageable;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.projectiles.ProjectileSource;

import java.util.ArrayList;
import java.util.Comparator;

class AIEventHandler implements Listener {
    static void sortPlayerIfEnabled(Inventory inventory) {
        new InventorySorter(inventory, 9).run();
    }

    static boolean isSortableChestInventory(Inventory inventory) {
        if (inventory == null) return false;

        InventoryType inventoryType = inventory.getType();
        if (inventoryType != InventoryType.CHEST && inventoryType != InventoryType.ENDER_CHEST && inventoryType != InventoryType.SHULKER_BOX)
            return false;

        String name = inventory.getName();
        if (name != null && name.contains("*")) return false;

        InventoryHolder holder = inventory.getHolder();
        return holder instanceof Chest || holder instanceof DoubleChest || holder instanceof StorageMinecart || holder instanceof ShulkerBox;
    }

    private EquipmentSlot getSlotWithItemStack(PlayerInventory inventory, ItemStack brokenItem) {
        if (inventory.getItemInMainHand().isSimilar(brokenItem)) {
            return EquipmentSlot.HAND;
        }
        if (inventory.getItemInOffHand().isSimilar(brokenItem)) {
            return EquipmentSlot.OFF_HAND;
        }

        return null;
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.MONITOR)

    public void onToolBreak(PlayerItemBreakEvent event) {
        Player player = event.getPlayer();
        PlayerInventory inventory = player.getInventory();
        EquipmentSlot slot = this.getSlotWithItemStack(inventory, event.getBrokenItem());

        tryRefillStackInHand(player, slot, false);
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.MONITOR)

    public void onBlockPlace(BlockPlaceEvent event) {
        Player player = event.getPlayer();
        tryRefillStackInHand(player, event.getHand(), true);
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.MONITOR)

    public void onConsumeItem(PlayerItemConsumeEvent event) {
        Player player = event.getPlayer();
        PlayerInventory inventory = player.getInventory();
        EquipmentSlot slot = this.getSlotWithItemStack(inventory, event.getItem());
        tryRefillStackInHand(player, slot, true);
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.MONITOR)

    public void onProjectileLaunch(ProjectileLaunchEvent event) {
        ProjectileSource source = event.getEntity().getShooter();
        if (!(source instanceof Player)) return;

        Player player = (Player) source;
        tryRefillStackInHand(player, EquipmentSlot.HAND, false);
    }

    private void tryRefillStackInHand(Player player, EquipmentSlot slot, boolean dataValueMatters) {
        if (slot == null) return;

        ItemStack stack;
        int slotIndex;
        if (slot == EquipmentSlot.HAND) {
            stack = player.getInventory().getItemInMainHand();
            slotIndex = player.getInventory().getHeldItemSlot();
        } else if (slot == EquipmentSlot.OFF_HAND) {
            stack = player.getInventory().getItemInOffHand();
            slotIndex = 40;
        } else {
            return;
        }

        if (AutomaticInventory.instance.config_noAutoRefillIDs.contains(stack.getType())) return;
        if (!dataValueMatters || stack.getAmount() == 1) {
            PlayerInventory inventory = player.getInventory();
            AutomaticInventory.instance.getServer().getScheduler().scheduleSyncDelayedTask(
                    AutomaticInventory.instance,
                    new AutoRefillHotBarTask(inventory, slotIndex, stack.clone()),
                    2L);
        }
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.MONITOR)

    public void onBlockDamage(BlockDamageEvent event) {
        Player player = event.getPlayer();
        if (!player.isSneaking()) return;

        Block clickedBlock = event.getBlock();
        if (clickedBlock == null) return;
        if (!(clickedBlock.getState() instanceof Container)) return;

        PlayerInteractEvent fakeEvent = AutomaticInventory.instance.new FakePlayerInteractEvent(player, Action.RIGHT_CLICK_BLOCK, player.getInventory().getItemInMainHand(), clickedBlock, BlockFace.EAST);
        Bukkit.getServer().getPluginManager().callEvent(fakeEvent);
        if (fakeEvent.isCancelled()) return;

        InventoryHolder chest = (InventoryHolder) clickedBlock.getState();
        Inventory chestInventory = chest.getInventory();
        PlayerInventory playerInventory = player.getInventory();

        event.setCancelled(true);

        Material aboveBlockID = clickedBlock.getRelative(BlockFace.UP).getType();
        if (AutomaticInventory.preventsChestOpen(aboveBlockID)) {
            AutomaticInventory.sendMessage(player, TextMode.Err, Messages.ChestLidBlocked);
            return;
        }

        DepositRecord deposits = AutomaticInventory.depositMatching(playerInventory, chestInventory, true);

        //send confirmation message to player with counts deposited.  if none deposited, give instructions on how to set up the chest.
        if (deposits.destinationFull && deposits.totalItems == 0) {
            AutomaticInventory.sendMessage(player, TextMode.Err, Messages.FailedDepositChestFull2);
        } else if (deposits.totalItems == 0) {
            AutomaticInventory.sendMessage(player, TextMode.Info, Messages.FailedDepositNoMatch);
        } else {
            AutomaticInventory.sendMessage(player, Messages.SuccessfulDeposit2, String.valueOf(deposits.totalItems));

            //make a note that quick deposit was used so that player will not be bothered with advertisement messages again.
            PlayerData playerData = PlayerData.FromPlayer(player);
            if (!playerData.isUsedQuickDeposit()) {
                playerData.setUsedQuickDeposit();
            }
        }
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.MONITOR)

    public void onInventoryOpen(InventoryOpenEvent event) {
        Inventory bottomInventory = event.getView().getBottomInventory();
        if (bottomInventory == null) return;
        if (bottomInventory.getType() != InventoryType.PLAYER) return;

        HumanEntity holder = ((PlayerInventory) bottomInventory).getHolder();
        if (!(holder instanceof Player)) return;

        Player player = (Player) holder;
        PlayerData playerData = PlayerData.FromPlayer(player);
        sortPlayerIfEnabled(bottomInventory);

        if (!player.isSneaking()) {
            Inventory topInventory = event.getView().getTopInventory();
            if (!isSortableChestInventory(topInventory)) return;

            InventorySorter sorter = new InventorySorter(topInventory, 0);
            Bukkit.getServer().getScheduler().scheduleSyncDelayedTask(AutomaticInventory.instance, sorter, 1L);
        }
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.MONITOR)

    public void onInventoryClose(InventoryCloseEvent event) {
        Inventory bottomInventory = event.getView().getBottomInventory();
        if (bottomInventory == null) return;
        if (bottomInventory.getType() != InventoryType.PLAYER) return;

        HumanEntity holder = ((PlayerInventory) bottomInventory).getHolder();
        if (!(holder instanceof Player)) return;

        Player player = (Player) holder;
        PlayerData playerData = PlayerData.FromPlayer(player);

        sortPlayerIfEnabled(bottomInventory);
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.MONITOR)

    public void onPickupItem(EntityPickupItemEvent event) {
        Entity entity = event.getEntity();
        if (!(entity instanceof Player)) return;
        Player player = (Player) entity;

        PlayerData playerData = PlayerData.FromPlayer(player);
        if (playerData.firstEmptySlot >= 0) return;

        PlayerInventory inventory = player.getInventory();
        int firstEmpty = inventory.firstEmpty();
        if (firstEmpty < 9) return;
        playerData.firstEmptySlot = firstEmpty;
        PickupSortTask task = new PickupSortTask(playerData, inventory);
        Bukkit.getServer().getScheduler().scheduleSyncDelayedTask(AutomaticInventory.instance, task, 100L);

    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.MONITOR)
    void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        PlayerData.Preload(player);
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.MONITOR)
    void onPlayerQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        PlayerData.FromPlayer(player).saveChanges();
    }

    class AutoRefillHotBarTask implements Runnable {
        private PlayerInventory targetInventory;
        private int slotToRefill;
        private ItemStack stackToReplace;

        AutoRefillHotBarTask(PlayerInventory targetInventory, int slotToRefill, ItemStack stackToReplace) {
            this.targetInventory = targetInventory;
            this.slotToRefill = slotToRefill;
            this.stackToReplace = stackToReplace;
        }

        @Override
        public void run() {
            ItemStack currentStack = this.targetInventory.getItem(this.slotToRefill);
            if (currentStack != null) return;

            ItemStack bestMatchStack = null;
            int bestMatchSlot = -1;
            int bestMatchStackSize = Integer.MAX_VALUE;
            for (int i = 0; i < 36; i++) {
                ItemStack itemInSlot = this.targetInventory.getItem(i);
                if (itemInSlot == null) continue;
                if (itemInSlot.isSimilar(this.stackToReplace)) {
                    int stackSize = itemInSlot.getAmount();
                    if (stackSize < bestMatchStackSize) {
                        bestMatchStack = itemInSlot;
                        bestMatchSlot = i;
                        bestMatchStackSize = stackSize;
                    }

                    if (bestMatchStackSize == 1) break;
                }
            }

            if (bestMatchStack == null) return;

            this.targetInventory.setItem(this.slotToRefill, bestMatchStack);
            this.targetInventory.clear(bestMatchSlot);
        }
    }
}

class PickupSortTask implements Runnable {
    private PlayerData playerData;
    private Inventory playerInventory;

    PickupSortTask(PlayerData playerData, Inventory playerInventory) {
        this.playerData = playerData;
        this.playerInventory = playerInventory;
    }

    @Override
    public void run() {
        if (this.playerData.firstEmptySlot == playerInventory.firstEmpty()) {
            this.playerData.firstEmptySlot = -1;
            return;
        }

        AIEventHandler.sortPlayerIfEnabled(this.playerInventory);

        this.playerData.firstEmptySlot = -1;
    }
}

class InventorySorter implements Runnable {
    private Inventory inventory;
    private int startIndex;

    InventorySorter(Inventory inventory, int startIndex) {
        this.inventory = inventory;
        this.startIndex = startIndex;
    }

    @Override
    public void run() {
        ArrayList<ItemStack> stacks = new ArrayList<>();
        ItemStack[] contents = this.inventory.getContents();
        int inventorySize = contents.length;
        if (this.inventory.getType() == InventoryType.PLAYER) inventorySize = Math.min(contents.length, 36);
        for (int i = this.startIndex; i < inventorySize; i++) {
            ItemStack stack = contents[i];
            if (stack != null) {
                stacks.add(stack);
            }
        }

        stacks.sort(new StackComparator());
        for (int i = 1; i < stacks.size(); i++) {
            ItemStack prevStack = stacks.get(i - 1);
            ItemStack thisStack = stacks.get(i);
            if (prevStack.isSimilar(thisStack)) {
                if (prevStack.getAmount() < prevStack.getMaxStackSize()) {
                    int moveCount = Math.min(prevStack.getMaxStackSize() - prevStack.getAmount(), thisStack.getAmount());
                    prevStack.setAmount(prevStack.getAmount() + moveCount);
                    thisStack.setAmount(thisStack.getAmount() - moveCount);
                    if (thisStack.getAmount() == 0) {
                        stacks.remove(i);
                        i--;
                    }
                }
            }
        }

        int i;
        for (i = 0; i < stacks.size(); i++) {
            this.inventory.setItem(i + this.startIndex, stacks.get(i));
        }

        for (i = i + this.startIndex; i < inventorySize; i++) {
            this.inventory.clear(i);
        }
    }

    private class StackComparator implements Comparator<ItemStack> {
        @Override
        public int compare(ItemStack a, ItemStack b) {
            int result = Integer.compare(b.getMaxStackSize(), a.getMaxStackSize());
            if (result != 0) return result;

            result = b.getType().compareTo(a.getType());
            if (result != 0) return result;

            ItemMeta metaA = a.getItemMeta();
            ItemMeta metaB = b.getItemMeta();

            result = Integer.compare(metaA instanceof Damageable ? ((Damageable) metaA).getDamage() : 0,
                    metaB instanceof Damageable ? ((Damageable) metaB).getDamage() : 0);
            if (result != 0) return result;

            result = Integer.compare(b.getAmount(), a.getAmount());
            return result;
        }
    }
}


