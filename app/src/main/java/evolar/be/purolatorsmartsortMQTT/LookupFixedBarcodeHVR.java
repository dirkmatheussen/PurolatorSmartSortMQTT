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
 * Lookup High Volume Receiver, last lookup before doing a 2D Address Parsing
 *
 */
public class LookupFixedBarcodeHVR {

    private static final String TAG = "LookupFixedBarcodeHVR";
    private static final boolean D = true;

    private SQLiteDatabase database;


    public LookupFixedBarcodeHVR(){
        EventBus.getDefault().register(this);
        //open the PM database for readonly
        //database = SQLiteDatabase.openDatabase("PIM.db3",null,SQLiteDatabase.OPEN_READONLY);

    }



    @Subscribe(priority = 4)    //
    public void onFixedBarcodeScanEvent(FixedBarcodeScan event){

        if (D) Log.d(TAG,"In event");

        //if Routeplan found, cancel Delivery to other classes with lower priority
        if (HVRFound(Utilities.getPostalCode(event.getBarcodeResult()),event.getBarcodeResult(), event.getScanDate(),event.getDimensionData())) {
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
     * Find a HVR Record in Routeplan, based on PostalCode
     *
     *
     * @param barcode
     * @return
     */

    private boolean HVRFound(String postalCode, String barcode, Date scanDate, String dimensionData){

        String[] selectColumns = {"RouteNumber", "ShelfNumber","TruckShelfOverride","DeliverySequenceID","MunicipalityName","AddressRecordType",
                "StreetName","StreetType","CustomerName","StreetDirection","FromStreetNumber","ToStreetNumber"};

        SQLiteDatabase database = PurolatorSmartsortMQTT.getsInstance().getDatabase(PurolatorSmartsortMQTT.RPM_DBTYPE);


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

        Cursor RPMCursor = database.query("RoutePlan", selectColumns, "PostalCode=? AND AddressRecordType=?", new String[]{postalCode,"UniqueAddress"}, null, null, null, null);
//        Cursor RPMCursor = database.rawQuery("SELECT * FROM RoutePlan WHERE PostalCode=\"L9W4Z5\"",null);


        if (D) Log.d(TAG,"QueryReturn: " + RPMCursor.toString());

        UIUpdater uiUpdater = new UIUpdater();

        // get the found data and post en event.
        if (RPMCursor.getCount()== 1){
            RPMCursor.moveToFirst();

            if (D) Log.d(TAG,"Found in HVR!!!!");
            if (PurolatorSmartsortMQTT.getsInstance().getConfigData().getDeviceType().equals("FIXED")) {


                final FixedScanResult fixedScanResult = new FixedScanResult();
                fixedScanResult.setRouteNumber(RPMCursor.getString(RPMCursor.getColumnIndex("RouteNumber")));
                String shelfNumber = RPMCursor.getString(RPMCursor.getColumnIndex("TruckShelfOverride"));
                if (shelfNumber.isEmpty()) {
                    shelfNumber = RPMCursor.getString(RPMCursor.getColumnIndex("ShelfNumber"));
                }
                fixedScanResult.setShelfNumber(shelfNumber);
                fixedScanResult.setDeliverySequence(RPMCursor.getString(RPMCursor.getColumnIndex("DeliverySequenceID")));
                fixedScanResult.setPostalCode(postalCode);
                fixedScanResult.setScannedCode(barcode);

                String municipality = RPMCursor.getString(RPMCursor.getColumnIndex("MunicipalityName"));
                String address = RPMCursor.getString(RPMCursor.getColumnIndex("StreetName"));
                String streetNo = RPMCursor.getString(RPMCursor.getColumnIndex("FromStreetNumber")) + "-" + RPMCursor.getString(RPMCursor.getColumnIndex("ToStreetNumber"));

                fixedScanResult.setStreetname(address);
                String fromStreetNo = RPMCursor.getString(RPMCursor.getColumnIndex("FromStreetNumber"));
                String toStreetNo = RPMCursor.getString(RPMCursor.getColumnIndex("ToStreetNumber"));

                if (fromStreetNo.equals(toStreetNo)) fixedScanResult.setStreetnumber(RPMCursor.getString(RPMCursor.getColumnIndex("FromStreetNumber")));

                String barcodeType = Utilities.getBarcodeLogType(barcode);

                if (barcodeType.equals(("2D")) || barcodeType.equals("NGB")) {
                    fixedScanResult.setPinCode(Utilities.getPINCode(barcode));
                } else {
                    fixedScanResult.setPinCode(address + " " + streetNo + " " + postalCode + " " + municipality);
                }
                fixedScanResult.setScanDate(scanDate);
                fixedScanResult.setGlassTarget("WAVE");
                fixedScanResult.setDimensionData(dimensionData);

                EventBus.getDefault().post(fixedScanResult);

                if (!PurolatorSmartsortMQTT.getsInstance().getConfigData().isWaveValidation()) {
                    PurolatorSmartsortMQTT.getsInstance().sendToGlass(fixedScanResult);

                }


                uiUpdater.setRouteNumber(RPMCursor.getString(RPMCursor.getColumnIndex("RouteNumber")));
                uiUpdater.setShelfNumber(shelfNumber);
                uiUpdater.setDeliverySequence("");
                uiUpdater.setPrimarySort("");
                uiUpdater.setSideofBelt("");
                uiUpdater.setScannedCode(barcode);
                uiUpdater.setPinCode(address + " " + streetNo + " " + postalCode + " " + municipality);
                EventBus.getDefault().post(uiUpdater);


                Logger logger = new Logger();
                logger.setDevice_id(PurolatorSmartsortMQTT.getsInstance().getConfigData().getDeviceName());
                logger.setUserCode(PurolatorSmartsortMQTT.getsInstance().getConfigData().getUserId());
                logger.setTerminalID(PurolatorSmartsortMQTT.getsInstance().getConfigData().getTerminalID());
                logger.setScannedCode(barcode);
                logger.setPostalCode(postalCode);
                logger.setShelfOverride(RPMCursor.getString(RPMCursor.getColumnIndex("TruckShelfOverride")));
                logger.setRouteNumber(RPMCursor.getString(RPMCursor.getColumnIndex("RouteNumber")));
                logger.setShelfNumber(RPMCursor.getString(RPMCursor.getColumnIndex("ShelfNumber")));
                logger.setDeliverySequence(RPMCursor.getString(RPMCursor.getColumnIndex("DeliverySequenceID")));
                logger.setRoutePlanVersionId(RPMCursor.getString(RPMCursor.getColumnIndex("RoutePlanVersionID")));

                logger.setStreetName(RPMCursor.getString(RPMCursor.getColumnIndex("StreetName")));
                logger.setStreetType(RPMCursor.getString(RPMCursor.getColumnIndex("StreetType")));
                logger.setStreetDirection(RPMCursor.getString(RPMCursor.getColumnIndex("StreetDirection")));
                logger.setCustomerName(RPMCursor.getString(RPMCursor.getColumnIndex("CustomerName")));
                logger.setBarcodeType("HVR");
                EventBus.getDefault().post(logger);
            } else if (PurolatorSmartsortMQTT.getsInstance().getConfigData().getDeviceType().equals("GLASS")) {

                uiUpdater.setUpdateType(PurolatorSmartsortMQTT.UPD_BOTTOMSCREEN);
                uiUpdater.setRouteNumber(RPMCursor.getString(RPMCursor.getColumnIndex("RouteNumber")));
                String shelfNumber = RPMCursor.getString(RPMCursor.getColumnIndex("TruckShelfOverride"));
                if (shelfNumber.isEmpty()) {
                    shelfNumber = RPMCursor.getString(RPMCursor.getColumnIndex("ShelfNumber"));
                }

                uiUpdater.setShelfNumber(shelfNumber);

                uiUpdater.setDeliverySequence(RPMCursor.getString(RPMCursor.getColumnIndex("DeliverySequenceID")));
                uiUpdater.setPrimarySort("");
                uiUpdater.setSideofBelt("");
                uiUpdater.setScannedCode(barcode);
                String municipality = RPMCursor.getString(RPMCursor.getColumnIndex("MunicipalityName"));
                String address = RPMCursor.getString(RPMCursor.getColumnIndex("StreetName"));
                String streetNo = RPMCursor.getString(RPMCursor.getColumnIndex("FromStreetNumber")) + "-" + RPMCursor.getString(RPMCursor.getColumnIndex("ToStreetNumber"));
                uiUpdater.setPinCode(address + " " + streetNo + " " + postalCode + " " + municipality);
                uiUpdater.setCorrectBay(false);

                EventBus.getDefault().post(uiUpdater);


            }

            RPMCursor.close();
            PurolatorSmartsortMQTT.getsInstance().setTransactionActive(PurolatorSmartsortMQTT.RPM_DBTYPE,false);

            return true;

        }

        RPMCursor.close();
        PurolatorSmartsortMQTT.getsInstance().setTransactionActive(PurolatorSmartsortMQTT.RPM_DBTYPE,false);

        return false;


    }
}
