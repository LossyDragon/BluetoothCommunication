package com.lossydragon.bluetoothcomms

import android.Manifest
import android.os.Bundle
import android.util.Log
import android.view.MenuItem
import butterknife.ButterKnife
import android.bluetooth.BluetoothAdapter
import android.content.Intent
import android.app.Activity
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.Handler
import android.view.Menu
import android.view.View
import android.widget.ProgressBar
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.DefaultItemAnimator
import androidx.recyclerview.widget.DividerItemDecoration
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import butterknife.BindView
import cat.ereza.customactivityoncrash.config.CaocConfig
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.google.android.material.snackbar.Snackbar
import com.lossydragon.bluetoothcomms.adapters.Devices
import com.lossydragon.bluetoothcomms.adapters.DevicesAdapter
import kotlinx.android.synthetic.main.activity_main.*
import java.util.*

//TODO auto-connect on resume?
//TODO Location Perms and Bluetooth enable prompt from appearing at the same time.
class MainActivity : AppCompatActivity() {

    @BindView(R.id.recycler_devices) lateinit var recyclerView: RecyclerView
    @BindView(R.id.progress_bar) lateinit var progressBar: ProgressBar
    @BindView(R.id.fab_scan) lateinit var fabScan: FloatingActionButton

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


        bluetoothManager = getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
        bluetoothAdapter = bluetoothManager!!.adapter

        val layoutManager = LinearLayoutManager(this)

        adapter = DevicesAdapter(this, deviceList)
        recyclerView.adapter = adapter

        recyclerView.setHasFixedSize(true)
        recyclerView.layoutManager = layoutManager
        recyclerView.addItemDecoration(DividerItemDecoration(this, LinearLayoutManager.VERTICAL))
        recyclerView.itemAnimator = DefaultItemAnimator()

        //Fab icon to start/cancel bluetooth scanning.
        fabScan.setOnClickListener { _ ->
            scanLeDevices()
        }
    }

    override fun onResume() {
        super.onResume()

        //Check to see if BT is enabled.
        if (!bluetoothAdapter!!.isEnabled) {
            val enableBtIntent = Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE)
            startActivityForResult(enableBtIntent, requestEnableBluetooth)
        } else
            fabScan.setImageResource(R.drawable.ic_bluetooth)


        //Check to see if Coarse Location is granted.
        if (ContextCompat.checkSelfPermission(this,
                        Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED)
            ActivityCompat.requestPermissions(this,
                    arrayOf(Manifest.permission.ACCESS_COARSE_LOCATION), permissionsRequestLocation)

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
            Toast.makeText(this, R.string.toast_bt_denied, Toast.LENGTH_LONG).show()
            finish()
            return
        }

        fabScan.setImageResource(R.drawable.ic_bluetooth)
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

        registerReceiver(bluetoothDiscoveryBroadcastReceiver,
                IntentFilter(BluetoothDevice.ACTION_FOUND))

        //Turn on bluetooth if not on during button press.
        if (!bluetoothAdapter!!.isEnabled) {
            fabScan.setImageResource(R.drawable.ic_bluetooth)
            Snackbar.make(mainLayout, "Turning on Bluetooth...", Snackbar.LENGTH_LONG).show()
            bluetoothAdapter?.enable()
        }

        if (!bluetoothAdapter!!.isDiscovering) {
            //Start Discovery
            fabScan.setImageResource(R.drawable.ic_bluetooth_searching)
            Snackbar.make(mainLayout, "Scanning for 10 seconds.", Snackbar.LENGTH_LONG).show()
            Log.d(TAG, "Discovery Started...")
            progressBar.visibility = View.VISIBLE
            bluetoothAdapter?.startDiscovery()

            //Stop Discovery after 10 sec.
            handler!!.postDelayed({
                fabScan.setImageResource(R.drawable.ic_bluetooth)
                Log.d(TAG, "Discovery Auto-Stopped...")
                progressBar.visibility = View.INVISIBLE
                bluetoothAdapter?.cancelDiscovery()

            }, DISCOVERY_TIME)

        } else {
            //Cancel Discovery
            Log.d(TAG, "Discovery Stopped...")
            fabScan.setImageResource(R.drawable.ic_bluetooth)
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
                    Log.i(TAG, "Name: " + result.name + " RSSI: " + rssi + " Type: " + result.type + " Bond: " + result.bondState)

                    deviceList.add(Devices(result.name, result.address, rssi.toString(), result.type))
                }
                adapter?.notifyDataSetChanged()

            }
        }
    }

    companion object {
        private const val TAG = "MainActivity"
        private const val DISCOVERY_TIME: Long = 10000
        const val requestEnableBluetooth = 1
    }
}
