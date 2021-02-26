package com.aya.salama.blescanning

import android.Manifest
import android.bluetooth.*
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.HandlerThread
import android.util.Log
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import kotlinx.android.synthetic.main.activity_main.*


@RequiresApi(Build.VERSION_CODES.LOLLIPOP)
class MainActivity : AppCompatActivity() {

    private val bluetoothAdapter: BluetoothAdapter by lazy(LazyThreadSafetyMode.NONE) {
        val bluetoothManager = getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
        bluetoothManager.adapter
    }

    private var mScanning: Boolean = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        setDeviceBluetoothDiscoverable()
        allowLocationDetectionPermissions()

        if (bluetoothAdapter.isEnabled) {
            scanLeDevice(true) //make sure scan function won't be called several times
        }

    }


    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<String>, grantResults: IntArray
    ) {
        when (requestCode) {
            FINE_LOCATION_PERMISSION_REQUEST -> {
                if ((grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED)) {
                    scanLeDevice(true)
                } else {
                    tvTestNote.text = getString(R.string.allow_location_detection)
                }
                return
            }
        }
    }


    private fun allowLocationDetectionPermissions() {
        if (ContextCompat.checkSelfPermission(
                this@MainActivity,
                Manifest.permission.ACCESS_FINE_LOCATION
            )
                == PackageManager.PERMISSION_DENIED) {
            ActivityCompat.requestPermissions(
                this@MainActivity,
                arrayOf(Manifest.permission.ACCESS_FINE_LOCATION), FINE_LOCATION_PERMISSION_REQUEST
            )
        }

    }


    private fun setDeviceBluetoothDiscoverable() {
        //no need to request bluetooth permission if  discoverability is requested
        val discoverableIntent = Intent(BluetoothAdapter.ACTION_REQUEST_DISCOVERABLE)
        discoverableIntent.putExtra(
            BluetoothAdapter.EXTRA_DISCOVERABLE_DURATION,
            0
        )// 0 to keep it always discoverable
        startActivity(discoverableIntent)
    }



    private fun scanLeDevice(enable: Boolean) {
        when (enable) {
            true -> {
                // Stops scanning after a pre-defined scan period.
                Handler().postDelayed({
                    mScanning = false
                    bluetoothAdapter?.bluetoothLeScanner?.stopScan(mLeScanCallback)
                }, 5000)
                mScanning = true
                bluetoothAdapter?.bluetoothLeScanner?.startScan(mLeScanCallback)
            }
            else -> {
                mScanning = false
                bluetoothAdapter?.bluetoothLeScanner?.stopScan(mLeScanCallback)
            }
        }

    }


    var onlyOnce = true;
    var context = this;
    var data = ""
    private var mLeScanCallback: ScanCallback =
            object : ScanCallback() {
                @RequiresApi(Build.VERSION_CODES.O)
                override fun onScanResult(callbackType: Int, result: ScanResult?) {
                    super.onScanResult(callbackType, result)


                    if (result?.getDevice()?.getName().toString().contains("TETRA") && onlyOnce){
                        onlyOnce = false


                        tvTestNote.text = "TETRA5678 found."
                        var bluetoothGatt: BluetoothGatt? = null
                        val device = result!!.device
                        bluetoothGatt = device.connectGatt(context, false, gattCallback)


                    }

//                    data = data + result?.getDevice()?.getName().toString()
//                    tvTestNote.text = data

//
                //    val device = result!!.device
//                    device.connectGatt(this, false, gattCallback);









                }

                override fun onBatchScanResults(results: List<ScanResult?>?) {
                    super.onBatchScanResults(results)
                    tvTestNote.text = getString(R.string.found_ble_devices)


                }

                override fun onScanFailed(errorCode: Int) {
                    super.onScanFailed(errorCode)
                    tvTestNote.text = getString(R.string.ble_device_scan_failed)+ errorCode

                }
            }



    private val gattCallback = object : BluetoothGattCallback() {
        override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
            val deviceAddress = gatt.device.address

            if (status == BluetoothGatt.GATT_SUCCESS) {
                if (newState == BluetoothProfile.STATE_CONNECTED) {
                    Log.w("BluetoothGattCallback", "Successfully connected to $deviceAddress")
                    // TODO: Store a reference to BluetoothGatt
                } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                    Log.w("BluetoothGattCallback", "Successfully disconnected from $deviceAddress")
                    gatt.close()
                }
            } else {
                Log.w("BluetoothGattCallback", "Error $status encountered for $deviceAddress! Disconnecting...")
                gatt.close()
            }
        }
    }











    companion object{
        private const val FINE_LOCATION_PERMISSION_REQUEST= 1001
    }

}

