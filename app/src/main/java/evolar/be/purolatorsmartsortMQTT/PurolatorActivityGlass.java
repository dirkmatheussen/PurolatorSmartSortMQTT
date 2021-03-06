package evolar.be.purolatorsmartsortMQTT;

/**
 * The main activity for the Wearable Device and act as an MQTT receiver.
 *
 */


import android.app.Activity;
import android.app.Dialog;
import android.content.Context;
import android.content.SharedPreferences;
import android.database.DataSetObserver;
import android.graphics.Color;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.provider.Settings;
import android.text.Editable;
import android.text.InputFilter;
import android.text.InputType;
import android.text.TextWatcher;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;
import android.view.inputmethod.BaseInputConnection;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.AutoCompleteTextView;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.ProgressBar;
import android.widget.RelativeLayout;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.ViewFlipper;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import org.greenrobot.eventbus.EventBus;
import org.greenrobot.eventbus.Subscribe;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Type;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

import evolar.be.purolatorsmartsortMQTT.events.FetchDb;
import evolar.be.purolatorsmartsortMQTT.events.FixedMessage;
import evolar.be.purolatorsmartsortMQTT.events.GlassDeviceInfo;
import evolar.be.purolatorsmartsortMQTT.events.GlassMessage;
import evolar.be.purolatorsmartsortMQTT.events.Logger;
import evolar.be.purolatorsmartsortMQTT.events.RPMLookUp;
import evolar.be.purolatorsmartsortMQTT.events.RingBarcodeScan;
import evolar.be.purolatorsmartsortMQTT.events.UIUpdater;

public class PurolatorActivityGlass extends Activity {


    // Debugging
    private static final String TAG = "PurolatorActivityGlass";
    private static final boolean D = true;


    EditText scanResult;
    String enteredKey = "";


    LinearLayout topLabelLayout;
    LinearLayout labelLayout;
    RelativeLayout screenLayout;

    TextView dateView;
    TextView pinView;
    TextView pudroView;
    TextView sideView;
    TextView routeView;
    TextView shelfView;
    TextView sequenceView;

    TextView messageView;
    TextView errorView;

    TextView infoView;
    TextView speedView;
    TextView parcelView;
    int maxParcelCount = 0;

    ListView mStreetNameEdit;         //for popupwindow


    ViewFlipper viewSwitcher;
    private float downXValue;


    ListView labelListView;

    ProgressBar progressBar;

    ScheduledExecutorService scheduleList;

    private long mPrevPudroTick = 0;
    boolean appReady =  false;


    ArrayList<Label> labels4Shelf = new ArrayList<Label>();
    ArrayList<Label> labels = new ArrayList<Label>();
    ScannedLabelsAdapter scannedLabels;
    ArrayList<String> rpmStreets = new ArrayList<String>();
    ArrayList<String> rpmPostalCodes = new ArrayList<String>();     //for autofill of postalcodes
    ArrayAdapter<String> streetAdapter;

    ArrayList<RPMLookUp> rpmLookUps = new ArrayList<RPMLookUp>();
    RPMLookUp  rpmEdit = null;  //edited address



    boolean popupActive = false;
    Button mOkBtn;
    Button mMisBtn;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        if(D) Log.d(TAG,"+++ On Create +++");

        this.requestWindowFeature(Window.FEATURE_NO_TITLE);
        this.getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN, WindowManager.LayoutParams.FLAG_FULLSCREEN);
        this.getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);


        setContentView(R.layout.activity_purolator_flip);

        //get & read the initialisation file
        if (!PurolatorSmartsortMQTT.getsInstance().isDatabasesLoaded()) {
            EventBus.getDefault().post(new FetchDb(PurolatorSmartsortMQTT.INIT_DBTYPE, true));      //no feedback on screen

        }


    }

    @Override
    protected void onStop() {
        super.onStop();

        if (D) Log.d(TAG, "+++onStop+++");

        PurolatorSmartsortMQTT.getsInstance().setStopped(true);

        //create Json Object of the labels
        Gson gson = new Gson();
        final String jsonLabelList = gson.toJson(labels);
        final String jsonLabels4ShelfList = gson.toJson(labels4Shelf);

        SharedPreferences settings = getApplicationContext().getSharedPreferences("storedLabels", 0);
        SharedPreferences.Editor editor = settings.edit();
        editor.putString("labels", jsonLabelList);
        editor.putString("labels4Shelf",jsonLabels4ShelfList);
        editor.apply();
        

        PurolatorSmartsortMQTT.getsInstance().setStopped(true);

        if (scheduleList != null) scheduleList.shutdown();

        Logger logger = new Logger();
        logger.setLogType("FTPLOG");
        EventBus.getDefault().post(logger);


        GlassDeviceInfo stopDeviceInfo = new GlassDeviceInfo();
        stopDeviceInfo.setActive(false);
        stopDeviceInfo.setDeviceName(PurolatorSmartsortMQTT.getsInstance().getConfigData().getDeviceName());
        stopDeviceInfo.setAssignedRoutes(PurolatorSmartsortMQTT.getsInstance().getConfigData().getValidRoutes());
        stopDeviceInfo.setUserName(PurolatorSmartsortMQTT.getsInstance().getConfigData().getUserId());
        EventBus.getDefault().post(stopDeviceInfo);

        //wait 2 seconds
        try {
            Thread.sleep(2000);                 //1000 milliseconds is one second.
        } catch(InterruptedException ex) {
            Thread.currentThread().interrupt();
        }
        if (PurolatorSmartsortMQTT.getsInstance().isStopped()) {
            PurolatorSmartsortMQTT.getsInstance().stopProcesses();
        }

        if (D) Log.d(TAG,"Now process the onStop action");

        finish();

    }
    @Override
    public void onBackPressed() {

        //do not react on backPressed
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        EventBus.getDefault().unregister(this);


        if (D) {
            Log.d(TAG, "+++onDestroy++++ Sending logcat to wispelberg@gmail.com");
            if(PurolatorSmartsortMQTT.getsInstance().getConfigData().isSendLogcat()) sendLogcatMail();
        }
    }


    @Override
    protected void onResume(){
        super.onResume();
        if (D) Log.d(TAG, "+++onResume+++");



    }

    @Override
    protected void onStart() {
        super.onStart();

        if (D) Log.d(TAG, "+++onStart+++");
        appReady = false;

        // Get from the SharedPreferences, when app is restarted
        SharedPreferences settings = getApplicationContext().getSharedPreferences("storedLabels", 0);
        String jsonLabels  = settings.getString("labels", "");
        String jsonLabels4Shelf = settings.getString("labels4Shelf","");

        Gson gson =  new Gson();
        Type listType = new TypeToken<ArrayList<Label>>() {}.getType();
        if (!jsonLabels.isEmpty()) labels = gson.fromJson(jsonLabels,listType);
        if (!jsonLabels4Shelf.isEmpty()) labels4Shelf = gson.fromJson(jsonLabels4Shelf,listType);



        if (D)Log.d(TAG,"Restoring data: " + jsonLabels);


        PurolatorSmartsortMQTT.getsInstance().setStopped(false);
        if (!EventBus.getDefault().isRegistered(this)) {
            EventBus.getDefault().register(this);
        }
        if (!EventBus.getDefault().isRegistered(PurolatorSmartsortMQTT.getsInstance())){
            EventBus.getDefault().register(PurolatorSmartsortMQTT.getsInstance());
        }



        topLabelLayout = (LinearLayout) findViewById(R.id.topLabelLayout);
        labelLayout = (LinearLayout) findViewById(R.id.labelLayout);
        screenLayout = (RelativeLayout)findViewById(R.id.rl);

        topLabelLayout.setVisibility(View.INVISIBLE);
        labelLayout.setVisibility(View.INVISIBLE);


        dateView = (TextView) findViewById(R.id.dateView);
        pinView = (TextView) findViewById(R.id.pinView);
        pudroView = (TextView) findViewById(R.id.pudroView);
        sideView = (TextView) findViewById(R.id.sideView);
        routeView = (TextView) findViewById(R.id.routeView);
        shelfView = (TextView) findViewById(R.id.shelfView);
        sequenceView = (TextView) findViewById(R.id.sequenceView);

        errorView = (TextView) findViewById(R.id.errorView);
        errorView.setVisibility(View.INVISIBLE);

        scannedLabels = new ScannedLabelsAdapter(this, labels);

        labelListView = (ListView) findViewById(R.id.labellistView);
        labelListView.setAdapter(scannedLabels);
        scannedLabels.registerDataSetObserver(new DataSetObserver()
        {
            @Override
            public void onChanged()
            {
                maxParcelCount = Math.max(scannedLabels.getCount(),maxParcelCount);
                parcelView.setText("Total Parcels: " + maxParcelCount);
            }
        });


        progressBar = (ProgressBar) findViewById(R.id.progressBar);
        messageView = (TextView) findViewById(R.id.messageView);
        messageView.setText(getResources().getString(R.string.datasync));

        infoView = (TextView) findViewById(R.id.infoView);
        infoView.setText("");

        speedView = (TextView) findViewById(R.id.speedView);
        speedView.setText("");

        parcelView = (TextView) findViewById(R.id.parcelView);


        scanResult = (EditText) findViewById(R.id.scanResult);
        InputMethodManager lManager = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
        lManager.hideSoftInputFromWindow(scanResult.getWindowToken(), 0);


        scanResult.setOnKeyListener(new View.OnKeyListener() {
            @Override
            public boolean onKey(View v, int keyCode, KeyEvent event) {

                if (event.getAction() != KeyEvent.ACTION_DOWN)
                    return true;

                if (!appReady){
                    return true;
                }


                if (event.getUnicodeChar() != 0 && event.getUnicodeChar() != 10) {
                    enteredKey = enteredKey + Character.toString((char) event.getUnicodeChar());
                }
                if (event.getUnicodeChar() == 10) {            //newline detected
                    if (D) Log.d(TAG, "Barcode fingerscan: " + enteredKey);

                    if (enteredKey.trim().length() > 0) {
                        EventBus.getDefault().post(new RingBarcodeScan(enteredKey));        //handle ringbarcode by default
                    }
                    enteredKey = "";
                }
                return true;
            }

        });

        // activate the viewswitcher
        Animation slide_in_left;
        Animation slide_out_right;
        viewSwitcher = (ViewFlipper) findViewById(R.id.viewswitcher);
        slide_in_left = AnimationUtils.loadAnimation(this, android.R.anim.slide_in_left);
        slide_out_right = AnimationUtils.loadAnimation(this, android.R.anim.slide_out_right);
        viewSwitcher.setInAnimation(slide_in_left);
        viewSwitcher.setOutAnimation(slide_out_right);

        final LinearLayout remediationView = (LinearLayout) findViewById(R.id.remediationLayout);



        topLabelLayout.setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View v, MotionEvent event) {


                switch(event.getAction()){


                    case MotionEvent.ACTION_DOWN:
                        downXValue = event.getX();
                        break;
                    case MotionEvent.ACTION_UP:
                        float currentX = event.getX();
                        //to the right

                        if (downXValue -200 < currentX){
                            viewSwitcher.showNext();
                            if (viewSwitcher.getCurrentView() == remediationView) addRoutes();
                        }

                        if (downXValue +200 > currentX){
                            viewSwitcher.showPrevious();
                            if (viewSwitcher.getCurrentView() == remediationView) addRoutes();
                        }

                        break;
                }

                return true;
            }
        });




        // if all databases are already loaded, setup screen, alert the main console that glass is active
        if (PurolatorSmartsortMQTT.getsInstance().isDatabasesLoaded()) {
            stopSpinner();


            if (PurolatorSmartsortMQTT.getsInstance().mqttGlassComms == null) {
                PurolatorSmartsortMQTT.getsInstance().mqttGlassComms = new MQTTGlassPahoComms();      //instantiate the subscriber object
            } else{
                PurolatorSmartsortMQTT.getsInstance().mqttGlassComms.stopMQTTConnection();
                PurolatorSmartsortMQTT.getsInstance().mqttGlassComms.setMQttConnection();
            }
            GlassDeviceInfo startDeviceInfo = new GlassDeviceInfo();
            startDeviceInfo.setActive(true);
            startDeviceInfo.setDeviceName(PurolatorSmartsortMQTT.getsInstance().getConfigData().getDeviceName());
            startDeviceInfo.setAssignedRoutes(PurolatorSmartsortMQTT.getsInstance().getConfigData().getValidRoutes());
            startDeviceInfo.setUserName(PurolatorSmartsortMQTT.getsInstance().getConfigData().getUserId());
            startDeviceInfo.setDeviceType(PurolatorSmartsortMQTT.getsInstance().getConfigData().getDeviceType());

            EventBus.getDefault().post(startDeviceInfo);

            PurolatorSmartsortMQTT.getsInstance().startSchedulers("GLASS");


            if ((PurolatorSmartsortMQTT.getsInstance().DISTANCE) > 0) {
                //restart the shedule list to display the ETA, it was stopped in onStop
                scheduleList = Executors.newScheduledThreadPool(5);
                ScheduledFuture scheduledFuture = scheduleList.scheduleAtFixedRate(new Runnable() {

                    public void run() {
                        runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                if (!popupActive) scannedLabels.notifyDataSetChanged();
                            }
                        });
                    }
                }, 5, 2, TimeUnit.SECONDS);
            }

        }
        if (PurolatorSmartsortMQTT.getsInstance().configData.getTerminalID() != null) {
            UIUpdater uiUpdater = new UIUpdater();
            uiUpdater.setUpdateType(PurolatorSmartsortMQTT.UPD_ERROR);
            uiUpdater.setErrorCode("NEWINFO");
            uiUpdater.setErrorMessage("Terminal ID: " + PurolatorSmartsortMQTT.getsInstance().configData.getTerminalID());
            EventBus.getDefault().post(uiUpdater);            //do a sticky post, the activity is perhaps not active yet
        }


    }



    /**
     * Receive different messages from the Tablet
     *
     * @param event
     */

    @Subscribe(priority = 0)    //lowest priority
    public void onFixedMessageEvent(final FixedMessage event) {


        switch(event.getMessage()){

            case "POSTALSTREETS":
                String jsonString = event.getValue();
                Gson gson =  new Gson();
                Type RPMLookUpType = new TypeToken<ArrayList<RPMLookUp>>() {}.getType();
                rpmLookUps = new ArrayList<RPMLookUp>();
                if (!jsonString.isEmpty()) rpmLookUps = gson.fromJson(jsonString,RPMLookUpType);


/*
                ArrayList<String> streetInfos = new ArrayList<String>();
                for (RPMLookUp rpmLookUp:rpmLookUps){
                    String streetInfo = new String();

                    streetInfo = rpmLookUp.getRouteNumber() +" ";
                    streetInfo = streetInfo + rpmLookUp.getStreetName() + " ";
                    streetInfo = streetInfo + rpmLookUp.getStreetType() + " ";
                    streetInfo = streetInfo + rpmLookUp.getFromNumber() + " ";
                    if (rpmLookUp.getToNumber() != rpmLookUp.getFromNumber()) streetInfo = streetInfo + rpmLookUp.getToNumber();
                    streetInfo = streetInfo + rpmLookUp.isUniqueStreet();

                    streetInfos.add(streetInfo.trim());
                }

                gson = new Gson();
                final String json = gson.toJson(streetInfos);
*/
                updatePopup(jsonString,true);

                break;
            case "START":
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        infoView.setBackgroundColor(Color.LTGRAY);
                    }
                });

                GlassDeviceInfo startDeviceInfo = new GlassDeviceInfo();
                startDeviceInfo.setActive(true);
                startDeviceInfo.setDeviceName(PurolatorSmartsortMQTT.getsInstance().getConfigData().getDeviceName());
                startDeviceInfo.setAssignedRoutes(PurolatorSmartsortMQTT.getsInstance().getConfigData().getValidRoutes());
                startDeviceInfo.setUserName(PurolatorSmartsortMQTT.getsInstance().getConfigData().getUserId());
                startDeviceInfo.setDeviceType(PurolatorSmartsortMQTT.getsInstance().getConfigData().getDeviceType());

                EventBus.getDefault().post(startDeviceInfo);

                break;
            case "STOP":
                //show that the tablet is deactivated
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        infoView.setBackgroundColor(Color.RED);
                    }
                });
                break;
            case "PUDRO":

                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {

                        double speed = Double.valueOf(event.getValue());
                        if (PurolatorSmartsortMQTT.getsInstance().DISTANCE > 0) {

                            Log.d(TAG, "Set Speed at: " + speed + "inch/sec");
                            PurolatorSmartsortMQTT.getsInstance().setPUDROSpeed(speed);
                            //make sure to update the change, speed calculations are done in ScannedLabelsAdapater
                            speedView.setText("PUDRO Speed: " + event.getValue() + " inch/sec");
                            if (!popupActive) scannedLabels.notifyDataSetChanged();
                        }
                    }
                });

                break;
        }



    }


        /**
         * triggered when an UI Update is needed. This can be posted by different objects
         */
    @Subscribe
    public void onUIUpdaterEvent(final UIUpdater event) {

        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                switch (event.getUpdateType()) {


                    case PurolatorSmartsortMQTT.UPD_BOTTOMSCREEN:
                        errorView.setText("");
                        errorView.setVisibility(View.INVISIBLE);
                        updateBottom(event);
                        break;

                    case PurolatorSmartsortMQTT.UPD_TOPSCREEN:
                        errorView.setText("");
                        errorView.setVisibility(View.INVISIBLE);
                        updateList(event);
                        break;
                    case PurolatorSmartsortMQTT.UPD_CLEARSCREEN:
                        PurolatorSmartsortMQTT.packagesList.getBarcodeScans().clear();      //clear barcodescans
                        updateBottom(event);

                        scannedLabels.clear();
                        if (!popupActive) scannedLabels.notifyDataSetChanged();
                        labels4Shelf.clear();

                        break;
                    case PurolatorSmartsortMQTT.UPD_ERROR:
                        if (D) Log.d(TAG, "Received INFORMATION Update Event: " + event.getErrorCode());
                        switch (event.getErrorCode()) {
                            case "NEWINFO":
                                infoView.setText(event.getErrorMessage());
                                break;
                            case "APPENDINFO":
                                String info = infoView.getText().toString();
                                info = info + " " + event.getErrorCode();
                                infoView.setText(info);
                                break;
                            case "CLEARERROR":
                                errorView.setText(" ");
                                errorView.setVisibility(View.INVISIBLE);
                                break;
                            case "SWITCHTOLIST":        //remove label from bottom line & put back in topline
                                switchToList(event.getScannedCode());
                                break;
                            case "MAKEPOPUP":
                                String[] messages = event.getErrorMessage().split("[;]");
                                if (messages.length ==2) makePopup(messages[0],messages[1],false);
                                break;
                            case "UPDATEPOPUP":
                                updatePopup(event.getErrorMessage(),true);
                                break;
                            default:
                                errorView.setText(event.getErrorMessage());
                                errorView.setVisibility(View.VISIBLE);
                                break;
                        }

                        break;

                    case PurolatorSmartsortMQTT.UPD_STATUSPINNER:
                        messageView.setText(event.getErrorMessage());


                        break;

                    case PurolatorSmartsortMQTT.UPD_STOPSPINNER:

                        stopSpinner();
                        //inform the tablet that glass has started
                        GlassDeviceInfo startDeviceInfo = new GlassDeviceInfo();
                        startDeviceInfo.setActive(true);
                        startDeviceInfo.setDeviceName(PurolatorSmartsortMQTT.getsInstance().getConfigData().getDeviceName());
                        startDeviceInfo.setAssignedRoutes(PurolatorSmartsortMQTT.getsInstance().getConfigData().getValidRoutes());
                        startDeviceInfo.setUserName(PurolatorSmartsortMQTT.getsInstance().getConfigData().getUserId());
                        startDeviceInfo.setDeviceType(PurolatorSmartsortMQTT.getsInstance().getConfigData().getDeviceType());

                        //
                        EventBus.getDefault().post(startDeviceInfo);  //

                        if ((PurolatorSmartsortMQTT.getsInstance().DISTANCE) > 0) {

                            scheduleList = Executors.newScheduledThreadPool(5);
                            ScheduledFuture scheduledFuture = scheduleList.scheduleAtFixedRate(new Runnable() {

                                public void run() {
                                    runOnUiThread(new Runnable() {
                                        @Override
                                        public void run() {

                                            if (!popupActive) scannedLabels.notifyDataSetChanged();
                                        }
                                    });
                                }
                            }, 5, 2, TimeUnit.SECONDS);
                        }

                        break;
                }

            }
        });

    }

    /**
     * Check if scanned box is a remediation or missorted box
     * If this is the case, show an error
     *
     * @param barcode
     * @return
     */

    private boolean checkREM(String barcode){

        String barcodeType = Utilities.getBarcodeLogType(barcode);
        String barcodeResult;
        if (barcodeType.equals(("2D"))|| barcodeType.equals("NGB")){
            barcodeResult = Utilities.getPINCode(barcode);
        } else {
            barcodeResult = barcode;
        }



        for (Label label : scannedLabels.getLabels()) {
            barcodeType = Utilities.getBarcodeLogType(label.getScannedCode());
            String labelBarcodeResult;
            if (barcodeType.equals(("2D"))|| barcodeType.equals("NGB")){
                labelBarcodeResult = Utilities.getPINCode(label.getScannedCode());
            } else {
                labelBarcodeResult = label.getScannedCode();
            }

            if (labelBarcodeResult.equals(barcodeResult) && (label.getRouteNumber().equals("REM") ||label.getRouteNumber().equals("XXX") || label.getRouteNumber().equals("MIS"))) {
                return true;
            }

        }

        return false;
    }

    /**
     * Switch the info from the bottomline back to the topline
     * This is when a new pacakge is scanned and previous package is not yet put in shelf
     *
     *
     * @param barcodeScan
     */
    private void switchToList(String barcodeScan){


        for (Label shelfLabel:labels4Shelf){
            if (shelfLabel.getScannedCode().equalsIgnoreCase(barcodeScan)){
                //remove from bottom & add in the list again.
                //add new entry
                changeLabelsAdapter(false,shelfLabel);
//                scannedLabels.add(shelfLabel);

                //sort ascending
                scannedLabels.sortDate(false);

                if (!popupActive) scannedLabels.notifyDataSetChanged();

                //remove from the bottom list
                UIUpdater uiUpdater = new UIUpdater();
                uiUpdater.setUpdateType(PurolatorSmartsortMQTT.UPD_BOTTOMSCREEN);
                uiUpdater.setScannedCode(null);
                updateBottom(uiUpdater);
                labels4Shelf.clear();       //should contain 0 or 1 label

//                labels4Shelf.remove(shelfLabel);
            }

        }

    }

    /**
     * Update the bottom part of the screen & clear the element of the array
     *
     * @param event
     */

    private void updateBottom(UIUpdater event) {

        SimpleDateFormat sdf = new SimpleDateFormat("MM/dd/yyyy");
        //first find & remove the scanned barcode from the list

        if (D) Log.i(TAG, "In updateBottom for ScanCode: " + event.getScannedCode());

        if (event.getScannedCode() == null){
            dateView.setText("");
            pinView.setText("");           //contains shelf scan information
            pudroView.setText("");
            sideView.setText("");
            routeView.setText("");
            shelfView.setText("");
            sequenceView.setText("");
            return;
        }


        //parcel is put in shelf
        if (event.getScannedCode().startsWith("SHELF")){


            String message = getResources().getString(R.string.packagedrop) +" ";
            String street = event.getPostalCode()+ " "+ event.getStreetname() + " " + event.getStreetnumber();
            message = message.replace("%1",street);

            dateView.setText("");
            pinView.setText(event.getErrorMessage());           //contains shelf scan information
            pudroView.setText("");
            sideView.setText("");
            routeView.setText("");
            shelfView.setText("");
            sequenceView.setText("");
//            scannedLabels.remove(PurolatorSmartsortMQTT.getsInstance().getPackageInShelf());
            if (PurolatorSmartsortMQTT.getsInstance().isRouteValid(event.getRouteNumber())){

                PurolatorSmartsortMQTT.getsInstance().setPackageInShelf(null);
                labelLayout.setBackgroundColor(getResources().getColor(android.R.color.holo_orange_light));
            } else {
                //change background color
                labelLayout.setBackgroundColor(getResources().getColor(android.R.color.holo_red_dark));
            }

            // if not popup the screen to complete the adress information
            if ((labels4Shelf.get(0).getStreetnumber() ==  null || labels4Shelf.get(0).getStreetnumber().isEmpty()) || (labels4Shelf.get(0).getStreetname() == null || labels4Shelf.get(0).getStreetname().isEmpty() || labels4Shelf.get(0).getStreetnumber().equals("0"))) {
                String pinCode = Utilities.getPINCode(labels4Shelf.get(0).getScannedCode());
                makePopup(pinCode, labels4Shelf.get(0).getPostalCode(), true);
            } else {
                dateView.setText(message);

                Logger manifestLog = new Logger();

                manifestLog.setPostalCode(labels4Shelf.get(0).getPostalCode());
                manifestLog.setStreetName(labels4Shelf.get(0).getStreetname());
                manifestLog.setStreetNumber(labels4Shelf.get(0).getStreetnumber());
                manifestLog.setStreetType(labels4Shelf.get(0).getStreettype());
                manifestLog.setStreetNumberSuffix(labels4Shelf.get(0).getStreetunit());
                manifestLog.setMunicipalityName(labels4Shelf.get(0).getMunicipality());
                manifestLog.setRouteNumber(labels4Shelf.get(0).getRouteNumber());
                manifestLog.setShelfNumber(labels4Shelf.get(0).getShelfNumber());
                manifestLog.setUnitNumber(labels4Shelf.get(0).getStreetunit());
                manifestLog.setLogType("manifestlog");
                manifestLog.setPinCode(labels4Shelf.get(0).getPinCode());
                manifestLog.setCustomerName(labels4Shelf.get(0).getAddressee());
                manifestLog.setDeliverySequence(labels4Shelf.get(0).getDeliverySequence());
                manifestLog.setScannedCode(labels4Shelf.get(0).getScannedCode());
                manifestLog.setShelfNumber(event.getShelfNumber());
                manifestLog.setScanDateTime(new Date());
                manifestLog.setTerminalID(PurolatorSmartsortMQTT.getsInstance().configData.getTerminalID());
                EventBus.getDefault().post(manifestLog);

                labels4Shelf.clear();       //should contain only 1 element
            }


            return;
        }


        if (checkREM(event.getScannedCode())){
            UIUpdater uiUpdater = new UIUpdater();
            uiUpdater.setUpdateType(PurolatorSmartsortMQTT.UPD_ERROR);
            uiUpdater.setErrorMessage("Remediation/Misdirect");
            //TODO Show popup when multiroutes
            if (event.getPostalCode() != null && event.getPostalCode().length() > 3 ){ //&& !event.getPinCode().contains("MISDIRECT")) {
                EventBus.getDefault().post(uiUpdater);
            }else {
                makePopup(Utilities.getPINCode(event.getScannedCode()), event.getScannedCode(),false);
            }
            dateView.setText("Remediation/Misdirect");
            pinView.setText("");
            pudroView.setText("");
            sideView.setText("");
            routeView.setText("REM");
            shelfView.setText("");
            sequenceView.setText("");
            if (PurolatorSmartsortMQTT.getsInstance().isRouteValid(event.getRouteNumber())){
                //change background color
                labelLayout.setBackgroundColor(getResources().getColor(android.R.color.holo_red_dark));
            } else {
                labelLayout.setBackgroundColor(getResources().getColor(android.R.color.holo_orange_light));

            }

            return;

        }

        String barcodeType = Utilities.getBarcodeLogType(event.getScannedCode());
        String barcodeResult;
        if (barcodeType.equals(("2D"))|| barcodeType.equals("NGB")){
            barcodeResult = Utilities.getPINCode(event.getScannedCode());
        } else {
            barcodeResult = event.getScannedCode();
        }
        if (event.isCorrectBay()) {
            for (Label label : scannedLabels.getLabels()) {
                barcodeType = Utilities.getBarcodeLogType(label.getScannedCode());
                String labelBarcodeResult;
                if (barcodeType.equals(("2D")) || barcodeType.equals("NGB")) {
                    labelBarcodeResult = Utilities.getPINCode(label.getScannedCode());
                } else {
                    labelBarcodeResult = label.getScannedCode();
                }

                if (labelBarcodeResult.equals(barcodeResult)) {


                    dateView.setText("D: " + sdf.format(new Date()));
                    pinView.setText(label.getPinCode());
                    pudroView.setText(label.getPrimarySort());
                    sideView.setText(label.getSideofBelt());
                    routeView.setText(label.getRouteNumber());
                    shelfView.setText(label.getShelfNumber());
                    sequenceView.setText(label.getDeliverySequence());


                    //if barcode type of the scanned package by the operator = 2D, update the label information
                    barcodeType = Utilities.getBarcodeLogType(event.getScannedCode());

                    if (barcodeType.equals("2D")){
                        String barcode = event.getScannedCode();
                        //check if there is a \ and replace by |
                        barcode = barcode.replace("\\","|");

                        String parsed[] = barcode.split("[|]");
                        for (String aParsed : parsed) {
                            String[] valuePair = aParsed.split("[~]");

                            if (valuePair.length >= 2) {


                                switch (valuePair[0].toUpperCase()) {
                                    case "RO1":
                                        label.setAddressee(valuePair[1]);
                                        break;
                                    case "R03":             //street n#
                                        label.setStreetnumber(valuePair[1]);
                                        break;
                                    case "R04":             //address
                                        String parsedAddress[] = Utilities.parseAddress(valuePair[1]);
                                        label.setStreetname(parsedAddress[1]);
                                        label.setStreetunit(parsedAddress[2]);

                                        break;
                                    case "R05":             //address

                                        break;
                                    case "R06":             //municipality
                                        label.setMunicipality(valuePair[1]);
                                        break;
                                    case "R07":             //postal code
                                        label.setPostalCode(valuePair[1]);
                                        break;
                                    case "S04":
//                                        logger.setDeliveryTime(valuePair[1]);
                                        break;
                                    case "S05":
//                                        logger.setShipmentType(valuePair[1]);
                                        break;
                                    case "S06":
//                                        logger.setDeliveryType(valuePair[1]);
                                        break;
                                    case "S07":
//                                        logger.setDiversionCode(valuePair[1]);
                                        break;
                                    case "S15":
//                                        logger.setHandlingClassType(valuePair[1]);
                                        break;
                                }
                            }
                        }

                    }




                    //package is scanned to put into shelf, remove from upper list, but store it
                    //in a separate list till package is dropped on the shelf
                    labels4Shelf.clear();       //only in label allowed due to change in logic
                    labels4Shelf.add(label);

                    changeLabelsAdapter(true, label);
//                scannedLabels.remove(label);
                    if (!popupActive) scannedLabels.notifyDataSetChanged();

                    labelLayout.setBackgroundColor(getResources().getColor(android.R.color.holo_green_light));
                    ScheduledExecutorService directGlass = Executors.newScheduledThreadPool(2);
                    ScheduledFuture scheduleLogFuture = directGlass.schedule(new Runnable() {
                        @Override
                        public void run() {
                            runOnUiThread(new Runnable() {
                                @Override
                                public void run() {
                                    labelLayout.setBackgroundColor(getResources().getColor(android.R.color.holo_orange_light));
                                }
                            });
                        }
                    }, 1, TimeUnit.SECONDS);


                    //make package ready to put into a shelf
                    PurolatorSmartsortMQTT.getsInstance().setPackageInShelf(label);

                    break;
                }

            }
        } else {//show scanned label not in correct bay
            if (D) Log.i(TAG,"In incorrect bay scan");
            dateView.setText("D: " + sdf.format(new Date()));
            pinView.setText(event.getPinCode());
            pudroView.setText(event.getPrimarySort());
            sideView.setText(event.getSideofBelt());
            routeView.setText(event.getRouteNumber());
            shelfView.setText(event.getShelfNumber());
            sequenceView.setText(event.getDeliverySequence());
            //change the background color
            if (PurolatorSmartsortMQTT.getsInstance().isRouteValid(event.getRouteNumber())){
                labelLayout.setBackgroundColor(getResources().getColor(android.R.color.holo_green_light));
                ScheduledExecutorService directGlass = Executors.newScheduledThreadPool(2);
                ScheduledFuture scheduleLogFuture = directGlass.schedule(new Runnable() {
                    @Override
                    public void run() {
                        runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                labelLayout.setBackgroundColor(getResources().getColor(android.R.color.holo_orange_light));
                            }
                        });
                    }
                }, 1, TimeUnit.SECONDS);
                // make label ready to put on shelf
                if (event.getRouteNumber().equals("REM") ||event.getRouteNumber().equals("XXX") || event.getRouteNumber().equals("MIS")){
                        makePopup(Utilities.getPINCode(event.getScannedCode()), event.getScannedCode(),false);

                } else {
                    Label slabel = new Label();
                    slabel.setDeliverySequence(event.getDeliverySequence());
                    slabel.setPinCode(event.getPinCode());
                    slabel.setPrimarySort(event.getPrimarySort());
                    slabel.setRouteNumber(event.getRouteNumber());

                    slabel.setScanTime(event.getScanDate());
                    if (event.getShelfNumber() != null) {
                        slabel.setShelfNumber(event.getShelfNumber());
                    } else {
                        slabel.setShelfNumber("");
                    }
                    slabel.setSideofBelt(event.getSideofBelt());
                    slabel.setScannedCode(event.getScannedCode());
                    slabel.setPostalCode(event.getPostalCode());
                    slabel.setAddressee(event.getAddressee());
                    slabel.setStreetname(event.getStreetname());
                    slabel.setStreetnumber(event.getStreetnumber());
                    slabel.setStreetunit(event.getStreetunit());
                    slabel.setMunicipality(event.getMunicipality());

                    //package is scanned to put into shelf, remove from upper list, but store it
                    //in a separate list till package is dropped on the shelf
                    labels4Shelf.clear();       //only in label allowed due to change in logic
                    labels4Shelf.add(slabel);

                    //make package ready to put into a shelf
                    PurolatorSmartsortMQTT.getsInstance().setPackageInShelf(slabel);
                }
            } else {
                labelLayout.setBackgroundColor(getResources().getColor(android.R.color.holo_red_dark));
            }
        }
        //check if the next first labels are remediation labels and remove them : not needed in the accumulation bay mode
        //labels are ordered in sequence of scanning
/**
        for (int i = 0; i<scannedLabels.getCount();i++){
            if(D) Log.d(TAG,"REMEDIATION RECORD: " + scannedLabels.getItem(i).getScannedCode());
            if (scannedLabels.getItem(i).getRouteNumber().equals("REM") ||scannedLabels.getItem(i).getRouteNumber().equals("XXX" )){
                changeLabelsAdapter(true,scannedLabels.getItem(i));
//                scannedLabels.remove(scannedLabels.getItem(i));
                i--;
            } else {
                break;
            }
        }
*/

        if (!popupActive && event.isCorrectBay()) scannedLabels.notifyDataSetChanged();



    }

    /**
     * Update the list adapter with a new entry & sort ascending, oldest scans are below
     *
     * @param event
     */

    private void updateList(UIUpdater event) {


        Label label = new Label();
        label.setDeliverySequence(event.getDeliverySequence());
        label.setPinCode(event.getPinCode());
        label.setPrimarySort(event.getPrimarySort());
        label.setRouteNumber(event.getRouteNumber());
        label.setScanTime(event.getScanDate());
        label.setShelfNumber(event.getShelfNumber());
        label.setSideofBelt(event.getSideofBelt());
        label.setScannedCode(event.getScannedCode());

        label.setPostalCode(event.getPostalCode());
        label.setAddressee(event.getAddressee());
        label.setStreetname(event.getStreetname());
        label.setStreetnumber(event.getStreetnumber());
        label.setStreetunit(event.getStreetunit());
        label.setMunicipality(event.getMunicipality());

        //add new entry
        changeLabelsAdapter(false,label);
//        scannedLabels.add(label);


        //sort ascending
        scannedLabels.sortDate(false);
        if (!popupActive) scannedLabels.notifyDataSetChanged();

    }

    /**
     * all databases are synced with the FTP server, free the screen & hide the spinner
     */

    private void stopSpinner() {

        appReady = true;            //app is ready, can now receive scanning inputs

        progressBar.setVisibility(View.INVISIBLE);
        messageView.setVisibility(View.INVISIBLE);

        topLabelLayout.setVisibility(View.VISIBLE);
        labelLayout.setVisibility(View.VISIBLE);




    }



    /**
     * Returns the speed of the PUDRO, based on the distance of 1 revolution of a wheel &
     * the time between 2 revolutions.
     * Returns the speed in cm/sec
     * Periphery is in cm.
     *
     * @param periphery of a wheel
     * @return
     */


    public double getPudroSpeed(double periphery){

        long nowPudroTick = new Date().getTime();


        if (mPrevPudroTick == 0) {
            mPrevPudroTick = nowPudroTick;
            return 0;
        }

        double revolDistance = Math.PI * periphery;
        long elapsedTime = nowPudroTick - mPrevPudroTick;

        double cmPerSec =  revolDistance/elapsedTime;

        mPrevPudroTick = nowPudroTick;
        return cmPerSec ;


    }

    /**
     * Add routes to postalCodes in remediation without routes
     *
     */

    private void addRoutes(){

        popupActive = true;     //indicate popup is active, to prevent updates on screen

        final ArrayList<PostalRoute> postalRoutes = new ArrayList<PostalRoute>();

        //Loop through the packages, identified with REM route
        for (Label label:labels){
            if (label.getRouteNumber().equals("REM") && Utilities.getPostalCode(label.getScannedCode())!= null ){
                //check if postalCode already exist
                boolean addPostalCode = true;
                for (PostalRoute postalRoute:postalRoutes){
                    if (postalRoute.getPostalCode().equalsIgnoreCase(Utilities.getPostalCode(label.getScannedCode()))){
                        addPostalCode = false;
                    }
                }
                if (addPostalCode){
                    PostalRoute postalRoute = new PostalRoute();
                    postalRoute.setPostCode(Utilities.getPostalCode(label.getScannedCode()));
                    postalRoutes.add(postalRoute);
                }

            }
        }


        if (postalRoutes.isEmpty()){
            popupActive = false;
            viewSwitcher.showPrevious();
            return;
        }

        final ListView editRoutes = (ListView) findViewById(R.id.addRoutesListView);
        //make a list of all found routes & edit the
        final AddRoutesAdapter addRoutes = new AddRoutesAdapter(this,  editRoutes, postalRoutes);
        editRoutes.setSmoothScrollbarEnabled(true);
        editRoutes.setAdapter(addRoutes);
        addRoutes.notifyDataSetChanged();



        Button okBtn = (Button) findViewById(R.id.ok_btn);
        Button cancelBtn = (Button) findViewById(R.id.cancel_btn);



        cancelBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                popupActive = false;
                viewSwitcher.showPrevious();

            }
        });

        okBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                // create a GlassMessage with the list of postalCodes & routes in jSon Format
                //create Json Object of the labels
                Gson gson = new Gson();
                final String jsonPostalRoutes = gson.toJson(postalRoutes);
                GlassMessage glassMessage = new GlassMessage();
                glassMessage.setMessage("POSTALROUTES::" + jsonPostalRoutes);
                glassMessage.setBarcode(null);
                glassMessage.setFixedTarget(Utilities.makeTargetName("FIXED","FIXED"));
                glassMessage.setGlassName(PurolatorSmartsortMQTT.getsInstance().getConfigData().getDeviceName());
                EventBus.getDefault().post(glassMessage);

                ArrayList<Label> copyLabels = new ArrayList<Label>(labels);     //otherwise you get a ConcurrentModificationException
                // delete all labels with the associated Postalcodes
                for (PostalRoute postalRoute:postalRoutes) {
                    if (postalRoute.getRoute() != null && postalRoute.getRoute().length() == 3) {
                        for (Label label : copyLabels) {
                            if (Utilities.getPostalCode(label.getScannedCode()) != null && Utilities.getPostalCode(label.getScannedCode()).equalsIgnoreCase(postalRoute.getPostalCode())) {
                                if (D) Log.d(TAG, "Remove: " + Utilities.getPostalCode(label.getScannedCode() + " " + postalRoute.getPostalCode()));
                                changeLabelsAdapter(true, label);
                                // scannedLabels.remove(label);
                            }
                        }
                    }
                }
                popupActive = false;
                viewSwitcher.showPrevious();
                scannedLabels.notifyDataSetChanged();

            }
        });

    }

    /**
     * Make a popupwindow to enter postalcode or streetcode for lookup in RPM database (on tablet)
     *
     * @param pinCode       //for identification
     *
     * @param shelfScan       //if this window is activated with a shelf scan or not
     */

    private void makePopup(final String pinCode,  final String barcode, final boolean shelfScan){

        runOnUiThread(new Runnable() {
            @Override
            public void run() {

                popupActive = true;     //indicate popup is active, to prevent updates on screen

                final Dialog popupWindow = new Dialog(PurolatorActivityGlass.this);

                popupWindow.setContentView(R.layout.editlabel_layout);
                popupWindow.setTitle("PIN: "+pinCode);
                popupWindow.setCancelable(false);
                popupWindow.setCanceledOnTouchOutside(false);



                InputFilter setPostalFilters[]  = new InputFilter[]{new InputFilter.AllCaps(),new InputFilter.LengthFilter(6)};
                InputFilter setRouteFilters[]  = new InputFilter[]{new InputFilter.AllCaps(),new InputFilter.LengthFilter(3)};

                if (rpmPostalCodes.isEmpty()){
                    rpmPostalCodes = PurolatorSmartsortMQTT.getsInstance().getPostalCodes();
                }
                final AutoCompleteTextView postalCodeEdit = (AutoCompleteTextView) popupWindow.findViewById(R.id.editPostalCode);


                ArrayAdapter<String> postalCodesAutofill = new ArrayAdapter<String>(popupWindow.getContext(), android.R.layout.simple_dropdown_item_1line, rpmPostalCodes);
                postalCodeEdit.setFilters(setPostalFilters);
                postalCodeEdit.setAdapter(postalCodesAutofill);

                String postalCode = Utilities.getPostalCode(barcode);
                if (postalCode !=null) postalCodeEdit.setText(postalCode);

                final EditText postalCodeEntry = (EditText) popupWindow.findViewById(R.id.postalCodeEntry);
                postalCodeEntry.setEnabled(false);
                postalCodeEntry.setInputType(InputType.TYPE_TEXT_FLAG_CAP_CHARACTERS);
                final EditText streetEntry = (EditText) popupWindow.findViewById(R.id.streetEntry);
                streetEntry.setEnabled(false);
                streetEntry.setInputType(InputType.TYPE_TEXT_FLAG_CAP_CHARACTERS);
                final EditText streetTypeEntry = (EditText) popupWindow.findViewById(R.id.streetTypeEntry);
                streetTypeEntry.setEnabled(false);
                streetTypeEntry.setInputType(InputType.TYPE_TEXT_FLAG_CAP_CHARACTERS);
                final EditText streetNrEntry = (EditText) popupWindow.findViewById(R.id.streetNrEntry);
                streetNrEntry.setEnabled(false);
                streetNrEntry.setInputType(InputType.TYPE_CLASS_NUMBER);
                final EditText streetUnitEntry = (EditText) popupWindow.findViewById(R.id.streetUnitEntry);
                streetUnitEntry.setEnabled(false);
                streetUnitEntry.setInputType(InputType.TYPE_TEXT_FLAG_CAP_CHARACTERS);
                final EditText municipalityEntry = (EditText) popupWindow.findViewById(R.id.municipalityEntry);
                municipalityEntry.setEnabled(false);
                municipalityEntry.setInputType(InputType.TYPE_TEXT_FLAG_CAP_CHARACTERS);

                mMisBtn = (Button) popupWindow.findViewById(R.id.mis_button);
                if (pinCode.contains("MIS")){
                    mMisBtn.setVisibility(View.VISIBLE);
                } else {
                    mMisBtn.setVisibility(View.INVISIBLE);
                }



                mOkBtn = (Button) popupWindow.findViewById(R.id.ok_button);
                mOkBtn.setEnabled(false);


                mStreetNameEdit = (ListView) popupWindow.findViewById(R.id.editStreetName);

                rpmStreets.clear();

                streetAdapter = new ArrayAdapter<String>(popupWindow.getContext(), android.R.layout.test_list_item,rpmStreets);

                mStreetNameEdit.setAdapter(streetAdapter);
                mStreetNameEdit.setFocusable(true);


                streetAdapter.registerDataSetObserver(new DataSetObserver() {
                    @Override
                    public void onChanged() {
                        super.onChanged();

                        if (streetAdapter.getCount() == 1){
                            InputMethodManager imm = (InputMethodManager)getSystemService(Context.INPUT_METHOD_SERVICE);
                            if (!shelfScan)
                                imm.hideSoftInputFromWindow(postalCodeEdit.getWindowToken(), 0);

                            postalCodeEntry.setText(rpmLookUps.get(0).getPostalCode());
                            streetEntry.setText(rpmLookUps.get(0).getStreetName());
                            if (rpmLookUps.get(0).getFromNumber() == rpmLookUps.get(0).getToNumber())
                                streetNrEntry.setText(""+rpmLookUps.get(0).getFromNumber());
                            //TODO streettype in dropdown
                            streetTypeEntry.setText(rpmLookUps.get(0).getStreetType());
                            municipalityEntry.setText(rpmLookUps.get(0).getMunicipality());

                            postalCodeEntry.setEnabled(true);
                            streetEntry.setEnabled(true);
                            streetTypeEntry.setEnabled(true);
                            streetNrEntry.setEnabled(true);
                            streetUnitEntry.setEnabled(true);
                            municipalityEntry.setEnabled(true);

                            mOkBtn.setEnabled(true);
                            streetNrEntry.requestFocus();
                            imm.showSoftInput(streetNrEntry, InputMethodManager.SHOW_IMPLICIT);

                            rpmEdit = new RPMLookUp();
                            rpmEdit.setRouteNumber(rpmLookUps.get(0).getRouteNumber());
                            mMisBtn.setVisibility(View.INVISIBLE);


                        }
                        if (streetAdapter.getCount() == 0){
                            if (streetAdapter.getCount() == 0) {
                                mMisBtn.setVisibility(View.VISIBLE);
                            }
                        }
                    }
                });


                if (shelfScan){
//                    streetAdapter.add("1");             //force a change
//                    streetAdapter.remove("1");

                    postalCodeEntry.setText(labels4Shelf.get(0).getPostalCode());
                    streetEntry.setText(labels4Shelf.get(0).getStreetname());
                    streetNrEntry.setText(labels4Shelf.get(0).getStreetnumber());
                    streetTypeEntry.setText(labels4Shelf.get(0).getStreettype());
                    streetUnitEntry.setText(labels4Shelf.get(0).getStreetunit());
                    municipalityEntry.setText(labels4Shelf.get(0).getMunicipality());

                    postalCodeEntry.setEnabled(true);
                    streetEntry.setEnabled(true);
                    streetTypeEntry.setEnabled(true);
                    streetNrEntry.setEnabled(true);
                    streetUnitEntry.setEnabled(true);
                    municipalityEntry.setEnabled(true);

                    mOkBtn.setEnabled(true);
                    streetNrEntry.requestFocus();
                    InputMethodManager imm = (InputMethodManager)getSystemService(Context.INPUT_METHOD_SERVICE);
                    imm.showSoftInput(streetNrEntry, InputMethodManager.SHOW_IMPLICIT);

                    rpmEdit = new RPMLookUp();
                    rpmEdit.setRouteNumber(labels4Shelf.get(0).getRouteNumber());

                    postalCodeEdit.setEnabled(false);

                } else {
                    postalCodeEdit.setEnabled(true);
                }


                mStreetNameEdit.setOnItemClickListener(new AdapterView.OnItemClickListener() {
                    @Override
                    public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                        InputMethodManager imm = (InputMethodManager)getSystemService(Context.INPUT_METHOD_SERVICE);
                        imm.hideSoftInputFromWindow(postalCodeEdit.getWindowToken(), 0);

                        postalCodeEntry.setText(rpmLookUps.get(position).getPostalCode());
                        streetEntry.setText(rpmLookUps.get(position).getStreetName());
                        if (rpmLookUps.get(position).getFromNumber() == rpmLookUps.get(position).getToNumber())
                            streetNrEntry.setText(""+rpmLookUps.get(position).getFromNumber());
                        //TODO streettype in dropdown
                        streetTypeEntry.setText(rpmLookUps.get(position).getStreetType());
                        municipalityEntry.setText(rpmLookUps.get(position).getMunicipality());

                        postalCodeEntry.setEnabled(true);
                        streetEntry.setEnabled(true);
                        streetTypeEntry.setEnabled(true);
                        streetNrEntry.setEnabled(true);
                        streetUnitEntry.setEnabled(true);
                        municipalityEntry.setEnabled(true);

                        mOkBtn.setEnabled(true);
                        streetNrEntry.requestFocus();
                        imm.showSoftInput(streetNrEntry, InputMethodManager.SHOW_IMPLICIT);

                        rpmEdit = new RPMLookUp();
                        rpmEdit.setRouteNumber(rpmLookUps.get(position).getRouteNumber());

                    }
                });
/*
                mStreetNameEdit.setOnItemLongClickListener(new AdapterView.OnItemLongClickListener() {
                    @Override
                    public boolean onItemLongClick(AdapterView<?> parent, View view, int position, long id) {


                        String barcode = null;
                        //delete from the toplist
                        for (Label label : labels) {
                            if ((label.getPinCode() != null && label.getPinCode().equalsIgnoreCase(pinCode))||(label.getScannedCode() != null && label.getScannedCode().equalsIgnoreCase(pinCode))) {
                                barcode = label.getScannedCode();
                                //also remove from list of scanned barcodes
                                PurolatorSmartsortMQTT.packagesList.getBarcodeScans().remove(barcode);
                                changeLabelsAdapter(true, label);
                                break;
                            }
                        }

                        makeOCRMessage(rpmLookUps.get(position),barcode);
                        PurolatorSmartsortMQTT.getsInstance().setPackageInShelf(null);

//                        popupWindow.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_HIDDEN);
                        popupWindow.dismiss();
                        popupActive = false;            //enable updates on screen again

                        return true;
                    }

                });

*/
                if (postalCode != null && postalCode.length() == 6) {

                    postalCodeEdit.clearListSelection();
                    postalCodeEdit.dismissDropDown();

                    // send the postcode to the tablet to retrieve possible streets & numbers
                    streetAdapter.clear();

                    if (!PurolatorSmartsortMQTT.getsInstance().getConfigData().isGlassRemediation()) {
                        GlassMessage glassMessage = new GlassMessage();
                        glassMessage.setMessage("POSTALCODE::" + postalCode);
                        glassMessage.setBarcode(null);
                        glassMessage.setFixedTarget(Utilities.makeTargetName("FIXED", "FIXED"));
                        glassMessage.setGlassName(PurolatorSmartsortMQTT.getsInstance().getConfigData().getDeviceName());
                        EventBus.getDefault().post(glassMessage);
                    } else {
                        //it is a database intensive action, run on new thread
                        new Thread(new Runnable() {
                            @Override
                            public void run() {PurolatorSmartsortMQTT.getsInstance().getAddresses(postalCodeEdit.getText().toString(),"");}
                        }).start();
                    }
                    InputMethodManager imm = (InputMethodManager) getSystemService(Activity.INPUT_METHOD_SERVICE);
                    imm.toggleSoftInput(InputMethodManager.HIDE_IMPLICIT_ONLY, 0);


                } else if (!shelfScan){

                    postalCodeEdit.postDelayed(new Runnable() {
                        public void run() {
                            postalCodeEdit.requestFocus();
                            InputMethodManager lManager = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
                            lManager.showSoftInput(postalCodeEdit, InputMethodManager.SHOW_FORCED);
                        }
                    }, 200);
                }
                postalCodeEdit.setOnEditorActionListener(new TextView.OnEditorActionListener() {
                    @Override
                    public boolean onEditorAction(TextView v, int actionId, KeyEvent event) {
                        if(actionId == EditorInfo.IME_ACTION_DONE || event.getKeyCode() == KeyEvent.KEYCODE_ENTER)  {
                            // send the postcode to the tablet to retrieve possible streets & numbers
                            postalCodeEdit.clearListSelection();
                            postalCodeEdit.dismissDropDown();

                            streetAdapter.clear();
                            if (postalCodeEdit.getText().length() == 6) {
                                if (!PurolatorSmartsortMQTT.getsInstance().getConfigData().isGlassRemediation()) {
                                    GlassMessage glassMessage = new GlassMessage();
                                    glassMessage.setMessage("POSTALCODE::" + postalCodeEdit.getText());
                                    glassMessage.setBarcode(null);
                                    glassMessage.setFixedTarget(Utilities.makeTargetName("FIXED", "FIXED"));
                                    glassMessage.setGlassName(PurolatorSmartsortMQTT.getsInstance().getConfigData().getDeviceName());
                                    EventBus.getDefault().post(glassMessage);
                                } else {
                                    //it is a database intensive action, run on new thread
                                    new Thread(new Runnable() {
                                        @Override
                                        public void run() {
                                            PurolatorSmartsortMQTT.getsInstance().getAddresses(postalCodeEdit.getText().toString(),"");
                                        }
                                    }).start();

                                }
                                if (event!=null && event.getKeyCode() == KeyEvent.KEYCODE_ENTER){
                                    InputMethodManager imm = (InputMethodManager) getSystemService(Activity.INPUT_METHOD_SERVICE);
                                    imm.toggleSoftInput(InputMethodManager.HIDE_IMPLICIT_ONLY, 0);
                                    return true;
                                }

                            }
                        }
                        return false;
                    }
                });


                //automatically search of postcode after 6 characters are entered
                postalCodeEdit.addTextChangedListener(new TextWatcher() {

                    public void afterTextChanged(Editable s) {
                        if (s.length() == 1||s.length()==3 || s.length() == 5){
                            postalCodeEdit.setInputType(InputType.TYPE_CLASS_NUMBER);
                        } else {
                            postalCodeEdit.setInputType(InputType.TYPE_TEXT_FLAG_CAP_CHARACTERS);

                        }
                        if (s.length() == 6){
                            BaseInputConnection inputConnection = new BaseInputConnection(postalCodeEdit, true);
                            inputConnection.sendKeyEvent(new KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_ENTER));

                        } else if (!streetAdapter.isEmpty()) {
                            streetAdapter.clear();
                            mOkBtn.setEnabled(false);
                            mMisBtn.setVisibility(View.INVISIBLE);


                            postalCodeEntry.setEnabled(false);
                            postalCodeEntry.setText("");
                            streetEntry.setEnabled(false);
                            streetEntry.setText("");
                            streetTypeEntry.setEnabled(false);
                            streetTypeEntry.setText("");
                            streetNrEntry.setEnabled(false);
                            streetNrEntry.setText("");
                            streetUnitEntry.setEnabled(false);
                            streetUnitEntry.setText("");
                            municipalityEntry.setEnabled(false);
                            municipalityEntry.setText("");


                        }

                    }

                    public void beforeTextChanged(CharSequence s, int start,
                                                  int count, int after) {
                    }

                    public void onTextChanged(CharSequence s, int start,
                                              int before, int count) {
                    }
                });

                mMisBtn.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        Toast.makeText(PurolatorActivityGlass.this,"Printing Misdirect Label",Toast.LENGTH_LONG).show();
                        //delete from the toplist
                        for (Label label : labels) {
                            if (label.getScannedCode() != null && label.getScannedCode().equalsIgnoreCase(barcode)) {
                                GlassMessage glassMessage = new GlassMessage();
                                glassMessage.setMessage("MISMESSAGE");
                                glassMessage.setBarcode(barcode);
                                glassMessage.setFixedTarget(Utilities.makeTargetName("FIXED", "FIXED"));
                                glassMessage.setGlassName(PurolatorSmartsortMQTT.getsInstance().getConfigData().getDeviceName());
                                EventBus.getDefault().post(glassMessage);
                                //also remove from list of scanned barcodes
                                PurolatorSmartsortMQTT.packagesList.getBarcodeScans().remove(barcode);
                                changeLabelsAdapter(true, label);
                                break;
                            }
                        }
                        PurolatorSmartsortMQTT.getsInstance().setPackageInShelf(null);



                        popupWindow.dismiss();
                        popupActive = false;            //enable updates on screen again

                    }
                });

                mOkBtn.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        rpmEdit.setPostalCode(postalCodeEntry.getText().toString());
                        rpmEdit.setStreetName(streetEntry.getText().toString());
                        rpmEdit.setStreetType(streetTypeEntry.getText().toString());
                        if (!streetNrEntry.getText().toString().trim().isEmpty())
                            rpmEdit.setFromNumber(Integer.valueOf(streetNrEntry.getText().toString()));
                        rpmEdit.setToNumber(rpmEdit.getFromNumber());
                        rpmEdit.setFromUnitNumber(streetUnitEntry.getText().toString());
                        if (rpmEdit.getFromUnitNumber().length() == 0) rpmEdit.setFromUnitNumber("0");
                        rpmEdit.setToUnitNumber(rpmEdit.getFromUnitNumber());
                        rpmEdit.setMunicipality(municipalityEntry.getText().toString());
                        if (rpmEdit.getMunicipality().length() == 0) rpmEdit.setMunicipality("0");
                        rpmEdit.setUniqueStreet(true);

                        String barcode = null;
                        //delete from the toplist
                        for (Label label : labels) {
                            if (label.getPinCode() != null && label.getPinCode().equalsIgnoreCase(pinCode)) {
                                barcode = label.getScannedCode();
                                //also remove from list of scanned barcodes
                                PurolatorSmartsortMQTT.packagesList.getBarcodeScans().remove(barcode);
                                changeLabelsAdapter(true, label);
                                break;
                            }
                        }

                        rpmEdit.setPinCode(barcode);            //the scanned barcode;
                        //
                        if (!shelfScan) {
                            makeOCRMessage(rpmEdit, barcode);
                            PurolatorSmartsortMQTT.getsInstance().setPackageInShelf(null);
                        }else {
                            labels4Shelf.get(0).setPostalCode(rpmEdit.getPostalCode());
                            labels4Shelf.get(0).setMunicipality(rpmEdit.getMunicipality());
                            labels4Shelf.get(0).setStreetunit(rpmEdit.getFromUnitNumber());
                            labels4Shelf.get(0).setStreetnumber(String.valueOf(rpmEdit.getFromNumber()));
//                            labels4Shelf.get(0).setAddressee(rpmEdit.get);
                            labels4Shelf.get(0).setPostalCode(rpmEdit.getPostalCode());
                            labels4Shelf.get(0).setStreettype(rpmEdit.getStreetType());
                            labels4Shelf.get(0).setRouteNumber(rpmEdit.getRouteNumber());
                            labels4Shelf.get(0).setShelfNumber(barcode);

                            Logger manifestLog = new Logger();

                            manifestLog.setPostalCode(labels4Shelf.get(0).getPostalCode());
                            manifestLog.setStreetName(labels4Shelf.get(0).getStreetname());
                            manifestLog.setStreetNumber(labels4Shelf.get(0).getStreetnumber());
                            manifestLog.setStreetType(labels4Shelf.get(0).getStreettype());
                            manifestLog.setStreetNumberSuffix(labels4Shelf.get(0).getStreetunit());
                            manifestLog.setMunicipalityName(labels4Shelf.get(0).getMunicipality());
                            manifestLog.setRouteNumber(labels4Shelf.get(0).getRouteNumber());
                            manifestLog.setShelfNumber(labels4Shelf.get(0).getShelfNumber());
                            manifestLog.setUnitNumber(labels4Shelf.get(0).getStreetunit());
                            manifestLog.setLogType("manifestlog");
                            manifestLog.setPinCode(labels4Shelf.get(0).getPinCode());
                            manifestLog.setCustomerName(labels4Shelf.get(0).getAddressee());
                            manifestLog.setDeliverySequence(labels4Shelf.get(0).getDeliverySequence());
                            manifestLog.setScannedCode(labels4Shelf.get(0).getScannedCode());
                            manifestLog.setShelfNumber(labels4Shelf.get(0).getShelfNumber());
                            manifestLog.setScanDateTime(new Date());
                            manifestLog.setTerminalID(PurolatorSmartsortMQTT.getsInstance().configData.getTerminalID());
                            EventBus.getDefault().post(manifestLog);

                            labels4Shelf.clear();

                        }
    //                        popupWindow.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_HIDDEN);
                        popupWindow.dismiss();
                        popupActive = false;            //enable updates on screen again

                    }
                });


                Button cancelBtn = (Button) popupWindow.findViewById(R.id.cancel_button);


                cancelBtn.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {

                        PurolatorSmartsortMQTT.packagesList.getBarcodeScans().add(barcode);

//                        popupWindow.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_HIDDEN);
                        popupWindow.dismiss();
                        popupActive = false;            //enable updates on screen again


                    }
                });

                popupWindow.show();

                DisplayMetrics metrics = new DisplayMetrics(); //get metrics of screen
                getWindowManager().getDefaultDisplay().getMetrics(metrics);
                int height = (int) (metrics.heightPixels*0.9); //set height to 90% of total
                int width = (int) (metrics.widthPixels*0.9); //set width to 90% of total

                popupWindow.getWindow().setLayout(width, height); //set layout
            }
        });





    }

    /**
     * Show the streets Autocomplete field in the popup Window
     *
     * @param jsonString
     * @param streets       //false= sorted on street, true = sorted on postcode
     *
     */


    private void updatePopup(final String jsonString, final boolean streets) {

        runOnUiThread(new Runnable() {
            @Override
            public void run() {


                //hide the keyboard
                View view = getCurrentFocus();
                if (view != null) {
                    InputMethodManager imm = (InputMethodManager)getSystemService(Context.INPUT_METHOD_SERVICE);
                    imm.hideSoftInputFromWindow(view.getWindowToken(), 0);
                }



                Gson gson =  new Gson();
                Type RPMLookUpType = new TypeToken<ArrayList<RPMLookUp>>() {}.getType();
                ArrayList<RPMLookUp> lRpmLookUps = new ArrayList<RPMLookUp>();
                if (!jsonString.isEmpty()) lRpmLookUps = gson.fromJson(jsonString,RPMLookUpType);


                ArrayList<String> streetInfos = new ArrayList<String>();
                for (RPMLookUp rpmLookUp:lRpmLookUps){
                    String streetInfo = new String();

                    streetInfo = rpmLookUp.getRouteNumber() +"    ";
                    streetInfo = streetInfo + rpmLookUp.getStreetName() + " ";
                    streetInfo = streetInfo + rpmLookUp.getStreetType() + "  Nr: ";
                    streetInfo = streetInfo + rpmLookUp.getFromNumber();
                    if (rpmLookUp.getToNumber() != rpmLookUp.getFromNumber()) streetInfo = streetInfo + "-"+ rpmLookUp.getToNumber();
                    if (rpmLookUp.getFromUnitNumber() != "") {
                        streetInfo = streetInfo + " Unit: " + rpmLookUp.getFromUnitNumber();
                        if (!rpmLookUp.getToUnitNumber().equals(rpmLookUp.getFromUnitNumber()))
                            streetInfo = streetInfo + "-" + rpmLookUp.getToUnitNumber();
                    }

                    streetInfos.add(streetInfo.trim());
                }

                if (!streetInfos.isEmpty()) {
                    streetAdapter.addAll(streetInfos);
                    streetAdapter.notifyDataSetChanged();

                }



            }
        });


    }

    /**
     * Make an OCR message and sends it like the OCR system
     *
     * @param message
     * @param barcode
     */
    private void makeOCRMessage(RPMLookUp message,String barcode){


        String pinCode = Utilities.getPINCode(barcode);

        if (pinCode != null) {
            String ocrString = "V01~A01|R01~|R02~%6|R03~%1|R04~%2|R05~|R06~%7|R07~%3|S02~%4|S03~|S04~0|S05~0|S06~0|S07~0|S15~0";
            if (message.getRouteNumber().length()>1){
                ocrString = "V01~A01|R01~|R02~%6|R03~%1|R04~%2|R05~|R06~%7|R07~%3|S02~%4|S03~|S04~0|S05~0|S06~0|S07~0|S15~0|S16~%5";
                ocrString = ocrString.replace("%5", message.getRouteNumber());

            }

            ocrString = ocrString.replace("%1", String.valueOf(message.getFromNumber()));                //streetnumber
            ocrString = ocrString.replace("%2", message.getStreetName() + " " + message.getStreetType());
            ocrString = ocrString.replace("%3", message.getPostalCode());
            ocrString = ocrString.replace("%4", Utilities.getPINCode(barcode));
            ocrString = ocrString.replace("%6", message.getFromUnitNumber());
            ocrString = ocrString.replace("%7", message.getMunicipality());

            GlassMessage glassMessage = new GlassMessage();
            glassMessage.setMessage("OCRMESSAGE::" + ocrString);
            glassMessage.setBarcode(barcode);
            glassMessage.setFixedTarget(Utilities.makeTargetName("FIXED", "FIXED"));
            glassMessage.setGlassName(PurolatorSmartsortMQTT.getsInstance().getConfigData().getDeviceName());
            EventBus.getDefault().post(glassMessage);
            //also, store in local database for later retrieval (with LookupFixedBarcodeADDR)
            PurolatorSmartsortMQTT.getsInstance().updatePSTStreets(message);
            // now send a new glass message event with the RPMLookup Message so that it is updated on the databases of the other devices
            glassMessage = new GlassMessage();
            glassMessage.setBarcode(barcode);
            glassMessage.setFixedTarget("BROADCAST");
            glassMessage.setGlassName(PurolatorSmartsortMQTT.getsInstance().getConfigData().getDeviceName());
            Gson gson = new Gson();
            glassMessage.setMessage(gson.toJson(message));

            EventBus.getDefault().post(glassMessage);

        }
    }

    /**
     * Synchronized method to delete or add elements in the labels array via the adapter
     * To eliminate concurrency problems when different threads have access to the label array
     *
     * @param delete
     * @param label
     */

    synchronized private void changeLabelsAdapter(boolean delete, Label label){

        if (delete){
            scannedLabels.remove(label);
        } else {
            scannedLabels.add(label);
        }

    }


    /**
     * send an email with all the logcat Entries
     *
     *
     */

    public void sendLogcatMail() {

        // save logcat in file
        File outputFile = new File(Environment.getExternalStorageDirectory(),
                "logcat.txt");
        try {
            Runtime.getRuntime().exec(
                    "logcat -f" + outputFile.getAbsolutePath());
        } catch (IOException e) {
            e.printStackTrace();
        }

        String[] params = {"wispelberg@gmail.com", "krid3112", outputFile.getAbsolutePath()};
        new SendMail().execute(params);

    }


    /**
     * Inner Async classc for sending email
     *
     * @author Dirk
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

            String[] toArr = {"wispelberg@gmail.com", "dirk@evolar.be"};
            m.setTo(toArr);
            m.setFrom("wispelberg@gmail.com");
            m.setSubject("Logging from Logcat");
            String serialNumber = Build.SERIAL != Build.UNKNOWN ? Build.SERIAL : Settings.Secure.getString(getContentResolver(), Settings.Secure.ANDROID_ID);
            m.setBody("This email is send by PurolatorSmartSort for Glass device: " + serialNumber);

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
