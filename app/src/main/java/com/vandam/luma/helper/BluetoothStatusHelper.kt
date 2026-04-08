package com.vandam.luma.helper

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.content.Context
import android.content.IntentFilter
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat

object BluetoothStatusHelper {
    private val bluetoothConnectPermissions = arrayOf(Manifest.permission.BLUETOOTH_CONNECT)

    enum class IndicatorState {
        Off,
        On,
        Connected,
    }

    fun bluetoothConnectPermissions(): Array<String> = bluetoothConnectPermissions.copyOf()

    fun hasBluetoothConnectPermission(context: Context): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED

    fun bluetoothIntentFilter(): IntentFilter =
        IntentFilter().apply {
            addAction(BluetoothAdapter.ACTION_STATE_CHANGED)
            addAction(BluetoothAdapter.ACTION_CONNECTION_STATE_CHANGED)
        }

    fun indicatorState(context: Context): IndicatorState? {
        if (!hasBluetoothConnectPermission(context)) return null
        val adapter = context.getSystemService(BluetoothManager::class.java)?.adapter ?: return IndicatorState.Off
        return runCatching {
            if (adapter.state != BluetoothAdapter.STATE_ON) {
                IndicatorState.Off
            } else if (hasConnectedDevice(context, adapter)) {
                IndicatorState.Connected
            } else {
                IndicatorState.On
            }
        }.getOrNull()
    }

    private fun hasConnectedDevice(
        context: Context,
        adapter: BluetoothAdapter,
    ): Boolean = hasConnectedAudioProfile(adapter) || hasConnectedGattDevice(context)

    private fun hasConnectedAudioProfile(adapter: BluetoothAdapter): Boolean =
        listOf(BluetoothProfile.A2DP, BluetoothProfile.HEADSET).any {
            adapter.getProfileConnectionState(it) == BluetoothProfile.STATE_CONNECTED
        }

    private fun hasConnectedGattDevice(context: Context): Boolean {
        val bluetoothManager = context.getSystemService(BluetoothManager::class.java) ?: return false
        return listOf(BluetoothProfile.GATT, BluetoothProfile.GATT_SERVER).any {
            bluetoothManager.getConnectedDevices(it).isNotEmpty()
        }
    }
}
