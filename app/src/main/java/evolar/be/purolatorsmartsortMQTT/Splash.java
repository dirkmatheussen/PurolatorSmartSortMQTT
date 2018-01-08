package evolar.be.purolatorsmartsortMQTT;


import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Type;
import java.net.InetAddress;
import java.util.ArrayList;
import java.util.List;





import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.wifi.WifiConfiguration;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.telephony.TelephonyManager;
import android.text.TextUtils;
import android.util.Log;
import android.view.KeyEvent;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;
import android.widget.TextView;

import org.greenrobot.eventbus.EventBus;
import org.greenrobot.eventbus.Subscribe;
import org.w3c.dom.Text;

import evolar.be.purolatorsmartsortMQTT.events.FetchDb;
import evolar.be.purolatorsmartsortMQTT.events.UIUpdater;

public class Splash extends Activity {
	
	private static final String TAG = "Splash Purolator";


	boolean isSoapActive = false;
	WifiManager wifiManager;
	

	static final int RESTART_REQUEST = 1;  // The request code

		
	TextView infoTextView;
	TextView serialTextView;
	Button okBtn;
	boolean canStart = false;
	boolean isTablet = false;
	
    
	private static final String PREFS_APPID = "AppID";
	public SharedPreferences prefs;
	public SharedPreferences.Editor prefsEditor;
    	


	@Override
	protected void onCreate(Bundle savedInstanceState) {
	
	    super.onCreate(savedInstanceState);
	    Log.w(TAG,"+++ onCreate ++++");

        EventBus.getDefault().unregister(this);

        prefs = PreferenceManager.getDefaultSharedPreferences(this);
        prefsEditor = prefs.edit();



        //inflate the base UI layer
		Log.d(TAG,"Manufacturer: " + Build.MANUFACTURER);
        Log.d(TAG,"Serial: " + PurolatorSmartsortMQTT.getsInstance().getConfigData().getSerialNumber());
		Log.d(TAG,"Device: " + Build.DEVICE);

//TODO Read wearable type from a local config file

    	if (Build.MANUFACTURER.equalsIgnoreCase("Vuzix")) {		//for Vuzix devices
			setContentView(R.layout.splash);
			infoTextView = (TextView) findViewById(R.id.splashView);
			serialTextView = (TextView) findViewById(R.id.serialView);


			infoTextView.setText(getResources().getString(R.string.fetchdata));
			serialTextView.setText("Serial: " + PurolatorSmartsortMQTT.getsInstance().getConfigData().getSerialNumber());

    	} else {
			//check if device is a tablet
			isTablet = checkEmulator();

//			TelephonyManager manager = (TelephonyManager)this.getSystemService(Context.TELEPHONY_SERVICE);
//			if(manager.getPhoneType() == TelephonyManager.PHONE_TYPE_NONE){
//				isTablet = true;
//			}else if (Build.SERIAL.equalsIgnoreCase("unknown")) {
//				isTablet = true;
//			}else {
//				isTablet = false;//
//			}
	        setContentView(R.layout.splash_tablet);
            infoTextView = (TextView) findViewById(R.id.splashViewTablet);
            infoTextView.setText(getResources().getString(R.string.fetchdata));
			serialTextView =(TextView) findViewById(R.id.serialViewTablet);
			serialTextView.setText("Serial: " + PurolatorSmartsortMQTT.getsInstance().getConfigData().getSerialNumber());


			TextView instruction=  (TextView) findViewById(R.id.scanInstructie);
            instruction.setText(getResources().getString(R.string.instruction));
            okBtn = (Button) findViewById(R.id.start_btn);
        	okBtn.setEnabled(false);
            
            okBtn.setOnClickListener(new OnClickListener(){

				@Override
				public void onClick(View v) {
					if (canStart){
					    Intent i;
						if (isTablet){
							i = new Intent(Splash.this, PurolatorActivityFixed.class);
						} else {
							i = new Intent(Splash.this, PurolatorActivityGlass.class);
						}
						i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
						Splash.this.startActivityForResult(i,RESTART_REQUEST);
					}					
				}

			});

    	}
        
         	    
        // check if there is a Wifi Connection.
    	// check is not done for a tablet/smartphone


  
    	if (!isTablet && getCurrentSsid(this) == null){

			infoTextView.setText("Geen Wifi netwerk, even geduld...");
			wifiManager = (WifiManager) this.getApplicationContext().getSystemService(Context.WIFI_SERVICE);
        		
			if (!wifiManager.isWifiEnabled()) {
				wifiManager.setWifiEnabled(true);
			}
	
			NetworkConnected connectEvent = new NetworkConnected();
			registerReceiver(connectEvent, new IntentFilter(WifiManager.NETWORK_STATE_CHANGED_ACTION));

			Log.d(TAG,"Wifi Connect started....");
				
    	} else {
			canStart = true;
			if (okBtn != null) okBtn.setEnabled(true);
			Intent i;
			if (isTablet){
				i = new Intent(Splash.this, PurolatorActivityFixed.class);
			} else {
				i = new Intent(Splash.this, PurolatorActivityGlass.class);
			}
			i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
			Splash.this.startActivityForResult(i,RESTART_REQUEST);
		}
	}

    /**
     *
    *    triggered when an UI Update is needed. This can be posted by different objects
    */
    @Subscribe
    public void onUIUpdaterEvent(final UIUpdater event){

        //first check if other activities are active, otherwise handle the event here

    }






	
	   public static void setIpAssignment(String assign , WifiConfiguration wifiConf)
			    throws SecurityException, IllegalArgumentException, NoSuchFieldException, IllegalAccessException{
			        setEnumField(wifiConf, assign, "ipAssignment");     
			    }

			    public static void setIpAddress(InetAddress addr, int prefixLength, WifiConfiguration wifiConf)
			    throws SecurityException, IllegalArgumentException, NoSuchFieldException, IllegalAccessException,
			    NoSuchMethodException, ClassNotFoundException, InstantiationException, InvocationTargetException{
			        Object linkProperties = getField(wifiConf, "linkProperties");
			        if(linkProperties == null)return;
			        Class laClass = Class.forName("android.net.LinkAddress");
			        Constructor laConstructor = laClass.getConstructor(new Class[]{InetAddress.class, int.class});
			        Object linkAddress = laConstructor.newInstance(addr, prefixLength);

			        ArrayList mLinkAddresses = (ArrayList)getDeclaredField(linkProperties, "mLinkAddresses");
			        mLinkAddresses.clear();
			        mLinkAddresses.add(linkAddress);        
			    }

			    public static void setGateway(InetAddress gateway, WifiConfiguration wifiConf)
			    throws SecurityException, IllegalArgumentException, NoSuchFieldException, IllegalAccessException, 
			    ClassNotFoundException, NoSuchMethodException, InstantiationException, InvocationTargetException{
			        Object linkProperties = getField(wifiConf, "linkProperties");
			        if(linkProperties == null)return;
			        Class routeInfoClass = Class.forName("android.net.RouteInfo");
			        Constructor routeInfoConstructor = routeInfoClass.getConstructor(new Class[]{InetAddress.class});
			        Object routeInfo = routeInfoConstructor.newInstance(gateway);

			        ArrayList mRoutes = (ArrayList)getDeclaredField(linkProperties, "mRoutes");
			        mRoutes.clear();
			        mRoutes.add(routeInfo);
			    }

			    public static void setDNS(InetAddress dns, WifiConfiguration wifiConf)
			    throws SecurityException, IllegalArgumentException, NoSuchFieldException, IllegalAccessException{
			        Object linkProperties = getField(wifiConf, "linkProperties");
			        if(linkProperties == null)return;

			        ArrayList<InetAddress> mDnses = (ArrayList<InetAddress>)getDeclaredField(linkProperties, "mDnses");
			        mDnses.clear(); //or add a new dns address , here I just want to replace DNS1
			        mDnses.add(dns); 
			    }

			    public static Object getField(Object obj, String name)
			    throws SecurityException, NoSuchFieldException, IllegalArgumentException, IllegalAccessException{
			        Field f = obj.getClass().getField(name);
			        Object out = f.get(obj);
			        return out;
			    }

			    public static Object getDeclaredField(Object obj, String name)
			    throws SecurityException, NoSuchFieldException,
			    IllegalArgumentException, IllegalAccessException {
			        Field f = obj.getClass().getDeclaredField(name);
			        f.setAccessible(true);
			        Object out = f.get(obj);
			        return out;
			    }  

			    public static void setEnumField(Object obj, String value, String name)
			    throws SecurityException, NoSuchFieldException, IllegalArgumentException, IllegalAccessException{
			        Field f = obj.getClass().getField(name);
			        f.set(obj, Enum.valueOf((Class<Enum>) f.getType(), value));
			    }
	
	
	
	@Override
	protected void onStart(){
		
		
		super.onStart();
		
		
	
		
	}


	@Override
	protected void onResume() {
	    super.onResume();
	    
        infoTextView.setVisibility(View.VISIBLE);

	}

	
	@Override
	protected void onPause() {
	    super.onPause();
//	    finish();
	}
	
	@Override
	protected void onStop() {
	    Log.w(TAG,"+++ onStop ++++");

	    super.onStop();
	}
	

    @Override
    protected void onNewIntent(Intent intent)
    {
    	Log.d(TAG,"OnNewIntent, stopping Splash");
    	super.onNewIntent(intent);
        finish();
    }

    @Override
	public void onBackPressed(){
    	Log.d(TAG,"Backpressed");
    	finish();
    	
    }
    
    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        // Check which request we're responding to

    }    
    
    
    
    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {

	    	
	    Log.d(TAG,"Key Down: " + keyCode);	
	    
	    
	    if (keyCode == 22 || keyCode == 24) {
	    	
	    	if (canStart){
			    Intent i;
				i = new Intent(this, PurolatorActivityGlass.class);
	            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);            		                   
				this.startActivityForResult(i,RESTART_REQUEST);  
	    	}
		    event.startTracking();
			return true;
	    }        
	    return super.onKeyDown(keyCode, event);
    }

	// return a connected SSID
    
    public static String getCurrentSsid(Context context) {
    	
  	  String ssid = null;
  	  ConnectivityManager connManager = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
  	  NetworkInfo networkInfo = connManager.getNetworkInfo(ConnectivityManager.TYPE_WIFI);
  	  if (networkInfo.isConnected()) {
  	    final WifiManager wifiManager = (WifiManager) context.getSystemService(Context.WIFI_SERVICE);
  	    final WifiInfo connectionInfo = wifiManager.getConnectionInfo();
  	    if (connectionInfo != null && !TextUtils.isEmpty(connectionInfo.getSSID())) {
  	      ssid = connectionInfo.getSSID();
  	    }
  	  }
  	  return ssid;
  	}
  
    class NetworkConnected extends  BroadcastReceiver {
            public void onReceive(Context context, Intent intent) {
            	
            	
                NetworkInfo networkInfo = intent.getParcelableExtra(WifiManager.EXTRA_NETWORK_INFO);
            	infoTextView.setText("Wifi connectie status: " + networkInfo.getState().name());
                Log.d(TAG, "NetworkInfo " + String.valueOf(networkInfo));                
                if(networkInfo.isConnected()) {
                    unregisterReceiver(this);

                    infoTextView.setText(getResources().getString(R.string.fetchdata));
                                        
                    canStart = true;

                    Log.d(TAG,"Current SSID " + getCurrentSsid(Splash.this));
					Intent i;
					if (isTablet){
						i = new Intent(Splash.this, PurolatorActivityFixed.class);
					} else {
						i = new Intent(Splash.this, PurolatorActivityGlass.class);
					}
					i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
					Splash.this.startActivityForResult(i,RESTART_REQUEST);


				} else if (networkInfo.getReason() != null){
                	infoTextView.setText("Wifi connection error: " + networkInfo.getState().name());                	
                }                            	
            }
    }

    //check if an emulator or real device is used
	// if it is real device, then also check if it is a smartphone or a tablet
	private boolean checkEmulator() {
		String buildDetails = (Build.FINGERPRINT + Build.DEVICE + Build.MODEL + Build.BRAND + Build.PRODUCT + Build.MANUFACTURER + Build.HARDWARE).toLowerCase();
		Log.d(TAG,"Check builddetails: " + buildDetails);
		if (buildDetails.contains("generic")
				|| buildDetails.contains("unknown")
				|| buildDetails.contains("emulator")
				|| buildDetails.contains("sdk")
				|| buildDetails.contains("genymotion")
				|| buildDetails.contains("x86") // this includes vbox86
				|| buildDetails.contains("goldfish")
				|| buildDetails.contains("test-keys"))
			return true;
		TelephonyManager tm = (TelephonyManager) this.getSystemService(Context.TELEPHONY_SERVICE);
		String operator = tm.getNetworkOperatorName().toLowerCase();
		if (operator.equals("android"))
			return true;
		if (new File("/init.goldfish.rc").exists())
			return true;

		return false;

	}

}

