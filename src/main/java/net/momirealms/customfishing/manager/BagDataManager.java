/*
 *  Copyright (C) <2022> <XiaoMoMi>
 *
 *  This program is free software: you can redistribute it and/or modify
 *  it under the terms of the GNU General Public License as published by
 *  the Free Software Foundation, either version 3 of the License, or
 *  any later version.
 *
 *  This program is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public License
 *  along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package net.momirealms.customfishing.manager;

import com.comphenix.protocol.events.PacketContainer;
import com.comphenix.protocol.reflect.StructureModifier;
import com.comphenix.protocol.wrappers.WrappedChatComponent;
import de.tr7zw.changeme.nbtapi.NBTCompound;
import de.tr7zw.changeme.nbtapi.NBTItem;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.serializer.gson.GsonComponentSerializer;
import net.momirealms.customfishing.CustomFishing;
import net.momirealms.customfishing.data.PlayerBagData;
import net.momirealms.customfishing.listener.InventoryListener;
import net.momirealms.customfishing.listener.JoinQuitListener;
import net.momirealms.customfishing.listener.WindowPacketListener;
import net.momirealms.customfishing.object.Function;
import net.momirealms.customfishing.util.AdventureUtil;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.event.HandlerList;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitTask;

import java.util.HashSet;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class BagDataManager extends Function {

    private final ConcurrentHashMap<UUID, PlayerBagData> dataMap;
    private final HashSet<PlayerBagData> tempData;
    private final InventoryListener inventoryListener;
    private final WindowPacketListener windowPacketListener;
    private final JoinQuitListener joinQuitListener;
    private final BukkitTask timerSave;
    private final CustomFishing plugin;

    public BagDataManager(CustomFishing plugin) {
        this.plugin = plugin;
        this.dataMap = new ConcurrentHashMap<>();
        this.tempData = new HashSet<>();

        this.inventoryListener = new InventoryListener(this);
        this.windowPacketListener = new WindowPacketListener(this);
        this.joinQuitListener = new JoinQuitListener(this);

        this.timerSave = Bukkit.getScheduler().runTaskTimerAsynchronously(plugin, () -> {
            DataManager dataManager = plugin.getDataManager();
            for (PlayerBagData playerBagData : dataMap.values()) {
                dataManager.getDataStorageInterface().saveBagData(playerBagData);
            }
            AdventureUtil.consoleMessage("[CustomFishing] Fishing bag data saving for " + dataMap.size() + " online players...");
        }, 12000, 12000);
    }

    @Override
    public void load() {
        if (!ConfigManager.enableFishingBag) return;
        Bukkit.getPluginManager().registerEvents(inventoryListener, plugin);
        Bukkit.getPluginManager().registerEvents(joinQuitListener, plugin);
        CustomFishing.getProtocolManager().addPacketListener(windowPacketListener);
    }

    @Override
    public void unload() {
        HandlerList.unregisterAll(inventoryListener);
        HandlerList.unregisterAll(joinQuitListener);
        CustomFishing.getProtocolManager().removePacketListener(windowPacketListener);
    }

    public void disable() {
        unload();
        for (PlayerBagData playerBagData : dataMap.values()) {
            DataManager dataManager = CustomFishing.getInstance().getDataManager();
            dataManager.getDataStorageInterface().saveBagData(playerBagData);
        }
        dataMap.clear();
        tempData.clear();
        timerSave.cancel();
    }

    public PlayerBagData getPlayerBagData(UUID uuid) {
        return dataMap.get(uuid);
    }

    public void openFishingBag(Player viewer, OfflinePlayer ownerOffline) {
        Player owner = ownerOffline.getPlayer();
        if (owner == null) {
            Inventory inventory = plugin.getDataManager().getDataStorageInterface().loadBagData(ownerOffline);
            PlayerBagData playerBagData = new PlayerBagData(ownerOffline, inventory);
            tempData.add(playerBagData);
            viewer.openInventory(inventory);
        }
        else {
            PlayerBagData playerBagData = dataMap.get(owner.getUniqueId());
            if (playerBagData == null) {
                AdventureUtil.consoleMessage("<red>[CustomFishing] Bag data is not loaded for player " + owner.getName());
            }
            else {
                tryOpen(owner, viewer, playerBagData);
            }
        }
    }

    @Override
    public void onQuit(Player player) {
        PlayerBagData playerBagData = dataMap.remove(player.getUniqueId());
        if (playerBagData != null) {
            Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
                plugin.getDataManager().getDataStorageInterface().saveBagData(playerBagData);
            });
        }
    }

    @Override
    public void onJoin(Player player) {
        Bukkit.getScheduler().runTaskLaterAsynchronously(plugin, () -> {
            readData(player);
        }, 20);
    }

    public void readData(Player player) {
        if (player == null || !player.isOnline()) return;
        Inventory inventory = plugin.getDataManager().getDataStorageInterface().loadBagData(player);
        if (inventory != null) {
            PlayerBagData playerBagData = new PlayerBagData(player, inventory);
            dataMap.put(player.getUniqueId(), playerBagData);
        }
    }

    @Override
    public void onWindowTitlePacketSend(PacketContainer packet, Player receiver) {
        StructureModifier<WrappedChatComponent> wrappedChatComponentStructureModifier = packet.getChatComponents();
        WrappedChatComponent component = wrappedChatComponentStructureModifier.getValues().get(0);
        String windowTitleJson = component.getJson();
        if (windowTitleJson.startsWith("{\"text\":\"{CustomFishing_Bag_")) {
            String player = windowTitleJson.substring(28, windowTitleJson.length() - 3);
            String text = ConfigManager.fishingBagTitle.replace("{player}", player);
            wrappedChatComponentStructureModifier.write(0,
                    WrappedChatComponent.fromJson(
                            GsonComponentSerializer.gson().serialize(
                                    MiniMessage.miniMessage().deserialize(
                                            AdventureUtil.replaceLegacy(text)
                                    )
                            )
                    )
            );
        }
    }

    @Override
    public void onClickInventory(InventoryClickEvent event) {
        final Player player = (Player) event.getWhoClicked();
        PlayerBagData playerBagData = dataMap.get(player.getUniqueId());
        if (playerBagData == null) return;
        if (playerBagData.getInventory() == event.getInventory()) {
            ItemStack currentItem = event.getCurrentItem();
            if (currentItem == null || currentItem.getType() == Material.AIR) return;
            NBTItem nbtItem = new NBTItem(currentItem);
            if (!nbtItem.hasTag("CustomFishing") && !ConfigManager.bagWhiteListItems.contains(currentItem.getType())) {
                event.setCancelled(true);
                return;
            }
            NBTCompound nbtCompound = nbtItem.getCompound("CustomFishing");
            if (nbtCompound == null) {
                event.setCancelled(true);
                return;
            }
            String type = nbtCompound.getString("type");
            if (!ConfigManager.canStoreLoot && type.equals("loot")) {
                event.setCancelled(true);
            }
        }
    }

    @Override
    public void onCloseInventory(InventoryCloseEvent event) {
        final Player player = (Player) event.getPlayer();
        Inventory inventory = event.getInventory();
        PlayerBagData playerBagData = dataMap.get(player.getUniqueId());
        if (playerBagData != null) {
            if (inventory == playerBagData.getInventory()) {
                for (ItemStack itemStack : event.getInventory().getContents()) {
                    if (itemStack == null || itemStack.getType() == Material.AIR) continue;
                    NBTItem nbtItem = new NBTItem(itemStack);
                    if (nbtItem.hasTag("CustomFishing") || ConfigManager.bagWhiteListItems.contains(itemStack.getType())) continue;
                    player.getInventory().addItem(itemStack.clone());
                    itemStack.setAmount(0);
                }
                return;
            }
            for (PlayerBagData temp : tempData) {
                if (temp.getInventory() == inventory) {
                    tempData.remove(temp);
                    Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
                        plugin.getDataManager().getDataStorageInterface().saveBagData(temp);
                    });
                }
            }
        }
    }

    public void tryOpen(Player owner, Player viewer, PlayerBagData playerBagData) {
        Inventory inventory = playerBagData.getInventory();
        int size = 1;
        for (int i = 6; i > 1; i--) {
            if (owner.hasPermission("fishingbag.rows." + i)) {
                size = i;
                break;
            }
        }
        if (size * 9 != inventory.getSize()) {
            ItemStack[] itemStacks = playerBagData.getInventory().getContents();
            Inventory newInv = Bukkit.createInventory(null, size * 9, "{CustomFishing_Bag_" + owner.getName() + "}");
            newInv.setContents(itemStacks);
            playerBagData.setInventory(newInv);
            viewer.openInventory(newInv);
        }
        else {
            viewer.openInventory(inventory);
        }
    }
}