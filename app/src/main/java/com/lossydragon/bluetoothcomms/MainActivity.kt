package com.lossydragon.bluetoothcomms

import android.Manifest
import android.app.Activity
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.Handler
import android.util.Log
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.DefaultItemAnimator
import androidx.recyclerview.widget.DividerItemDecoration
import androidx.recyclerview.widget.LinearLayoutManager
import cat.ereza.customactivityoncrash.config.CaocConfig
import com.lossydragon.bluetoothcomms.adapters.Devices
import com.lossydragon.bluetoothcomms.adapters.DevicesAdapter
import kotlinx.android.synthetic.main.activity_main.*
import java.util.*

//TODO auto-connect on resume?
//TODO Location Perms and Bluetooth enable prompt from appearing at the same time.
class MainActivity : AppCompatActivity() {

    private lateinit var bluetoothManager: BluetoothManager
    private lateinit var bluetoothAdapter: BluetoothAdapter
    private var permissionsRequestLocation = 0

    private var handler: Handler = Handler()
    private lateinit var devicesAdapter: DevicesAdapter
    private var deviceList: ArrayList<Devices> = ArrayList()

    override fun onCreate(savedInstanceState: Bundle?) {

        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        setSupportActionBar(bottom_app_bar)

        /* Custom app crash logger */
        CaocConfig.Builder.create()
                .backgroundMode(CaocConfig.BACKGROUND_MODE_SILENT)
                .enabled(true)
                .showErrorDetails(true)
                .showRestartButton(true)
                .logErrorOnRestart(true)
                .trackActivities(true)
                .apply()

        bluetoothManager = getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
        bluetoothAdapter = BluetoothAdapter.getDefaultAdapter()

        bluetoothAdapter = bluetoothManager.adapter
        devicesAdapter = DevicesAdapter(this, deviceList)

        recycler_devices.apply {
            adapter = devicesAdapter
            setHasFixedSize(true)
            layoutManager = LinearLayoutManager(context)
            addItemDecoration(DividerItemDecoration(context, LinearLayoutManager.VERTICAL))
            itemAnimator = DefaultItemAnimator()
        }

        //Fab icon to start/cancel bluetooth scanning.
        fab_scan.setOnClickListener {
            scanLeDevices()
            scan_hint.text = getText(R.string.scan_hint_no_devices)
        }
    }

    override fun onResume() {
        super.onResume()

        //Check to see if BT is enabled.
        if (!bluetoothAdapter.isEnabled) {
            startActivityForResult(
                    Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE),
                    requestEnableBluetooth
            )
        } else {
            fab_scan.setImageResource(R.drawable.ic_bluetooth)
        }

        //Check to see if Coarse Location is granted.
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) !=
                PackageManager.PERMISSION_GRANTED ||
                ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) !=
                PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this,
                    arrayOf(Manifest.permission.ACCESS_COARSE_LOCATION,
                            Manifest.permission.ACCESS_FINE_LOCATION),
                    permissionsRequestLocation
            )
        }


        deviceList.clear()
        devicesAdapter.notifyDataSetChanged()
    }

    override fun onPause() {
        super.onPause()

        bluetoothAdapter.cancelDiscovery()
        deviceList.clear()
        devicesAdapter.notifyDataSetChanged()
    }

    override fun onDestroy() {
        super.onDestroy()

        deviceList.clear()
        devicesAdapter.notifyDataSetChanged()
        scan_hint.visibility = View.VISIBLE
        unregisterReceiver(bluetoothDiscoveryBroadcastReceiver)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        if (requestCode == requestEnableBluetooth && resultCode == Activity.RESULT_CANCELED) {
            Toast.makeText(this, R.string.toast_bt_denied, Toast.LENGTH_LONG).show()
            finish()
            return
        }

        fab_scan.setImageResource(R.drawable.ic_bluetooth)
        super.onActivityResult(requestCode, resultCode, data)
    }


    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        super.onCreateOptionsMenu(menu)
        menuInflater.inflate(R.menu.menu_main, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {

        return when (item.itemId) {
            R.id.menu_settings -> {
                startActivity(Intent(this, SettingsActivity::class.java))
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    /**
     * Function to start bluetooth discovery
     * Enables a broadcast receiver and find them
     * Stops discovery after 10 seconds.
     */
    private fun scanLeDevices() {

        registerReceiver(
                bluetoothDiscoveryBroadcastReceiver,
                IntentFilter(BluetoothDevice.ACTION_FOUND)
        )

        //Turn on bluetooth if not on during button press.
        if (!bluetoothAdapter.isEnabled) {
            fab_scan.setImageResource(R.drawable.ic_bluetooth)
            bluetoothAdapter.enable()
        }

        if (!bluetoothAdapter.isDiscovering) {
            //Start Discovery
            fab_scan.setImageResource(R.drawable.ic_bluetooth_searching)
            toast("Scanning for 10 seconds.")
            Log.d(TAG, "Discovery Started...")
            progress_bar.visibility = View.VISIBLE
            bluetoothAdapter.startDiscovery()

            //Stop Discovery after 10 sec.
            handler.postDelayed({
                fab_scan.setImageResource(R.drawable.ic_bluetooth)
                Log.d(TAG, "Discovery Auto-Stopped...")
                progress_bar.visibility = View.INVISIBLE
                bluetoothAdapter.cancelDiscovery()

            }, DISCOVERY_TIME)

        } else {
            //Cancel Discovery
            Log.d(TAG, "Discovery Stopped...")
            fab_scan.setImageResource(R.drawable.ic_bluetooth)
            progress_bar.visibility = View.INVISIBLE
            bluetoothAdapter.cancelDiscovery()
            handler.removeCallbacksAndMessages(null)
        }
    }

    /**
     * Updates already discovered devices. Mostly Signal strength.
     */
    fun listAddSkip(address: String, rssi: Int): Boolean {

        for ((index, device) in deviceList.withIndex()) {
            val foundAddress = device.address
            val bool = foundAddress == address

            if (bool) {
                deviceList[index] = Devices(device.name, device.address, rssi.toString(), device.type)
                return false
            }
        }
        return true
    }

    /**
     * Receives found devices and adds them to an ArrayList
     */
    private val bluetoothDiscoveryBroadcastReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {

            if (intent.action == BluetoothDevice.ACTION_FOUND) {
                val result = intent.getParcelableExtra<BluetoothDevice>(BluetoothDevice.EXTRA_DEVICE)!!
                val rssi = intent.getShortExtra(BluetoothDevice.EXTRA_RSSI, java.lang.Short.MIN_VALUE)

                if (listAddSkip(result.address, rssi.toInt())) {
                    Log.i(TAG, "Name: " + result.name + " RSSI: " + rssi + " Type: " + result.type + " Bond: " + result.bondState)

                    deviceList.add(Devices(result.name, result.address, rssi.toString(), result.type))
                    scan_hint.visibility = View.GONE
                }
                devicesAdapter.notifyDataSetChanged()

            }
        }
    }

    companion object {
        private const val TAG = "MainActivity"
        private const val DISCOVERY_TIME: Long = 10000
        const val requestEnableBluetooth = 1
    }
}
