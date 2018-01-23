package evolar.be.purolatorsmartsortMQTT;

import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.util.Log;

import org.greenrobot.eventbus.EventBus;
import org.greenrobot.eventbus.Subscribe;

import java.util.Date;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

import evolar.be.purolatorsmartsortMQTT.events.FixedBarcodeScan;
import evolar.be.purolatorsmartsortMQTT.events.FixedScanResult;
import evolar.be.purolatorsmartsortMQTT.events.Logger;
import evolar.be.purolatorsmartsortMQTT.events.UIUpdater;


/**
 * Created by Dirk on 17/03/16.
 * If Lookup for PIN failed and Lookup for PrePrint failed, do a lookup for CrossDock or Misdirect
 *
 */
public class LookupFixedBarcodeCROSS {

    private static final String TAG = "LookupFixedBarcodeCROSS";
    private static final boolean D = true;

    private SQLiteDatabase database;


    public LookupFixedBarcodeCROSS(){
        EventBus.getDefault().register(this);

    }



    @Subscribe(priority = 6)    //
    public void onFixedBarcodeScanEvent(FixedBarcodeScan event){

        if (D) Log.d(TAG,"In event");

        //if found in PostalRoute database, cancel CROSS check and continue the search logic.
        if (inPSTFound(Utilities.getPostalCode(event.getBarcodeResult()))){
            return;
        }
        //if Routeplan found, cancel Delivery to other classes with lower priority
        if (CrossFound(Utilities.getPostalCode(event.getBarcodeResult()),event.getBarcodeResult(),event.getDimensionData())) {
            EventBus.getDefault().cancelEventDelivery(event);
            //scan is valid, add in the list
            //scan is valid, now check if this is the package, scanned to put into a shelf
            if (PurolatorSmartsortMQTT.getsInstance().getPackageInShelf() !=null
                    && PurolatorSmartsortMQTT.getsInstance().getPackageInShelf().getScannedCode().equals(event.getBarcodeResult())){

                UIUpdater uiUpdater = new UIUpdater();
                uiUpdater.setUpdateType(PurolatorSmartsortMQTT.UPD_ERROR);
                uiUpdater.setErrorMessage("Scan the route/shelf code");
                EventBus.getDefault().post(uiUpdater);

            } else{
                //scan is valid, add in the list
//                PurolatorSmartsortMQTT.packagesList.getBarcodeScans().add(event.getBarcodeResult());
            }

        }
    }


    /**
     * Check if found in postalCode database, if this is the case skip Cross Deck & continue to search logic
     *
     *
     * @param postalCode
     * @return
     */
    private boolean inPSTFound(String postalCode){

        String[] selectColumns = {"PostalCode","Route"};


        SQLiteDatabase database = PurolatorSmartsortMQTT.getsInstance().getDatabase(PurolatorSmartsortMQTT.PST_DBTYPE);



        Log.d(TAG,"Query parameters: " + postalCode);

        if (database == null){
            return false;
        }

        if (postalCode == null){
            return false;
        }

        //check if database is being moved
        while (PurolatorSmartsortMQTT.getsInstance().movePSTDatabase){
            try{
                Thread.sleep(10);       //wait 10 milliseconds
            } catch (InterruptedException ex){

            }
        }
        PurolatorSmartsortMQTT.getsInstance().setTransactionActive(PurolatorSmartsortMQTT.PST_DBTYPE,true);

        // check for misdirect
        Cursor PSTCursor = database.query("PostalRoute", selectColumns, "PostalCode=?", new String[]{postalCode}, null, null, null, null);


        UIUpdater uiUpdater = new UIUpdater();


        if (D) Log.d(TAG,"QueryReturn for PostalCode in CROSS: " + PSTCursor.toString());
        // get the found data and post en event.
        if (PSTCursor.moveToFirst()) {
            PSTCursor.close();
            PurolatorSmartsortMQTT.getsInstance().setTransactionActive(PurolatorSmartsortMQTT.PST_DBTYPE,false);

            return true;

        }

        PSTCursor.close();
        PurolatorSmartsortMQTT.getsInstance().setTransactionActive(PurolatorSmartsortMQTT.PST_DBTYPE,false);
        return false;

    }


    /**
     * Find a Route bases on the PostalCode
     *
     * @param postalCode
     * @param barcode
     * @return
     */

    private boolean CrossFound(String postalCode, String barcode, String dimensionData) {

        String[] selectColumns = {"AddressRecordType","RouteNumber","ShelfNumber","TruckShelfOverride"};


        SQLiteDatabase database = PurolatorSmartsortMQTT.getsInstance().getDatabase(PurolatorSmartsortMQTT.RPM_DBTYPE);



        Log.d(TAG,"Query parameters: " + postalCode);

        if (database == null){
            return false;
        }

        if (postalCode == null){
            return false;
        }

        if (PurolatorSmartsortMQTT.getsInstance().getPackageInShelf() !=null
                && PurolatorSmartsortMQTT.getsInstance().getPackageInShelf().getScannedCode().equals(barcode)) {
            return true;        //PIN is found, but not in the database
        }

        //check if database is being moved
        while (PurolatorSmartsortMQTT.getsInstance().moveRPMDatabase){
            try{
                Thread.sleep(10);       //wait 10 milliseconds
            } catch (InterruptedException ex){

            }
        }
        PurolatorSmartsortMQTT.getsInstance().setTransactionActive(PurolatorSmartsortMQTT.RPM_DBTYPE,true);

        // check for misdirect
        Cursor RPMCursor = database.query("RoutePlan", selectColumns, "PostalCode=?",
                new String[]{postalCode}, null, null, null, null);


        UIUpdater uiUpdater = new UIUpdater();

        // get the found data and post en event.
        if (!RPMCursor.moveToFirst()) {

            //first check if route is assigned to the preloader
//            if (PurolatorSmartsortMQTT.getsInstance().isRouteValid(RPMCursor.getString(RPMCursor.getColumnIndex("RouteNumber")))) {
            if (D) Log.d(TAG,"Not found in RoutePlan, Misdirect");
            if (PurolatorSmartsortMQTT.getsInstance().getConfigData().getDeviceType().equals("FIXED")) {


                String pinCode = Utilities.getPINCode(barcode);
                if (pinCode == null) {
                    pinCode = barcode;
                }

                //Let go instruction
                final FixedScanResult fixedScanResult = new FixedScanResult();
                fixedScanResult.setRouteNumber("REM");      //remediation
                fixedScanResult.setShelfNumber("00");
                fixedScanResult.setPinCode(pinCode + " MISDIRECT");
                fixedScanResult.setSideofBelt("X");
                fixedScanResult.setScannedCode(barcode);
                fixedScanResult.setScanDate(new Date());
                fixedScanResult.setGlassTarget("WAVE");
                fixedScanResult.setDimensionData(dimensionData);

                EventBus.getDefault().post(fixedScanResult);

                if (!PurolatorSmartsortMQTT.getsInstance().getConfigData().isWaveValidation()) {
                    PurolatorSmartsortMQTT.getsInstance().sendToGlass(fixedScanResult);

                }


                uiUpdater.setUpdateType(PurolatorSmartsortMQTT.UPD_BOTTOMSCREEN);
                uiUpdater.setPinCode("Package " + pinCode + " MISDIRECT");
                uiUpdater.setScannedCode(barcode);
                uiUpdater.setPostalCode(Utilities.getPostalCode(barcode));
                uiUpdater.setRemediationId(pinCode);

                EventBus.getDefault().post(uiUpdater);
                Logger logger = new Logger();
                logger.setDevice_id(PurolatorSmartsortMQTT.getsInstance().getConfigData().getDeviceName());
                logger.setUserCode(PurolatorSmartsortMQTT.getsInstance().getConfigData().getUserId());
                logger.setTerminalID(PurolatorSmartsortMQTT.getsInstance().getConfigData().getTerminalID());
                logger.setScannedCode(barcode);
                logger.setPinCode(pinCode);
                logger.setPostalCode(Utilities.getPostalCode(barcode));
                logger.setBarcodeType("MISDIRECT");

                EventBus.getDefault().post(logger);

            } else  if (PurolatorSmartsortMQTT.getsInstance().getConfigData().getDeviceType().equals("GLASS")) {
                //TODO HANDLE MISDIRECT

            }

            RPMCursor.close();
            PurolatorSmartsortMQTT.getsInstance().setTransactionActive(PurolatorSmartsortMQTT.RPM_DBTYPE,false);

            return true;
        }


        if (D) Log.d(TAG,"QueryReturn for CrossDock: " + RPMCursor.toString());
        // check for crossdock
        if (RPMCursor !=null && !RPMCursor.isClosed()) RPMCursor.close();

        RPMCursor = database.query("RoutePlan", selectColumns, "PostalCode=? AND AddressRecordType=?",
                new String[]{postalCode,"CrossDock"}, null, null, null, null);


        uiUpdater = new UIUpdater();

        // get the found data and post en event.
        if (RPMCursor.moveToFirst()) {
            //first check if route is assigned to the preloader
//            if (PurolatorSmartsortMQTT.getsInstance().isRouteValid(RPMCursor.getString(RPMCursor.getColumnIndex("RouteNumber")))) {
            if (D) Log.d(TAG,"Found in CrossDock, 1D Barcode!!!!");
            if (PurolatorSmartsortMQTT.getsInstance().getConfigData().getDeviceType().equals("FIXED")) {


                String pinCode = Utilities.getPINCode(barcode);
                if (pinCode == null) {
                    pinCode = barcode;
                }
                uiUpdater.setUpdateType(PurolatorSmartsortMQTT.UPD_BOTTOMSCREEN);
                uiUpdater.setPinCode("Package " + pinCode + " in Cross Dock");
                uiUpdater.setScannedCode(barcode);
                uiUpdater.setPostalCode(Utilities.getPostalCode(barcode));

                if (D) EventBus.getDefault().post(uiUpdater);
            } else if (PurolatorSmartsortMQTT.getsInstance().getConfigData().getDeviceType().equals("GLASS")){

                //TODO HANDLE CROSSDOCK for Wearable
            }

            RPMCursor.close();
            PurolatorSmartsortMQTT.getsInstance().setTransactionActive(PurolatorSmartsortMQTT.RPM_DBTYPE,false);
            return true;
        }

//        }

        RPMCursor.close();
        PurolatorSmartsortMQTT.getsInstance().setTransactionActive(PurolatorSmartsortMQTT.RPM_DBTYPE,false);
        return false;


    }

}
