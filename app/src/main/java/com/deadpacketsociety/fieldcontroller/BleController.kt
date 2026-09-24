package com.deadpacketsociety.fieldcontroller

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.bluetooth.le.BluetoothLeScanner
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.content.Context
import android.os.Handler
import android.os.Looper
import java.util.UUID

class BleController(
    private val context: Context,
    private val events: Events
) {

    interface Events {
        fun connected(name: String)
        fun disconnected()
        fun status(text: String)
        fun log(text: String)
    }

    companion object {

        // ====================================================
        // DPS BLE UUIDs
        // ====================================================

        val DPS_SERVICE_UUID: UUID =
            UUID.fromString(
                "12345678-1234-1234-1234-1234567890AB"
            )

        val DPS_COMMAND_UUID: UUID =
            UUID.fromString(
                "12345678-1234-1234-1234-1234567890AC"
            )

        val DPS_STATUS_UUID: UUID =
            UUID.fromString(
                "12345678-1234-1234-1234-1234567890AD"
            )

        val DPS_CCCD_UUID: UUID =
            UUID.fromString(
                "00002902-0000-1000-8000-00805f9b34fb"
            )

        private const val DEVICE_NAME = "WD-Hub"

        private const val MAX_RX_BUFFER = 16384

        private const val RECONNECT_DELAY_MS = 2500L
    }

    // ========================================================
    // BLUETOOTH
    // ========================================================

    private val bluetoothManager =
        context.getSystemService(
            Context.BLUETOOTH_SERVICE
        ) as BluetoothManager

    private val bluetoothAdapter: BluetoothAdapter? =
        bluetoothManager.adapter

    private val handler =
        Handler(Looper.getMainLooper())

    // ========================================================
    // CONNECTION STATE
    // ========================================================

    private var bluetoothGatt: BluetoothGatt? = null

    private var commandCharacteristic:
            BluetoothGattCharacteristic? = null

    private var statusCharacteristic:
            BluetoothGattCharacteristic? = null

    private var scanner:
            BluetoothLeScanner? = null

    private var manuallyDisconnected = false

    private var reconnectPending = false

    // ========================================================
    // RECEIVE BUFFER
    // ========================================================

    private var rxBuffer =
        StringBuilder()


    // ========================================================
    // CONNECT BONDED DEVICE OR SCAN
    // ========================================================

    @SuppressLint("MissingPermission")
    fun connectBondedOrScan() {

        val adapter =
            bluetoothAdapter

        if (adapter == null) {

            events.log(
                "Bluetooth adapter unavailable"
            )

            return
        }

        if (!adapter.isEnabled) {

            events.log(
                "Bluetooth is disabled"
            )

            return
        }

        manuallyDisconnected = false

        events.log(
            "Looking for bonded WD-Hub..."
        )

        val bondedDevice =
            adapter.bondedDevices.firstOrNull {

                it.name == DEVICE_NAME
            }

        if (bondedDevice != null) {

            events.log(
                "Found bonded WD-Hub"
            )

            connect(
                bondedDevice
            )

            return
        }

        events.log(
            "WD-Hub not bonded; starting BLE scan"
        )

        startScan()
    }


    // ========================================================
    // START BLE SCAN
    // ========================================================

    @SuppressLint("MissingPermission")
    private fun startScan() {

        if (scanner != null) {

            events.log(
                "BLE scan already active"
            )

            return
        }

        val adapter =
            bluetoothAdapter

        if (adapter == null) {

            events.log(
                "Bluetooth adapter unavailable"
            )

            return
        }

        scanner =
            adapter.bluetoothLeScanner

        if (scanner == null) {

            events.log(
                "BLE scanner unavailable"
            )

            return
        }

        events.log(
            "Scanning for WD-Hub..."
        )

        scanner?.startScan(
            scanCallback
        )
    }


    // ========================================================
    // BLE SCAN CALLBACK
    // ========================================================

    private val scanCallback =
        object : ScanCallback() {

            @SuppressLint("MissingPermission")
            override fun onScanResult(
                callbackType: Int,
                result: ScanResult
            ) {

                val device =
                    result.device

                val name =
                    device.name

                if (
                    name == DEVICE_NAME
                ) {

                    events.log(
                        "Found WD-Hub: ${device.address}"
                    )

                    stopScan()

                    connect(
                        device
                    )
                }
            }

            override fun onScanFailed(
                errorCode: Int
            ) {

                scanner = null

                events.log(
                    "BLE scan failed: $errorCode"
                )
            }
        }


    // ========================================================
    // STOP BLE SCAN
    // ========================================================

    @SuppressLint("MissingPermission")
    private fun stopScan() {

        val activeScanner =
            scanner

        if (activeScanner != null) {

            try {

                activeScanner.stopScan(
                    scanCallback
                )

            } catch (_: Exception) {
            }
        }

        scanner = null
    }


    // ========================================================
    // CONNECT
    // ========================================================

    @SuppressLint("MissingPermission")
    private fun connect(
        device: BluetoothDevice
    ) {

        manuallyDisconnected = false

        reconnectPending = false

        rxBuffer =
            StringBuilder()

        try {
            bluetoothGatt?.close()
        } catch (_: Exception) {
        }

        bluetoothGatt = null

        commandCharacteristic = null

        statusCharacteristic = null

        events.log(
            "Connecting to ${device.name ?: DEVICE_NAME}..."
        )

        bluetoothGatt =
            device.connectGatt(
                context,
                false,
                gattCallback,
                BluetoothDevice.TRANSPORT_LE
            )
    }


    // ========================================================
    // SEND COMMAND
    // ========================================================

    @SuppressLint("MissingPermission")
    fun send(
        command: String
    ) {

        val gatt =
            bluetoothGatt

        val characteristic =
            commandCharacteristic

        if (
            gatt == null ||
            characteristic == null
        ) {

            events.log(
                "BLE send failed: not connected"
            )

            return
        }

        val value =
            command.toByteArray(
                Charsets.UTF_8
            )

        characteristic.writeType =
            BluetoothGattCharacteristic
                .WRITE_TYPE_NO_RESPONSE

        characteristic.value =
            value

        events.log(
            "APP -> HELTEC: $command"
        )

        val requested =
            gatt.writeCharacteristic(
                characteristic
            )

        events.log(
            "BLE write requested=$requested"
        )
    }


    // ========================================================
    // DISCONNECT
    // ========================================================

    @SuppressLint("MissingPermission")
    fun disconnect() {

        manuallyDisconnected = true

        reconnectPending = false

        stopScan()

        rxBuffer =
            StringBuilder()

        commandCharacteristic = null

        statusCharacteristic = null

        val gatt =
            bluetoothGatt

        bluetoothGatt = null

        if (gatt != null) {

            try {
                gatt.disconnect()
            } catch (_: Exception) {
            }

            try {
                gatt.close()
            } catch (_: Exception) {
            }
        }

        events.log(
            "BLE disconnected by app"
        )
    }


    // ========================================================
    // GATT CALLBACK
    // ========================================================

    private val gattCallback =
        object : BluetoothGattCallback() {

            @SuppressLint("MissingPermission")
            override fun onConnectionStateChange(
                gatt: BluetoothGatt,
                status: Int,
                newState: Int
            ) {

                if (
                    newState ==
                    BluetoothProfile.STATE_CONNECTED
                ) {

                    events.log(
                        "BLE connected"
                    )

                    events.connected(
                        gatt.device.name
                            ?: DEVICE_NAME
                    )

                    gatt.discoverServices()

                    return
                }

                if (
                    newState ==
                    BluetoothProfile.STATE_DISCONNECTED
                ) {

                    events.log(
                        "BLE disconnected status=$status"
                    )

                    commandCharacteristic = null

                    statusCharacteristic = null

                    rxBuffer =
                        StringBuilder()

                    if (
                        bluetoothGatt == gatt
                    ) {

                        bluetoothGatt = null
                    }

                    try {
                        gatt.close()
                    } catch (_: Exception) {
                    }

                    events.disconnected()

                    if (
                        !manuallyDisconnected
                    ) {

                        scheduleReconnect()
                    }
                }
            }


            override fun onServicesDiscovered(
                gatt: BluetoothGatt,
                status: Int
            ) {

                if (
                    status !=
                    BluetoothGatt.GATT_SUCCESS
                ) {

                    events.log(
                        "Service discovery failed: $status"
                    )

                    return
                }

                events.log(
                    "BLE services discovered"
                )

                // =================================================
                // DPS SERVICE
                // =================================================

                val service =
                    gatt.getService(
                        DPS_SERVICE_UUID
                    )

                if (service == null) {

                    events.log(
                        "DPS BLE service not found"
                    )

                    return
                }

                events.log(
                    "DPS BLE service found"
                )

                // =================================================
                // COMMAND CHARACTERISTIC
                // =================================================

                commandCharacteristic =
                    service.getCharacteristic(
                        DPS_COMMAND_UUID
                    )

                if (
                    commandCharacteristic == null
                ) {

                    events.log(
                        "Command characteristic not found"
                    )

                } else {

                    events.log(
                        "Command characteristic ready"
                    )
                }

                // =================================================
                // STATUS CHARACTERISTIC
                // =================================================

                statusCharacteristic =
                    service.getCharacteristic(
                        DPS_STATUS_UUID
                    )

                if (
                    statusCharacteristic == null
                ) {

                    events.log(
                        "Status characteristic not found"
                    )

                } else {

                    events.log(
                        "Status characteristic found"
                    )

                    enableStatusNotifications(
                        gatt
                    )
                }
            }


            @SuppressLint("MissingPermission")
            override fun onDescriptorWrite(
                gatt: BluetoothGatt,
                descriptor: BluetoothGattDescriptor,
                status: Int
            ) {

                if (
                    descriptor.uuid ==
                    DPS_CCCD_UUID
                ) {

                    if (
                        status ==
                        BluetoothGatt.GATT_SUCCESS
                    ) {

                        events.log(
                            "BLE status notifications enabled"
                        )

                    } else {

                        events.log(
                            "BLE notification setup failed: $status"
                        )
                    }
                }
            }


            // =====================================================
            // BLE NOTIFICATION CALLBACK
            //
            // IMPORTANT:
            // Use the ByteArray supplied directly by Android.
            // Do NOT read characteristic.value here.
            // =====================================================

            override fun onCharacteristicChanged(
                gatt: BluetoothGatt,
                characteristic: BluetoothGattCharacteristic,
                value: ByteArray
            ) {

                events.log(
                    "BLE RX CALLBACK • UUID=${characteristic.uuid}"
                )

                events.log(
                    "BLE RX CALLBACK VALUE • ${value.size} bytes"
                )

                if (
                    characteristic.uuid ==
                    DPS_STATUS_UUID
                ) {

                    if (value.isEmpty()) {

                        events.log(
                            "BLE RX STATUS • CALLBACK VALUE EMPTY"
                        )

                        return
                    }

                    val payload =
                        value.copyOf()

                    events.log(
                        "BLE RX STATUS • ${payload.size} bytes"
                    )

                    consumeBytes(
                        payload
                    )

                } else {

                    events.log(
                        "BLE RX UNKNOWN CHARACTERISTIC • ${characteristic.uuid}"
                    )
                }
            }
        }


    // ========================================================
    // ENABLE STATUS NOTIFICATIONS
    // ========================================================

    @SuppressLint("MissingPermission")
    private fun enableStatusNotifications(
        gatt: BluetoothGatt
    ) {

        val characteristic =
            statusCharacteristic
                ?: return

        val enabled =
            gatt.setCharacteristicNotification(
                characteristic,
                true
            )

        events.log(
            "Status notification local enable=$enabled"
        )

        val descriptor =
            characteristic.getDescriptor(
                DPS_CCCD_UUID
            )

        if (descriptor == null) {

            events.log(
                "CCCD descriptor not found"
            )

            return
        }

        descriptor.value =
            BluetoothGattDescriptor
                .ENABLE_NOTIFICATION_VALUE

        val requested =
            gatt.writeDescriptor(
                descriptor
            )

        events.log(
            "CCCD write requested=$requested"
        )
    }


    // ========================================================
    // RECEIVE / REASSEMBLE JSON
    // ========================================================

    private fun consumeBytes(
        bytes: ByteArray
    ) {

        if (bytes.isEmpty()) {

            events.log(
                "BLE RX CONSUME • 0 bytes"
            )

            return
        }

        events.log(
            "BLE RX CONSUME • ${bytes.size} bytes"
        )

        val incoming =
            String(
                bytes,
                Charsets.UTF_8
            )

        events.log(
            "BLE RX TEXT • $incoming"
        )

        rxBuffer.append(
            incoming
        )

        if (
            rxBuffer.length >
            MAX_RX_BUFFER
        ) {

            events.log(
                "BLE RX buffer exceeded limit; clearing"
            )

            rxBuffer =
                StringBuilder()

            return
        }

        while (true) {

            val text =
                rxBuffer.toString()

            if (text.isEmpty()) {
                return
            }

            val firstBrace =
                text.indexOf('{')

            if (firstBrace < 0) {

                rxBuffer =
                    StringBuilder()

                return
            }

            if (firstBrace > 0) {

                rxBuffer.delete(
                    0,
                    firstBrace
                )
            }

            var depth = 0

            var inString = false

            var escaped = false

            var completeEnd = -1

            val current =
                rxBuffer.toString()

            for (
            i in current.indices
            ) {

                val c =
                    current[i]

                if (escaped) {

                    escaped = false

                    continue
                }

                if (
                    c == '\\' &&
                    inString
                ) {

                    escaped = true

                    continue
                }

                if (c == '"') {

                    inString =
                        !inString

                    continue
                }

                if (inString) {
                    continue
                }

                if (c == '{') {

                    depth++

                } else if (
                    c == '}'
                ) {

                    depth--

                    if (depth == 0) {

                        completeEnd =
                            i

                        break
                    }
                }
            }

            if (
                completeEnd < 0
            ) {

                events.log(
                    "BLE RX waiting for more JSON data..."
                )

                return
            }

            val json =
                current.substring(
                    0,
                    completeEnd + 1
                )

            rxBuffer.delete(
                0,
                completeEnd + 1
            )

            events.status(
                json
            )

            events.log(
                "HELTEC -> APP: $json"
            )
        }
    }


    // ========================================================
    // RECONNECT
    // ========================================================

    private fun scheduleReconnect() {

        if (
            manuallyDisconnected
        ) {

            return
        }

        if (
            reconnectPending
        ) {

            return
        }

        reconnectPending = true

        events.log(
            "Scheduling BLE reconnect..."
        )

        handler.postDelayed(
            {

                reconnectPending = false

                if (
                    !manuallyDisconnected
                ) {

                    connectBondedOrScan()
                }

            },
            RECONNECT_DELAY_MS
        )
    }
}