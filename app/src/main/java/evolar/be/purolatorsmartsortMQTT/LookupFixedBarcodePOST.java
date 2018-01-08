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
 * If Lookup for PIN failed and Lookup for PrePrint failed, do a lookup in the local postal Database if there is no Postal Code in RPM file
 *
 */
public class LookupFixedBarcodePOST {

    private static final String TAG = "LookupFixedBarcodePOST";
    private static final boolean D = true;

    private SQLiteDatabase database;


    public LookupFixedBarcodePOST(){
        EventBus.getDefault().register(this);

    }



    @Subscribe(priority = 3)    //
    public void onFixedBarcodeScanEvent(FixedBarcodeScan event){

        if (D) Log.d(TAG,"In event");

        //if Routeplan found, cancel Delivery to other classes with lower priority
        if (POSTFound(Utilities.getPostalCode(event.getBarcodeResult()),event.getBarcodeResult(),event.getScanDate(),event.getDimensionData())) {
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



    /**
     * Find a Route bases on the PostalCode
     *
     * @param postalCode
     * @param barcode
     * @return
     * 
     */

    private boolean POSTFound(String postalCode, String barcode,Date scanDate, String dimensionData) {

        String[] selectColumns = {"PostalCode","Route"};


        SQLiteDatabase database = PurolatorSmartsortMQTT.getsInstance().getDatabase(PurolatorSmartsortMQTT.PST_DBTYPE);



        if (D) Log.d(TAG,"Query parameters: " + postalCode + " Barcode: " + barcode);

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
        while (PurolatorSmartsortMQTT.getsInstance().movePSTDatabase){
            try{
                Thread.sleep(10);       //wait 10 milliseconds
            } catch (InterruptedException ex){

            }
        }
        PurolatorSmartsortMQTT.getsInstance().setTransactionActive(PurolatorSmartsortMQTT.PST_DBTYPE,true);

        // check for Postalcode
        Cursor PSTCursor = database.query("PostalRoute", selectColumns, "PostalCode=?", new String[]{postalCode}, null, null, null, null);


        UIUpdater uiUpdater = new UIUpdater();


        if (D) Log.d(TAG,"QueryReturn for PostalCode: " + PSTCursor.toString());
        // get the found data and post en event.
        if (PSTCursor.moveToFirst()) {

            //first check if route is assigned to the preloader
            //           if (PurolatorSmartsortMQTT.getsInstance().isRouteValid(PINCursor.getString(PINCursor.getColumnIndex("RouteNumber")))) {
            if (PurolatorSmartsortMQTT.getsInstance().getConfigData().getDeviceType().equals("FIXED")){

                if (D) Log.d(TAG,"Found in PST!!!!");
                final FixedScanResult fixedScanResult = new FixedScanResult();

                fixedScanResult.setRouteNumber(PSTCursor.getString(PSTCursor.getColumnIndex("Route")));
                fixedScanResult.setShelfNumber("");
                fixedScanResult.setDeliverySequence("");
                fixedScanResult.setPrimarySort("");
                fixedScanResult.setSideofBelt("");
                fixedScanResult.setPostalCode(Utilities.getPostalCode(barcode));
                String readablePinCode = Utilities.getPINCode(barcode);
                if (readablePinCode == null) readablePinCode = barcode;
                if (readablePinCode.length()>3) readablePinCode = readablePinCode.substring(readablePinCode.length()-4);
                fixedScanResult.setPinCode(readablePinCode+ "  " + (fixedScanResult.getPostalCode() == null? " ":fixedScanResult.getPostalCode()));
                fixedScanResult.setScannedCode(barcode);
                fixedScanResult.setScanDate(scanDate);
                fixedScanResult.setGlassTarget("WAVE");
                fixedScanResult.setDimensionData(dimensionData);

                EventBus.getDefault().post(fixedScanResult);

                if (!PurolatorSmartsortMQTT.getsInstance().getConfigData().isWaveValidation()) {
                    PurolatorSmartsortMQTT.getsInstance().sendToGlass(fixedScanResult);

                }


                uiUpdater.setRouteNumber(PSTCursor.getString(PSTCursor.getColumnIndex("Route")));
                uiUpdater.setShelfNumber("");
                uiUpdater.setDeliverySequence("");
                uiUpdater.setPrimarySort("");
                uiUpdater.setSideofBelt("");
                uiUpdater.setPinCode(readablePinCode+ "  " + (fixedScanResult.getPostalCode() == null? " ":fixedScanResult.getPostalCode()));
                uiUpdater.setScannedCode(barcode);
                EventBus.getDefault().post(uiUpdater);

                Logger logger = new Logger();
                logger.setDevice_id(PurolatorSmartsortMQTT.getsInstance().getConfigData().getDeviceName());
                logger.setUserCode(PurolatorSmartsortMQTT.getsInstance().getConfigData().getUserId());
                logger.setTerminalID(PurolatorSmartsortMQTT.getsInstance().getConfigData().getTerminalID());
                logger.setScannedCode(barcode);
                logger.setBarcodeType("NEWROUTE");
                logger.setPinCode(readablePinCode+ "  " + (fixedScanResult.getPostalCode() == null? " ":fixedScanResult.getPostalCode()));
                logger.setPostalCode(Utilities.getPostalCode(barcode));

                logger.setPrimarySort("");
                logger.setSideofBelt("");
                logger.setRouteNumber(PSTCursor.getString(PSTCursor.getColumnIndex("Route")));
                logger.setShelfNumber("");
                logger.setShelfOverride("");
                logger.setDeliverySequence("");



                EventBus.getDefault().post(logger);
            } else if (PurolatorSmartsortMQTT.getsInstance().getConfigData().getDeviceType().equals("GLASS")){
                uiUpdater.setUpdateType(PurolatorSmartsortMQTT.UPD_BOTTOMSCREEN);
                uiUpdater.setRouteNumber(PSTCursor.getString(PSTCursor.getColumnIndex("Route")));
                uiUpdater.setShelfNumber("");
                uiUpdater.setDeliverySequence("");
                uiUpdater.setPrimarySort("");
                uiUpdater.setSideofBelt("");
                String readablePinCode = Utilities.getPINCode(barcode);
                if (readablePinCode == null) readablePinCode = barcode;
                if (readablePinCode.length()>3) readablePinCode = readablePinCode.substring(readablePinCode.length()-4);
                uiUpdater.setPinCode(readablePinCode+ "  " + (Utilities.getPostalCode(barcode) == null? " ":Utilities.getPostalCode(barcode)));
                uiUpdater.setScannedCode(barcode);
                uiUpdater.setCorrectBay(false);
                EventBus.getDefault().post(uiUpdater);

            }
            PSTCursor.close();
            PurolatorSmartsortMQTT.getsInstance().setTransactionActive(PurolatorSmartsortMQTT.PST_DBTYPE,false);

            return true;

        }

        PSTCursor.close();
        PurolatorSmartsortMQTT.getsInstance().setTransactionActive(PurolatorSmartsortMQTT.PST_DBTYPE,false);
        return false;





    }

}
