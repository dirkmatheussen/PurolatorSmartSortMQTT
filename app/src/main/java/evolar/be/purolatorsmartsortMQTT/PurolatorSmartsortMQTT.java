package evolar.be.purolatorsmartsortMQTT;

import android.app.Application;
import android.content.BroadcastReceiver;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.net.NetworkInfo;
import android.net.wifi.WifiManager;
import android.os.Build;
import android.util.Log;

import com.google.gson.Gson;

import org.greenrobot.eventbus.EventBus;
import org.greenrobot.eventbus.Subscribe;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Properties;
import java.util.Timer;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

import evolar.be.purolatorsmartsortMQTT.events.DatabaseName;
import evolar.be.purolatorsmartsortMQTT.events.FetchDb;
import evolar.be.purolatorsmartsortMQTT.events.FixedMessage;
import evolar.be.purolatorsmartsortMQTT.events.FixedScanResult;
import evolar.be.purolatorsmartsortMQTT.events.GlassDeviceInfo;
import evolar.be.purolatorsmartsortMQTT.events.GlassMessage;
import evolar.be.purolatorsmartsortMQTT.events.Logger;
import evolar.be.purolatorsmartsortMQTT.events.RPMLookUp;
import evolar.be.purolatorsmartsortMQTT.events.UIUpdater;


/**
 * Created by Dirk on 17/03/16.
 */
public class PurolatorSmartsortMQTT extends Application {

    private static final String TAG = "PurolatorSmartsortMQTT";
    private static final boolean D = true;

    //Parameters, read from INI file

    public int DISTANCE = 150;             //in cm
    public int DISTANCE_TIMER_GREEN = 10;             //in seconds
    public int DISTANCE_TIMER_RED = 0;             //in seconds


    //Parameters, read from the config.xml file


    //Create the eventListeners
    BarcodeDispatch mBarcodeDispatch = new BarcodeDispatch();
    HandleRingBarcode mHandleRingBarcode = new HandleRingBarcode();
    LookupFixedBarcodeOCR mLookupFixedBarcodeOCR = new LookupFixedBarcodeOCR();
    LookupFixedBarcodePIN mLookupFixedBarcodePIN = new LookupFixedBarcodePIN();
    LookupFixedBarcodePRE mLookupFixedBarcodePRE = new LookupFixedBarcodePRE();
    LookupFixedBarcodeHVR mLookupFixedBarcodeHVR = new LookupFixedBarcodeHVR();
    LookupFixedBarcodeHFPU mLookupFixedBarcodeHFPU = new LookupFixedBarcodeHFPU();
    LookupFixedBarcodeREM mLookupFixedBarcodeREM = new LookupFixedBarcodeREM();
    LookupFixedBarcodeRPM mlookupFixedBarcodeRPM = new LookupFixedBarcodeRPM();
    LookupFixedBarcodeSSLWS mLookupFixedBarcodeSSLWS = new LookupFixedBarcodeSSLWS();
    LookupFixedBarcodeCROSS mLookupFixedBarcodeCROSS = new LookupFixedBarcodeCROSS();
    LookupFixedBarcodePOST mLookupFixedBarcodePOST = new LookupFixedBarcodePOST();
    LookupFixedBarcodeADDR mLookupFixedBarcodeADDR = new LookupFixedBarcodeADDR();


    // conditionally created eventlistners
//    MQTTFixComms mqttFixComms;
    MQTTFixPahoComms mqttFixComms;
//    MQTTGlassComms mqttGlassComms;
    MQTTGlassPahoComms mqttGlassComms;
    boolean mqttRestart = false;            //indicator to restart MQTT in case an error occured
    HandleFixedResult handleFixedResult;

    ArrayList<String> streetTypes = new ArrayList<String>();

    FTPComms mFtpComs = new FTPComms(this);

    //create the singleton class that manages all scanned packages
    public static PackagesList packagesList = PackagesList.getInstance();

    //create the singleton class for the packages, ready to put into a truck/shelf
    private static Label packageInShelf;

    //List of all OCRed packaged
    ArrayList<OCRData> ocrDatas = new ArrayList<OCRData>();

    //create object that keeps the config data
    ConfigData configData = new ConfigData();

    //Collection of all connected glasses, with their route information
    ArrayList<GlassDeviceInfo> connectedGlasses = new ArrayList<GlassDeviceInfo>();

    //A singleton instance of the application class for easy access in other places
    private static PurolatorSmartsortMQTT sInstance;

    // Parameters

    public static final String PIN_DBTYPE = "_PINFILE";
    public static final String RPM_DBTYPE = "_RPM";
    public static final String PRE_DBTYPE = "_PREPRINT";
    public static final String HFPUMASTER_DBTYPE = "_HFPULOCATIONMASTER";
    public static final String HFPUPC_DBTYPE = "_HFPULOCTO";
    public static final String PARK_DBTYPE ="_PARKING";
    public static final String PST_DBTYPE ="_PSTRPM";


    public static final String NONE_TYPE = "NONE";          //could not identift the type used

    public static final String EMPLOYEE_TYPE = "EMP";       // employee scan
    public static final String INIT_DBTYPE = "INIT";        // init file per device

    public static final String UPD_STOPSPINNER =  "stopspinner";
    public static final String UPD_STATUSPINNER =  "statusspinner";
    public static final String UPD_ERROR =  "errormessage";
    public static final String UPD_TOPSCREEN =  "topscreenupdate";
    public static final String UPD_CLEARSCREEN =  "clearcreenupdate";
    public static final String UPD_BOTTOMSCREEN =  "bottomscreenupdate";
    public static final String UPD_POPUPSCREEN =  "popupscreenupdate";


    public static final int MAX_MQTTRETRY_COUNT = 10;          //number of retry's to restore MQTT Connection
    public int mqttRetryCount = 0;


    private String PINdatabaseName;
    private String RPMdatabaseName;
    private String PREdatabaseName;
    private String HFPUMASTERdatabaseName;
    private String HFPUPCdatabaseName;
    private String PARKdatabaseName;
    private String PSTdatabaseName;

    private boolean PINtransactionActive = false;
    private boolean RPMtransactionActive = false;
    private boolean PREtransactionActive = false;
    private boolean HFPUMASTERtransactionActive = false;
    private boolean HFPUPCtransactionActive = false;
    private boolean PARKtransactionActive = false;
    private boolean PSTtransactionActive = false;


    private boolean refreshPINDatabase = false;
    private boolean refreshRPMDatabase = false;
    private boolean refreshPREDatabase = false;
    private boolean refreshHFPUMASTERDatabase = false;
    private boolean refreshHFPUPCDatabase = false;
    private boolean refreshPARKDatabase = false;
    private boolean refreshPSTDatabase = false;

    public boolean movePINDatabase = false;
    public boolean moveRPMDatabase = false;
    public boolean movePREDatabase = false;
    public boolean moveHFPUMASTERDatabase = false;
    public boolean moveHFPUPCDatabase = false;
    public boolean movePARKDatabase = false;
    public boolean movePSTDatabase = false;


    private SQLiteDatabase PINdatabase;
    private SQLiteDatabase RPMdatabase;
    private SQLiteDatabase PREdatabase;
    private SQLiteDatabase HFPUMASTERdatabase;
    private SQLiteDatabase HFPUPCdatabase;
    private SQLiteDatabase PARKdatabase;
    private SQLiteDatabase PSTdatabase;

    private boolean databasesLoaded = false;

    ScheduledExecutorService scheduleList;
    ScheduledExecutorService scheduleLogger;

    double PUDROSpeed;              //in cm per second
    boolean stopped = false;        //indicate that the app has onStop or onStart event

    // placeholder to keep GlassMessages that are not send yet. Must be thread-safe
    List<GlassMessage> failedGlassMessages = Collections.synchronizedList(new ArrayList<GlassMessage>());
//    private ArrayList<GlassMessage> failedGlassMessages = new ArrayList<GlassMessage>();

    // Class for WifiReconnection
    NetworkConnected connectEvent = new NetworkConnected();
    private long wifiScheduleTimer = 1000*10;			//retry a wifi connection every 10 seconds
    private Timer wifiScheduler = new Timer();


    private int maxTotalScans = 0;                      //total number of parcels scanned by ARC

    @Override
    public void onCreate() {
        super.onCreate();
/*
        Bugfender.init(this, "q1OsrXouEUapiYQR2vmoABKfIfEgch2f", BuildConfig.DEBUG);
        Bugfender.enableLogcatLogging();
        Bugfender.enableUIEventLogging(this);
        Bugfender.enableCrashReporting();
*/
        sInstance = this;
        EventBus.getDefault().register(this);

 //       deleteDatabases();  //--> no need to comment out once in production

        SharedPreferences settings = getApplicationContext().getSharedPreferences("storedLabels", 0);
        SharedPreferences.Editor editor = settings.edit();
        editor.clear();
        editor.apply();


        Log.e(TAG,"+++SERIAL NUMBER: " + getConfigData().getSerialNumber()+"++++");

    }

    @Override
    public void onTerminate() {
        super.onTerminate();
        if (D) Log.d(TAG,"+++ OnTerminate +++");
        stopProcesses();

        try {
            unregisterReceiver(connectEvent);
        } catch (IllegalArgumentException e){
            e.printStackTrace();
        }


    }

    public void stopProcesses(){
        EventBus.getDefault().unregister(this);
        if (scheduleList !=null) scheduleList.shutdown();
        if (scheduleLogger !=null) scheduleLogger.shutdown();

        if (mqttFixComms !=null) mqttFixComms.stopMQTTConnection();
        if (mqttGlassComms !=null) mqttGlassComms.stopMQTTConnection();


    }

    public ConfigData getConfigData() {
        return configData;
    }

    public void setConfigData(ConfigData configData) {
        this.configData = configData;
    }

    public static PurolatorSmartsortMQTT getsInstance() {
        return sInstance;
    }

    public static void setsInstance(PurolatorSmartsortMQTT sInstance) {
        PurolatorSmartsortMQTT.sInstance = sInstance;
    }

    public Label getPackageInShelf() {
        return packageInShelf;
    }

    public void setPackageInShelf(Label packageInShelf) {
        PurolatorSmartsortMQTT.packageInShelf = packageInShelf;
    }

    public boolean isDatabasesLoaded() {
        return databasesLoaded;
    }

    public void setDatabasesLoaded(boolean databasesLoaded) {
        this.databasesLoaded = databasesLoaded;
    }

    public boolean isMqttRestart() {
        return mqttRestart;
    }

    public void setMqttRestart(boolean mqttRestart) {
        this.mqttRestart = mqttRestart;
    }

    public ArrayList<GlassDeviceInfo> getConnectedGlasses() {
        return connectedGlasses;
    }

    public void setConnectedGlasses(ArrayList<GlassDeviceInfo> connectedGlasses) {
        this.connectedGlasses = connectedGlasses;
    }

    public String getDatabaseName(String databaseType) {

        switch (databaseType) {

            case PIN_DBTYPE:
                return PINdatabaseName;

            case RPM_DBTYPE:
                return RPMdatabaseName;

            case HFPUMASTER_DBTYPE:
                return HFPUMASTERdatabaseName;

            case HFPUPC_DBTYPE:
                return HFPUPCdatabaseName;

            case PRE_DBTYPE:
                return PREdatabaseName;

            case PARK_DBTYPE:
                return PARKdatabaseName;

            case PST_DBTYPE:
                return PSTdatabaseName;
        }


        return null;
    }

    public void setDatabaseName(String databaseType,String databaseName) {

        switch (databaseType) {

            case PIN_DBTYPE:
                this.PINdatabaseName= databaseName;
                break;
            case RPM_DBTYPE:
                this.RPMdatabaseName = databaseName;
                break;
            case HFPUMASTER_DBTYPE:
                this.HFPUMASTERdatabaseName = databaseName;
                break;
            case HFPUPC_DBTYPE:
                this.HFPUPCdatabaseName = databaseName;
                break;
            case PRE_DBTYPE:
                this.PREdatabaseName = databaseName;
                break;
            case PARK_DBTYPE:
                this.PARKdatabaseName = databaseName;
                break;
            case PST_DBTYPE:
                this.PSTdatabaseName = databaseName;
                break;

        }


    }



    public void addFailedGlassMessage(GlassMessage failedGlassMessage){
        failedGlassMessages.add(failedGlassMessage);
    }

    public boolean hasNextFailedGlassMessage(){

      return failedGlassMessages.iterator().hasNext();

    };

    public List<GlassMessage> getFailedGlassMessages(){
        return failedGlassMessages;
    }

    public GlassMessage getNextFailedGlassMessage(){

        GlassMessage failedMessage = null;

        if (failedGlassMessages.iterator().hasNext()){
            failedMessage = failedGlassMessages.iterator().next();
            failedGlassMessages.remove(failedMessage);

        }

        return failedMessage;

    }



    /**
     * Get the Pudro Speed in cm/sec
     *
     * @return
     */
    public double getPUDROSpeed() {
        return PUDROSpeed;
    }

    public void setPUDROSpeed(double PUDROSpeed) {
        this.PUDROSpeed = PUDROSpeed;
    }


    public boolean isStopped() {
        return stopped;
    }

    public void setStopped(boolean stopped) {
        this.stopped = stopped;
    }

    public int getMaxTotalScans() {
        return maxTotalScans;
    }

    public void setMaxTotalScans(int maxTotalScans) {
        this.maxTotalScans = maxTotalScans;
    }

    public ArrayList<String> getStreetTypes() {
        return streetTypes;
    }

    public void setStreetTypes(ArrayList<String> streetTypes) {
        this.streetTypes = streetTypes;
    }

    public SQLiteDatabase getDatabase(String databaseType) {

        switch (databaseType) {

            case PIN_DBTYPE:
                return PINdatabase;

            case RPM_DBTYPE:
                return RPMdatabase;

            case HFPUMASTER_DBTYPE:
                return HFPUMASTERdatabase;

            case HFPUPC_DBTYPE:
                return HFPUPCdatabase;

            case PRE_DBTYPE:
                return PREdatabase;

            case PARK_DBTYPE:
                return PARKdatabase;

            case PST_DBTYPE:
                return PSTdatabase;

        }


        return null;

    }


    public boolean isTransactionActive(String databaseType) {

        switch (databaseType) {

            case PIN_DBTYPE:
                return PINtransactionActive;

            case RPM_DBTYPE:
                return RPMtransactionActive;

            case HFPUMASTER_DBTYPE:
                return HFPUMASTERtransactionActive;

            case HFPUPC_DBTYPE:
                return HFPUPCtransactionActive;

            case PRE_DBTYPE:
                return PREtransactionActive;

            case PARK_DBTYPE:
                return PARKtransactionActive;

            case PST_DBTYPE:
                return PSTtransactionActive;

        }


        return false;
    }

    public void setTransactionActive(String databaseType,boolean transactionActive) {

        switch (databaseType) {

            case PIN_DBTYPE:
                this.PINtransactionActive = transactionActive;
                if (!transactionActive && refreshPINDatabase) {
                    setDatabaseFile(databaseType, PINdatabaseName);
                }
                break;
            case RPM_DBTYPE:
                this.RPMtransactionActive = transactionActive;
                if (!transactionActive && refreshRPMDatabase) {
                    setDatabaseFile(databaseType, RPMdatabaseName);
                }
                break;

            case HFPUMASTER_DBTYPE:
                this.HFPUMASTERtransactionActive = transactionActive;
                if (!transactionActive && refreshHFPUMASTERDatabase) {
                    setDatabaseFile(databaseType, HFPUMASTERdatabaseName);
                }
                break;

            case HFPUPC_DBTYPE:
                this.HFPUPCtransactionActive = transactionActive;
                if (!transactionActive && refreshHFPUPCDatabase) {
                    setDatabaseFile(databaseType, HFPUPCdatabaseName);
                }
                break;
            case PRE_DBTYPE:
                this.PREtransactionActive = transactionActive;
                if (!transactionActive && refreshPREDatabase) {
                    setDatabaseFile(databaseType, PREdatabaseName);
                }
                break;
            case PARK_DBTYPE:
                this.PARKtransactionActive = transactionActive;
                if (!transactionActive && refreshPARKDatabase) {
                    setDatabaseFile(databaseType, PARKdatabaseName);
                }
                break;

            case PST_DBTYPE:
                this.PSTtransactionActive = transactionActive;
                if (!transactionActive && refreshPSTDatabase) {
                    setDatabaseFile(databaseType, PSTdatabaseName);
                }
                break;

        }


    }

    /**
     * Update the RPM database with address information handled by remediation
     *
     * @param event
     */

    @Subscribe
    public void onRPMLookupEvent(RPMLookUp event){

        updatePSTStreets(event);

    }


    /**
     * Message received from a new registered Glass or a Glass to unregister
     * Message comes from MQTTFixComms or from getInitValue (from this class)
     * @param event
     */
    @Subscribe(priority = 2)    //highest priority
    public void onGlassDeviceInfoEvent(GlassDeviceInfo event){


        if (D) Log.d(TAG,"Handle the connectedGlasses");

        if (!event.getActive()){
            for(GlassDeviceInfo glassDeviceInfo:connectedGlasses){
                if (glassDeviceInfo.getDeviceName().equals(event.getDeviceName())){
                    connectedGlasses.remove(glassDeviceInfo);
                    break;          //stop the loop, otherwise it gives a java.util.ConcurrentModificationException
                }
            }

        } else{
            for(GlassDeviceInfo glassDeviceInfo:connectedGlasses){
                //check if glass is already registered
                if (glassDeviceInfo.getDeviceName().equals(event.getDeviceName())){
                    glassDeviceInfo.setAssignedRoutes(event.getAssignedRoutes());
                    return;
                }
            }

            connectedGlasses.add(event);
        }
        //update the screen --> done by capturing onGlassDeviceInfoEvent in PurolatorActivityFixed

        for(GlassDeviceInfo glassDeviceInfo:connectedGlasses){
            if(D) Log.d(TAG,"Connected Device: " + glassDeviceInfo.getDeviceName());
        }
    }


    @Subscribe
    public void onDatabaseNameEvent(DatabaseName event){

        if(D) Log.i(TAG,"Database event received, open the database for: " + event.getDatabaseName());

        switch (event.getDatabaseType()){
            case PIN_DBTYPE:
                PINdatabaseName = event.getDatabaseName();
                setDatabaseFile(PIN_DBTYPE, PINdatabaseName);
                break;
            case RPM_DBTYPE:
                RPMdatabaseName = event.getDatabaseName();
                setDatabaseFile(RPM_DBTYPE, RPMdatabaseName);
                streetTypes = fetchStreetTypes();
                break;
            case HFPUMASTER_DBTYPE:
                HFPUMASTERdatabaseName = event.getDatabaseName();
                setDatabaseFile(HFPUMASTER_DBTYPE, HFPUMASTERdatabaseName);
                break;
            case HFPUPC_DBTYPE:
                HFPUPCdatabaseName = event.getDatabaseName();
                setDatabaseFile(HFPUPC_DBTYPE, HFPUPCdatabaseName);
                break;
            case PRE_DBTYPE:
                PREdatabaseName = event.getDatabaseName();
                setDatabaseFile(PRE_DBTYPE, PREdatabaseName);
                break;
            case PARK_DBTYPE:
                PARKdatabaseName = event.getDatabaseName();
                setDatabaseFile(PARK_DBTYPE, PARKdatabaseName);
                break;
            case PST_DBTYPE:
                PSTdatabaseName = event.getDatabaseName();
                setDatabaseFile(PST_DBTYPE, PSTdatabaseName);
                break;
            case INIT_DBTYPE:                   //read the config file

                if (getInitValues(event.getDatabaseName())) {


                    UIUpdater uiUpdater = new UIUpdater();
                    uiUpdater.setUpdateType(PurolatorSmartsortMQTT.UPD_ERROR);
                    uiUpdater.setErrorCode("NEWINFO");
                    uiUpdater.setErrorMessage("Terminal ID: " +configData.getTerminalID());
                    EventBus.getDefault().postSticky(uiUpdater);            //do a sticky post, the activity is perhaps not active yet

                    if (configData.getDeviceType().equals("GLASS")){
                        if (configData.isGlassRemediation()){       //load the database locally if remediation is done in glass device
                            EventBus.getDefault().post(new FetchDb(PIN_DBTYPE, false));      //show feedback on screen
                            EventBus.getDefault().post(new FetchDb(RPM_DBTYPE, false));      //show feeback on screen
                            EventBus.getDefault().post(new FetchDb(PST_DBTYPE, false));      //show feeback on screen
                            EventBus.getDefault().post(new FetchDb(PRE_DBTYPE,false));
                            EventBus.getDefault().post(new FetchDb(HFPUMASTER_DBTYPE,false));
                            EventBus.getDefault().post(new FetchDb(HFPUPC_DBTYPE,false));
                            EventBus.getDefault().post(new FetchDb(PARK_DBTYPE,false));
                            startSchedulers("GLASS");



                        }
                        mqttGlassComms = new MQTTGlassPahoComms();      //instantiate the subscriber object
                        handleFixedResult = new HandleFixedResult();
                        if (!configData.isGlassRemediation()){
                            UIUpdater uiUpdate = new UIUpdater();
                            uiUpdate.setUpdateType(PurolatorSmartsortMQTT.UPD_STOPSPINNER);
                            EventBus.getDefault().postSticky(uiUpdate);
                            PurolatorSmartsortMQTT.getsInstance().setDatabasesLoaded(true);     //indicate all databases are loaded

                        }

                    } else if (configData.getDeviceType().equals("FIXED")){  //for the fixed device "FIXED"

                        configData.setDeviceName(configData.getDeviceType()+"_" + configData.getTerminalID());

//                        mqttFixComms = new MQTTFixComms();      //instantiate the publisher object
                        mqttFixComms = new MQTTFixPahoComms();      //instantiate the publisher object
                        //FTPComms is triggered & when database is found, DatabaseName event is triggered
                        //get the last version of the database from the FP Server
                        EventBus.getDefault().post(new FetchDb(PIN_DBTYPE, false));      //show feedback on screen
                        EventBus.getDefault().post(new FetchDb(RPM_DBTYPE, false));      //show feeback on screen
                        EventBus.getDefault().post(new FetchDb(PST_DBTYPE, false));      //show feeback on screen
                        EventBus.getDefault().post(new FetchDb(PRE_DBTYPE,false));
                        EventBus.getDefault().post(new FetchDb(HFPUMASTER_DBTYPE,false));
                        EventBus.getDefault().post(new FetchDb(HFPUPC_DBTYPE,false));
                        EventBus.getDefault().post(new FetchDb(PARK_DBTYPE,false));
                        startSchedulers("FIXED");

                    }



                } else {
                    UIUpdater uiUpdater = new UIUpdater();
                    uiUpdater.setUpdateType(PurolatorSmartsortMQTT.UPD_ERROR);
                    uiUpdater.setErrorMessage("Can not read the configuration file");
                    EventBus.getDefault().postSticky(uiUpdater);            //do a sticky post, the activity is perhaps not active yet
                }
                break;
        }


    }

    /**
     * Start the schedulers for the Tablet
     *
     *
     */

    public void startSchedulers(String type){


        if(D) Log.d(TAG,"Start Schedulers of type:  " + type);

        if (type.equals("FIXED")) {
            //schedule an upload every 15 minutes.
            scheduleList = Executors.newScheduledThreadPool(5);
            ScheduledFuture scheduledFuture = scheduleList.scheduleAtFixedRate(new Runnable() {

                public void run() {

                    EventBus.getDefault().post(new FetchDb(PIN_DBTYPE, true));      //do not show feedback on screen
                    EventBus.getDefault().post(new FetchDb(RPM_DBTYPE, true));      //do not show feeback on screen
                    EventBus.getDefault().post(new FetchDb(PST_DBTYPE, true));      //do not show feeback on screen
                    EventBus.getDefault().post(new FetchDb(PRE_DBTYPE, true));
                    EventBus.getDefault().post(new FetchDb(HFPUMASTER_DBTYPE, true));
                    EventBus.getDefault().post(new FetchDb(HFPUPC_DBTYPE, true));
                    EventBus.getDefault().post(new FetchDb(PARK_DBTYPE, true));
                }
            }, 7, 15, TimeUnit.MINUTES);          //after 7 minutes of grace period

            scheduleLogger = Executors.newScheduledThreadPool(3);
            ScheduledFuture scheduleLogFuture = scheduleLogger.scheduleAtFixedRate(new Runnable() {
                @Override
                public void run() {
                    Logger logger = new Logger();
                    logger.setLogType("FTPLOG");
                    EventBus.getDefault().post(logger);
                }
            }, 120, 120, TimeUnit.SECONDS);       //log every 2 minutes after 2 minutes of grace period
        }

        if (type.equals("GLASS")) {
            //schedule an upload every 15 minutes.
            scheduleList = Executors.newScheduledThreadPool(5);
            ScheduledFuture scheduledFuture = scheduleList.scheduleAtFixedRate(new Runnable() {

                public void run() {

                    EventBus.getDefault().post(new FetchDb(PIN_DBTYPE, true));      //do not show feedback on screen
                    EventBus.getDefault().post(new FetchDb(RPM_DBTYPE, true));      //do not show feeback on screen
                    EventBus.getDefault().post(new FetchDb(PST_DBTYPE, true));      //do not show feeback on screen
                    EventBus.getDefault().post(new FetchDb(PRE_DBTYPE, true));
                    EventBus.getDefault().post(new FetchDb(HFPUMASTER_DBTYPE, true));
                    EventBus.getDefault().post(new FetchDb(HFPUPC_DBTYPE, true));
                    EventBus.getDefault().post(new FetchDb(PARK_DBTYPE, true));
                }
            }, 7, 15, TimeUnit.MINUTES);          //after 7 minutes of grace period

            scheduleLogger = Executors.newScheduledThreadPool(3);
            ScheduledFuture scheduleLogFuture = scheduleLogger.scheduleAtFixedRate(new Runnable() {
                @Override
                public void run() {
                    Logger logger = new Logger();
                    logger.setLogType("FTPLOG");
                    EventBus.getDefault().post(logger);
                }
            }, 120, 120, TimeUnit.SECONDS);       //log every 2 minutes after 2 minutes of grace period
        }

    }


    /**
     * Read in the initvalues, fetched from the FTP server
     *
     * @param fileName
     */

    private boolean getInitValues(String fileName){
        Properties props = new Properties();


        try {

            File config = new File(fileName);


            InputStream in = new FileInputStream(config);
            props.loadFromXML(in);


            configData.setTerminalID(props.getProperty("TERMINALID","000"));

            String[] routes = props.getProperty("ROUTES","AAA;BBB;CCC").split("[;]");
            for (String route : routes) {
                configData.getValidRoutes().add(route);

            }

            configData.setPudroZone(props.getProperty("PUDROZONE","000"));
            configData.setDeviceType(props.getProperty("DEVICETYPE","GLASS"));
            configData.setDeviceName(configData.getDeviceType()+"_"+ props.getProperty("DEVICENAME", Build.SERIAL));
            if (props.getProperty("SENDLOGCAT","FALSE").equals("TRUE")){
                configData.setSendLogcat(true);
            } else{
                configData.setSendLogcat(false);
            }

            if (props.getProperty("WAVEVALIDATION","FALSE").equals("TRUE")){
                configData.setWaveValidation(true);
            } else{
                configData.setWaveValidation(false);
            }

            if (props.getProperty("SHELFVALIDATION","TRUE").equals("TRUE")){
                configData.setShelfValidation(true);
            } else{
                configData.setShelfValidation(false);
            }

            if (props.getProperty("POSTALCODELOOKUP","TRUE").equals("TRUE")){
                configData.setPostalCodeLookup(true);
            } else{
                configData.setPostalCodeLookup(false);
            }

            if (props.getProperty("SMARTGLASS","TRUE").equals("TRUE")){
                configData.setSmartGlass(true);
            } else{
                configData.setSmartGlass(false);
            }

            if (props.getProperty("SLAVEMODE","FALSE").equals("TRUE")){
                configData.setSlaveMode(true);
            } else{
                configData.setSlaveMode(false);
            }

            if (props.getProperty("AUTORESTART","FALSE").equals("TRUE")){
                configData.setAutorestart(true);
            } else{
                configData.setAutorestart(false);
            }

            if (props.getProperty("GLASSREMEDIATION","TRUE").equals("TRUE")){
                configData.setGlassRemediation(true);
            } else{
                configData.setGlassRemediation(false);
            }

/**
            if (configData.getDeviceType().equals("FIXED")) {
                configData.getFtpParams().setFTP_PORT(Integer.valueOf(props.getProperty("FTPPORT", "449")));
                configData.getFtpParams().setFTP_USER(props.getProperty("FTPUSER", "Google"));
                configData.getFtpParams().setFTP_URL(props.getProperty("FTPURL", "ftp2.purolator.com"));
                configData.getFtpParams().setFTP_PWSD(props.getProperty("FTPPSWD", "r9Dx(6LqgR"));
            }
*/
            configData.getMqttParams().setMQTT_URL(props.getProperty("MQTTURL","iot.eclipse.org"));
            configData.getMqttParams().setMQTT_PORT(Integer.valueOf(props.getProperty("MQTTPORT", "1883")));

            if (configData.getDeviceType().equals("GLASS")) {
                this.DISTANCE = Integer.valueOf(props.getProperty("DISTANCE", "0"));                   // in cm
                this.DISTANCE_TIMER_GREEN = Integer.valueOf(props.getProperty("DISTANCE_GREEN", "10"));  //in seconds
                this.DISTANCE_TIMER_RED = Integer.valueOf(props.getProperty("DISTANCE_RED", "0"));  //in seconds
            }

            in.close();


        } catch (FileNotFoundException e) {
            Log.e(TAG, "File not found: " + e.toString());

            return false;
        } catch (IOException e) {
            Log.e(TAG, "Can not read file: " + e.toString());
            return false;
        }

        return true;




    }

    /**
     * Delete all databases
     *
     */

    public void deleteDatabases(){


        File databases[] = getFilesDir().listFiles();

        for (File database:databases){

            if (D) Log.d(TAG,"Delete database: " + database.getName());
            if(!database.getName().endsWith((".xml"))){               //keep the XML config file
                database.delete();
            }

        }

    }

    /**
     * Set the  database File and open the database
     * @param databaseFile
     */
    public void setDatabaseFile(String databaseType, String databaseFile){

        if(D) Log.d(TAG,"Database is found: " + databaseFile + " for type: " + databaseType);

        switch (databaseType){
            case PIN_DBTYPE:
                if (!this.PINtransactionActive) {

                    SQLiteDatabase tempDatabase = SQLiteDatabase.openDatabase(databaseFile, null, SQLiteDatabase.OPEN_READWRITE);

                    if (tempDatabase != null) {
                        this.movePINDatabase = true;
                        if (PINdatabase != null) PINdatabase.close();

                        PINdatabase = tempDatabase;
                        this.movePINDatabase = false;
                    }
                } else {
                    refreshPINDatabase = true;
                }
                break;
            case RPM_DBTYPE:
                if (!this.RPMtransactionActive) {
                    SQLiteDatabase tempDatabase = SQLiteDatabase.openDatabase(databaseFile, null, SQLiteDatabase.OPEN_READWRITE);

                    if (tempDatabase != null) {
                        this.moveRPMDatabase = true;
                        if (RPMdatabase != null) RPMdatabase.close();

                        RPMdatabase = tempDatabase;
                        this.moveRPMDatabase = false;
                    }
                } else {
                    refreshRPMDatabase = true;
                }
                break;
            case HFPUMASTER_DBTYPE:
                if (!this.HFPUMASTERtransactionActive) {
                    SQLiteDatabase tempDatabase = SQLiteDatabase.openDatabase(databaseFile, null, SQLiteDatabase.OPEN_READWRITE);

                    if (tempDatabase != null) {
                        this.moveHFPUMASTERDatabase = true;
                        if (HFPUMASTERdatabase != null) HFPUMASTERdatabase.close();
                        HFPUMASTERdatabase = tempDatabase;
                        this.moveHFPUMASTERDatabase = false;
                    }
                } else {
                    refreshHFPUMASTERDatabase = true;
                }
                break;
            case HFPUPC_DBTYPE:
                if (!this.HFPUPCtransactionActive) {
                    SQLiteDatabase tempDatabase = SQLiteDatabase.openDatabase(databaseFile, null, SQLiteDatabase.OPEN_READWRITE);

                    if (tempDatabase != null) {
                        this.moveHFPUPCDatabase = true;

                        if (HFPUPCdatabase != null) HFPUPCdatabase.close();

                        HFPUPCdatabase = tempDatabase;
                        this.moveHFPUPCDatabase = false;
                    }
                } else {
                    refreshHFPUPCDatabase = true;
                }
                break;
            case PRE_DBTYPE:
                if (!this.PREtransactionActive) {
                    SQLiteDatabase tempDatabase = SQLiteDatabase.openDatabase(databaseFile, null, SQLiteDatabase.OPEN_READWRITE);

                    if (tempDatabase != null) {
                        this.movePREDatabase = true;

                        if (PREdatabase != null) PREdatabase.close();

                        PREdatabase = tempDatabase;
                        this.movePREDatabase = false;

                    }
                } else {
                    refreshPREDatabase = true;
                }
                break;
            case PARK_DBTYPE:
                if (!this.PARKtransactionActive) {
                    SQLiteDatabase tempDatabase = SQLiteDatabase.openDatabase(databaseFile, null, SQLiteDatabase.OPEN_READWRITE);

                    if (tempDatabase != null) {
                        this.movePARKDatabase = true;

                        if (PARKdatabase != null) PARKdatabase.close();

                        PARKdatabase = tempDatabase;
                        this.movePARKDatabase = false;

                    }
                } else {
                    refreshPARKDatabase = true;
                }
                break;
            case PST_DBTYPE:
                if (!this.PSTtransactionActive) {
                    SQLiteDatabase tempDatabase = SQLiteDatabase.openDatabase(databaseFile, null, SQLiteDatabase.OPEN_READWRITE);

                    if (tempDatabase != null) {
                        this.movePSTDatabase = true;

                        if (PSTdatabase != null) PSTdatabase.close();

                        PSTdatabase = tempDatabase;
                        this.movePSTDatabase = false;

                    }
                } else {
                    refreshPSTDatabase = true;
                }
                break;




        }

    }

    /**
     * * Check is route is valid, a routes that is assigned to the pre-loader
    **  Assigned routes are in config file.
     * * @param route
    * @return
     * */

    public boolean isRouteValid(String route) {

        for (String validRoute:configData.getValidRoutes()){
            if (validRoute.toUpperCase().equals(route.toUpperCase())){
                return true;
            }

        }

        return false;

    }

    /**
     * Get all the streetTypes from the RoutePlan database.
     * Needed to construct the correct address parameters from the 2D Barcode scan
     *
     * @return
     */

    public  ArrayList<String> fetchStreetTypes(){

        ArrayList<String> streetTypes = new ArrayList<String>();

        SQLiteDatabase database = PurolatorSmartsortMQTT.getsInstance().getDatabase(PurolatorSmartsortMQTT.RPM_DBTYPE);
        PurolatorSmartsortMQTT.getsInstance().setTransactionActive(PurolatorSmartsortMQTT.RPM_DBTYPE,true);

        Cursor RPMCursor;
        RPMCursor = database.rawQuery("SELECT DISTINCT StreetType FROM RoutePlan",null);
        if (RPMCursor.moveToFirst()) {
            do {
                streetTypes.add(RPMCursor.getString(RPMCursor.getColumnIndex("StreetType")));

            } while (RPMCursor.moveToNext());
        }

        streetTypes.add("ROAD");
        RPMCursor.close();
        PurolatorSmartsortMQTT.getsInstance().setTransactionActive(PurolatorSmartsortMQTT.RPM_DBTYPE, false);

        return streetTypes;
    }

    /**
     * Change this tablet from Slave state to active stater or vice versa
     * Look out, you can not have 2 active tablets.
     *
     * @param activate
     */


    public void changeSlaveState(boolean activate){

        if (activate) {
            //TODO first check if another tablet is also a master tablet, this is not allowed
            getsInstance().getConfigData().setSlaveMode(false);
            FixedMessage fixedMessage = new FixedMessage();
            fixedMessage.setFixedSender(PurolatorSmartsortMQTT.getsInstance().getConfigData().getDeviceName());
            fixedMessage.setReceiver(PurolatorSmartsortMQTT.getsInstance().getConfigData().getTerminalID()); //broadcast a message to all glasses
            fixedMessage.setMessage("START");
            EventBus.getDefault().post(fixedMessage);
        } else {
            getsInstance().getConfigData().setSlaveMode(true);

        }

    }

    /**
     * Send the result directly to the glasses, if wavevalidation is set to false.
     *
     * @param fixedScanResult
     */
    public void sendToGlass(final FixedScanResult fixedScanResult){

        /** This is causing an out of memory --> do not use

        ScheduledExecutorService directGlass = Executors.newScheduledThreadPool(1);
        ScheduledFuture scheduleLogFuture = directGlass.schedule(new Runnable() {
            @Override
            public void run() {
                if (D) Log.d(TAG, "Also send to Glass");
                fixedScanResult.setGlassTarget("GLASS");
                EventBus.getDefault().post(fixedScanResult);
            }
        }, 1, TimeUnit.SECONDS);       //send message to Glass after 1 second
        */

        FixedScanResult glassFixedScanresult = new FixedScanResult();
        glassFixedScanresult.setPinCode(fixedScanResult.getPinCode());
        glassFixedScanresult.setPostalCode((fixedScanResult.getPostalCode()));
        glassFixedScanresult.setGlassTarget(fixedScanResult.getGlassTarget());
        glassFixedScanresult.setDeliverySequence((fixedScanResult.getDeliverySequence()));
        glassFixedScanresult.setDimensionData(fixedScanResult.getDimensionData());
        glassFixedScanresult.setMissort(fixedScanResult.isMissort());
        glassFixedScanresult.setPrimarySort(fixedScanResult.getPrimarySort());
        glassFixedScanresult.setRouteNumber(fixedScanResult.getRouteNumber());
        glassFixedScanresult.setScanDate(fixedScanResult.getScanDate());
        glassFixedScanresult.setScannedCode(fixedScanResult.getScannedCode());
        glassFixedScanresult.setSenderType(fixedScanResult.getSenderType());
        glassFixedScanresult.setShelfNumber(fixedScanResult.getShelfNumber());
        glassFixedScanresult.setShelfOverride(fixedScanResult.getShelfOverride());
        glassFixedScanresult.setAddressee(fixedScanResult.getAddressee());
        glassFixedScanresult.setStreetname(fixedScanResult.getStreetname());
        glassFixedScanresult.setStreetnumber(fixedScanResult.getStreetnumber());
        glassFixedScanresult.setStreetunit(fixedScanResult.getStreetunit());
        glassFixedScanresult.setMunicipality(fixedScanResult.getMunicipality());
        glassFixedScanresult.setSideofBelt(fixedScanResult.getSideofBelt());


        if (D) Log.d(TAG, "Also send to Glass");
        fixedScanResult.setGlassTarget("GLASS");
        EventBus.getDefault().post(glassFixedScanresult);

    }


    /**
     * Event triggered by WifiMonitor
     *
     * @param wifiOnline
     */
    public void onWifiStateChanged(boolean wifiOnline){

        if (D)Log.d(TAG,"Wifi State Changed:  " + wifiOnline);
        if (!wifiOnline){
            UIUpdater uiUpdater = new UIUpdater();
            uiUpdater.setUpdateType(PurolatorSmartsortMQTT.UPD_ERROR);
            uiUpdater.setErrorCode(PurolatorSmartsortMQTT.NONE_TYPE);
            uiUpdater.setErrorMessage("WIFI Connection Lost...");
            EventBus.getDefault().post(uiUpdater);
/*
            //try to restore wifi connection
            WifiManager wifiManager = (WifiManager) getSystemService(WIFI_SERVICE);
            for (WifiConfiguration wifiConfiguration : wifiManager.getConfiguredNetworks()) {

                Log.d(TAG,"SSID " + wifiConfiguration.SSID);
                if (wifiConfiguration.SSID.contains("Mosq")) {
                    Log.d(TAG,"SSID " + wifiConfiguration.SSID + " rescheduled");
                    wifiManager.enableNetwork(wifiConfiguration.networkId, true);
                    wifiManager.startScan();
                    wifiScheduler.scheduleAtFixedRate(new TimerTask() {
                        @Override
                        public void run() {

                            registerReceiver(connectEvent, new IntentFilter(WifiManager.NETWORK_STATE_CHANGED_ACTION));
                        }

                    }, 100, wifiScheduleTimer);
                }
            }
*/
        } else {
            UIUpdater uiUpdater = new UIUpdater();
            uiUpdater.setUpdateType(PurolatorSmartsortMQTT.UPD_ERROR);
            uiUpdater.setErrorCode(PurolatorSmartsortMQTT.NONE_TYPE);
            uiUpdater.setErrorMessage(" ");
            EventBus.getDefault().post(uiUpdater);

        }

    }

    /**
     * Returns the list of possible postalcodes, alphabetically ordered
     * Used for autocompletion when enterin a postalcode in remediation mode
     *
     */


    public ArrayList<String> getPostalCodes() {

        ArrayList<String> postalCodes = new ArrayList<String>();
        SQLiteDatabase database = PurolatorSmartsortMQTT.getsInstance().getDatabase(PurolatorSmartsortMQTT.RPM_DBTYPE);
        if (database == null) {
            return null;
        }


        //check if database is being moved
        while (PurolatorSmartsortMQTT.getsInstance().moveRPMDatabase) {
            try {
                Thread.sleep(10);       //wait 10 milliseconds
            } catch (InterruptedException ex) {

            }
        }
        //TODO Check if transaction is active
        try {
            PurolatorSmartsortMQTT.getsInstance().setTransactionActive(PurolatorSmartsortMQTT.RPM_DBTYPE, true);

            String[] selectColumns = {"PostalCode"};
            Cursor RPMCursor = database.query(true, "RoutePlan", selectColumns, null, null, null, null, "PostalCode", null);

            ArrayList<RPMLookUp> rpmLookUps = new ArrayList<RPMLookUp>();


            if (RPMCursor.moveToFirst()) {
                do {

                    postalCodes.add(RPMCursor.getString(RPMCursor.getColumnIndex("PostalCode")));

                } while (RPMCursor.moveToNext());
            }

        } catch (Exception ex){

        }
        return postalCodes;
    }

    /**
     * Get the list of possible addresses from a postalcode & send back to the glass/wearable device
     * This is run on a separate thread so it will not block other actions.
     *
     * @param postalCode
     */

    public void getAddresses(String postalCode, String return2Sender){


        SQLiteDatabase database = PurolatorSmartsortMQTT.getsInstance().getDatabase(PurolatorSmartsortMQTT.RPM_DBTYPE);
        if (database == null) {
            return;
        }


        //check if database is being moved
        while (PurolatorSmartsortMQTT.getsInstance().moveRPMDatabase) {
            try {
                Thread.sleep(10);       //wait 10 milliseconds
            } catch (InterruptedException ex) {

            }
        }
        //TODO Check if transaction is active
        try {
            PurolatorSmartsortMQTT.getsInstance().setTransactionActive(PurolatorSmartsortMQTT.RPM_DBTYPE, true);

            String[] selectColumns = {"PostalCode", "StreetName","StreetType","AddressRecordType","FromStreetNumber","ToStreetNumber","FromUnitNumber","ToUnitNumber","MunicipalityName","RouteNumber"};
            Cursor RPMCursor = database.query("RoutePlan", selectColumns, "PostalCode=? ", new String[]{postalCode}, null, null, null, null);

            ArrayList<RPMLookUp> rpmLookUps = new ArrayList<RPMLookUp>();


            if (RPMCursor.moveToFirst()){
                do{

                    String addressRecordType = RPMCursor.getString(RPMCursor.getColumnIndex("AddressRecordType"));

                    if (addressRecordType.startsWith("Unique")|| addressRecordType.startsWith("AddressRang")) {
                        RPMLookUp rpmLookUp = new RPMLookUp();
                        rpmLookUp.setPostalCode(postalCode);
                        rpmLookUp.setStreetName(RPMCursor.getString(RPMCursor.getColumnIndex("StreetName")));
                        rpmLookUp.setStreetType(RPMCursor.getString(RPMCursor.getColumnIndex("StreetType")));
                        rpmLookUp.setFromNumber(RPMCursor.getInt(RPMCursor.getColumnIndex("FromStreetNumber")));
                        rpmLookUp.setToNumber(RPMCursor.getInt(RPMCursor.getColumnIndex("ToStreetNumber")));
                        if (rpmLookUp.getToNumber()==0) rpmLookUp.setToNumber(rpmLookUp.getFromNumber());
                        rpmLookUp.setFromUnitNumber(RPMCursor.getString(RPMCursor.getColumnIndex("FromUnitNumber")));
                        rpmLookUp.setToUnitNumber(RPMCursor.getString(RPMCursor.getColumnIndex("ToUnitNumber")));
                        rpmLookUp.setMunicipality(RPMCursor.getString(RPMCursor.getColumnIndex("MunicipalityName")));
                        rpmLookUp.setRouteNumber(RPMCursor.getString(RPMCursor.getColumnIndex("RouteNumber")));
                        rpmLookUp.setUniqueStreet(false);
                        if (addressRecordType.startsWith("Unique")) {
                            rpmLookUp.setUniqueStreet(true);
                        }
                        rpmLookUps.add(rpmLookUp);
                    }


                } while (RPMCursor.moveToNext());
            }
            if (rpmLookUps.size() == 1 && (rpmLookUps.get(0).getFromNumber() == rpmLookUps.get(0).getToNumber())){
                rpmLookUps.get(0).setUniqueStreet(true);
            }

            if (rpmLookUps.size() > 0){
                Gson gson = new Gson();
                final String jsonPostalRoutes = gson.toJson(rpmLookUps);
                FixedMessage fixedMessage = new FixedMessage();
                fixedMessage.setMessage("POSTALSTREETS");
                fixedMessage.setValue(jsonPostalRoutes);
                fixedMessage.setReceiver(PurolatorSmartsortMQTT.getsInstance().getConfigData().getTerminalID() + "/" + return2Sender);

                EventBus.getDefault().post(fixedMessage);

            } else {

            }

        } catch (Exception ex){

        }
        PurolatorSmartsortMQTT.getsInstance().setTransactionActive(PurolatorSmartsortMQTT.RPM_DBTYPE,false);
        return;


    }


    /**
     * Update the Streetinformation in the local PST Database
     * @param rpmMessage
     */

    public void updatePSTStreets(RPMLookUp rpmMessage){

        String[] selectColumns = {"PostalCode","Route","PIN","StreetName","StreetType","StreetNumber","UnitNumber","MunicipalityName","Addressee","ShelfNumber","DeliverySequenceID"};

        SQLiteDatabase database = PurolatorSmartsortMQTT.getsInstance().getDatabase(PurolatorSmartsortMQTT.PST_DBTYPE);
        if (database == null){
            return;
        }
        //check if database is being moved
        while (PurolatorSmartsortMQTT.getsInstance().movePSTDatabase){
            try{
                Thread.sleep(10);       //wait 10 milliseconds
            } catch (InterruptedException ex){

            }
        }
        //TODO Check if transaction is active


        try {
            PurolatorSmartsortMQTT.getsInstance().setTransactionActive(PurolatorSmartsortMQTT.PST_DBTYPE, true);

            ContentValues insertValues = new ContentValues();
            insertValues.put("PostalCode", rpmMessage.getPostalCode());
            insertValues.put("Route", rpmMessage.getRouteNumber());
            insertValues.put("PIN", rpmMessage.getPinCode());
            insertValues.put("StreetName",rpmMessage.getStreetName());
            insertValues.put("StreetType",rpmMessage.getStreetType());
            insertValues.put("StreetNumber",rpmMessage.getFromNumber());
            insertValues.put("UnitNumber",rpmMessage.getFromUnitNumber());
            insertValues.put("MunicipalityName",rpmMessage.getMunicipality());
            //TODO Check duplicate key issues
            // check for PINcode
            Cursor PSTCursor = database.query("PostalRoute", selectColumns, "PIN=?", new String[]{rpmMessage.getPinCode()}, null, null, null, null);
            if (PSTCursor.getCount()> 0){
                database.update("PostalRoute",insertValues,"PIN=?",new String[]{rpmMessage.getPinCode()});
            } else {
                database.insert("PostalRoute", null, insertValues);
            }
        } catch (Exception ex){
            Log.e(TAG,"Error during PSTRPM database update actions: " +ex.getMessage());
        }
        PurolatorSmartsortMQTT.getsInstance().setTransactionActive(PurolatorSmartsortMQTT.PST_DBTYPE,false);

    }


    /**
     * Detect the barcode type as entry in chain of lookup events
     *
     *
     *
     * @param barcode
     * @return
     */
    public String getBarcodeType(String barcode){

        //employee scan
        if (barcode.length() == 8 && barcode.startsWith("60")) {
            return PurolatorSmartsortMQTT.EMPLOYEE_TYPE;
        }

        if (barcode.length() == 11) {
            return PurolatorSmartsortMQTT.PIN_DBTYPE;
        }

        if (barcode.length() == 12){
            return PurolatorSmartsortMQTT.PIN_DBTYPE;
        }

        if (barcode.length() == 18 && barcode.startsWith("1Z")){
            return PurolatorSmartsortMQTT.PIN_DBTYPE;
        }


//        if (barcode.substring(9,11).equals("94") && barcode.length() == 34){
        if (barcode.length() == 34){

            return PurolatorSmartsortMQTT.PIN_DBTYPE;
        }

        //check if this a Datamatrix barcode scanned, also contains a PIN Code

        String[] parsed = barcode.split("[|]");

        for (String aParsed : parsed) {
            Log.d(TAG, "Parsed: " + aParsed);
            if (aParsed.startsWith("S02") && aParsed.length() > 6) {
                return PurolatorSmartsortMQTT.PIN_DBTYPE;
            }

        }

//        return PurolatorSmartsortMQTT.RPM_DBTYPE;

        return PurolatorSmartsortMQTT.NONE_TYPE; //could not identify the scanned value;
    }

    /**
     * Called when Scan if wifi networks is done
     *
     * @author Dirk
     *
     */

    class NetworkConnected extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
            NetworkInfo networkInfo = intent.getParcelableExtra(WifiManager.EXTRA_NETWORK_INFO);
            Log.d(TAG, "Reconnection:  " + String.valueOf(networkInfo));
            if(networkInfo.isConnected()) {
                unregisterReceiver(this);
                wifiScheduler.cancel();
                wifiScheduler = new Timer();			//create a new timer again, you can not restart an canceled timer

            }
        }

    }
}
