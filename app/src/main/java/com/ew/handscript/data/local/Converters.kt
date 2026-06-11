/**
 * 文件名: Converters.kt
 * 负责Agent: Agent-D (Android开发)
 * 所属模块: data/local
 * 最后修改: 2026-06-09
 * 版本: 0.4.2-wiki
 * 
 * 功能说明: Converters功能实现
 * 关键约束: 华为Mate30兼容，包体积<50MB
 */

package com.ew.handscript.data.local

import androidx.room.TypeConverter

/**
 * Room类型转换器
 *
 * 负责Kotlin类型与SQLite存储类型之间的双向转换。
 */
class Converters {
    @TypeConverter
    fun fromStringList(value: String): List<String> {
        return if (value.isEmpty()) emptyList() else value.split(",")
    }

    @TypeConverter
    fun toStringList(list: List<String>): String {
        return list.joinToString(",")
    }

    @TypeConverter
    fun fromIntList(value: String?): List<Int>? {
        return value?.let {
            if (it.isEmpty()) emptyList() else it.split(",").map { s -> s.toInt() }
        }
    }

    @TypeConverter
    fun toIntList(list: List<Int>?): String? {
        return list?.joinToString(",")
    }

    @TypeConverter
    fun fromProcessingStatus(status: ProcessingStatus): String = status.name

    @TypeConverter
    fun toProcessingStatus(value: String): ProcessingStatus =
        ProcessingStatus.valueOf(value)
}
