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
 * Do a Terminal, CrossDock & HFPU Check, this is called when PIN is not found in the PINMASTER file & PREPRINT FILE
 *
 * Only for NGN/Puro2D/Maxicode barcodes
 */
public class LookupFixedBarcodeHFPU {

    private static final String TAG = "LookupFixedBarcodeHFPU";
    private static final boolean D = true;



    public LookupFixedBarcodeHFPU(){
        EventBus.getDefault().register(this);
    }

    @Subscribe(priority = 5)
    public void onFixedBarcodeScanEvent(FixedBarcodeScan event){

        if (D) Log.d(TAG,"In event");
        //first check if correct barcode type is scanned

        if(event.getBarcodeType().equals(PurolatorSmartsortMQTT.RPM_DBTYPE)){
            return;
        }



        //if PIN found, cancel Delivery to other classes with lower priority

        String postalCode = Utilities.getPostalCode(event.getBarcodeResult());

        if (pinFound(postalCode,event.getBarcodeResult(),event.getScanDate(),event.getDimensionData())) {
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
//                PurolatorSmartsortMQTT.packagesList.getBarcodeScans().add(event.getBarcodeResult());
            }
        }
    }

    /**
     * Find the Hold For Pick Up status in NGB/Puro2D code
     *
      * @param barcode
     * @return boolean
     */
    private boolean isHFPUCode(String barcode) {

        boolean HFPUCode = false;


        if (barcode.length() == 34 && barcode.substring(9, 10).equals("9")) {
            if (barcode.substring(28,29).equals("2")) HFPUCode = true;


        } else {
            String[] parsed = barcode.split("[|]");

            for (String aParsed : parsed) {
                Log.d(TAG, "Parsed: " + aParsed);
                if (aParsed.startsWith("S06")) {
                    if (aParsed.substring(4).equals("2")) HFPUCode = true;
                    break;
                }

            }
        }

        return HFPUCode;
    }
    /**
     * Find the scanned barcode in the HFPU Files
     *
     * @param postalCode : the postalCode
     * @param barcode : the original scanned barcode
     * @return
     */
    private boolean pinFound(String postalCode, String barcode, Date scanDate, String dimensionData) {

        Log.d(TAG,"Query parameters: postcode: " + postalCode);

        if (postalCode == null){
            return false;
        }

        if (!isHFPUCode(barcode)){
            //now do a query in RPM, only with PostalCode and with AddressRecordType = HFPU
            return RPM1DFound(barcode,scanDate,dimensionData);
        }


        SQLiteDatabase database = PurolatorSmartsortMQTT.getsInstance().getDatabase(PurolatorSmartsortMQTT.HFPUPC_DBTYPE);
        if (database == null){
            return false;
        }

        //check if database is being moved
        while (PurolatorSmartsortMQTT.getsInstance().moveHFPUPCDatabase){
            try{
                Thread.sleep(10);       //wait 10 milliseconds
            } catch (InterruptedException ex){

            }
        }

        PurolatorSmartsortMQTT.getsInstance().setTransactionActive(PurolatorSmartsortMQTT.HFPUPC_DBTYPE,true);

        String[] selectHFPUColumns = {"HFPULocationID", "PostalCode"};
        Cursor HFPUCursor = database.query("HFPULocationToPC", selectHFPUColumns, "PostalCode=?",
                new String[]{postalCode}, null, null, null, null);

        UIUpdater uiUpdater = new UIUpdater();

        if (HFPUCursor.moveToFirst()){
            String HFPULocationID  = HFPUCursor.getString(HFPUCursor.getColumnIndex("HFPULocationID"));

            HFPUCursor.close();
            PurolatorSmartsortMQTT.getsInstance().setTransactionActive(PurolatorSmartsortMQTT.HFPUPC_DBTYPE,false);

            database = PurolatorSmartsortMQTT.getsInstance().getDatabase(PurolatorSmartsortMQTT.HFPUMASTER_DBTYPE);
            //check if database is being moved
            while (PurolatorSmartsortMQTT.getsInstance().moveHFPUMASTERDatabase){
                try{
                    Thread.sleep(10);       //wait 10 milliseconds
                } catch (InterruptedException ex){

                }
            }

            PurolatorSmartsortMQTT.getsInstance().setTransactionActive(PurolatorSmartsortMQTT.HFPUMASTER_DBTYPE,true);

            String[] selectHFPUMasterColumns = {"HFPULocationID", "LocationName","StreetName","StreetType","StreetNumber","MunicipalityName","PostalCode"};
            HFPUCursor = database.query("HFPULocationMaster", selectHFPUMasterColumns, "HFPULocationID=?",
                    new String[]{HFPULocationID}, null, null, null, null);
            if (HFPUCursor.moveToFirst()){

                if (D) Log.d(TAG,"Found in HFPU, fetch in RPM File");
                String[] RPMselectColumns = {"RoutePlanVersionID","RouteNumber", "ShelfNumber","MunicipalityName",
                        "TruckShelfOverride","DeliverySequenceID","StreetName","StreetType","StreetDirection","CustomerName"};
                SQLiteDatabase RPMdatabase = PurolatorSmartsortMQTT.getsInstance().getDatabase(PurolatorSmartsortMQTT.RPM_DBTYPE);
                if (RPMdatabase == null){
                    HFPUCursor.close();
                    PurolatorSmartsortMQTT.getsInstance().setTransactionActive(PurolatorSmartsortMQTT.HFPUMASTER_DBTYPE,false);
                    return false;
                }

                //check if database is being moved
                while (PurolatorSmartsortMQTT.getsInstance().moveRPMDatabase){
                    try{
                        Thread.sleep(10);       //wait 10 milliseconds
                    } catch (InterruptedException ex){

                    }
                }

                PurolatorSmartsortMQTT.getsInstance().setTransactionActive(PurolatorSmartsortMQTT.RPM_DBTYPE,true);


                String municipality = HFPUCursor.getString(HFPUCursor.getColumnIndex("MunicipalityName"));
                String address = HFPUCursor.getString(HFPUCursor.getColumnIndex("StreetName"));
                String streetType = HFPUCursor.getString(HFPUCursor.getColumnIndex("StreetType"));
                String streetNo = HFPUCursor.getString(HFPUCursor.getColumnIndex("StreetNumber"));
                postalCode = HFPUCursor.getString(HFPUCursor.getColumnIndex("PostalCode"));

                Log.d(TAG,"RPM Query parameters: " + address+ " nr: " + streetNo + " streetType: " + streetType +" postcode: " + postalCode);

                Cursor RPMCursor;

                if (streetType.isEmpty()) {
                    RPMCursor = database.query("RoutePlan", RPMselectColumns, "PostalCode=? AND StreetName=? AND (FromStreetNumber<=? AND ToStreetNumber>=?)",
                            new String[]{postalCode, address, streetNo, streetNo}, null, null, null, null);
                } else {
                    RPMCursor = database.query("RoutePlan", RPMselectColumns, "PostalCode=? AND StreetName=? AND StreetType=?(FromStreetNumber<=? AND ToStreetNumber>=?)",
                            new String[]{postalCode, address, streetType,streetNo, streetNo}, null, null, null, null);
                    if (!RPMCursor.moveToFirst() && streetType.equals("RD") ) {
                        RPMCursor = database.query("RoutePlan", RPMselectColumns, "PostalCode=? AND StreetName=? AND StreetType=? AND (FromStreetNumber<=? AND ToStreetNumber>=?)",
                                new String[]{postalCode, address, "ROAD", streetNo, streetNo}, null, null, null, null);
                    }
                }

                if (RPMCursor.moveToFirst()) {
                    if (PurolatorSmartsortMQTT.getsInstance().getConfigData().getDeviceType().equals("FIXED")) {


                        final FixedScanResult fixedScanResult = new FixedScanResult();
                        //TODO New Change                   fixedScanResult.setRouteNumber(RPMCursor.getString(RPMCursor.getColumnIndex("RouteNumber")));
                        fixedScanResult.setRouteNumber("HFP");

                        String shelfNumber = RPMCursor.getString(RPMCursor.getColumnIndex("TruckShelfOverride"));
                        if (shelfNumber.isEmpty()) {
                            shelfNumber = RPMCursor.getString(RPMCursor.getColumnIndex("ShelfNumber"));
                        }
                        fixedScanResult.setShelfNumber(shelfNumber);
                        fixedScanResult.setDeliverySequence(RPMCursor.getString(RPMCursor.getColumnIndex("DeliverySequencedID")));

                        fixedScanResult.setPostalCode(postalCode);
                        fixedScanResult.setScannedCode(barcode);
                        if (municipality.isEmpty())
                            municipality = RPMCursor.getString(RPMCursor.getColumnIndex("MunicipalityName"));
                        fixedScanResult.setPinCode(address + " " + streetNo + " " + postalCode + " " + municipality);
                        fixedScanResult.setScanDate(scanDate);
                        fixedScanResult.setGlassTarget("WAVE");
                        fixedScanResult.setDimensionData(dimensionData);

                        EventBus.getDefault().post(fixedScanResult);
                        if (!PurolatorSmartsortMQTT.getsInstance().getConfigData().isWaveValidation()) {
                            PurolatorSmartsortMQTT.getsInstance().sendToGlass(fixedScanResult);

                        }

//TODO New Change                    uiUpdater.setRouteNumber(RPMCursor.getString(RPMCursor.getColumnIndex("RouteNumber")));
                        uiUpdater.setRouteNumber("HFP");
                        uiUpdater.setShelfNumber(shelfNumber);
                        uiUpdater.setDeliverySequence(RPMCursor.getString(RPMCursor.getColumnIndex("DeliverySequencedID")));
                        uiUpdater.setPrimarySort("");
                        uiUpdater.setSideofBelt("");
                        uiUpdater.setPostalCode(Utilities.getPostalCode(barcode));
                        uiUpdater.setScannedCode(barcode);
                        if (municipality.isEmpty())
                            municipality = RPMCursor.getString(RPMCursor.getColumnIndex("MunicipalityName"));
                        uiUpdater.setPinCode(address + " " + streetNo + " " + postalCode + " " + municipality);
                        EventBus.getDefault().post(uiUpdater);


                        Logger logger = new Logger();
                        logger.setDevice_id(PurolatorSmartsortMQTT.getsInstance().getConfigData().getDeviceName());
                        logger.setUserCode(PurolatorSmartsortMQTT.getsInstance().getConfigData().getUserId());
                        logger.setTerminalID(PurolatorSmartsortMQTT.getsInstance().getConfigData().getTerminalID());
                        logger.setScannedCode(barcode);
                        logger.setPostalCode(Utilities.getPostalCode(barcode));

                        logger.setShelfOverride(RPMCursor.getString(RPMCursor.getColumnIndex("TruckShelfOverride")));
//TODO New Change                    logger.setRouteNumber(RPMCursor.getString(RPMCursor.getColumnIndex("RouteNumber")));
                        logger.setRouteNumber("HFU");
                        logger.setShelfNumber(RPMCursor.getString(RPMCursor.getColumnIndex("ShelfNumber")));
                        logger.setDeliverySequence(RPMCursor.getString(RPMCursor.getColumnIndex("DeliverySequenceID")));
                        logger.setRoutePlanVersionId(RPMCursor.getString(RPMCursor.getColumnIndex("RoutePlanVersionID")));

                        logger.setStreetName(RPMCursor.getString(RPMCursor.getColumnIndex("StreetName")));
                        logger.setStreetType(RPMCursor.getString(RPMCursor.getColumnIndex("StreetType")));
                        logger.setStreetDirection(RPMCursor.getString(RPMCursor.getColumnIndex("StreetDirection")));
                        logger.setCustomerName(RPMCursor.getString(RPMCursor.getColumnIndex("CustomerName")));
                        logger.setBarcodeType("HFP");

                        EventBus.getDefault().post(logger);

                    } else if (PurolatorSmartsortMQTT.getsInstance().getConfigData().getDeviceType().equals("GLASS")){

                        uiUpdater.setUpdateType(PurolatorSmartsortMQTT.UPD_BOTTOMSCREEN);
                        uiUpdater.setRouteNumber("HFP");
                        String shelfNumber = RPMCursor.getString(RPMCursor.getColumnIndex("TruckShelfOverride"));
                        if (shelfNumber.isEmpty()) {
                            shelfNumber = RPMCursor.getString(RPMCursor.getColumnIndex("ShelfNumber"));
                        }
                        uiUpdater.setShelfNumber(shelfNumber);
                        uiUpdater.setPrimarySort("");
                        uiUpdater.setSideofBelt("");
                        if (municipality.isEmpty())
                            municipality = RPMCursor.getString(RPMCursor.getColumnIndex("MunicipalityName"));
                        uiUpdater.setPinCode(address + " " + streetNo + " " + postalCode + " " + municipality);

                        uiUpdater.setScannedCode(barcode);
                        uiUpdater.setPostalCode(Utilities.getPostalCode(barcode));
                        uiUpdater.setCorrectBay(false);
                        EventBus.getDefault().post(uiUpdater);

                    }


                    RPMCursor.close();
                    PurolatorSmartsortMQTT.getsInstance().setTransactionActive(PurolatorSmartsortMQTT.RPM_DBTYPE, false);
                    HFPUCursor.close();
                    PurolatorSmartsortMQTT.getsInstance().setTransactionActive(PurolatorSmartsortMQTT.HFPUMASTER_DBTYPE, false);

                    return true;
                }
            }

            PurolatorSmartsortMQTT.getsInstance().setTransactionActive(PurolatorSmartsortMQTT.RPM_DBTYPE,false);
            HFPUCursor.close();
            PurolatorSmartsortMQTT.getsInstance().setTransactionActive(PurolatorSmartsortMQTT.HFPUMASTER_DBTYPE,false);
//            return false;
        }

        PurolatorSmartsortMQTT.getsInstance().setTransactionActive(PurolatorSmartsortMQTT.HFPUPC_DBTYPE,false);


        //now do a query in RPM, only with PostalCode and with AddressRecordType = HFPU

        return RPM1DFound(barcode,scanDate,dimensionData);

    }

    /**
     * Find a Route based on the PostalCode, part of NGB Code with AddressRecordType = HFPU
     * This is a very similar logic as in LookupFixedBarcodeREM
     *
     * @param barcode
     * @return
     */

    private boolean RPM1DFound(String barcode, Date scanDate, String dimensionData) {

        //check if Postalcode lookup is switched on, otherwise return

        if (!PurolatorSmartsortMQTT.getsInstance().getConfigData().isPostalCodeLookup()){
            return false;
        }

        String[] selectColumns = {"RouteNumber", "ShelfNumber","MunicipalityName","AddressRecordType","TruckShelfOverride","DeliverySequenceID"};
        String pincode = Utilities.getPINCode(barcode);

        SQLiteDatabase database = PurolatorSmartsortMQTT.getsInstance().getDatabase(PurolatorSmartsortMQTT.RPM_DBTYPE);


        Log.d(TAG,"Query parameters for HFPU PostalCode: " + barcode);

        if (database == null){
            return false;
        }

        if (PurolatorSmartsortMQTT.getsInstance().getPackageInShelf() !=null
                && PurolatorSmartsortMQTT.getsInstance().getPackageInShelf().getScannedCode().equals(barcode)) {
            return true;        //PIN is found, but not in the database
        }

        String postalCode = Utilities.getPostalCode(barcode);
        if (postalCode == null) {
            return false;
        }

        //check if database is being moved
        while (PurolatorSmartsortMQTT.getsInstance().moveRPMDatabase){
            try{
                Thread.sleep(10);       //wait 10 milliseconds
            } catch (InterruptedException ex){

            }
        }

        PurolatorSmartsortMQTT.getsInstance().setTransactionActive(PurolatorSmartsortMQTT.RPM_DBTYPE,true);


        Cursor RPMCursor = database.query("RoutePlan", selectColumns, "PostalCode=? ",
                new String[]{postalCode}, null, null, null, null);


        if (D) Log.d(TAG,"QueryReturn for HFPU in PostalCode: " + RPMCursor.toString());

        UIUpdater uiUpdater = new UIUpdater();

        // get the found data and post en event.
        if (RPMCursor.moveToFirst()) {
            do {
                String addressRecordType = RPMCursor.getString(RPMCursor.getColumnIndex("AddressRecordType"));

                if (addressRecordType.startsWith("HFP")) {

                    if (D) Log.d(TAG, "Found in RPM, with HFPU!");

                    if (PurolatorSmartsortMQTT.getsInstance().getConfigData().getDeviceType().equals("FIXED")) {


                        final FixedScanResult fixedScanResult = new FixedScanResult();
//TODO New Change                    fixedScanResult.setRouteNumber(RPMCursor.getString(RPMCursor.getColumnIndex("RouteNumber")));
                        fixedScanResult.setRouteNumber("HFU");
                        String shelfNumber = RPMCursor.getString(RPMCursor.getColumnIndex("TruckShelfOverride"));
                        if (shelfNumber == null || shelfNumber.isEmpty()) {
                            shelfNumber = RPMCursor.getString(RPMCursor.getColumnIndex("ShelfNumber"));
                        }
                        fixedScanResult.setShelfNumber(shelfNumber);

                        fixedScanResult.setPostalCode(postalCode);
                        fixedScanResult.setScannedCode(barcode);
                        String readablePincode = pincode;
                        if (pincode.length() > 3)
                            readablePincode = pincode.substring(pincode.length() - 4);

                        fixedScanResult.setPinCode(readablePincode + " " + postalCode);
                        fixedScanResult.setScanDate(scanDate);
                        fixedScanResult.setGlassTarget("WAVE");
                        fixedScanResult.setDimensionData(dimensionData);

                        EventBus.getDefault().post(fixedScanResult);

                        if (!PurolatorSmartsortMQTT.getsInstance().getConfigData().isWaveValidation()) {
                            PurolatorSmartsortMQTT.getsInstance().sendToGlass(fixedScanResult);

                        }

                        uiUpdater.setRouteNumber("HFU");
                        uiUpdater.setShelfNumber(shelfNumber);
                        uiUpdater.setDeliverySequence(RPMCursor.getString(RPMCursor.getColumnIndex("DeliverySequenceID")));
                        uiUpdater.setPrimarySort("");
                        uiUpdater.setSideofBelt("");
                        uiUpdater.setPostalCode(Utilities.getPostalCode(barcode));
                        uiUpdater.setScannedCode(barcode);
                        uiUpdater.setRemediationId(pincode);
                        uiUpdater.setPinCode(pincode + " " + postalCode);
                        EventBus.getDefault().post(uiUpdater);


                        Logger logger = new Logger();
                        logger.setDevice_id(PurolatorSmartsortMQTT.getsInstance().getConfigData().getDeviceName());
                        logger.setUserCode(PurolatorSmartsortMQTT.getsInstance().getConfigData().getUserId());
                        logger.setTerminalID(PurolatorSmartsortMQTT.getsInstance().getConfigData().getTerminalID());
                        logger.setScannedCode(barcode);
                        logger.setBarcodeType("HFPU");
                        logger.setShelfOverride(RPMCursor.getString(RPMCursor.getColumnIndex("TruckShelfOverride")));
                        logger.setRouteNumber("HFU");
                        logger.setShelfNumber(RPMCursor.getString(RPMCursor.getColumnIndex("ShelfNumber")));
                        logger.setDeliverySequence(RPMCursor.getString(RPMCursor.getColumnIndex("DeliverySequenceID")));

                        EventBus.getDefault().post(logger);
                    } else if (PurolatorSmartsortMQTT.getsInstance().getConfigData().getDeviceType().equals("GLASS")) {
                        uiUpdater.setUpdateType(PurolatorSmartsortMQTT.UPD_BOTTOMSCREEN);
                        uiUpdater.setRouteNumber("HFP");
                        String shelfNumber = RPMCursor.getString(RPMCursor.getColumnIndex("TruckShelfOverride"));
                        if (shelfNumber == null || shelfNumber.isEmpty()) {
                            shelfNumber = RPMCursor.getString(RPMCursor.getColumnIndex("ShelfNumber"));
                        }
                        uiUpdater.setShelfNumber(shelfNumber);
                        uiUpdater.setDeliverySequence(RPMCursor.getString(RPMCursor.getColumnIndex("DeliverySequenceID")));
                        uiUpdater.setPrimarySort("");
                        uiUpdater.setSideofBelt("");
                        uiUpdater.setPostalCode(Utilities.getPostalCode(barcode));
                        uiUpdater.setScannedCode(barcode);
                        uiUpdater.setRemediationId(pincode);
                        uiUpdater.setPinCode(pincode + " " + postalCode);
                        uiUpdater.setCorrectBay(false);
                        EventBus.getDefault().post(uiUpdater);

                    }
                    RPMCursor.close();
                    PurolatorSmartsortMQTT.getsInstance().setTransactionActive(PurolatorSmartsortMQTT.RPM_DBTYPE, false);


                    return true;
                }
            } while (RPMCursor.moveToNext());


        }



        RPMCursor.close();
        PurolatorSmartsortMQTT.getsInstance().setTransactionActive(PurolatorSmartsortMQTT.RPM_DBTYPE,false);
        return false;



    }

}
