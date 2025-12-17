package org.mcediagui

import org.bukkit.plugin.java.JavaPlugin

class McediaGUIPlugin : JavaPlugin() {

    lateinit var mcediaManager: McediaManager
        private set
    lateinit var mcediaGUI: McediaGUI
        private set

    override fun onEnable() {
        saveDefaultConfig()

        DatabaseManager.init(this)

        mcediaManager = McediaManager(this)
        mcediaGUI = McediaGUI(this, mcediaManager)

        val pm = server.pluginManager
        pm.registerEvents(mcediaGUI, this)
        pm.registerEvents(McediaListener(this, mcediaManager, mcediaGUI), this)
        pm.registerEvents(McediaChatListener(mcediaGUI), this)

        getCommand("mcedia")?.let { cmd ->
            val executor = McediaCommand(this, mcediaManager, mcediaGUI)
            cmd.setExecutor(executor)
            cmd.tabCompleter = executor
        }

        logger.info("========================================")
        logger.info("  McediaGUI v${description.version}")
        logger.info("  作者: Zvbj")
        logger.info("  已加载 ${mcediaManager.getAllPlayers().size} 个播放器")
        logger.info("  反馈: QQ 348913197")
        logger.info("  GitHub: github.com/Cherry7593/McediaGUI")
        logger.info("========================================")
    }

    override fun onDisable() {
        if (::mcediaManager.isInitialized) {
            mcediaManager.shutdown()
        }
        DatabaseManager.close()
        logger.info("McediaGUI 已关闭")
    }

    fun reload() {
        reloadConfig()
        mcediaManager.loadConfig()
        logger.info("配置已重载")
    }
}
