package com.lossydragon.bluetoothcomms

import java.util.HashMap;

object GattAttributes {
    private val attributes = HashMap<String, String>()
    var HM_RX_TX = "0000ffe1-0000-1000-8000-00805f9b34fb"

    init {
        // Sample Services.
        attributes["0000ffe0-0000-1000-8000-00805f9b34fb"] = "HM 10 Serial"
        attributes["00001800-0000-1000-8000-00805f9b34fb"] = "Device Information Service"
        // Sample Characteristics.
        attributes[HM_RX_TX] = "RX/TX data"
        attributes["00002a29-0000-1000-8000-00805f9b34fb"] = "Manufacturer Name String"
    }

    fun lookup(uuid: String, defaultName: String): String {
        return attributes[uuid] ?: defaultName
    }
}