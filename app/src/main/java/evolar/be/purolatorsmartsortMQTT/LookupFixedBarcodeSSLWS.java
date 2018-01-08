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
import evolar.be.purolatorsmartsortMQTT.events.UIUpdater;

/**
 * Created by Dirk on 17/03/16.
 * Do a lookup in the with the SSLWS Webservice
 *
 */
public class LookupFixedBarcodeSSLWS {

    private static final String TAG = "LookupFixedBarcodeSSLWS";
    private static final boolean D = true;



    public LookupFixedBarcodeSSLWS(){
        EventBus.getDefault().register(this);
    }

    @Subscribe(priority = 1)    //second lowest priority
    public void onFixedBarcodeScanEvent(FixedBarcodeScan event){

        if (D) Log.d(TAG,"In event");
        //first check if correct barcode type is scanned

        if(event.getBarcodeType().equals(PurolatorSmartsortMQTT.RPM_DBTYPE)){
            return;
        }

        //if PIN found, cancel Delivery to other classes with lower priority


        if (pinFound(Utilities.getPINCode(event.getBarcodeResult()),event.getBarcodeResult(),event.getScanDate(), event.getDimensionData())) {
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
     * Find the scanned barcode in the Webservice
     *
     * @param pincode : the pincode
     * @param barcode : the original scanned barcode
     * @return
     */
    private boolean pinFound(String pincode, String barcode, Date scanDate, String dimensionData) {

        String[] selectColumns = {"RouteNumber", "ShelfNumber", "DeliverySequenceID", "PrimarySort", "SideofBelt","PIN"};

        if (pincode == null){
            return false;
        }

        SQLiteDatabase database = PurolatorSmartsortMQTT.getsInstance().getDatabase(PurolatorSmartsortMQTT.PIN_DBTYPE);


        if (database == null){
            return false;
        }

        if (PurolatorSmartsortMQTT.getsInstance().getPackageInShelf() !=null
                && PurolatorSmartsortMQTT.getsInstance().getPackageInShelf().getScannedCode().equals(barcode)) {
            return true;        //PIN is found, but not in the database
        }

        //check if database is being moved
        while (PurolatorSmartsortMQTT.getsInstance().movePINDatabase){
            try{
                Thread.sleep(10);       //wait 10 milliseconds
            } catch (InterruptedException ex){

            }
        }

        //start the transaction
        PurolatorSmartsortMQTT.getsInstance().setTransactionActive(PurolatorSmartsortMQTT.PIN_DBTYPE,true);

        Cursor PINCursor = database.query("PIN", selectColumns, "PIN=?", new String[]{pincode}, null, null, null, null);


        if (D) Log.d(TAG,"QueryReturn: " + PINCursor.toString());

        UIUpdater uiUpdater = new UIUpdater();

        // get the found data and post en event.
        if (PINCursor.moveToFirst()) {

            //first check if route is assigned to the preloader
            if (PurolatorSmartsortMQTT.getsInstance().isRouteValid(PINCursor.getString(PINCursor.getColumnIndex("RouteNumber")))) {

                if (D) Log.d(TAG,"Found in SSLWS!!!!");
                if (PurolatorSmartsortMQTT.getsInstance().getConfigData().getDeviceType().equals("FIXED")) {

                    //Let go instruction
                    final FixedScanResult fixedScanResult = new FixedScanResult();
                    fixedScanResult.setRouteNumber("REM");          //remediation
                    fixedScanResult.setShelfNumber("00");
                    fixedScanResult.setSideofBelt("X");
                    fixedScanResult.setScannedCode(barcode);
                    fixedScanResult.setScanDate(new Date());
                    fixedScanResult.setGlassTarget("WAVE");
                    fixedScanResult.setDimensionData(dimensionData);

                    EventBus.getDefault().post(fixedScanResult);

                    if (!PurolatorSmartsortMQTT.getsInstance().getConfigData().isWaveValidation()) {
                        PurolatorSmartsortMQTT.getsInstance().sendToGlass(fixedScanResult);

                    }


                    uiUpdater.setRouteNumber(PINCursor.getString(PINCursor.getColumnIndex("RouteNumber")));
                    uiUpdater.setShelfNumber(PINCursor.getString(PINCursor.getColumnIndex("ShelfNumber")));
                    uiUpdater.setDeliverySequence(PINCursor.getString(PINCursor.getColumnIndex("DeliverySequenceID")));
                    uiUpdater.setPrimarySort(PINCursor.getString(PINCursor.getColumnIndex("PrimarySort")));
                    uiUpdater.setSideofBelt(PINCursor.getString(PINCursor.getColumnIndex("SideofBelt")));
                    uiUpdater.setPinCode(PINCursor.getString(PINCursor.getColumnIndex("PIN")));
                    uiUpdater.setScannedCode(barcode);
                    uiUpdater.setRemediationId(pincode);
                    EventBus.getDefault().post(uiUpdater);

                } else if (PurolatorSmartsortMQTT.getsInstance().getConfigData().getDeviceType().equals("GLASS")) {

                }
                PINCursor.close();
                PurolatorSmartsortMQTT.getsInstance().setTransactionActive(PurolatorSmartsortMQTT.PIN_DBTYPE,false);

                return true;

            }

        }


        PINCursor.close();
        PurolatorSmartsortMQTT.getsInstance().setTransactionActive(PurolatorSmartsortMQTT.PIN_DBTYPE,false);

        return false;

    }


}
