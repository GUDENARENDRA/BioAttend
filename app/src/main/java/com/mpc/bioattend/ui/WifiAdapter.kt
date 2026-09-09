package com.mpc.bioattend.ui

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.mpc.bioattend.R
import com.mpc.bioattend.model.WifiNetwork

class WifiAdapter(
    private var networks: List<WifiNetwork>,
    private val onNetworkSelected: (WifiNetwork) -> Unit
) : RecyclerView.Adapter<WifiAdapter.WifiViewHolder>() {

    class WifiViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val tvSsid: TextView = itemView.findViewById(R.id.tvWifiSsid)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): WifiViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_wifi, parent, false)
        return WifiViewHolder(view)
    }

    override fun onBindViewHolder(holder: WifiViewHolder, position: Int) {
        val network = networks[position]
        // Display ONLY exact original network name - no lock symbols, no bars, no rssi
        holder.tvSsid.text = network.ssid

        holder.itemView.setOnClickListener {
            onNetworkSelected(network)
        }
    }

    override fun getItemCount(): Int = networks.size

    fun updateNetworks(newNetworks: List<WifiNetwork>) {
        this.networks = newNetworks.sortedByDescending { it.rssi }
        notifyDataSetChanged()
    }
}
