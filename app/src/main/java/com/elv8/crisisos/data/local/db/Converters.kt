package com.elv8.crisisos.data.local.db

import androidx.room.TypeConverter
import com.elv8.crisisos.domain.model.MessageStatus
import com.elv8.crisisos.domain.model.MessageType
import com.elv8.crisisos.domain.model.ThreatLevel
import com.elv8.crisisos.domain.model.SupplyType
import com.elv8.crisisos.domain.model.RequestStatus

class Converters {
    @TypeConverter
    fun fromThreatLevel(value: ThreatLevel): String {
        return value.name
    }

    @TypeConverter
    fun toThreatLevel(value: String): ThreatLevel {
        return try {
            ThreatLevel.valueOf(value)
        } catch (e: Exception) {
            ThreatLevel.UNKNOWN
        }
    }

    @TypeConverter
    fun fromMessageStatus(value: MessageStatus): String {
        return value.name
    }

    @TypeConverter
    fun toMessageStatus(value: String): MessageStatus {
        return try {
            MessageStatus.valueOf(value)
        } catch (e: Exception) {
            MessageStatus.FAILED
        }
    }

    @TypeConverter
    fun fromMessageType(value: MessageType): String {
        return value.name
    }

    @TypeConverter
    fun toMessageType(value: String): MessageType {
        return try {
            MessageType.valueOf(value)
        } catch (e: Exception) {
            MessageType.SYSTEM
        }
    }

    @TypeConverter
    fun fromSupplyType(value: SupplyType): String {
        return value.name
    }

    @TypeConverter
    fun toSupplyType(value: String): SupplyType {
        return try {
            SupplyType.valueOf(value)
        } catch (e: Exception) {
            SupplyType.EMERGENCY
        }
    }

    @TypeConverter
    fun fromRequestStatus(value: RequestStatus): String {
        return value.name
    }

    @TypeConverter
    fun toRequestStatus(value: String): RequestStatus {
        return try {
            RequestStatus.valueOf(value)
        } catch (e: Exception) {
            RequestStatus.EXPIRED
        }
    }
}
