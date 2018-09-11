package com.lossydragon.bluetoothcomms.adapters

import android.bluetooth.BluetoothDevice
import android.content.Context
import android.content.Intent
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.lossydragon.bluetoothcomms.DeviceControlActivity
import com.lossydragon.bluetoothcomms.R

class DevicesAdapter(private var context: Context, private val list: ArrayList<Devices>):
                                        RecyclerView.Adapter<DevicesAdapter.DeviceViewHolder>() {

    private val inflater: LayoutInflater = LayoutInflater.from(context)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): DeviceViewHolder {
        val view = inflater.inflate(R.layout.device_list_row, parent, false)

        return DeviceViewHolder(view, context, list)
    }

    override fun onBindViewHolder(holder: DeviceViewHolder, position: Int) {

        holder.listName.text = list[position].name
        holder.listAddress.text = list[position].address
        holder.listRssi.text = list[position].rssi

        //List whether the device is LE or pre LE
        when {
            list[position].type == BluetoothDevice.DEVICE_TYPE_LE -> holder.listType.text = context.getString(R.string.bluetooth_le)
            list[position].type == BluetoothDevice.DEVICE_TYPE_DUAL -> holder.listType.text = context.getString(R.string.bluetooth_dual)
            else -> holder.listType.text = context.getString(R.string.bluetooth_cl)
        }


    }

    override fun getItemCount(): Int {
        return list.size
    }

    class DeviceViewHolder(view: View, private var context: Context, private var devices: ArrayList<Devices>)
                                            : RecyclerView.ViewHolder(view), View.OnClickListener {

        val listName: TextView
        val listAddress: TextView
        val listRssi: TextView
        val listType: TextView

        init {
            view.setOnClickListener(this)
            this.listName = view.findViewById(R.id.device_name)
            this.listAddress = view.findViewById(R.id.device_address)
            this.listRssi = view.findViewById(R.id.device_rssi)
            this.listType = view.findViewById(R.id.device_type)
        }

        override fun onClick(view: View) {
            val position = adapterPosition
            val devices = this.devices[position]

            //Toast.makeText(context, "${devices.name} @ position $position clicked", Toast.LENGTH_LONG).show()

            val intent = Intent(context, DeviceControlActivity::class.java)
            intent.putExtra(DeviceControlActivity.EXTRAS_DEVICE_NAME, devices.name)
            intent.putExtra(DeviceControlActivity.EXTRAS_DEVICE_ADDRESS, devices.address.toUpperCase())
            intent.putExtra(DeviceControlActivity.EXTRAS_DEVICE_TYPE, devices.type)

            //IDK if this is normal/good or not.
            //There is no plan yet for back stack
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

            context.startActivity(intent)


        }
    }
}