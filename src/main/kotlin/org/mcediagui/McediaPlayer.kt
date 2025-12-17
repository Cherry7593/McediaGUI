package org.mcediagui

import org.bukkit.Location
import java.util.UUID

data class McediaPlayer(
    val uuid: UUID,
    var name: String,
    var location: Location,
    var videoUrl: String = "",
    var startTime: String = "",
    var scale: Double = 1.0,
    var volume: Int = 100,
    var maxVolumeRange: Double = 10.0,
    var hearingRange: Double = 50.0,
    var offsetX: Double = 0.0,
    var offsetY: Double = 0.0,
    var offsetZ: Double = 0.0,
    var looping: Boolean = false,
    var noDanmaku: Boolean = false,
    val createdBy: UUID,
    val createdAt: Long = System.currentTimeMillis()
)

data class McediaTemplate(
    val id: Int,
    val ownerUuid: UUID,
    val name: String,
    val scale: Double,
    val volume: Int,
    val maxVolumeRange: Double,
    val hearingRange: Double,
    val offsetX: Double,
    val offsetY: Double,
    val offsetZ: Double,
    val looping: Boolean,
    val noDanmaku: Boolean,
    val createdAt: Long = System.currentTimeMillis()
)

enum class VideoPlatform(val displayName: String) {
    BILIBILI("哔哩哔哩"),
    DOUYIN("抖音"),
    YHDM("樱花动漫"),
    DIRECT("直链"),
    UNKNOWN("未知");

    companion object {
        fun detect(url: String): VideoPlatform {
            return when {
                url.contains("bilibili.com") || url.contains("b23.tv") -> BILIBILI
                url.contains("douyin.com") || url.contains("v.douyin") -> DOUYIN
                url.contains("yhdm") || url.contains("yinghuacd") -> YHDM
                url.isNotEmpty() -> DIRECT
                else -> UNKNOWN
            }
        }
    }
}

enum class PendingOperationType { DELETE }

data class PendingOperation(
    val uuid: UUID,
    val operationType: PendingOperationType,
    val worldName: String,
    val x: Double,
    val y: Double,
    val z: Double
)
