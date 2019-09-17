package com.lossydragon.bluetoothcomms

import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattService
import android.content.*
import android.os.Bundle
import android.os.Handler
import android.os.IBinder
import android.os.Message
import android.util.Log
import android.view.Menu
import android.view.MenuItem
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.snackbar.Snackbar
import com.lossydragon.bluetoothcomms.bluetooth.BluetoothClassic
import com.lossydragon.bluetoothcomms.bluetooth.BluetoothLE
import kotlinx.android.synthetic.main.activity_device_control.*
import java.util.*

//TODO when paired to arduino device, add method to see if its an Arduino or not.
//TODO when launching to ControlActivity Activity, indicate a progress until a connection.

/**
 * This class is called after selecting an available device.
 * Some LE/GATT uses are from GoogleSamples
 */
class DeviceControlActivity : AppCompatActivity() {

    private lateinit var intentName: String
    private lateinit var intentAddress: String
    private var intentType: Int = 0

    private var bluetoothLE: BluetoothLE? = null
    private var leConnected: Boolean = false
    private var characteristicTX: BluetoothGattCharacteristic? = null
    private var characteristicRX: BluetoothGattCharacteristic? = null

    internal var bluetoothThread: BluetoothClassic? = null
    private var writeHandler: Handler? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_device_control)
        setSupportActionBar(toolbar2)

        //Grab the intents we passed and put them into some vars.
        intentName = intent.getStringExtra(EXTRAS_DEVICE_NAME)!!
        intentAddress = intent.getStringExtra(EXTRAS_DEVICE_ADDRESS)!!
        intentType = intent.getIntExtra(EXTRAS_DEVICE_TYPE, 1)

        Log.i(TAG, "Intent Stuff: $intentName / $intentAddress / $intentType")


        if (intentType == BluetoothDevice.DEVICE_TYPE_CLASSIC) {
            //Starting Classic thread
            startThread(intentAddress)
        } else {
            //Starting LE Thread
            val gattServiceIntent = Intent(this, BluetoothLE::class.java)
            bindService(gattServiceIntent, serviceConnection, BIND_AUTO_CREATE)
            registerReceiver(mGattUpdateReceiver, makeGattUpdateIntentFilter())
        }


        control_Button.setOnClickListener {

            val message = control_editText.text.toString()

            //Write to Classic Thread
            if (intentType == BluetoothDevice.DEVICE_TYPE_CLASSIC) {
                Log.i(TAG, "Classic Button Enabled")
                val msg = Message.obtain()
                msg.obj = message

                if (writeHandler != null)
                    writeHandler!!.sendMessage(msg)
            }
            //Write to LE thread
            else {
                Log.i(TAG, "LE Button Enabled")
                val tx = (message + '\n').toByteArray()
                if (leConnected) {
                    characteristicTX?.value = tx
                    bluetoothLE!!.writeCharacteristic(characteristicTX!!)
                    bluetoothLE!!.setCharacteristicNotification(characteristicRX!!, true)
                }
            }

            //editText.text.clear()
        }
    }

    /**
     * Starts a BT classic connection
     */
    @SuppressLint("HandlerLeak")
    fun startThread(address: String) {

        if (bluetoothThread != null)
            return

        bluetoothThread = BluetoothClassic(address, object : Handler() {

            override fun handleMessage(message: Message) {
                when (val s = message.obj as String) {
                    "CONNECTED" -> {
                        supportActionBar?.title = "Connected"
                    }
                    "DISCONNECTED" -> {
                        supportActionBar?.title = "Disconnected"
                    }
                    "CONNECTION FAILED" -> {
                        supportActionBar?.title = "Connection Failed"
                        bluetoothThread = null
                    }
                    else -> {
                        control_textView.text = s
                    }
                }
            }
        })

        writeHandler = bluetoothThread!!.writeHandler
        bluetoothThread!!.start()
    }

    /**
     * LE service connection
     */
    private val serviceConnection = object : ServiceConnection {

        override fun onServiceConnected(componentName: ComponentName, service: IBinder) {
            bluetoothLE = (service as BluetoothLE.LocalBinder).service
            if (!bluetoothLE!!.initialize()) {
                Log.e(TAG, "Unable to initialize Bluetooth")
                finish()
            }
            // Automatically connects to the device upon successful start-up initialization.
            bluetoothLE!!.connect(intentAddress)
        }

        override fun onServiceDisconnected(componentName: ComponentName) {
            bluetoothLE = null
        }
    }

    /**
     * Handles various events fired by the Service.
     * ACTION_GATT_CONNECTED: connected to a GATT server.
     * ACTION_GATT_DISCONNECTED: disconnected from a GATT server.
     * ACTION_GATT_SERVICES_DISCOVERED: discovered GATT services.
     * ACTION_DATA_AVAILABLE: received data from the device...
     * This can be a result of read or notification operations.
     */
    val sb = StringBuilder()
    private val mGattUpdateReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            when (intent.action) {
                BluetoothLE.ACTION_GATT_CONNECTED -> {
                    leConnected = true
                    supportActionBar?.title = "LE: Connected"
                    invalidateOptionsMenu()
                }
                BluetoothLE.ACTION_GATT_DISCONNECTED -> {
                    leConnected = false
                    supportActionBar?.title = "LE: Disconnected"
                    invalidateOptionsMenu()
                }
                BluetoothLE.ACTION_GATT_SERVICES_DISCOVERED -> {
                    // Show all the supported services and characteristics on the user interface.
                    displayGattServices(bluetoothLE?.getSupportedGattServices())
                }
                BluetoothLE.ACTION_DATA_AVAILABLE -> {
                    //Broadcast update when something is received.
                    if (intent.getStringExtra(EXTRA_DATA) != "\n") {
                        sb.append(intent.getStringExtra(EXTRA_DATA))
                        Log.d(TAG, sb.toString())
                    } else {
                        control_textView.text = sb
                        sb.setLength(0)
                    }

                }
            }
        }
    }

    /**
     * Demonstrates how to iterate through the supported GATT Services/Characteristics.
     * In this sample, we populate the data structure that is bound to the ExpandableListView
     * on the UI.
     */
    private fun displayGattServices(gattServices: List<BluetoothGattService>?) {

        if (gattServices == null)
            return

        // Loops through available GATT Services.
        for (gattService in gattServices) {
            // get characteristic when UUID matches RX/TX UUID
            characteristicTX = gattService.getCharacteristic(UUID.fromString(SH_HC_08_RWN))
            characteristicRX = gattService.getCharacteristic(UUID.fromString(SH_HC_08_RWN))
        }
    }

    /**
     * LE Related Intent Filters
     */
    private fun makeGattUpdateIntentFilter(): IntentFilter {
        val intentFilter = IntentFilter()
        intentFilter.addAction(BluetoothLE.ACTION_GATT_CONNECTED)
        intentFilter.addAction(BluetoothLE.ACTION_GATT_DISCONNECTED)
        intentFilter.addAction(BluetoothLE.ACTION_GATT_SERVICES_DISCOVERED)
        intentFilter.addAction(BluetoothLE.ACTION_DATA_AVAILABLE)
        return intentFilter
    }

    override fun onPause() {
        super.onPause()

        //DEVICE_TYPE_CLASSIC = 1, DEVICE_TYPE_LE = 2, DEVICE_TYPE_DUAL = 3
        if (intentType == 1 || intentType == 3)
            bluetoothThread?.interrupt()

        if (intentType == 2)
            unregisterReceiver(mGattUpdateReceiver)
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        super.onCreateOptionsMenu(menu)
        menuInflater.inflate(R.menu.menu_control, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {

        return when (item.itemId) {
            R.id.menu_disconnect -> {
                Snackbar.make(deviceLayout, "Disconnected", Snackbar.LENGTH_LONG).show()

                if (intentType != BluetoothDevice.DEVICE_TYPE_LE)
                    bluetoothThread!!.interrupt()

                if (intentType == BluetoothDevice.DEVICE_TYPE_LE) {
                    bluetoothLE?.disconnect()
                    unbindService(serviceConnection)
                }

                val intent = Intent(applicationContext, MainActivity::class.java)
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                applicationContext.startActivity(intent)

                return true
            }
            R.id.menu_settings -> {
                Snackbar.make(deviceLayout, "Settings TODO", Snackbar.LENGTH_LONG).show()
                return true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    companion object {
        private const val TAG = "DeviceControlActivity"

        //Intent values
        const val EXTRAS_DEVICE_NAME = "DEVICE_NAME"
        const val EXTRAS_DEVICE_ADDRESS = "DEVICE_ADDRESS"
        const val EXTRAS_DEVICE_TYPE = "DEVICE_RSSI"

        const val SH_HC_08_RWN = "0000ffe1-0000-1000-8000-00805f9b34fb"
        const val EXTRA_DATA = "com.lossydragon.bluetoothcomms.le.EXTRA_DATA"
    }

}
