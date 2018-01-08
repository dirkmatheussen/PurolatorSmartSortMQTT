/*
 * Copyright (C) 2013 EatIT
 * Put application for Vuzix Glasses
 *
 */

package evolar.be.purolatorsmartsortMQTT;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

public class BootUpReceiver extends BroadcastReceiver{
	
    // Debugging
 

    @Override
    public void onReceive(Context context, Intent intent) {

            Intent i = new Intent(context, Splash.class);
    		i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            i.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
            i.addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP);
            context.startActivity(i);  
                    
    }
    

}