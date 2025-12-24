package org.mcediagui

import org.bukkit.entity.ArmorStand
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener
import org.bukkit.event.entity.EntityDamageByEntityEvent
import org.bukkit.event.player.PlayerArmorStandManipulateEvent
import org.bukkit.event.player.PlayerInteractAtEntityEvent
import org.bukkit.event.world.ChunkLoadEvent
import org.bukkit.plugin.java.JavaPlugin

class McediaListener(
    private val plugin: JavaPlugin,
    private val manager: McediaManager,
    private val gui: McediaGUI
) : Listener {

    private val prefix: String get() = manager.getPlayerNamePrefix()

    @EventHandler(priority = EventPriority.HIGH)
    fun onPlayerInteractAtEntity(event: PlayerInteractAtEntityEvent) {
        if (!manager.isEnabled()) return
        val entity = event.rightClicked as? ArmorStand ?: return
        val customName = entity.customName()?.let {
            net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer.plainText().serialize(it)
        } ?: ""
        if (!customName.startsWith(prefix)) return

        if (event.player.isSneaking && event.player.hasPermission("mcedia.use")) {
            val itemInHand = event.player.inventory.itemInMainHand
            val triggerItem = manager.getTriggerItem()
            val canTrigger = if (triggerItem == null) itemInHand.type.isAir else itemInHand.type == triggerItem
            if (!canTrigger) return
            event.isCancelled = true
            
            var mcediaPlayer = manager.getPlayer(entity.uniqueId)
            
            // 如果不在缓存中，尝试自动注册（支持手动放置书本召唤的盔甲架）
            if (mcediaPlayer == null) {
                val playerName = customName.removePrefix("$prefix:").ifEmpty { customName }
                mcediaPlayer = McediaPlayer(
                    uuid = entity.uniqueId,
                    name = playerName,
                    location = entity.location,
                    createdBy = event.player.uniqueId
                )
                manager.addPlayer(mcediaPlayer)
                event.player.sendMessage("§6[Mcedia] §a已自动记录播放器: §f$playerName")
            }
            
            event.player.scheduler.run(plugin, { _ -> gui.openPlayerEdit(event.player, mcediaPlayer) }, null)
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    fun onArmorStandManipulate(event: PlayerArmorStandManipulateEvent) {
        if (!manager.isEnabled()) return
        val customName = event.rightClicked.customName()?.let {
            net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer.plainText().serialize(it)
        } ?: ""
        if (!customName.startsWith(prefix)) return

        if (event.player.isSneaking && event.player.hasPermission("mcedia.use")) {
            val itemInHand = event.player.inventory.itemInMainHand
            val triggerItem = manager.getTriggerItem()
            if ((triggerItem == null && itemInHand.type.isAir) || itemInHand.type == triggerItem) {
                event.isCancelled = true; return
            }
        }
        if (!event.player.hasPermission("mcedia.admin")) event.isCancelled = true
    }

    @EventHandler(priority = EventPriority.HIGH)
    fun onEntityDamage(event: EntityDamageByEntityEvent) {
        if (!manager.isEnabled()) return
        val entity = event.entity as? ArmorStand ?: return
        val customName = entity.customName()?.let {
            net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer.plainText().serialize(it)
        } ?: ""
        if (!customName.startsWith(prefix)) return

        val damager = event.damager as? Player ?: run { event.isCancelled = true; return }
        if (!damager.hasPermission("mcedia.delete")) {
            event.isCancelled = true
            damager.sendMessage("§c没有权限破坏播放器")
            return
        }
        manager.deletePlayer(entity.uniqueId)
    }

    @EventHandler
    fun onChunkLoad(event: ChunkLoadEvent) {
        if (!manager.isEnabled()) return
        manager.processPendingOperations(event.chunk.world.name, event.chunk.x, event.chunk.z)
    }
}
