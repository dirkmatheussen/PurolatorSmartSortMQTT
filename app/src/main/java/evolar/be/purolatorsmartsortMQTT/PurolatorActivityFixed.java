package evolar.be.purolatorsmartsortMQTT;

import android.app.Activity;
import android.app.AlarmManager;
import android.app.AlertDialog;
import android.app.PendingIntent;
import android.content.ContentValues;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.database.DataSetObserver;
import android.database.sqlite.SQLiteDatabase;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.preference.PreferenceManager;
import android.provider.Settings;
import android.util.Log;
import android.view.KeyEvent;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.ProgressBar;
import android.widget.TextView;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import org.greenrobot.eventbus.EventBus;
import org.greenrobot.eventbus.Subscribe;
import org.greenrobot.eventbus.ThreadMode;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Type;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.concurrent.ScheduledExecutorService;

import evolar.be.purolatorsmartsortMQTT.events.BarcodeScan;
import evolar.be.purolatorsmartsortMQTT.events.FetchDb;
import evolar.be.purolatorsmartsortMQTT.events.FixedMessage;
import evolar.be.purolatorsmartsortMQTT.events.FixedScanResult;
import evolar.be.purolatorsmartsortMQTT.events.GlassDeviceInfo;
import evolar.be.purolatorsmartsortMQTT.events.GlassMessage;
import evolar.be.purolatorsmartsortMQTT.events.Logger;
import evolar.be.purolatorsmartsortMQTT.events.PutDb;
import evolar.be.purolatorsmartsortMQTT.events.UIUpdater;

public class PurolatorActivityFixed extends Activity {


    // Debugging
    private static final String TAG = "PurolatorActivityFixed";
    private static final boolean D = true;


    EditText scanResult;
    String enteredKey = "";
    String enteredKeyA[] = new String[]{"","","","",""};
    String enteredDeviceName[] = new String[]{"","","","",""};


    TextView messageView;
    TextView errorView;

    TextView infoView;


    ListView labelListView;
    ListView remLabelListView;
    ListView connectListView;

    TextView correctScansView;
    TextView errorScansView;
    TextView totalScansView;

    int maxCorrectScans = 0;
    int maxErrorScans = 0;

    Button resetBtn;
    Button resetAppBtn;

    ProgressBar progressBar;

    ScheduledExecutorService scheduleList;


    ArrayList<Label> labels = new ArrayList<Label>();
    ArrayList<Label> remLabels = new ArrayList<Label>();

    ScannedLabelsAdapter scannedLabels;
    ScannedLabelsAdapter scannedRemLabels;      //labels scanned for remediation
    ConnectLabelsAdapter connectLabels;         //list of connected devices



    boolean appReady =  false;
    boolean appStarting =true;                  //once app is started, onResume may not be called anymore


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        if (D) Log.d(TAG, "+++ On Create +++");

        this.requestWindowFeature(Window.FEATURE_NO_TITLE);
        this.getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN, WindowManager.LayoutParams.FLAG_FULLSCREEN);
        this.getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);


        setContentView(R.layout.fixed_activity_purolator);


        // Get from the SharedPreferences, when app is restarted
//        SharedPreferences settings = PurolatorSmartsortMQTT.getsInstance().getSharedPreferences("storedLabels", 0);

        if (!PurolatorSmartsortMQTT.getsInstance().isDatabasesLoaded()) {
            EventBus.getDefault().post(new FetchDb(PurolatorSmartsortMQTT.INIT_DBTYPE, true));      //no feedback on screen
        }

//        if (restart){
//            PurolatorSmartsortMQTT.getsInstance().setDatabasesLoaded(true);
//        }
    }

    @Override
    protected void onStop(){
        super.onStop();
        if(D) Log.d(TAG,"+++ onStop +++");

        PurolatorSmartsortMQTT.getsInstance().setStopped(true);


        storeData(false);

        //clear all barcode scans
        PurolatorSmartsortMQTT.packagesList.getBarcodeScans().clear();

        //clear all connected glasses, in onStart a request for the connected glasses is send
        PurolatorSmartsortMQTT.getsInstance().connectedGlasses.clear();

        //Upload the PSTRPM Database to the FTP server
        PutDb putDb = new PutDb(PurolatorSmartsortMQTT.PST_DBTYPE);
        EventBus.getDefault().post(putDb);



        Logger logger = new Logger();
        logger.setLogType("FTPLOG");
        EventBus.getDefault().post(logger);

        //broadcast a message to all connected glasses
        FixedMessage fixedMessage = new FixedMessage();
        fixedMessage.setFixedSender(PurolatorSmartsortMQTT.getsInstance().getConfigData().getDeviceName());
        fixedMessage.setReceiver(PurolatorSmartsortMQTT.getsInstance().getConfigData().getTerminalID()); //broadcast a message to all glasses
        fixedMessage.setMessage("STOP");
        EventBus.getDefault().post(fixedMessage);

        if (scheduleList!= null) scheduleList.shutdown();

        //wait 3 seconds
        try {
            Thread.sleep(3000);                 //1000 milliseconds is one second.
        } catch(InterruptedException ex) {
            Thread.currentThread().interrupt();
        }

        if (D) Log.d(TAG,"Now process the onStop action");


        finish();
    }

    @Override
    protected void onDestroy(){
        super.onDestroy();
        EventBus.getDefault().unregister(this);

        if (PurolatorSmartsortMQTT.getsInstance().isStopped()) {
            PurolatorSmartsortMQTT.getsInstance().stopProcesses();
        }

        if (D) {
            Log.d(TAG,"+++onDestroy++++ Sending logcat to wispelberg@gmail.com");
            if(PurolatorSmartsortMQTT.getsInstance().getConfigData().isSendLogcat()) sendLogcatMail();
        }


    }




    @Override
    protected void onStart(){
        super.onStart();
        if(D) Log.d(TAG,"+++ onStart +++");

        appReady = false;         //indicate that the app is not ready yet. Once Spinner is stopped; app is ready



        PurolatorSmartsortMQTT.getsInstance().setStopped(false);
        if (!EventBus.getDefault().isRegistered(this)) {
            EventBus.getDefault().register(this);
        }

        if (!EventBus.getDefault().isRegistered(PurolatorSmartsortMQTT.getsInstance())){
            EventBus.getDefault().register(PurolatorSmartsortMQTT.getsInstance());
        }

        errorView   = (TextView) findViewById(R.id.errorView);
        errorView.setVisibility(View.INVISIBLE);


        scannedLabels = new ScannedLabelsAdapter(this,labels);

        scannedLabels.registerDataSetObserver(new DataSetObserver()
        {
            @Override
            public void onChanged()
            {
                maxCorrectScans =  Math.max(scannedLabels.getCount(),maxCorrectScans);
                correctScansView.setText("Routed Scans: " + maxCorrectScans);
                totalScansView.setText("Total Parcels: " + PurolatorSmartsortMQTT.getsInstance().getMaxTotalScans());
            }
        });


        scannedRemLabels = new ScannedLabelsAdapter(this,remLabels);

        scannedRemLabels.registerDataSetObserver(new DataSetObserver()
        {
            @Override
            public void onChanged()
            {
                maxErrorScans = Math.max(scannedRemLabels.getCount(),maxErrorScans);
                errorScansView.setText("Remediation Scans: " + maxErrorScans);
                totalScansView.setText("Total Parcels: " + PurolatorSmartsortMQTT.getsInstance().getMaxTotalScans());
            }
        });


        connectLabels = new ConnectLabelsAdapter(this,PurolatorSmartsortMQTT.getsInstance().getConnectedGlasses());

        labelListView = (ListView) findViewById(R.id.labellistView);
        labelListView.setAdapter(scannedLabels);

        remLabelListView = (ListView) findViewById(R.id.remediationlistView);
        remLabelListView.setAdapter(scannedRemLabels);

        connectListView = (ListView) findViewById(R.id.connectView);
        connectListView.setAdapter(connectLabels);

        progressBar = (ProgressBar) findViewById(R.id.progressBar);
        messageView = (TextView) findViewById(R.id.messageView);
        messageView.setText(getResources().getString(R.string.datasync));

        infoView    = (TextView) findViewById(R.id.infoView);
        infoView.setText("");

        scanResult = (EditText) findViewById(R.id.scanResult);

        scanResult.setOnKeyListener(new View.OnKeyListener(){
            @Override
            public boolean onKey(View v, int keyCode, KeyEvent event) {

                if (event.getAction()!=KeyEvent.ACTION_DOWN)
                    return true;

                if (!appReady){
                    return true;
                }

                int devicePosition =  getDevicePosition(event.getDevice().getName());

 //               Log.d(TAG,"Entered Key: " + event.getKeyCode());

                if (event.getUnicodeChar() !=0 && event.getUnicodeChar() != 10){
                    enteredKeyA[devicePosition] = enteredKeyA[devicePosition] +  Character.toString((char)event.getUnicodeChar());
                }
                if (event.getUnicodeChar() == 10){			//newline detected
                    if (D) Log.d(TAG,"Barcode fingerscan: " + enteredKeyA[devicePosition]);

                    if (enteredKeyA[devicePosition].trim().length() > 0){
                        //a message from PUDRO Speed sensor, coming in as a HID BT device
                        if (enteredKeyA[devicePosition].startsWith("PUDRO")) {

                            double speed = Double.valueOf(enteredKeyA[devicePosition].substring(enteredKeyA[devicePosition].indexOf("@") + 1, enteredKeyA[devicePosition].length()));
                            if (D) Log.d(TAG, "Speed from PUDRO Device detected: " + speed);
                            PurolatorSmartsortMQTT.getsInstance().setPUDROSpeed(speed);
                            FixedMessage fixedMessage = new FixedMessage();
                            fixedMessage.setReceiver(PurolatorSmartsortMQTT.getsInstance().getConfigData().getTerminalID());
                            fixedMessage.setValue(enteredKeyA[devicePosition].substring(enteredKeyA[devicePosition].indexOf("@") + 1, enteredKeyA[devicePosition].length()));
                            fixedMessage.setMessage("PUDRO");
                            fixedMessage.setFixedSender(PurolatorSmartsortMQTT.getsInstance().getConfigData().getDeviceName());
                                    //process the result, handle by MQTTFixComms

                            EventBus.getDefault().post(fixedMessage);




                        } else {
                            EventBus.getDefault().post(new BarcodeScan(enteredKeyA[devicePosition],new Date()));
                        }
                    }
                    enteredKeyA[devicePosition] = "";
                }
                return true;
            }

        });

        correctScansView = (TextView) findViewById(R.id.correctAmountView);
        errorScansView = (TextView) findViewById(R.id.errorAmountView);

        correctScansView.setText("Routed Scans: " + scannedLabels.getCount());
        errorScansView.setText("Remediation Scans: " + scannedRemLabels.getCount());

        totalScansView = (TextView) findViewById(R.id.scannnedView);
        totalScansView.setText("Total Parcels: " + PurolatorSmartsortMQTT.getsInstance().getMaxTotalScans());

        resetBtn = (Button) findViewById(R.id.reset_btn);
        resetBtn.setVisibility(View.INVISIBLE);

        resetBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                resetAll();
            }
        });

        resetAppBtn = (Button) findViewById(R.id.resetapp_btn);
        resetAppBtn.setVisibility(View.INVISIBLE);

        resetAppBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                AlertDialog.Builder alertDialogBuilder = new AlertDialog.Builder(PurolatorActivityFixed.this);
                alertDialogBuilder.setTitle("Restart the application");

                alertDialogBuilder
                        .setMessage("Click YES to restart the application !\nCaution, this may result in data loss!!")
                        .setCancelable(false)
                        .setPositiveButton("Yes",new DialogInterface.OnClickListener() {
                            public void onClick(DialogInterface dialog, int id) {
                                // if this button is clicked, restart the app
                                restartApp();
                            }
                        })
                        .setNegativeButton("No",new DialogInterface.OnClickListener() {
                            public void onClick(DialogInterface dialog,int id) {
                                // if this button is clicked, just close
                                // the dialog box and do nothing
                                dialog.cancel();
                            }
                        });

                // create alert dialog
                AlertDialog alertDialog = alertDialogBuilder.create();

                // show it
                alertDialog.show();
            }
        });



    }

    @Override
    protected void onResume(){
        super.onResume();



        if (!appStarting){
            return;
        }

        Log.d(TAG,"+++ on Resume +++");

        appStarting = false;            //app is started
        // if all databases are already loaded, setup screen
        if (PurolatorSmartsortMQTT.getsInstance().isDatabasesLoaded()) {
            stopSpinner();

            if (PurolatorSmartsortMQTT.getsInstance().mqttFixComms == null) {
//                PurolatorSmartsortMQTT.getsInstance().mqttFixComms = new MQTTFixComms();      //instantiate the subscriber object
                PurolatorSmartsortMQTT.getsInstance().mqttFixComms = new MQTTFixPahoComms();      //instantiate the subscriber object
            } else{
                PurolatorSmartsortMQTT.getsInstance().mqttFixComms.stopMQTTConnection();
                PurolatorSmartsortMQTT.getsInstance().mqttFixComms.setMQttConnection();
            }
 //           FixedMessage fixedMessage = new FixedMessage();
 //           fixedMessage.setFixedSender(PurolatorSmartsortMQTT.getsInstance().getConfigData().getDeviceName());
 //           fixedMessage.setReceiver(PurolatorSmartsortMQTT.getsInstance().getConfigData().getTerminalID()); //broadcast a message to all glasses
 //           fixedMessage.setMessage("START");
 //           EventBus.getDefault().post(fixedMessage);

            PurolatorSmartsortMQTT.getsInstance().startSchedulers("FIXED");



        }
        if (PurolatorSmartsortMQTT.getsInstance().configData.getTerminalID()!=null) {
            UIUpdater uiUpdater = new UIUpdater();
            uiUpdater.setUpdateType(PurolatorSmartsortMQTT.UPD_ERROR);
            uiUpdater.setErrorCode("NEWINFO");
            uiUpdater.setErrorMessage("Terminal ID: " + PurolatorSmartsortMQTT.getsInstance().configData.getTerminalID());
            EventBus.getDefault().post(uiUpdater);            //do a sticky post, the activity is perhaps not active yet

            if (PurolatorSmartsortMQTT.getsInstance().getPUDROSpeed()> 0) {
                FixedMessage fixedMessage = new FixedMessage();
                fixedMessage.setFixedSender(PurolatorSmartsortMQTT.getsInstance().getConfigData().getDeviceName());
                fixedMessage.setReceiver(PurolatorSmartsortMQTT.getsInstance().getConfigData().getTerminalID()); //broadcast a message to all glasses
                fixedMessage.setValue(String.valueOf(PurolatorSmartsortMQTT.getsInstance().getPUDROSpeed()));
                fixedMessage.setMessage("PUDRO");
                EventBus.getDefault().post(fixedMessage);
            }
        }


    }




    /**
     * Return the position in the enteredKeyA array of the device sending the keystrokes
     * @param deviceName
     * @return
     */


    public int getDevicePosition(String deviceName) {


        int nextFreePosition = 0;

        for (int i= 0; i< enteredDeviceName.length;i++){
            if (enteredDeviceName[i].length()> 1) nextFreePosition++;
            if (enteredDeviceName[i].equals(deviceName)) return i;
        }

        enteredDeviceName[nextFreePosition] = deviceName;
        return nextFreePosition;
    }


    @Subscribe(priority = 0)    //lowest priority
    public void onGlassDeviceInfoEvent(GlassDeviceInfo event){
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                connectLabels.notifyDataSetChanged();
            }
        });

        //check if there are scannedLabels with no receive confirmation, to synchronize the Smart Glass again
        if (event.getActive() && event.getDeviceType().equals("GLASS")){
            for(Label label:scannedLabels.getLabels()){
                if (!label.isSendToGlass()){
                    FixedScanResult fixedScanResult = new FixedScanResult();
                    fixedScanResult.setScannedCode(label.getScannedCode());
                    fixedScanResult.setSideofBelt(label.getSideofBelt());
                    fixedScanResult.setPrimarySort(label.getPrimarySort());
                    fixedScanResult.setDeliverySequence(label.getDeliverySequence());

                    //check if it is a 2D Scan or an NGB Scan, use the PIN code as BarcodeScan.  --> this logic is also applied in HandleRingBarcode
                    String barcodeType = Utilities.getBarcodeLogType(label.getScannedCode());
                    String barcode;

                    if (barcodeType.equals(("2D"))|| barcodeType.equals("NGB")){
                        barcode = Utilities.getPINCode(label.getScannedCode());
                    } else {
                        barcode = label.getPinCode();
                    }
                    fixedScanResult.setPinCode(barcode);


                    fixedScanResult.setScanDate(label.getScanTime());
//                    fixedScanResult.setPostalCode(label.getPostalCode);
                    fixedScanResult.setRouteNumber(label.getRouteNumber());
                    fixedScanResult.setShelfNumber(label.getShelfNumber());
                    fixedScanResult.setPostalCode(label.getPostalCode());
                    fixedScanResult.setStreetunit(label.getStreetunit());
                    fixedScanResult.setMunicipality(label.getMunicipality());
                    fixedScanResult.setStreetnumber(label.getStreetnumber());
                    fixedScanResult.setStreetname(label.getStreetname());
                    fixedScanResult.setAddressee(label.getAddressee());

                    EventBus.getDefault().post(fixedScanResult);
                }
            }
        }

    }

    /**
     * Message from a Glass (Package picked or still alive message) or the Wave Sorter (Package Missorted)
     * Also more complex messages from the Glasses
     *  - a set of routes added to postalcodes
     *  - a postalcode to return all the streets linked to that postcode
     *  - a street to return all associated numbers
     *
     *
     * @param event
     */

    @Subscribe
    public void onGlassMessageEvent(final GlassMessage event) {


        if (event.getMessage().contains("POSTALCODE")) {
            if (!PurolatorSmartsortMQTT.getsInstance().getConfigData().isGlassRemediation()) {
                final String postalCode = event.getMessage().substring(event.getMessage().indexOf("::") + 2);
                final String return2sender = event.getGlassName();
                //it is a database intensive action, run on new thread
                new Thread(new Runnable() {
                    @Override
                    public void run() {
                        PurolatorSmartsortMQTT.getsInstance().getAddresses(postalCode, return2sender);
                    }
                }).start();
            }
            return;
        }

        if (event.getMessage().contains("OCRMESSAGE")){                 // postal code & street entered in the smartglasses
            final String ocrString = event.getMessage().substring(event.getMessage().indexOf("::") + 2);

            OCRData ocrData = new OCRData();
            ocrData.setLookupBarcode(Utilities.getPINCode(ocrString));
            ocrData.setOcrResult(ocrString);
            ocrData.setScannedBarcode(event.getBarcode());
            PurolatorSmartsortMQTT.getsInstance().ocrDatas.add(ocrData);

            // remove package from the remediation list, ready to rescan
            UIUpdater uiUpdater = new UIUpdater();
            uiUpdater.setRemediationId(Utilities.getPINCode(ocrData.getOcrResult()));
            uiUpdater.setErrorCode("REM");
            uiUpdater.setUpdateType(PurolatorSmartsortMQTT.UPD_BOTTOMSCREEN);
            EventBus.getDefault().post(uiUpdater);

            return;
        }

        if (event.getMessage().contains("MISMESSAGE")){                 // postal code & street entered in the smartglasses


            // remove package from the remediation list, ready to rescan
            UIUpdater uiUpdater = new UIUpdater();
            uiUpdater.setRemediationId(Utilities.getPINCode(event.getBarcode()));
            uiUpdater.setErrorCode("REM");
            uiUpdater.setUpdateType(PurolatorSmartsortMQTT.UPD_BOTTOMSCREEN);
            EventBus.getDefault().post(uiUpdater);

            return;
        }

        runOnUiThread(new Runnable() {
            @Override
            public void run() {

                if (event.getMessage().contains("POSTALROUTES")) {

                    String jsonString = event.getMessage().substring(event.getMessage().indexOf("::")+2);
                    Gson gson =  new Gson();
                    Type postalRouteType = new TypeToken<ArrayList<PostalRoute>>() {}.getType();
                    ArrayList<PostalRoute> postalRoutes = new ArrayList<PostalRoute>();
                    if (!jsonString.isEmpty()) postalRoutes = gson.fromJson(jsonString,postalRouteType);
                    //update the PSTRPM database with the postalroutes....

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
                        for (PostalRoute postalRoute : postalRoutes) {
                            if (postalRoute.getRoute() != null && postalRoute.getRoute().length() == 3) {
                                ContentValues insertValues = new ContentValues();
                                insertValues.put("PostalCode", postalRoute.getPostalCode());
                                insertValues.put("Route", postalRoute.getRoute());
                                //TODO Check duplicate key issues
                                database.insert("PostalRoute", null, insertValues);
                                //also delete on the rem labels
                                ArrayList<Label> copyLabels = new ArrayList<Label>(remLabels);     //otherwise you get a ConcurrentModificationException
                                for (Label toDelete : copyLabels) {
                                    if (Utilities.getPostalCode(toDelete.getScannedCode()) != null &&
                                            Utilities.getPostalCode(toDelete.getScannedCode()).equalsIgnoreCase((postalRoute.getPostalCode()))) {
                                        changeRemAdapter(true, toDelete);
                                        //                            scannedRemLabels.remove(toDelete);
                                    }

                                }
                                scannedRemLabels.notifyDataSetChanged();
                            }
                        }
                    } catch (Exception ex){
                        Log.e(TAG,"Error during PSTRPM database update actions: " +ex.getMessage());
                    }
                    PurolatorSmartsortMQTT.getsInstance().setTransactionActive(PurolatorSmartsortMQTT.PST_DBTYPE,false);


                    return;
                }


                if (event.getMessage().contains("HEARTBEAT")){
                    //clear the disconnect scheduler for the connected glass else send the message that a glass is disconnected

                    return;
                }

                String barcodeType = Utilities.getBarcodeLogType(event.getBarcode());
                String barcodeResult;
                if (barcodeType.equals(("2D"))|| barcodeType.equals("NGB")){
                    barcodeResult = Utilities.getPINCode(event.getBarcode());
                } else {
                    barcodeResult = event.getBarcode();
                }


                for(Label label:scannedLabels.getLabels()){
                    barcodeType = Utilities.getBarcodeLogType(label.getScannedCode());
                    String labelBarcodeResult;
                    if (barcodeType.equals(("2D"))|| barcodeType.equals("NGB")){
                        labelBarcodeResult = Utilities.getPINCode(label.getScannedCode());
                    } else {
                        labelBarcodeResult = label.getScannedCode();
                    }

                    if (labelBarcodeResult.equals(barcodeResult)){
                        if (event.getMessage().equals("CONFIRMED")) {
                            label.setSendToGlass(true);
                        } else if (event.getMessage().equals("MISSORT")){
                            label.setMissort(true);
                        } else {
                            scannedLabels.remove(label);
                            //remove from list of scanned barcodes when it is scanned to put in shelf
                            //check if it is a 2D Scan or an NGB Scan, use the PIN code as BarcodeScan.
                            String barcodeLogType = Utilities.getBarcodeLogType(event.getBarcode());
                            String adjustedBarcodeScan = event.getBarcode();
                            if (barcodeLogType.equals(("2D"))|| barcodeLogType.equals("NGB")){
                                adjustedBarcodeScan =  Utilities.getPINCode(event.getBarcode());
                            }
                            PurolatorSmartsortMQTT.packagesList.getBarcodeScans().remove(adjustedBarcodeScan);

                        }
//                        correctScansView.setText("Routed Scans: " + scannedLabels.getCount());

                        scannedLabels.notifyDataSetChanged();
                    }
                }

            }
        });



    }

        /**
         *    triggered when an UI Update is needed. This can be posted by different objects
         *
         */
    @Subscribe
    public void onUIUpdaterEvent(final UIUpdater event){

        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                switch (event.getUpdateType()){

                    case PurolatorSmartsortMQTT.UPD_BOTTOMSCREEN:
                        if (D) Log.d(TAG, "Received BOTTOM Screen Update Event: " + event.getPinCode());
                        errorView.setText("");
                        errorView.setVisibility(View.INVISIBLE);
                        updateBottom(event);
                        break;

                    case PurolatorSmartsortMQTT.UPD_TOPSCREEN:
                        if (D) Log.d(TAG, "Received TOP Screen Update Event "+ event.getPinCode());
                        errorView.setText("");
                        errorView.setVisibility(View.INVISIBLE);
                        updateList(event);
//                        cleanBottom(event);
                        break;
                    case PurolatorSmartsortMQTT.UPD_ERROR:
                        if (D) Log.d(TAG, "Received ERROR Update Event: " + event.getErrorMessage());


                        switch (event.getErrorCode()) {
                            case "NEWINFO":
                                infoView.setText(event.getErrorMessage());
                                break;
                            case "APPENDINFO":
                                String info = infoView.getText().toString();
                                info = info + " " + event.getErrorMessage();
                                infoView.setText(info);
                                break;
                            case "CLEARERROR":
                                errorView.setText(" ");
                                errorView.setVisibility(View.INVISIBLE);
                                break;

                            default:
//                                errorView.setText(event.getErrorMessage());
//                                errorView.setVisibility(View.VISIBLE);
//                                break;
                        }

                        break;

                    case PurolatorSmartsortMQTT.UPD_STATUSPINNER:
                        messageView.setText(event.getErrorMessage());
                        break;

                    case PurolatorSmartsortMQTT.UPD_STOPSPINNER:

                        stopSpinner();

 //                       FixedMessage fixedMessage = new FixedMessage();
 //                       fixedMessage.setFixedSender(PurolatorSmartsortMQTT.getsInstance().getConfigData().getDeviceName());
 //                       fixedMessage.setReceiver(PurolatorSmartsortMQTT.getsInstance().getConfigData().getTerminalID()); //broadcast a message to all glasses
 //                       fixedMessage.setMessage("START");
 //                       EventBus.getDefault().post(fixedMessage);  --> handled once connection is done

                        //handle here, once all elements are build in screen
                        SharedPreferences settings = PreferenceManager.getDefaultSharedPreferences(getApplicationContext());
                        boolean restart = settings.getBoolean("restart", false);
                        Log.d(TAG, "Restart: " + restart);
                        if (restart) {
                            String jsonLabels = settings.getString("labels", "");
                            String jsonRemLabels = settings.getString("remLabels", "");
                            String jsonOCRDatas = settings.getString("ocrDatas", "");
                            int totalParcels = PurolatorSmartsortMQTT.getsInstance().getMaxTotalScans();
                            totalParcels = totalParcels +settings.getInt("maxTotalScans", 0);
                            PurolatorSmartsortMQTT.getsInstance().setMaxTotalScans(totalParcels);
                            Gson gson = new Gson();
                            Type listType = new TypeToken<ArrayList<Label>>() {}.getType();
                            Type ocrType = new TypeToken<ArrayList<OCRData>>() {}.getType();

                            if (!jsonLabels.isEmpty()) {
                                ArrayList<Label> tempLabels = gson.fromJson(jsonLabels, listType);
                                for (Label label:tempLabels){
                                    scannedLabels.add(label);
                                }
                                scannedLabels.notifyDataSetChanged();
                            }
                            if (!jsonRemLabels.isEmpty()) {
                                ArrayList<Label> tempLabels = gson.fromJson(jsonRemLabels, listType);
                                for (Label label:tempLabels){
                                    scannedRemLabels.add(label);
                                }
                                scannedRemLabels.notifyDataSetChanged();
                            }
                            if (!jsonOCRDatas.isEmpty()) {
                                ArrayList<OCRData> tempOcrData = new ArrayList<OCRData>();
                                PurolatorSmartsortMQTT.getsInstance().ocrDatas.addAll(tempOcrData);
                            }

                            if (D) Log.d(TAG, "Data restored Labels: " + jsonLabels);
                            if (D) Log.d(TAG, "Data restored RemLabels: " + jsonRemLabels);

                        }


                        if (PurolatorSmartsortMQTT.getsInstance().getPUDROSpeed()> 0) {

                            FixedMessage fixedMessage = new FixedMessage();
                            fixedMessage.setFixedSender(PurolatorSmartsortMQTT.getsInstance().getConfigData().getDeviceName());
                            fixedMessage.setReceiver(PurolatorSmartsortMQTT.getsInstance().getConfigData().getTerminalID()); //broadcast a message to all glasses
                            fixedMessage.setValue(String.valueOf(PurolatorSmartsortMQTT.getsInstance().getPUDROSpeed()));
                            fixedMessage.setMessage("PUDRO");
                            EventBus.getDefault().post(fixedMessage);
                        }

                        //Catch an uncought exception, restart the app
                        if (PurolatorSmartsortMQTT.getsInstance().getConfigData().isAutorestart()) {
                            Thread.setDefaultUncaughtExceptionHandler(new FixedUnCoughtExceptionHandler(PurolatorActivityFixed.this));
                        }
                        break;
                }


            }
        });

    }

    /**
     * If there is a label rescanned and now found (after OCR/Remediation action) then remove label from bottom list
     *
     * @param event
     */

    private void cleanBottom(UIUpdater event){

        if (D) Log.d(TAG,"Clean bottom ");
        for (Label label:scannedRemLabels.getLabels()){
            if (label.getRemediationId().equals(event.getRemediationId())) {
                changeRemAdapter(true,label);
//                scannedRemLabels.remove(label);
                break;
            }
        }

//        errorScansView.setText("Remediation Scans: " + scannedRemLabels.getCount());
        scannedRemLabels.notifyDataSetChanged();
    }

    /**
     * Update the bottom part of the screen & clear the element of the array
     * @param event
     */

    private void updateBottom(UIUpdater event){

        SimpleDateFormat sdf = new SimpleDateFormat("MM/dd/yyyy");
        //first find & remove the scanned barcode from the list

//        if(D) Log.d(TAG,"In updateBottom for PINCode: " + event.getScannedCode());
        if (event.getErrorCode().equals("REM")){
            cleanBottom(event);
            return;
        }

        Label label = new Label();

        label.setDeliverySequence(event.getDeliverySequence());
        label.setPinCode(event.getPinCode());
        label.setPrimarySort(event.getPrimarySort());
        label.setRouteNumber(event.getRouteNumber());
        label.setScanTime(new Date());
        label.setShelfNumber(event.getShelfNumber());
        label.setSideofBelt(event.getSideofBelt());
        label.setScannedCode(event.getScannedCode());
        label.setLabelType(1);
        label.setRouteNumber(event.getPostalCode());        //show the postalcode as routecode
        label.setRemediationId(event.getRemediationId());
        changeRemAdapter(false,label);
//        scannedRemLabels.add(label);

//        errorScansView.setText("Remediation Scans: " + scannedRemLabels.getCount());


    }

    /**
     * Update the list adapter
     *
     *
     *
     * @param event
     */

    private void updateList(UIUpdater event){

        Label label = new Label();
        label.setDeliverySequence(event.getDeliverySequence());
        label.setPinCode(event.getPinCode());
        label.setPrimarySort(event.getPrimarySort());
        label.setRouteNumber(event.getRouteNumber());
        label.setScanTime(new Date());
        label.setShelfNumber(event.getShelfNumber());
        label.setSideofBelt(event.getSideofBelt());
        label.setScannedCode(event.getScannedCode());
        label.setLabelType(0);
        label.setRemediationId(event.getRemediationId());

        label.setPostalCode(event.getPostalCode());
        label.setStreetunit(event.getStreetunit());
        label.setStreetname(event.getStreetname());
        label.setMunicipality(event.getMunicipality());
        label.setAddressee(event.getAddressee());


        //add new entry

        scannedLabels.add(label);


    }

    /**
     *
     * Do a full restart of the application.
     */

    private void restartApp(){



        PurolatorSmartsortMQTT.getsInstance().setStopped(true);


        storeData(true);


        if (scheduleList!= null) scheduleList.shutdown();




        Intent intent = new Intent(this, PurolatorActivityFixed.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP
                | Intent.FLAG_ACTIVITY_CLEAR_TASK
                | Intent.FLAG_ACTIVITY_NEW_TASK);

        PendingIntent pendingIntent = PendingIntent.getActivity(PurolatorSmartsortMQTT.getsInstance().getBaseContext(), 0, intent, PendingIntent.FLAG_ONE_SHOT);

        AlarmManager mgr = (AlarmManager) PurolatorSmartsortMQTT.getsInstance().getBaseContext().getSystemService(Context.ALARM_SERVICE);
        mgr.set(AlarmManager.RTC, System.currentTimeMillis() + 1000, pendingIntent);

        this.finish();
        System.exit(2);

    }

    /**
     * Reset all the buffers & counters
     *
     */

    private void resetAll(){


        PurolatorSmartsortMQTT.getsInstance().setMaxTotalScans(0);
        PurolatorSmartsortMQTT.getsInstance().ocrDatas.clear();

        scannedRemLabels.clear();
        scannedLabels.clear();
        scannedLabels.notifyDataSetChanged();
        scannedRemLabels.notifyDataSetChanged();

        maxCorrectScans = 0;
        maxErrorScans = 0;

        correctScansView.setText("Routed Scans: " + maxCorrectScans);
        totalScansView.setText("Total Parcels: " + PurolatorSmartsortMQTT.getsInstance().getMaxTotalScans());
        errorScansView.setText("Routed Scans: " + maxErrorScans);


        //clear all barcode scans
        PurolatorSmartsortMQTT.packagesList.getBarcodeScans().clear();


    }

    public void storeData(boolean restart){

        //create Json Object of the labels &
        Gson gson = new Gson();
        final String jsonLabelList = gson.toJson(labels);
        final String jsonRemLabelList = gson.toJson(remLabels);
        final String jsonOcrDatas = gson.toJson(PurolatorSmartsortMQTT.getsInstance().ocrDatas);

        SharedPreferences settings = PreferenceManager.getDefaultSharedPreferences(getApplicationContext());
        SharedPreferences.Editor editor = settings.edit();
        editor.clear();
        editor.putString("labels", jsonLabelList);
        editor.putString("remLabels",jsonRemLabelList);
        editor.putString("ocrDatas",jsonOcrDatas);
        editor.putInt("maxTotalScans",PurolatorSmartsortMQTT.getsInstance().getMaxTotalScans());
        editor.putBoolean("restart",restart);

        if (D)Log.d(TAG,"Saving data: " + restart);

        editor.apply();

    }

    /**
     *      all databases are synced with the FTP server, free the screen & hide the spinner

     */

    private void stopSpinner(){

        appReady = true;

        progressBar.setVisibility(View.INVISIBLE);
        messageView.setVisibility(View.INVISIBLE);

        resetBtn.setVisibility(View.VISIBLE);
        resetAppBtn.setVisibility(View.VISIBLE);

    }

    /**
     * Check every checkIntervalSecs seconds if the connected glasses are still alive.
     * A glass sends back GlassMessage with "HEARTBEAT" content. If it is not returned after delaySecs seconds, remove the glass from the list
     *
     * @param checkIntervalSecs
     * @param delaySecs
     *
     */

    //TODO Not Implemented yet

    public void checkConnectedGlass(int checkIntervalSecs, int delaySecs){


        FixedMessage fixedMessage = new FixedMessage();
        fixedMessage.setReceiver(PurolatorSmartsortMQTT.getsInstance().getConfigData().getTerminalID());
        fixedMessage.setValue("");
        fixedMessage.setMessage("HEARTBEAT");
        fixedMessage.setFixedSender(PurolatorSmartsortMQTT.getsInstance().getConfigData().getDeviceName());
        //process the result, handle by MQTTFixComms
        EventBus.getDefault().post(fixedMessage);


    }

    /**
     * Synchronized method to delete or add elements in the labels array via the adapter
     * To eliminate concurrency problems when different threads have access to the label array
     *
     * @param delete
     * @param label
     */

    synchronized private void changeRemAdapter(boolean delete, Label label){

        if (delete){
            scannedRemLabels.remove(label);
        } else {
            scannedRemLabels.add(label);
        }

    }



    //send an email with all the logcat Entries

    public void sendLogcatMail(){

        // save logcat in file
        File outputFile = new File(Environment.getExternalStorageDirectory(),
                "logcat.txt");
        try {
            Runtime.getRuntime().exec(
                    "logcat -f" + outputFile.getAbsolutePath());
        } catch (IOException e) {
            e.printStackTrace();
        }

        String[] params = {"wispelberg@gmail.com", "krid3112",outputFile.getAbsolutePath()};
        new SendMail().execute(params);

    }
    /**
    * Inner Async classc for sending email
    *
            * @author Dirk
    *
            */
    private class SendMail extends AsyncTask<String, Void, String> {


        protected void onPreExecute() {

        }

        protected String doInBackground(String... syncParams) {


            String username = syncParams[0];
            String password = syncParams[1];
            String dbFile = syncParams[2];


            if (D) Log.d(TAG, "Start sending email for file : " + dbFile);
            Mail m = new Mail(username, password);

            String[] toArr = {"dirk@evolar.be"};
            m.setTo(toArr);
            m.setFrom("wispelberg@gmail.com");
            m.setSubject("Logging from Logcat");
            String serialNumber = Build.SERIAL != Build.UNKNOWN ? Build.SERIAL : Settings.Secure.getString(getContentResolver(), Settings.Secure.ANDROID_ID);
            m.setBody("This email is send by PurolatorSmartSort for Tablet device: " + serialNumber);
            try {
                m.addAttachment(dbFile);

                if (m.send()) {
                    if (D) Log.d(TAG, "Mail send successfully");
                } else {
                    if (D) Log.d(TAG, "Mail was not send");
                }
            } catch (Exception e) {
                //Toast.makeText(MailApp.this, "There was a problem sending the email.", Toast.LENGTH_LONG).show();
                if (D) Log.d(TAG, "Could not send email", e);
            }


            return "";
        }
    }



}
