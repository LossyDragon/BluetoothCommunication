package com.lossydragon.bluetoothcomms

import android.Manifest
import android.os.Bundle
import android.support.design.widget.Snackbar
import android.support.v7.app.AppCompatActivity
import android.util.Log
import android.view.MenuItem
import butterknife.ButterKnife
import kotlinx.android.synthetic.main.activity_main.*
import android.bluetooth.BluetoothAdapter
import android.content.Intent
import android.app.Activity
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.IntentFilter
import android.os.Handler
import android.support.v4.app.ActivityCompat
import android.support.v7.widget.DefaultItemAnimator
import android.support.v7.widget.DividerItemDecoration
import android.support.v7.widget.LinearLayoutManager
import android.support.v7.widget.RecyclerView
import android.view.Menu
import android.view.View
import android.widget.ProgressBar
import android.widget.Toast
import butterknife.BindView
import cat.ereza.customactivityoncrash.config.CaocConfig
import com.lossydragon.bluetoothcomms.adapters.Devices
import com.lossydragon.bluetoothcomms.adapters.DevicesAdapter

//TODO auto-connect on resume?
class MainActivity : AppCompatActivity() {

    @BindView(R.id.recycler_devices) lateinit var recyclerView: RecyclerView
    @BindView(R.id.progress_bar) lateinit var progressBar: ProgressBar

    private var bluetoothManager: BluetoothManager? = null
    private var bluetoothAdapter: BluetoothAdapter? = null
    private var permissionsRequestLocation = 0

    private var handler: Handler? = null
    private var adapter: DevicesAdapter? = null
    private lateinit var deviceList: ArrayList<Devices>

    override fun onCreate(savedInstanceState: Bundle?) {

        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        setSupportActionBar(toolbar)

        /* Custom app crash logger */
        CaocConfig.Builder.create()
                .backgroundMode(CaocConfig.BACKGROUND_MODE_SILENT)
                .enabled(true)
                .showErrorDetails(true)
                .showRestartButton(true)
                .logErrorOnRestart(true)
                .trackActivities(true)
                .apply()

        Log.i(TAG, "Created...")
        ButterKnife.bind(this)

        handler = Handler()
        bluetoothAdapter = BluetoothAdapter.getDefaultAdapter()
        deviceList = ArrayList()

        ActivityCompat.requestPermissions(this,
                arrayOf(Manifest.permission.ACCESS_COARSE_LOCATION), permissionsRequestLocation)


        bluetoothManager = getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
        bluetoothAdapter = bluetoothManager!!.adapter

        val layoutManager = LinearLayoutManager(this)

        adapter = DevicesAdapter(this, deviceList)
        recyclerView.adapter = adapter

        recyclerView.setHasFixedSize(true)
        recyclerView.layoutManager = layoutManager
        recyclerView.addItemDecoration(DividerItemDecoration(this, LinearLayoutManager.VERTICAL))
        recyclerView.itemAnimator = DefaultItemAnimator()

    }

    override fun onResume() {
        super.onResume()

        //Make sure the Bluetooth Adapter is enabled.
        if (!bluetoothAdapter!!.isEnabled) {
            val enableBtIntent = Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE)
            startActivityForResult(enableBtIntent, requestEnableBluetooth)
        }
        deviceList.clear()
        adapter?.notifyDataSetChanged()
    }

    override fun onPause() {
        super.onPause()

        bluetoothAdapter?.cancelDiscovery()
        deviceList.clear()
        adapter?.notifyDataSetChanged()
    }


    override fun onDestroy() {
        super.onDestroy()

        deviceList.clear()
        adapter?.notifyDataSetChanged()
        unregisterReceiver(bluetoothDiscoveryBroadcastReceiver)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        if (requestCode == requestEnableBluetooth && resultCode == Activity.RESULT_CANCELED) {
            finish()
            Toast.makeText(this, R.string.toast_bt_denied, Toast.LENGTH_LONG).show()
            return
        }
        super.onActivityResult(requestCode, resultCode, data)
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        super.onCreateOptionsMenu(menu)
        menuInflater.inflate(R.menu.menu_main, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {

        return when (item.itemId) {
            R.id.menu_scan -> {
                scanLeDevices()
                return true
            }
            R.id.menu_settings -> {
                Snackbar.make(recycler_devices, "Settings TODO", Snackbar.LENGTH_LONG).show()
                return true
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

        registerReceiver(bluetoothDiscoveryBroadcastReceiver,
                IntentFilter(BluetoothDevice.ACTION_FOUND))

        //Turn on bluetooth if not on during button press.
        if (!bluetoothAdapter!!.isEnabled) {
            Snackbar.make(recycler_devices, "Turning on Bluetooth...", Snackbar.LENGTH_LONG).show()
            bluetoothAdapter?.enable()
        }

        if (!bluetoothAdapter!!.isDiscovering) {
            //Start Discovery
            Snackbar.make(recycler_devices, "Scanning", Snackbar.LENGTH_LONG).show()
            Log.i(TAG, "Discovery Started...")
            progressBar.visibility = View.VISIBLE
            bluetoothAdapter?.startDiscovery()

            //Stop Discovery after 10 sec.
            handler!!.postDelayed({
                Log.i(TAG, "Discovery Auto-Stopped...")
                progressBar.visibility = View.INVISIBLE
                bluetoothAdapter?.cancelDiscovery()

            }, 10000)

        } else {
            //Cancel Discovery
            Log.i(TAG, "Discovery Stopped...")
            progressBar.visibility = View.INVISIBLE
            bluetoothAdapter?.cancelDiscovery()
            handler!!.removeCallbacksAndMessages(null)
            return
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
     * Receives found devices and adds them to an Arraylist
     */
    private val bluetoothDiscoveryBroadcastReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            val action = intent.action

            if (action == BluetoothDevice.ACTION_FOUND) {
                val result = intent.getParcelableExtra<BluetoothDevice>(BluetoothDevice.EXTRA_DEVICE)
                val rssi = intent.getShortExtra(BluetoothDevice.EXTRA_RSSI, java.lang.Short.MIN_VALUE)

                if (listAddSkip(result.address, rssi.toInt())) {
                    Log.d(TAG, "Name: " + result.name + " RSSI: " + rssi + " Type: " + result.type + " Bond: " + result.bondState)

                    deviceList.add(Devices(result.name, result.address, rssi.toString(), result.type))
                }
                adapter?.notifyDataSetChanged()

            }
        }
    }

    companion object {
        private const val TAG = "MainActivity"
        const val requestEnableBluetooth = 1
    }
}
