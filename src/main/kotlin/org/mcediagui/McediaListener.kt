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
            manager.getPlayer(entity.uniqueId)?.let { p ->
                event.player.scheduler.run(plugin, { _ -> gui.openPlayerEdit(event.player, p) }, null)
            }
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
