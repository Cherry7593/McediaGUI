package org.mcediagui

import net.kyori.adventure.text.Component
import org.bukkit.Bukkit
import org.bukkit.Location
import org.bukkit.Material
import org.bukkit.entity.ArmorStand
import org.bukkit.entity.EntityType
import org.bukkit.inventory.ItemStack
import org.bukkit.inventory.meta.BookMeta
import org.bukkit.plugin.java.JavaPlugin
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

class McediaManager(private val plugin: JavaPlugin) {

    private var enabled: Boolean = true
    private var playerNamePrefix: String = "mcedia"
    private var defaultScale: Double = 1.0
    private var defaultVolume: Int = 100
    private var maxPlayers: Int = 50
    private var triggerItem: Material? = null

    private val players: ConcurrentHashMap<UUID, McediaPlayer> = ConcurrentHashMap()
    private var storage: McediaStorage? = null
    private val messages: MutableMap<String, String> = mutableMapOf()

    init {
        loadConfig()
        initStorage()
        loadPlayersFromDatabase()
    }

    private fun initStorage() {
        if (!enabled) return
        storage = SQLiteMcediaStorage(plugin)
    }

    private fun loadPlayersFromDatabase() {
        storage?.loadAll()?.thenAccept { loadedPlayers ->
            players.clear()
            loadedPlayers.forEach { player -> players[player.uuid] = player }
        }
    }

    fun loadConfig() {
        val config = plugin.config
        enabled = config.getBoolean("enabled", true)
        playerNamePrefix = config.getString("player-name-prefix", "mcedia") ?: "mcedia"
        defaultScale = config.getDouble("default-scale", 1.0)
        defaultVolume = config.getInt("default-volume", 100)
        maxPlayers = config.getInt("max-players", 50)

        val triggerItemStr = config.getString("trigger-item", "") ?: ""
        triggerItem = if (triggerItemStr.isBlank() || triggerItemStr.equals("AIR", ignoreCase = true)) {
            null
        } else {
            try { Material.valueOf(triggerItemStr.uppercase()) } catch (_: Exception) { null }
        }

        val prefix = config.getString("messages.prefix", "&6[McediaGUI]&r ") ?: "&6[McediaGUI]&r "
        messages.clear()
        config.getConfigurationSection("messages")?.getKeys(false)?.forEach { key ->
            if (key != "prefix") {
                messages[key] = (config.getString("messages.$key") ?: "").replace("%prefix%", prefix)
            }
        }
    }

    fun createPlayerSync(location: Location, name: String, createdBy: UUID): McediaPlayer? {
        if (!enabled || players.size >= maxPlayers) return null
        val world = location.world ?: return null
        val armorStand = world.spawnEntity(location, EntityType.ARMOR_STAND) as ArmorStand

        armorStand.customName(Component.text("$playerNamePrefix:$name"))
        armorStand.isCustomNameVisible = false
        armorStand.setGravity(false)
        armorStand.isVisible = false
        armorStand.isSmall = false
        armorStand.setArms(false)
        armorStand.isMarker = false
        armorStand.isInvulnerable = false
        armorStand.equipment.setItemInMainHand(null)
        armorStand.equipment.setItemInOffHand(null)

        val player = McediaPlayer(uuid = armorStand.uniqueId, name = name, location = location.clone(),
            scale = defaultScale, volume = defaultVolume, createdBy = createdBy)
        players[armorStand.uniqueId] = player
        storage?.save(player)
        return player
    }

    fun deletePlayer(uuid: UUID): Boolean {
        val player = players.remove(uuid) ?: return false
        storage?.delete(uuid)
        val entity = Bukkit.getEntity(uuid)
        if (entity is ArmorStand) {
            entity.scheduler.run(plugin, { _ -> entity.remove() }, null)
        } else {
            storage?.addPendingOperation(PendingOperation(uuid, PendingOperationType.DELETE,
                player.location.world?.name ?: "world", player.location.x, player.location.y, player.location.z))
        }
        return true
    }

    fun setVideo(uuid: UUID, videoUrl: String, startTime: String = ""): Boolean {
        val player = players[uuid] ?: return false
        player.videoUrl = videoUrl
        player.startTime = startTime
        storage?.save(player)
        updateArmorStandBooks(uuid)
        return true
    }

    fun updatePlayerConfig(uuid: UUID, config: McediaPlayer.() -> Unit): Boolean {
        val player = players[uuid] ?: return false
        player.config()
        storage?.save(player)
        updateArmorStandBooks(uuid)
        return true
    }

    private fun updateArmorStandBooks(uuid: UUID) {
        val player = players[uuid] ?: return
        val entity = Bukkit.getEntity(uuid) as? ArmorStand ?: return
        entity.scheduler.run(plugin, { _ ->
            if (player.videoUrl.isNotEmpty()) {
                entity.equipment.setItemInMainHand(createBook(player.generateMainHandBookContent(), "Video"))
            }
            entity.equipment.setItemInOffHand(createBook(player.generateOffHandBookContent(), "Config"))
        }, null)
    }

    private fun createBook(pages: List<String>, title: String): ItemStack {
        val book = ItemStack(Material.WRITABLE_BOOK)
        val meta = book.itemMeta as BookMeta
        pages.forEach { meta.addPages(Component.text(it)) }
        meta.displayName(Component.text("$title:${System.currentTimeMillis()}"))
        book.itemMeta = meta
        return book
    }

    fun processPendingOperations(worldName: String, chunkX: Int, chunkZ: Int) {
        storage?.getPendingOperations(worldName, chunkX, chunkZ)?.thenAccept { ops ->
            ops.forEach { op ->
                if (op.operationType == PendingOperationType.DELETE) {
                    (Bukkit.getEntity(op.uuid) as? ArmorStand)?.let { it.scheduler.run(plugin, { _ -> it.remove() }, null) }
                    storage?.removePendingOperation(op.uuid)
                }
            }
        }
    }

    fun getPlayer(uuid: UUID): McediaPlayer? = players[uuid]
    fun getAllPlayers(): List<McediaPlayer> = players.values.toList()
    
    /**
     * 添加播放器到缓存并保存到数据库（用于手动放置的播放器自动注册）
     */
    fun addPlayer(player: McediaPlayer): Boolean {
        if (!enabled || players.size >= maxPlayers) return false
        if (players.containsKey(player.uuid)) return false
        players[player.uuid] = player
        storage?.save(player)
        return true
    }
    fun findPlayerByName(name: String): McediaPlayer? = players.values.find { it.name.equals(name, ignoreCase = true) }
    fun scanExistingPlayers() {
        Bukkit.getWorlds().forEach { world ->
            world.entities.filterIsInstance<ArmorStand>().forEach { armorStand ->
                val customName = armorStand.customName()?.let {
                    net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer.plainText().serialize(it)
                } ?: ""
                if (customName.startsWith(playerNamePrefix) && !players.containsKey(armorStand.uniqueId)) {
                    val name = customName.removePrefix("$playerNamePrefix:").ifEmpty { customName }
                    players[armorStand.uniqueId] = McediaPlayer(armorStand.uniqueId, name, armorStand.location, createdBy = UUID.randomUUID())
                }
            }
        }
    }

    fun isEnabled(): Boolean = enabled
    fun getPlayerNamePrefix(): String = playerNamePrefix
    fun getTriggerItem(): Material? = triggerItem
    fun getPlugin(): JavaPlugin = plugin
    fun getTemplates(playerUUID: UUID): List<McediaTemplate> = storage?.getTemplates(playerUUID)?.join() ?: emptyList()

    fun saveAsTemplate(playerUUID: UUID, mcediaPlayer: McediaPlayer): Int? {
        val nextId = storage?.getNextTemplateId(playerUUID)?.join() ?: return null
        val template = McediaTemplate(nextId, playerUUID, mcediaPlayer.name, mcediaPlayer.scale, mcediaPlayer.volume,
            mcediaPlayer.maxVolumeRange, mcediaPlayer.hearingRange, mcediaPlayer.offsetX, mcediaPlayer.offsetY,
            mcediaPlayer.offsetZ, mcediaPlayer.looping, mcediaPlayer.noDanmaku)
        return if (storage?.saveTemplate(template)?.join() == true) nextId else null
    }

    fun applyTemplate(mcediaPlayerUUID: UUID, template: McediaTemplate): Boolean {
        val p = players[mcediaPlayerUUID] ?: return false
        p.scale = template.scale; p.volume = template.volume; p.maxVolumeRange = template.maxVolumeRange
        p.hearingRange = template.hearingRange; p.offsetX = template.offsetX; p.offsetY = template.offsetY
        p.offsetZ = template.offsetZ; p.looping = template.looping; p.noDanmaku = template.noDanmaku
        storage?.save(p); updateArmorStandBooks(mcediaPlayerUUID)
        return true
    }

    fun deleteTemplate(playerUUID: UUID, templateId: Int): Boolean = storage?.deleteTemplate(playerUUID, templateId)?.join() ?: false
    fun shutdown() { storage?.close(); storage = null; players.clear() }
}

fun McediaPlayer.generateMainHandBookContent(): List<String> = listOf(videoUrl)
fun McediaPlayer.generateOffHandBookContent(): List<String> {
    val sb = StringBuilder()
    sb.append("scale:$scale\n").append("volume:$volume\n").append("maxVolumeRange:$maxVolumeRange\n")
    sb.append("hearingRange:$hearingRange\n").append("offsetX:$offsetX\n").append("offsetY:$offsetY\n").append("offsetZ:$offsetZ\n")
    if (looping) sb.append("loop:true\n"); if (noDanmaku) sb.append("noDanmaku:true\n")
    if (startTime.isNotEmpty()) sb.append("startTime:$startTime\n")
    return listOf(sb.toString())
}
