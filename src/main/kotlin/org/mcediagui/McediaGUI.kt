package org.mcediagui

import net.kyori.adventure.text.Component
import org.bukkit.Bukkit
import org.bukkit.Material
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.inventory.InventoryClickEvent
import org.bukkit.inventory.Inventory
import org.bukkit.inventory.InventoryHolder
import org.bukkit.inventory.ItemStack
import org.bukkit.plugin.java.JavaPlugin
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

class McediaGUI(private val plugin: JavaPlugin, private val manager: McediaManager) : Listener {

    private val playerGUIState: ConcurrentHashMap<UUID, GUIState> = ConcurrentHashMap()

    enum class GUIType { MAIN_MENU, PLAYER_LIST, PLAYER_EDIT, VIDEO_SELECT, AUDIO_CONFIG, DISPLAY_CONFIG, CONFIRM_DELETE }
    data class GUIState(val type: GUIType, val page: Int = 0, val editingPlayerUUID: UUID? = null, val tempData: MutableMap<String, Any> = mutableMapOf())
    class McediaHolder(val type: GUIType, val page: Int = 0, val editingPlayerUUID: UUID? = null) : InventoryHolder {
        override fun getInventory(): Inventory = throw UnsupportedOperationException()
    }

    fun openMainMenu(player: Player) {
        if (!player.hasPermission("mcedia.admin")) return
        val inv = Bukkit.createInventory(McediaHolder(GUIType.MAIN_MENU), 27, Component.text("§6McediaGUI"))
        inv.setItem(11, item(Material.ARMOR_STAND, "§a创建播放器"))
        inv.setItem(13, item(Material.BOOK, "§b播放器列表 (${manager.getAllPlayers().size})"))
        inv.setItem(15, item(Material.ENDER_EYE, "§e扫描播放器"))
        fillBorder(inv)
        playerGUIState[player.uniqueId] = GUIState(GUIType.MAIN_MENU)
        player.openInventory(inv)
    }

    fun openPlayerList(player: Player, page: Int = 0) {
        val inv = Bukkit.createInventory(McediaHolder(GUIType.PLAYER_LIST, page), 54, Component.text("§6播放器列表"))
        val players = manager.getAllPlayers()
        val start = page * 45
        for (i in start until minOf(start + 45, players.size)) {
            val p = players[i]
            val status = if (p.videoUrl.isEmpty()) "§c未设置" else "§a${VideoPlatform.detect(p.videoUrl).displayName}"
            inv.setItem(i - start, item(Material.ARMOR_STAND, "§f${p.name}", "§7视频: $status", "§e点击编辑"))
        }
        if (page > 0) inv.setItem(45, item(Material.ARROW, "§e上一页"))
        inv.setItem(49, item(Material.BARRIER, "§c返回"))
        if ((page + 1) * 45 < players.size) inv.setItem(53, item(Material.ARROW, "§e下一页"))
        playerGUIState[player.uniqueId] = GUIState(GUIType.PLAYER_LIST, page)
        player.openInventory(inv)
    }

    fun openPlayerEdit(player: Player, mp: McediaPlayer) {
        val inv = Bukkit.createInventory(McediaHolder(GUIType.PLAYER_EDIT, editingPlayerUUID = mp.uuid), 45, Component.text("§6编辑: ${mp.name}"))
        val platform = VideoPlatform.detect(mp.videoUrl)
        inv.setItem(4, item(Material.ARMOR_STAND, "§f${mp.name}"))
        inv.setItem(10, item(Material.MUSIC_DISC_CAT, "§b设置视频", "§7当前: ${if (mp.videoUrl.isEmpty()) "§c未设置" else "§a${platform.displayName}"}"))
        inv.setItem(12, item(Material.SPYGLASS, "§e显示配置", "§7缩放: ${mp.scale}", "§7偏移: ${mp.offsetX},${mp.offsetY},${mp.offsetZ}"))
        inv.setItem(14, item(Material.NOTE_BLOCK, "§d音频配置", "§7音量: ${mp.volume}", "§7范围: ${mp.hearingRange}"))
        inv.setItem(16, item(Material.NAME_TAG, "§a标签", "§7循环: ${if (mp.looping) "§a开" else "§c关"}", "§7弹幕: ${if (mp.noDanmaku) "§c关" else "§a开"}", "§e左键循环/右键弹幕"))
        if (player.hasPermission("mcedia.admin")) inv.setItem(19, item(Material.ENDER_PEARL, "§b传送"))
        inv.setItem(21, item(Material.WRITABLE_BOOK, "§a保存模板"))
        if (player.hasPermission("mcedia.admin")) inv.setItem(23, item(Material.BARRIER, "§c返回"))
        inv.setItem(25, item(Material.TNT, "§c删除"))
        val templates = manager.getTemplates(player.uniqueId).associateBy { it.id }
        for (i in 1..7) {
            val t = templates[i]
            inv.setItem(27 + i, if (t != null) item(Material.FILLED_MAP, "§6模板#$i", "§e左键应用/右键删除") else item(Material.MAP, "§8空模板#$i"))
        }
        for (slot in listOf(0,1,2,3,5,6,7,8,9,17,18,26,27,35,36,37,38,39,40,41,42,43,44)) inv.setItem(slot, item(Material.GRAY_STAINED_GLASS_PANE, " "))
        playerGUIState[player.uniqueId] = GUIState(GUIType.PLAYER_EDIT, editingPlayerUUID = mp.uuid)
        player.openInventory(inv)
    }

    fun openVideoSelect(player: Player, mp: McediaPlayer) {
        val inv = Bukkit.createInventory(McediaHolder(GUIType.VIDEO_SELECT, editingPlayerUUID = mp.uuid), 36, Component.text("§6设置视频"))
        inv.setItem(10, item(Material.RED_CONCRETE, "§c哔哩哔哩"))
        inv.setItem(12, item(Material.PINK_CONCRETE, "§d抖音"))
        inv.setItem(14, item(Material.MAGENTA_CONCRETE, "§5樱花动漫"))
        inv.setItem(16, item(Material.CYAN_CONCRETE, "§b直链"))
        inv.setItem(27, item(Material.ARROW, "§c返回"))
        fillBorder(inv)
        playerGUIState[player.uniqueId] = GUIState(GUIType.VIDEO_SELECT, editingPlayerUUID = mp.uuid)
        player.openInventory(inv)
    }

    fun openAudioConfig(player: Player, mp: McediaPlayer) {
        val inv = Bukkit.createInventory(McediaHolder(GUIType.AUDIO_CONFIG, editingPlayerUUID = mp.uuid), 36, Component.text("§6音频配置"))
        inv.setItem(10, item(Material.RED_STAINED_GLASS_PANE, "§c-10")); inv.setItem(11, item(Material.ORANGE_STAINED_GLASS_PANE, "§6-1"))
        inv.setItem(13, item(Material.NOTE_BLOCK, "§f音量: ${mp.volume}"))
        inv.setItem(15, item(Material.LIME_STAINED_GLASS_PANE, "§a+1")); inv.setItem(16, item(Material.GREEN_STAINED_GLASS_PANE, "§2+10"))
        inv.setItem(19, item(Material.RED_STAINED_GLASS_PANE, "§c-50")); inv.setItem(22, item(Material.BELL, "§f范围: ${mp.hearingRange}")); inv.setItem(25, item(Material.GREEN_STAINED_GLASS_PANE, "§a+50"))
        inv.setItem(31, item(Material.ARROW, "§c返回")); fillBorder(inv)
        playerGUIState[player.uniqueId] = GUIState(GUIType.AUDIO_CONFIG, editingPlayerUUID = mp.uuid)
        player.openInventory(inv)
    }

    fun openDisplayConfig(player: Player, mp: McediaPlayer) {
        val inv = Bukkit.createInventory(McediaHolder(GUIType.DISPLAY_CONFIG, editingPlayerUUID = mp.uuid), 36, Component.text("§6显示配置"))
        inv.setItem(10, item(Material.RED_STAINED_GLASS_PANE, "§c-0.5")); inv.setItem(13, item(Material.SPYGLASS, "§f缩放: ${mp.scale}")); inv.setItem(16, item(Material.GREEN_STAINED_GLASS_PANE, "§a+0.5"))
        inv.setItem(19, item(Material.RED_STAINED_GLASS_PANE, "§cX-")); inv.setItem(20, item(Material.ORANGE_STAINED_GLASS_PANE, "§6Y-"))
        inv.setItem(22, item(Material.COMPASS, "§f偏移: ${mp.offsetX},${mp.offsetY},${mp.offsetZ}"))
        inv.setItem(24, item(Material.LIME_STAINED_GLASS_PANE, "§aY+")); inv.setItem(25, item(Material.GREEN_STAINED_GLASS_PANE, "§2X+"))
        inv.setItem(28, item(Material.BLUE_STAINED_GLASS_PANE, "§9Z-")); inv.setItem(30, item(Material.CYAN_STAINED_GLASS_PANE, "§bZ+"))
        inv.setItem(31, item(Material.ARROW, "§c返回")); fillBorder(inv)
        playerGUIState[player.uniqueId] = GUIState(GUIType.DISPLAY_CONFIG, editingPlayerUUID = mp.uuid)
        player.openInventory(inv)
    }

    fun openConfirmDelete(player: Player, mp: McediaPlayer) {
        val inv = Bukkit.createInventory(McediaHolder(GUIType.CONFIRM_DELETE, editingPlayerUUID = mp.uuid), 27, Component.text("§c确认删除"))
        inv.setItem(11, item(Material.RED_WOOL, "§c确认删除")); inv.setItem(15, item(Material.GREEN_WOOL, "§a取消")); fillBorder(inv)
        playerGUIState[player.uniqueId] = GUIState(GUIType.CONFIRM_DELETE, editingPlayerUUID = mp.uuid)
        player.openInventory(inv)
    }

    @EventHandler
    fun onInventoryClick(event: InventoryClickEvent) {
        val holder = event.inventory.holder as? McediaHolder ?: return
        event.isCancelled = true
        val player = event.whoClicked as? Player ?: return
        val slot = event.rawSlot; if (slot < 0) return
        val right = event.isRightClick

        when (holder.type) {
            GUIType.MAIN_MENU -> when (slot) {
                11 -> { player.closeInventory(); player.sendMessage("§7输入播放器名称:"); playerGUIState[player.uniqueId] = GUIState(GUIType.MAIN_MENU, tempData = mutableMapOf("awaiting_input" to "create_name")) }
                13 -> openPlayerList(player)
                15 -> { manager.scanExistingPlayers(); player.sendMessage("§a扫描完成"); openMainMenu(player) }
            }
            GUIType.PLAYER_LIST -> when { slot < 45 -> manager.getAllPlayers().getOrNull(holder.page * 45 + slot)?.let { openPlayerEdit(player, it) }; slot == 45 -> openPlayerList(player, holder.page - 1); slot == 49 -> openMainMenu(player); slot == 53 -> openPlayerList(player, holder.page + 1) }
            GUIType.PLAYER_EDIT -> holder.editingPlayerUUID?.let { uuid -> manager.getPlayer(uuid)?.let { mp ->
                when (slot) {
                    10 -> openVideoSelect(player, mp); 12 -> openDisplayConfig(player, mp); 14 -> openAudioConfig(player, mp)
                    16 -> { manager.updatePlayerConfig(uuid) { if (right) noDanmaku = !noDanmaku else looping = !looping }; openPlayerEdit(player, manager.getPlayer(uuid)!!) }
                    19 -> { player.closeInventory(); player.teleportAsync(mp.location) }
                    21 -> { val id = manager.saveAsTemplate(player.uniqueId, mp); player.sendMessage(if (id != null) "§a保存模板#$id" else "§c模板已满"); openPlayerEdit(player, mp) }
                    23 -> openPlayerList(player); 25 -> openConfirmDelete(player, mp)
                    in 28..34 -> { val tid = slot - 27; manager.getTemplates(player.uniqueId).find { it.id == tid }?.let { t -> if (right) { manager.deleteTemplate(player.uniqueId, tid); player.sendMessage("§c删除模板") } else { manager.applyTemplate(uuid, t); player.sendMessage("§a应用模板") }; openPlayerEdit(player, manager.getPlayer(uuid)!!) } }
                    else -> {}
                }
            }}
            GUIType.VIDEO_SELECT -> when (slot) { 27 -> holder.editingPlayerUUID?.let { manager.getPlayer(it) }?.let { openPlayerEdit(player, it) }; 10,12,14,16 -> { player.closeInventory(); player.sendMessage("§7输入视频链接:"); playerGUIState[player.uniqueId] = GUIState(GUIType.VIDEO_SELECT, editingPlayerUUID = holder.editingPlayerUUID, tempData = mutableMapOf("awaiting_input" to "video_url")) } }
            GUIType.AUDIO_CONFIG -> holder.editingPlayerUUID?.let { uuid -> when (slot) { 10 -> manager.updatePlayerConfig(uuid) { volume = maxOf(0, volume - 10) }; 11 -> manager.updatePlayerConfig(uuid) { volume = maxOf(0, volume - 1) }; 15 -> manager.updatePlayerConfig(uuid) { volume = minOf(200, volume + 1) }; 16 -> manager.updatePlayerConfig(uuid) { volume = minOf(200, volume + 10) }; 19 -> manager.updatePlayerConfig(uuid) { hearingRange = maxOf(10.0, hearingRange - 50) }; 25 -> manager.updatePlayerConfig(uuid) { hearingRange += 50 }; 31 -> { manager.getPlayer(uuid)?.let { openPlayerEdit(player, it) }; return } }; manager.getPlayer(uuid)?.let { openAudioConfig(player, it) } }
            GUIType.DISPLAY_CONFIG -> holder.editingPlayerUUID?.let { uuid -> when (slot) { 10 -> manager.updatePlayerConfig(uuid) { scale = maxOf(0.1, scale - 0.5) }; 16 -> manager.updatePlayerConfig(uuid) { scale += 0.5 }; 19 -> manager.updatePlayerConfig(uuid) { offsetX -= 0.5 }; 20 -> manager.updatePlayerConfig(uuid) { offsetY -= 0.5 }; 24 -> manager.updatePlayerConfig(uuid) { offsetY += 0.5 }; 25 -> manager.updatePlayerConfig(uuid) { offsetX += 0.5 }; 28 -> manager.updatePlayerConfig(uuid) { offsetZ -= 0.5 }; 30 -> manager.updatePlayerConfig(uuid) { offsetZ += 0.5 }; 31 -> { manager.getPlayer(uuid)?.let { openPlayerEdit(player, it) }; return } }; manager.getPlayer(uuid)?.let { openDisplayConfig(player, it) } }
            GUIType.CONFIRM_DELETE -> when (slot) { 11 -> { holder.editingPlayerUUID?.let { manager.deletePlayer(it) }; player.sendMessage("§a已删除"); openPlayerList(player) }; 15 -> holder.editingPlayerUUID?.let { manager.getPlayer(it) }?.let { openPlayerEdit(player, it) } }
        }
    }

    fun handleChatInput(player: Player, message: String): Boolean {
        val state = playerGUIState[player.uniqueId] ?: return false
        val awaiting = state.tempData["awaiting_input"] as? String ?: return false
        when (awaiting) {
            "video_url" -> { state.editingPlayerUUID?.let { manager.setVideo(it, message); player.sendMessage("§a视频已设置"); player.scheduler.run(plugin, { _ -> manager.getPlayer(it)?.let { p -> openPlayerEdit(player, p) } }, null) } }
            "create_name" -> { player.scheduler.run(plugin, { _ -> val p = manager.createPlayerSync(player.location, message, player.uniqueId); if (p != null) { player.sendMessage("§a创建成功"); openPlayerEdit(player, p) } else player.sendMessage("§c创建失败") }, null) }
        }
        playerGUIState.remove(player.uniqueId); return true
    }

    private fun item(m: Material, name: String, vararg lore: String): ItemStack {
        val item = ItemStack(m); val meta = item.itemMeta
        meta.displayName(Component.text(name)); meta.lore(lore.filter { it.isNotEmpty() }.map { Component.text(it) })
        item.itemMeta = meta; return item
    }
    private fun fillBorder(inv: Inventory) { val rows = inv.size / 9; for (i in 0 until inv.size) { val r = i / 9; val c = i % 9; if ((r == 0 || r == rows - 1 || c == 0 || c == 8) && inv.getItem(i) == null) inv.setItem(i, item(Material.GRAY_STAINED_GLASS_PANE, " ")) } }
}
