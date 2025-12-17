package org.mcediagui

import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer
import org.bukkit.command.Command
import org.bukkit.command.CommandExecutor
import org.bukkit.command.CommandSender
import org.bukkit.command.TabCompleter
import org.bukkit.entity.Player

class McediaCommand(
    private val plugin: McediaGUIPlugin,
    private val manager: McediaManager,
    private val gui: McediaGUI
) : CommandExecutor, TabCompleter {

    private val s = LegacyComponentSerializer.legacyAmpersand()

    override fun onCommand(sender: CommandSender, command: Command, label: String, args: Array<out String>): Boolean {
        if (!manager.isEnabled()) { sender.sendMessage(s.deserialize("&c功能已禁用")); return true }
        if (args.isEmpty()) return showHelp(sender)
        return when (args[0].lowercase()) {
            "create" -> handleCreate(sender, args)
            "list" -> handleList(sender)
            "delete" -> handleDelete(sender, args)
            "scan" -> handleScan(sender)
            "reload" -> handleReload(sender)
            "gui" -> handleGui(sender)
            else -> showHelp(sender)
        }
    }

    private fun handleCreate(sender: CommandSender, args: Array<out String>): Boolean {
        if (sender !is Player) { sender.sendMessage(s.deserialize("&c只能由玩家执行")); return true }
        if (!sender.hasPermission("mcedia.create")) { sender.sendMessage(s.deserialize("&c没有权限")); return true }
        if (args.size < 2) { sender.sendMessage(s.deserialize("&c用法: /mcedia create <名称>")); return true }
        val name = args.drop(1).joinToString(" ")
        sender.scheduler.run(manager.getPlugin(), { _ ->
            val player = manager.createPlayerSync(sender.location, name, sender.uniqueId)
            sender.sendMessage(s.deserialize(if (player != null) "&a创建成功: ${player.name}" else "&c创建失败"))
        }, null)
        return true
    }

    private fun handleList(sender: CommandSender): Boolean {
        if (!sender.hasPermission("mcedia.admin")) { sender.sendMessage(s.deserialize("&c没有权限")); return true }
        val players = manager.getAllPlayers()
        if (players.isEmpty()) { sender.sendMessage(s.deserialize("&7没有播放器")); return true }
        sender.sendMessage(s.deserialize("&6===== 播放器列表 ====="))
        players.forEachIndexed { i, p ->
            val status = if (p.videoUrl.isEmpty()) "&c未配置" else "&a${VideoPlatform.detect(p.videoUrl).displayName}"
            sender.sendMessage(s.deserialize("&f${i+1}. &e${p.name} &7- $status"))
        }
        return true
    }

    private fun handleDelete(sender: CommandSender, args: Array<out String>): Boolean {
        if (!sender.hasPermission("mcedia.delete")) { sender.sendMessage(s.deserialize("&c没有权限")); return true }
        if (args.size < 2) { sender.sendMessage(s.deserialize("&c用法: /mcedia delete <名称>")); return true }
        val name = args.drop(1).joinToString(" ")
        val player = manager.findPlayerByName(name)
        if (player == null) { sender.sendMessage(s.deserialize("&c未找到: $name")); return true }
        sender.sendMessage(s.deserialize(if (manager.deletePlayer(player.uuid)) "&a已删除: $name" else "&c删除失败"))
        return true
    }

    private fun handleScan(sender: CommandSender): Boolean {
        if (!sender.hasPermission("mcedia.admin")) { sender.sendMessage(s.deserialize("&c没有权限")); return true }
        manager.scanExistingPlayers()
        sender.sendMessage(s.deserialize("&a扫描完成，共 ${manager.getAllPlayers().size} 个"))
        return true
    }

    private fun handleReload(sender: CommandSender): Boolean {
        if (!sender.hasPermission("mcedia.reload")) { sender.sendMessage(s.deserialize("&c没有权限")); return true }
        plugin.reload()
        sender.sendMessage(s.deserialize("&a配置已重载"))
        return true
    }

    private fun handleGui(sender: CommandSender): Boolean {
        if (sender !is Player) { sender.sendMessage(s.deserialize("&c只能由玩家执行")); return true }
        if (!sender.hasPermission("mcedia.admin")) { sender.sendMessage(s.deserialize("&c没有权限")); return true }
        gui.openMainMenu(sender)
        return true
    }

    private fun showHelp(sender: CommandSender): Boolean {
        sender.sendMessage(s.deserialize("&6===== McediaGUI ====="))
        sender.sendMessage(s.deserialize("&e/mcedia create <名称> &7- 创建"))
        sender.sendMessage(s.deserialize("&e/mcedia list &7- 列表"))
        sender.sendMessage(s.deserialize("&e/mcedia delete <名称> &7- 删除"))
        sender.sendMessage(s.deserialize("&e/mcedia gui &7- 管理界面"))
        sender.sendMessage(s.deserialize("&e/mcedia reload &7- 重载"))
        sender.sendMessage(s.deserialize("&7蹲下+右键播放器可编辑"))
        return true
    }

    override fun onTabComplete(sender: CommandSender, command: Command, alias: String, args: Array<out String>): List<String> {
        if (!manager.isEnabled()) return emptyList()
        return when (args.size) {
            1 -> listOf("create", "list", "delete", "scan", "gui", "reload").filter { it.startsWith(args[0], true) }
            2 -> if (args[0].equals("delete", true)) manager.getAllPlayers().map { it.name }.filter { it.startsWith(args[1], true) } else emptyList()
            else -> emptyList()
        }
    }
}
