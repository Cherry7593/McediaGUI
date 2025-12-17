package org.mcediagui

import org.bukkit.plugin.java.JavaPlugin
import java.io.File
import java.sql.Connection
import java.sql.DriverManager

object DatabaseManager {
    private var connection: Connection? = null
    private lateinit var plugin: JavaPlugin

    fun init(plugin: JavaPlugin) {
        this.plugin = plugin
        val dbFile = File(plugin.dataFolder, "mcediagui.db")
        plugin.dataFolder.mkdirs()
        connection = DriverManager.getConnection("jdbc:sqlite:${dbFile.absolutePath}")
        plugin.logger.info("数据库连接已建立")
    }

    fun getConnection(): Connection? {
        if (connection?.isClosed == true) {
            val dbFile = File(plugin.dataFolder, "mcediagui.db")
            connection = DriverManager.getConnection("jdbc:sqlite:${dbFile.absolutePath}")
        }
        return connection
    }

    fun close() {
        try {
            connection?.close()
        } catch (_: Exception) {}
    }
}
