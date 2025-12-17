package org.mcediagui

import org.bukkit.Bukkit
import org.bukkit.Location
import org.bukkit.plugin.java.JavaPlugin
import java.util.UUID
import java.util.concurrent.CompletableFuture

interface McediaStorage {
    fun loadAll(): CompletableFuture<List<McediaPlayer>>
    fun save(player: McediaPlayer): CompletableFuture<Boolean>
    fun delete(uuid: UUID): CompletableFuture<Boolean>
    fun getTemplates(playerUUID: UUID): CompletableFuture<List<McediaTemplate>>
    fun getNextTemplateId(playerUUID: UUID): CompletableFuture<Int?>
    fun saveTemplate(template: McediaTemplate): CompletableFuture<Boolean>
    fun deleteTemplate(playerUUID: UUID, templateId: Int): CompletableFuture<Boolean>
    fun addPendingOperation(op: PendingOperation)
    fun getPendingOperations(worldName: String, chunkX: Int, chunkZ: Int): CompletableFuture<List<PendingOperation>>
    fun removePendingOperation(uuid: UUID)
    fun close()
}

class SQLiteMcediaStorage(private val plugin: JavaPlugin) : McediaStorage {
    init { initTables() }

    private fun initTables() {
        DatabaseManager.getConnection()?.use { conn -> conn.createStatement().use { stmt ->
            stmt.executeUpdate("CREATE TABLE IF NOT EXISTS mcedia_players (uuid TEXT PRIMARY KEY, name TEXT NOT NULL, world TEXT NOT NULL, x REAL NOT NULL, y REAL NOT NULL, z REAL NOT NULL, yaw REAL NOT NULL, pitch REAL NOT NULL, video_url TEXT DEFAULT '', start_time TEXT DEFAULT '', scale REAL DEFAULT 1.0, volume INTEGER DEFAULT 100, max_volume_range REAL DEFAULT 10.0, hearing_range REAL DEFAULT 50.0, offset_x REAL DEFAULT 0.0, offset_y REAL DEFAULT 0.0, offset_z REAL DEFAULT 0.0, looping INTEGER DEFAULT 0, no_danmaku INTEGER DEFAULT 0, created_by TEXT NOT NULL, created_at INTEGER NOT NULL)")
            stmt.executeUpdate("CREATE TABLE IF NOT EXISTS mcedia_templates (id INTEGER NOT NULL, owner_uuid TEXT NOT NULL, name TEXT NOT NULL, scale REAL DEFAULT 1.0, volume INTEGER DEFAULT 100, max_volume_range REAL DEFAULT 10.0, hearing_range REAL DEFAULT 50.0, offset_x REAL DEFAULT 0.0, offset_y REAL DEFAULT 0.0, offset_z REAL DEFAULT 0.0, looping INTEGER DEFAULT 0, no_danmaku INTEGER DEFAULT 0, created_at INTEGER NOT NULL, PRIMARY KEY (id, owner_uuid))")
            stmt.executeUpdate("CREATE TABLE IF NOT EXISTS mcedia_pending_ops (uuid TEXT PRIMARY KEY, operation_type TEXT NOT NULL, world_name TEXT NOT NULL, x REAL NOT NULL, y REAL NOT NULL, z REAL NOT NULL)")
        }}
    }

    override fun loadAll(): CompletableFuture<List<McediaPlayer>> = CompletableFuture.supplyAsync {
        val players = mutableListOf<McediaPlayer>()
        try { DatabaseManager.getConnection()?.use { conn -> conn.prepareStatement("SELECT * FROM mcedia_players").use { stmt ->
            val rs = stmt.executeQuery()
            while (rs.next()) { Bukkit.getWorld(rs.getString("world"))?.let { world ->
                players.add(McediaPlayer(UUID.fromString(rs.getString("uuid")), rs.getString("name"), Location(world, rs.getDouble("x"), rs.getDouble("y"), rs.getDouble("z"), rs.getFloat("yaw"), rs.getFloat("pitch")), rs.getString("video_url") ?: "", rs.getString("start_time") ?: "", rs.getDouble("scale"), rs.getInt("volume"), rs.getDouble("max_volume_range"), rs.getDouble("hearing_range"), rs.getDouble("offset_x"), rs.getDouble("offset_y"), rs.getDouble("offset_z"), rs.getInt("looping") == 1, rs.getInt("no_danmaku") == 1, UUID.fromString(rs.getString("created_by")), rs.getLong("created_at")))
            }}
        }}} catch (_: Exception) {}
        players
    }

    override fun save(player: McediaPlayer): CompletableFuture<Boolean> = CompletableFuture.supplyAsync {
        try { DatabaseManager.getConnection()?.use { conn -> conn.prepareStatement("INSERT OR REPLACE INTO mcedia_players (uuid, name, world, x, y, z, yaw, pitch, video_url, start_time, scale, volume, max_volume_range, hearing_range, offset_x, offset_y, offset_z, looping, no_danmaku, created_by, created_at) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)").use { stmt ->
            stmt.setString(1, player.uuid.toString()); stmt.setString(2, player.name); stmt.setString(3, player.location.world?.name ?: "world")
            stmt.setDouble(4, player.location.x); stmt.setDouble(5, player.location.y); stmt.setDouble(6, player.location.z)
            stmt.setFloat(7, player.location.yaw); stmt.setFloat(8, player.location.pitch); stmt.setString(9, player.videoUrl); stmt.setString(10, player.startTime)
            stmt.setDouble(11, player.scale); stmt.setInt(12, player.volume); stmt.setDouble(13, player.maxVolumeRange); stmt.setDouble(14, player.hearingRange)
            stmt.setDouble(15, player.offsetX); stmt.setDouble(16, player.offsetY); stmt.setDouble(17, player.offsetZ)
            stmt.setInt(18, if (player.looping) 1 else 0); stmt.setInt(19, if (player.noDanmaku) 1 else 0)
            stmt.setString(20, player.createdBy.toString()); stmt.setLong(21, player.createdAt); stmt.executeUpdate()
        }}; true } catch (_: Exception) { false }
    }

    override fun delete(uuid: UUID): CompletableFuture<Boolean> = CompletableFuture.supplyAsync {
        try { DatabaseManager.getConnection()?.use { conn -> conn.prepareStatement("DELETE FROM mcedia_players WHERE uuid = ?").use { it.setString(1, uuid.toString()); it.executeUpdate() }}; true } catch (_: Exception) { false }
    }

    override fun getTemplates(playerUUID: UUID): CompletableFuture<List<McediaTemplate>> = CompletableFuture.supplyAsync {
        val templates = mutableListOf<McediaTemplate>()
        try { DatabaseManager.getConnection()?.use { conn -> conn.prepareStatement("SELECT * FROM mcedia_templates WHERE owner_uuid = ? ORDER BY id").use { stmt ->
            stmt.setString(1, playerUUID.toString()); val rs = stmt.executeQuery()
            while (rs.next()) { templates.add(McediaTemplate(rs.getInt("id"), UUID.fromString(rs.getString("owner_uuid")), rs.getString("name"), rs.getDouble("scale"), rs.getInt("volume"), rs.getDouble("max_volume_range"), rs.getDouble("hearing_range"), rs.getDouble("offset_x"), rs.getDouble("offset_y"), rs.getDouble("offset_z"), rs.getInt("looping") == 1, rs.getInt("no_danmaku") == 1, rs.getLong("created_at"))) }
        }}} catch (_: Exception) {}
        templates
    }

    override fun getNextTemplateId(playerUUID: UUID): CompletableFuture<Int?> = CompletableFuture.supplyAsync {
        try { DatabaseManager.getConnection()?.use { conn -> conn.prepareStatement("SELECT id FROM mcedia_templates WHERE owner_uuid = ?").use { stmt ->
            stmt.setString(1, playerUUID.toString()); val rs = stmt.executeQuery(); val used = mutableSetOf<Int>()
            while (rs.next()) { used.add(rs.getInt("id")) }
            for (i in 1..7) { if (i !in used) return@supplyAsync i }
        }}; null } catch (_: Exception) { null }
    }

    override fun saveTemplate(template: McediaTemplate): CompletableFuture<Boolean> = CompletableFuture.supplyAsync {
        try { DatabaseManager.getConnection()?.use { conn -> conn.prepareStatement("INSERT OR REPLACE INTO mcedia_templates (id, owner_uuid, name, scale, volume, max_volume_range, hearing_range, offset_x, offset_y, offset_z, looping, no_danmaku, created_at) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)").use { stmt ->
            stmt.setInt(1, template.id); stmt.setString(2, template.ownerUuid.toString()); stmt.setString(3, template.name)
            stmt.setDouble(4, template.scale); stmt.setInt(5, template.volume); stmt.setDouble(6, template.maxVolumeRange); stmt.setDouble(7, template.hearingRange)
            stmt.setDouble(8, template.offsetX); stmt.setDouble(9, template.offsetY); stmt.setDouble(10, template.offsetZ)
            stmt.setInt(11, if (template.looping) 1 else 0); stmt.setInt(12, if (template.noDanmaku) 1 else 0); stmt.setLong(13, template.createdAt); stmt.executeUpdate()
        }}; true } catch (_: Exception) { false }
    }

    override fun deleteTemplate(playerUUID: UUID, templateId: Int): CompletableFuture<Boolean> = CompletableFuture.supplyAsync {
        try { DatabaseManager.getConnection()?.use { conn -> conn.prepareStatement("DELETE FROM mcedia_templates WHERE owner_uuid = ? AND id = ?").use { it.setString(1, playerUUID.toString()); it.setInt(2, templateId); it.executeUpdate() }}; true } catch (_: Exception) { false }
    }

    override fun addPendingOperation(op: PendingOperation) { CompletableFuture.runAsync {
        try { DatabaseManager.getConnection()?.use { conn -> conn.prepareStatement("INSERT OR REPLACE INTO mcedia_pending_ops (uuid, operation_type, world_name, x, y, z) VALUES (?, ?, ?, ?, ?, ?)").use {
            it.setString(1, op.uuid.toString()); it.setString(2, op.operationType.name); it.setString(3, op.worldName); it.setDouble(4, op.x); it.setDouble(5, op.y); it.setDouble(6, op.z); it.executeUpdate()
        }}} catch (_: Exception) {}
    }}

    override fun getPendingOperations(worldName: String, chunkX: Int, chunkZ: Int): CompletableFuture<List<PendingOperation>> = CompletableFuture.supplyAsync {
        val ops = mutableListOf<PendingOperation>()
        try { val minX = chunkX * 16.0; val maxX = minX + 16; val minZ = chunkZ * 16.0; val maxZ = minZ + 16
            DatabaseManager.getConnection()?.use { conn -> conn.prepareStatement("SELECT * FROM mcedia_pending_ops WHERE world_name = ? AND x >= ? AND x < ? AND z >= ? AND z < ?").use { stmt ->
                stmt.setString(1, worldName); stmt.setDouble(2, minX); stmt.setDouble(3, maxX); stmt.setDouble(4, minZ); stmt.setDouble(5, maxZ)
                val rs = stmt.executeQuery(); while (rs.next()) { ops.add(PendingOperation(UUID.fromString(rs.getString("uuid")), PendingOperationType.valueOf(rs.getString("operation_type")), rs.getString("world_name"), rs.getDouble("x"), rs.getDouble("y"), rs.getDouble("z"))) }
            }}
        } catch (_: Exception) {}
        ops
    }

    override fun removePendingOperation(uuid: UUID) { CompletableFuture.runAsync {
        try { DatabaseManager.getConnection()?.use { conn -> conn.prepareStatement("DELETE FROM mcedia_pending_ops WHERE uuid = ?").use { it.setString(1, uuid.toString()); it.executeUpdate() }}} catch (_: Exception) {}
    }}

    override fun close() {}
}
