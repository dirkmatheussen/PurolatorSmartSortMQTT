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
 * Do a lookup in the PREPRINT File, this is called when PIN is not found in the PINMASTER file
 * Only for Legacy Barcodes
 */
public class LookupFixedBarcodePRE {

    private static final String TAG = "LookupFixedBarcodePRE";
    private static final boolean D = true;



    public LookupFixedBarcodePRE(){
        EventBus.getDefault().register(this);
    }

    @Subscribe(priority = 7)    //4rd highest priority
    public void onFixedBarcodeScanEvent(FixedBarcodeScan event){

        if (D) Log.d(TAG,"In event");
        //first check if correct barcode type is scanned

        if(event.getBarcodeType().equals(PurolatorSmartsortMQTT.RPM_DBTYPE)){
            return;
        }

        //if PIN found, cancel Delivery to other classes with lower priority



        if (pinFound(getPINCode(event.getBarcodeResult()),event.getBarcodeResult(),event.getScanDate(),event.getDimensionData())) {
            EventBus.getDefault().cancelEventDelivery(event);
            //scan is valid, now check if this is the package, scanned to put into a shelf
            if (PurolatorSmartsortMQTT.getsInstance().getPackageInShelf() !=null
                    && PurolatorSmartsortMQTT.getsInstance().getPackageInShelf().getScannedCode().equals(event.getBarcodeResult())){

                UIUpdater uiUpdater = new UIUpdater();
                uiUpdater.setUpdateType(PurolatorSmartsortMQTT.UPD_ERROR);
                uiUpdater.setErrorMessage("Scan the route/shelf code");
                EventBus.getDefault().post(uiUpdater);

            } else{
                //scan is valid, add in the list
 //               PurolatorSmartsortMQTT.packagesList.getBarcodeScans().add(event.getBarcodeResult());
            }
        }
    }

    /**
     * Check get PIN Code for a PREPRINT Scan
     *
     * @param barcode
     * @return
     */

    private String getPINCode(String barcode){

        return Utilities.getPINCode(barcode);

    }



    /**
     * Find the scanned barcode in the PREPRINTFile
     *
     * @param pincode : the pincode
     * @param barcode : the original scanned barcode
     * @return
     */
    private boolean pinFound(String pincode, String barcode, Date scanDate, String dimensionData) {

        String[] selectColumns = {"PrePrintID","RouteNumber", "ShelfNumber", "PrimarySort", "SideofBelt","FromPIN","ToPIN"};

        if (pincode == null){
            return false;
        }

        SQLiteDatabase database = PurolatorSmartsortMQTT.getsInstance().getDatabase(PurolatorSmartsortMQTT.PRE_DBTYPE);


        if (database == null){
            return false;
        }


        if (PurolatorSmartsortMQTT.getsInstance().getPackageInShelf() !=null
                && PurolatorSmartsortMQTT.getsInstance().getPackageInShelf().getScannedCode().equals(barcode)) {
            return true;        //PIN is found, but not in the database
        }

        //check if database is being moved
        while (PurolatorSmartsortMQTT.getsInstance().movePREDatabase){
            try{
                Thread.sleep(10);       //wait 10 milliseconds
            } catch (InterruptedException ex){

            }
        }

        PurolatorSmartsortMQTT.getsInstance().setTransactionActive(PurolatorSmartsortMQTT.PRE_DBTYPE,true);

        Cursor PRECursor = database.query("PrePrintPIN", selectColumns, "FromPIN<=? AND ToPIN>=?", new String[]{pincode,pincode}, null, null, null, null);


        if (D) Log.d(TAG,"Parameter: PrePrintPIN = " + pincode);

        UIUpdater uiUpdater = new UIUpdater();

        // get the found data and post en event.
        if (PRECursor.moveToFirst() && PRECursor.getCount()==1) {           //only a unique match is allowed

            //first check if route is assigned to the preloader
//            if (PurolatorSmartsortMQTT.getsInstance().isRouteValid(PRECursor.getString(PRECursor.getColumnIndex("RouteNumber")))) {
            if (D) Log.d(TAG,"Found in PRE!!!!");
            if (PurolatorSmartsortMQTT.getsInstance().getConfigData().getDeviceType().equals("FIXED")) {


                final FixedScanResult fixedScanResult = new FixedScanResult();
                fixedScanResult.setRouteNumber(PRECursor.getString(PRECursor.getColumnIndex("RouteNumber")));
                fixedScanResult.setShelfNumber(PRECursor.getString(PRECursor.getColumnIndex("ShelfNumber")));
                fixedScanResult.setPrimarySort(PRECursor.getString(PRECursor.getColumnIndex("PrimarySort")));
                fixedScanResult.setSideofBelt(PRECursor.getString(PRECursor.getColumnIndex("SideofBelt")));
                fixedScanResult.setScannedCode(barcode);
                fixedScanResult.setPinCode(pincode);
                fixedScanResult.setScannedCode(barcode);
                fixedScanResult.setScanDate(scanDate);
                fixedScanResult.setGlassTarget("WAVE");
                fixedScanResult.setDimensionData(dimensionData);

                EventBus.getDefault().post(fixedScanResult);

                if (!PurolatorSmartsortMQTT.getsInstance().getConfigData().isWaveValidation()) {
                    PurolatorSmartsortMQTT.getsInstance().sendToGlass(fixedScanResult);

                }


                uiUpdater.setRouteNumber(PRECursor.getString(PRECursor.getColumnIndex("RouteNumber")));
                uiUpdater.setShelfNumber(PRECursor.getString(PRECursor.getColumnIndex("ShelfNumber")));
                uiUpdater.setPrimarySort(PRECursor.getString(PRECursor.getColumnIndex("PrimarySort")));
                uiUpdater.setSideofBelt(PRECursor.getString(PRECursor.getColumnIndex("SideofBelt")));
                uiUpdater.setPinCode(pincode);
                uiUpdater.setScannedCode(barcode);
                EventBus.getDefault().post(uiUpdater);

                Logger logger = new Logger();
                logger.setDevice_id(PurolatorSmartsortMQTT.getsInstance().getConfigData().getDeviceName());
                logger.setUserCode(PurolatorSmartsortMQTT.getsInstance().getConfigData().getUserId());
                logger.setTerminalID(PurolatorSmartsortMQTT.getsInstance().getConfigData().getTerminalID());
                logger.setScannedCode(barcode);
                logger.setBarcodeType("PREPRINT");
                logger.setPinCode(pincode);
                logger.setPostalCode(Utilities.getPostalCode(barcode));

                logger.setPrimarySort(PRECursor.getString(PRECursor.getColumnIndex("PrimarySort")));
                logger.setSideofBelt(PRECursor.getString(PRECursor.getColumnIndex("SideofBelt")));
                logger.setRouteNumber(PRECursor.getString(PRECursor.getColumnIndex("RouteNumber")));
                logger.setShelfNumber(PRECursor.getString(PRECursor.getColumnIndex("ShelfNumber")));
                logger.setPrePrintID(PRECursor.getString(PRECursor.getColumnIndex("PrePrintID")));


                EventBus.getDefault().post(logger);


            } else  if (PurolatorSmartsortMQTT.getsInstance().getConfigData().getDeviceType().equals("GLASS")){

                uiUpdater.setUpdateType(PurolatorSmartsortMQTT.UPD_BOTTOMSCREEN);
                uiUpdater.setRouteNumber(PRECursor.getString(PRECursor.getColumnIndex("RouteNumber")));
                uiUpdater.setShelfNumber(PRECursor.getString(PRECursor.getColumnIndex("ShelfNumber")));
                uiUpdater.setPrimarySort(PRECursor.getString(PRECursor.getColumnIndex("PrimarySort")));
                uiUpdater.setSideofBelt(PRECursor.getString(PRECursor.getColumnIndex("SideofBelt")));
                uiUpdater.setPinCode(pincode + " " + (Utilities.getPostalCode(barcode) == null? " ":Utilities.getPostalCode(barcode)));
                uiUpdater.setScannedCode(barcode);
                uiUpdater.setPostalCode(Utilities.getPostalCode(barcode));
                uiUpdater.setCorrectBay(false);

                EventBus.getDefault().post(uiUpdater);


            }

                PRECursor.close();
                PurolatorSmartsortMQTT.getsInstance().setTransactionActive(PurolatorSmartsortMQTT.PRE_DBTYPE,false);
                return true;

//            }

        }


        PRECursor.close();
        PurolatorSmartsortMQTT.getsInstance().setTransactionActive(PurolatorSmartsortMQTT.PRE_DBTYPE,false);

        return false;

    }


}
