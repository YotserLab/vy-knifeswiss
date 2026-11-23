package com.example.utils.net

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.net.wifi.p2p.WifiP2pManager
import android.os.Build
import android.os.Looper
import androidx.core.app.ActivityCompat

object WifiDirectManager {

    fun createGroup(
        context: Context, 
        onSuccess: () -> Unit, 
        onGroupInfoAvailable: (android.net.wifi.p2p.WifiP2pGroup?) -> Unit,
        onFailure: (Int) -> Unit
    ) {
        if (ActivityCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            onFailure(-1)
            return
        }
        if (Build.VERSION.SDK_INT >= 31 && ActivityCompat.checkSelfPermission(context, Manifest.permission.NEARBY_WIFI_DEVICES) != PackageManager.PERMISSION_GRANTED) {
            onFailure(-1)
            return
        }
        
        val manager = context.getSystemService(Context.WIFI_P2P_SERVICE) as? WifiP2pManager
        val channel = manager?.initialize(context, Looper.getMainLooper(), null)
        
        if (manager != null && channel != null) {
            manager.createGroup(channel, object : WifiP2pManager.ActionListener {
                override fun onSuccess() {
                    onSuccess()
                    manager.requestGroupInfo(channel) { group ->
                        onGroupInfoAvailable(group)
                    }
                }
                override fun onFailure(reason: Int) {
                    onFailure(reason)
                }
            })
        } else {
            onFailure(-2)
        }
    }
    
    fun removeGroup(context: Context) {
        if (ActivityCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) return
        if (Build.VERSION.SDK_INT >= 31 && ActivityCompat.checkSelfPermission(context, Manifest.permission.NEARBY_WIFI_DEVICES) != PackageManager.PERMISSION_GRANTED) return

        val manager = context.getSystemService(Context.WIFI_P2P_SERVICE) as? WifiP2pManager
        val channel = manager?.initialize(context, Looper.getMainLooper(), null)
        if (manager != null && channel != null) {
            manager.removeGroup(channel, null)
        }
    }
}
