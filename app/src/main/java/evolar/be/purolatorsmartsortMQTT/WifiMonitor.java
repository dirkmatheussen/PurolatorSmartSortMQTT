package evolar.be.purolatorsmartsortMQTT;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.util.Log;

/**
 * Created by Dirk on 2/05/17.
 */

public class WifiMonitor extends BroadcastReceiver {




    @Override
    public void onReceive(Context context, Intent intent) {
        ConnectivityManager conMan = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
        NetworkInfo netInfo = conMan.getActiveNetworkInfo();
/*
        if (netInfo != null && netInfo.getType() == ConnectivityManager.TYPE_WIFI) {
            PurolatorSmartsortMQTT.getsInstance().onWifiStateChanged(true);
            Log.d("WifiMonitor", "Have Wifi Connection");
        } else {
            PurolatorSmartsortMQTT.getsInstance().onWifiStateChanged(false);
            Log.d("WifiMonitor", "Don't have Wifi Connection");
        }
*/
    }


}
