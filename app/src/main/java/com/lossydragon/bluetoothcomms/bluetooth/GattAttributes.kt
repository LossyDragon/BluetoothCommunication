/*
 * Copyright (C) 2013 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.lossydragon.bluetoothcomms.bluetooth

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