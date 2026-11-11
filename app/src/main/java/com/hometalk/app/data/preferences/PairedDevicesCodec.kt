package com.hometalk.app.data.preferences

import com.hometalk.app.domain.model.NetworkDefaults
import com.hometalk.app.domain.model.PairedDeviceRecord
import org.json.JSONArray
import org.json.JSONObject

object PairedDevicesCodec {
    fun encode(records: List<PairedDeviceRecord>): String {
        val array = JSONArray()
        records.forEach { record ->
            array.put(
                JSONObject()
                    .put("id", record.id)
                    .put("name", record.name)
                    .put("hostAddress", record.hostAddress)
                    .put("controlPort", record.controlPort)
                    .put("pairedAtEpochMs", record.pairedAtEpochMs)
                    .put("lastSeenEpochMs", record.lastSeenEpochMs),
            )
        }
        return array.toString()
    }

    fun decode(raw: String?): List<PairedDeviceRecord> {
        if (raw.isNullOrBlank()) return emptyList()
        return try {
            val array = JSONArray(raw)
            buildList {
                for (index in 0 until array.length()) {
                    val json = array.getJSONObject(index)
                    add(
                        PairedDeviceRecord(
                            id = json.getString("id"),
                            name = json.optString("name"),
                            hostAddress = json.optString("hostAddress").takeIf { it.isNotBlank() },
                            controlPort = json.optInt("controlPort", NetworkDefaults.TCP_CONTROL_PORT),
                            pairedAtEpochMs = json.optLong("pairedAtEpochMs", 0L),
                            lastSeenEpochMs = json.optLong("lastSeenEpochMs")
                                .takeIf { json.has("lastSeenEpochMs") && !json.isNull("lastSeenEpochMs") },
                        ),
                    )
                }
            }
        } catch (_: Exception) {
            emptyList()
        }
    }
}
