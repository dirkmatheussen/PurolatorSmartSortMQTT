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
 * If Lookup for PIN failed, try to do a lookup with the RoutePlan
 *
 */
public class LookupFixedBarcodeRPM {

    private static final String TAG = "LookupFixedBarcodeRPM";
    private static final boolean D = true;

    private SQLiteDatabase database;


    public LookupFixedBarcodeRPM(){
        EventBus.getDefault().register(this);
        //open the PM database for readonly
        //database = SQLiteDatabase.openDatabase("PIM.db3",null,SQLiteDatabase.OPEN_READONLY);

    }



    @Subscribe(priority = 2)    //
    public void onFixedBarcodeScanEvent(FixedBarcodeScan event){

        if (D) Log.d(TAG,"In event");

        //if Routeplan found, cancel Delivery to other classes with lower priority
        if (RPMFound(event.getBarcodeResult(),event.getScanDate(), event.getDimensionData())) {
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

    private boolean RPMFound(String barcode, Date scanDate, String dimensionData) {
        if (Utilities.getBarcodeLogType(barcode).equals("2D")) {
            if (RPM2DFound(barcode, scanDate, dimensionData)) return true;
        } else if (Utilities.getBarcodeLogType(barcode).equals("NGB")) {
            if (RPM1DFound(barcode, scanDate, dimensionData)) return true;
        }
        return false;

    }

    /**
     * Find a Route based on the PostalCode, part of NGB Code (not found in PINfile)
     *
     * @param barcode
     * @return
     */

    private boolean RPM1DFound(String barcode, Date scanDate, String dimensionData) {

        //check if Postalcode lookup is switched on, otherwise return

        if (!PurolatorSmartsortMQTT.getsInstance().getConfigData().isPostalCodeLookup()){
            return false;
        }

        String[] selectColumns = {"RouteNumber", "ShelfNumber","CustomerName","StreetName","StreetType","FromStreetNumber","ToStreetNumber","FromUnitNumber","ToUnitNumber","MunicipalityName","AddressRecordType","TruckShelfOverride","DeliverySequenceID"};
        String pincode = Utilities.getPINCode(barcode);

        SQLiteDatabase database = PurolatorSmartsortMQTT.getsInstance().getDatabase(PurolatorSmartsortMQTT.RPM_DBTYPE);


        Log.d(TAG,"Query parameters: " + barcode);

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



        if (D) Log.d(TAG,"QueryReturn: " + RPMCursor.toString());

        UIUpdater uiUpdater = new UIUpdater();

        // get the found data and post en event.
        if (RPMCursor.moveToFirst()) {
            //first check if route is assigned to the preloader
//            if (PurolatorSmartsortMQTT.getsInstance().isRouteValid(RPMCursor.getString(RPMCursor.getColumnIndex("RouteNumber")))) {
            do {
                String addressRecordType = RPMCursor.getString(RPMCursor.getColumnIndex("AddressRecordType"));

                if (addressRecordType.startsWith("Unique")) {

                    if (D) Log.d(TAG, "Found in RPM, with UniqueAdress!");
                    if (PurolatorSmartsortMQTT.getsInstance().getConfigData().getDeviceType().equals("FIXED")) {


                        final FixedScanResult fixedScanResult = new FixedScanResult();
                        fixedScanResult.setRouteNumber(RPMCursor.getString(RPMCursor.getColumnIndex("RouteNumber")));
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

                        String municipality = RPMCursor.getString(RPMCursor.getColumnIndex("MunicipalityName"));
                        String address = RPMCursor.getString(RPMCursor.getColumnIndex("StreetName"));
                        String fromStreetNo = RPMCursor.getString(RPMCursor.getColumnIndex("FromStreetNumber"));
                        String toStreetNo = RPMCursor.getString(RPMCursor.getColumnIndex("ToStreetNumber"));

                        if (fromStreetNo.equals(toStreetNo)) fixedScanResult.setStreetnumber(RPMCursor.getString(RPMCursor.getColumnIndex("FromStreetNumber")));

                        fixedScanResult.setStreetname(address);
                        fixedScanResult.setMunicipality(municipality);

                        if (RPMCursor.getString(RPMCursor.getColumnIndex("ToUnitNumber")).isEmpty())
                            fixedScanResult.setStreetunit(RPMCursor.getString(RPMCursor.getColumnIndex("FromUnitNumber")));


                        fixedScanResult.setScanDate(scanDate);
                        fixedScanResult.setGlassTarget("WAVE");
                        fixedScanResult.setDimensionData(dimensionData);

                        EventBus.getDefault().post(fixedScanResult);

                        if (!PurolatorSmartsortMQTT.getsInstance().getConfigData().isWaveValidation()) {
                            PurolatorSmartsortMQTT.getsInstance().sendToGlass(fixedScanResult);

                        }


                        uiUpdater.setRouteNumber(RPMCursor.getString(RPMCursor.getColumnIndex("RouteNumber")));
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
                        logger.setBarcodeType("RPM-Unique-PCLookup");
                        logger.setShelfOverride(RPMCursor.getString(RPMCursor.getColumnIndex("TruckShelfOverride")));
                        logger.setRouteNumber(RPMCursor.getString(RPMCursor.getColumnIndex("RouteNumber")));
                        logger.setShelfNumber(RPMCursor.getString(RPMCursor.getColumnIndex("ShelfNumber")));
                        logger.setDeliverySequence(RPMCursor.getString(RPMCursor.getColumnIndex("DeliverySequenceID")));
                        logger.setPostalCode(Utilities.getPostalCode(barcode));

                        EventBus.getDefault().post(logger);
                    } else if (PurolatorSmartsortMQTT.getsInstance().getConfigData().getDeviceType().equals("GLASS")) {

                        uiUpdater.setUpdateType(PurolatorSmartsortMQTT.UPD_BOTTOMSCREEN);
                        uiUpdater.setRouteNumber(RPMCursor.getString(RPMCursor.getColumnIndex("RouteNumber")));
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

                        String municipality = RPMCursor.getString(RPMCursor.getColumnIndex("MunicipalityName"));
                        String address = RPMCursor.getString(RPMCursor.getColumnIndex("StreetName"));
                        String fromStreetNo = RPMCursor.getString(RPMCursor.getColumnIndex("FromStreetNumber"));
                        String toStreetNo = RPMCursor.getString(RPMCursor.getColumnIndex("ToStreetNumber"));

                        uiUpdater.setMunicipality(municipality);
                        uiUpdater.setStreetname(address);
                        if (fromStreetNo.equals(toStreetNo)) uiUpdater.setStreetnumber(RPMCursor.getString(RPMCursor.getColumnIndex("FromStreetNumber")));
                        if (RPMCursor.getString(RPMCursor.getColumnIndex("ToUnitNumber")).isEmpty())
                            uiUpdater.setStreetunit(RPMCursor.getString(RPMCursor.getColumnIndex("FromUnitNumber")));


                        uiUpdater.setCorrectBay(false);

                        EventBus.getDefault().post(uiUpdater);


                    }

                        RPMCursor.close();
                    PurolatorSmartsortMQTT.getsInstance().setTransactionActive(PurolatorSmartsortMQTT.RPM_DBTYPE, false);


                    return true;
                }
            } while (RPMCursor.moveToNext());
            //check if there is a valid AdressRange
            RPMCursor.moveToFirst();
            do {
                String addressRecordType = RPMCursor.getString(RPMCursor.getColumnIndex("AddressRecordType"));

                if (addressRecordType.startsWith("AddressRang") ) {             //only when 1 record found
//                if (addressRecordType.startsWith("AddressRang") && RPMCursor.getCount() <= 2) {             //only when 1 record found

                    if (D) Log.d(TAG, "Found in RPM, With AddressRange");
                    if (PurolatorSmartsortMQTT.getsInstance().getConfigData().getDeviceType().equals("FIXED")) {


                        final FixedScanResult fixedScanResult = new FixedScanResult();
                        fixedScanResult.setRouteNumber(RPMCursor.getString(RPMCursor.getColumnIndex("RouteNumber")));
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

                        String municipality = RPMCursor.getString(RPMCursor.getColumnIndex("MunicipalityName"));
                        String address = RPMCursor.getString(RPMCursor.getColumnIndex("StreetName"));
                        String fromStreetNo = RPMCursor.getString(RPMCursor.getColumnIndex("FromStreetNumber"));
                        String toStreetNo = RPMCursor.getString(RPMCursor.getColumnIndex("ToStreetNumber"));

                        if (fromStreetNo.equals(toStreetNo)) fixedScanResult.setStreetnumber(RPMCursor.getString(RPMCursor.getColumnIndex("FromStreetNumber")));


                        fixedScanResult.setStreetname(address);
                        fixedScanResult.setMunicipality(municipality);

                        if (RPMCursor.getString(RPMCursor.getColumnIndex("ToUnitNumber")).isEmpty())
                            fixedScanResult.setStreetunit(RPMCursor.getString(RPMCursor.getColumnIndex("FromUnitNumber")));


                        fixedScanResult.setScanDate(scanDate);
                        fixedScanResult.setGlassTarget("WAVE");
                        fixedScanResult.setDimensionData(dimensionData);

                        EventBus.getDefault().post(fixedScanResult);
                        if (!PurolatorSmartsortMQTT.getsInstance().getConfigData().isWaveValidation()) {
                            PurolatorSmartsortMQTT.getsInstance().sendToGlass(fixedScanResult);

                        }


                        uiUpdater.setRouteNumber(RPMCursor.getString(RPMCursor.getColumnIndex("RouteNumber")));
                        uiUpdater.setShelfNumber(shelfNumber);
                        uiUpdater.setDeliverySequence(RPMCursor.getString(RPMCursor.getColumnIndex("DeliverySequenceID")));
                        uiUpdater.setPrimarySort("");
                        uiUpdater.setSideofBelt("");
                        uiUpdater.setPostalCode(Utilities.getPostalCode(barcode));
                        uiUpdater.setScannedCode(barcode);
                        uiUpdater.setRemediationId(pincode);
                        uiUpdater.setPinCode(readablePincode + " " + postalCode);
                        EventBus.getDefault().post(uiUpdater);


                        Logger logger = new Logger();
                        logger.setDevice_id(PurolatorSmartsortMQTT.getsInstance().getConfigData().getDeviceName());
                        logger.setUserCode(PurolatorSmartsortMQTT.getsInstance().getConfigData().getUserId());
                        logger.setTerminalID(PurolatorSmartsortMQTT.getsInstance().getConfigData().getTerminalID());
                        logger.setScannedCode(barcode);
                        logger.setBarcodeType("RPM-Range-PCLookup");
                        logger.setPostalCode(Utilities.getPostalCode(barcode));
                        logger.setShelfOverride(RPMCursor.getString(RPMCursor.getColumnIndex("TruckShelfOverride")));
                        logger.setRouteNumber(RPMCursor.getString(RPMCursor.getColumnIndex("RouteNumber")));
                        logger.setShelfNumber(RPMCursor.getString(RPMCursor.getColumnIndex("ShelfNumber")));
                        logger.setDeliverySequence(RPMCursor.getString(RPMCursor.getColumnIndex("DeliverySequenceID")));

                        EventBus.getDefault().post(logger);
                    } else if (PurolatorSmartsortMQTT.getsInstance().getConfigData().getDeviceType().equals("GLASS")) {
                        uiUpdater.setUpdateType(PurolatorSmartsortMQTT.UPD_BOTTOMSCREEN);
                        uiUpdater.setRouteNumber(RPMCursor.getString(RPMCursor.getColumnIndex("RouteNumber")));
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

                        String municipality = RPMCursor.getString(RPMCursor.getColumnIndex("MunicipalityName"));
                        String address = RPMCursor.getString(RPMCursor.getColumnIndex("StreetName"));
                        String fromStreetNo = RPMCursor.getString(RPMCursor.getColumnIndex("FromStreetNumber"));
                        String toStreetNo = RPMCursor.getString(RPMCursor.getColumnIndex("ToStreetNumber"));

                        uiUpdater.setMunicipality(municipality);
                        uiUpdater.setStreetname(address);
                        if (fromStreetNo.equals(toStreetNo)) uiUpdater.setStreetnumber(RPMCursor.getString(RPMCursor.getColumnIndex("FromStreetNumber")));
                        if (RPMCursor.getString(RPMCursor.getColumnIndex("ToUnitNumber")).isEmpty())
                            uiUpdater.setStreetunit(RPMCursor.getString(RPMCursor.getColumnIndex("FromUnitNumber")));


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

    /**
     * Find a Route based on the address in the barcode
     * Identical as in LookupFixedBarcodeOCR
     *
     * @param barcode
     * @return
     */

    private boolean RPM2DFound(String barcode, Date scanDate,String dimensionData){


        //check if there is a \ and replace by |
        barcode = barcode.replace("\\","|");


        String[] parsed = barcode.split("[|]");
        String streetNo = "-1";
        String address = "";
        String municipality =  "";
        String postalCode = "";

        String pincode = Utilities.getPINCode(barcode);

        for (String aParsed : parsed) {
            String[] valuePair = aParsed.split("[~]");

            if (valuePair.length >= 2) {

                //TODO Check is address is filled in correctly in 2D Code R02/R03
                switch (valuePair[0].toUpperCase()) {
                    case "R02":

                        break;
                    case "R03":             //street n#
                        streetNo = valuePair[1];
                        break;
                    case "R04":             //address
                        address = valuePair[1].toUpperCase();

                        break;
                    case "R05":             //address

                        break;
                    case "R06":             //municipality
                        municipality = valuePair[1].toUpperCase();
                        break;
                    case "R07":             //postal code
                        postalCode = valuePair[1].toUpperCase();
                        break;
                }
            }
        }

        //do additional parsing

 //       String[] selectColumns = {"RouteNumber", "ShelfNumber","MunicipalityName","AddressRecordType","TruckShelfOverride","DeliverySequenceID"};
        String[] selectColumns = {"RouteNumber", "ShelfNumber","CustomerName","StreetName","StreetType","FromStreetNumber","ToStreetNumber","FromUnitNumber","ToUnitNumber","MunicipalityName","AddressRecordType","TruckShelfOverride","DeliverySequenceID"};

        SQLiteDatabase database = PurolatorSmartsortMQTT.getsInstance().getDatabase(PurolatorSmartsortMQTT.RPM_DBTYPE);

        String parsedAddress[] = Utilities.parseAddress(address);
        String streetNumber =  parsedAddress[0];
        String streetName = parsedAddress[1];
        String streetType = parsedAddress[2];

        Log.d(TAG,"Query parameters: Straat: " + streetName+ "nr: " + streetNumber + "StreetType: " + streetType + "postcode: " + postalCode);

        if (database == null){
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

        Cursor RPMCursor;
        if (streetType.isEmpty()){
            RPMCursor = database.query("RoutePlan", selectColumns, "PostalCode=? AND StreetName=? AND (FromStreetNumber<=? AND ToStreetNumber>=?) ",
                    new String[]{postalCode,streetName,streetNumber,streetNumber}, null, null, null, null);

        } else {
            RPMCursor = database.query("RoutePlan", selectColumns, "PostalCode=? AND StreetName=? AND StreetType=? AND (FromStreetNumber<=? AND ToStreetNumber>=?)",
                    new String[]{postalCode,streetName,streetType,streetNumber,streetNumber}, null, null, null, null);

            if (!RPMCursor.moveToFirst() && streetType.equals("RD") ){
                RPMCursor = database.query("RoutePlan", selectColumns, "PostalCode=? AND StreetName=? AND StreetType=? AND (FromStreetNumber<=? AND ToStreetNumber>=?)",
                        new String[]{postalCode,streetName,"ROAD",streetNumber,streetNumber}, null, null, null, null);
            }
        }

        if (RPMCursor.getCount() == 0){
            RPMCursor = database.query("RoutePlan", selectColumns, "PostalCode=?",
                    new String[]{postalCode}, null, null, null, null);
        }

        if (D) Log.d(TAG,"QueryReturn: " + RPMCursor.toString());

        UIUpdater uiUpdater = new UIUpdater();

        // get the found data and post en event.
        if (RPMCursor.moveToFirst()) {
            //first check if route is assigned to the preloader
//            if (PurolatorSmartsortMQTT.getsInstance().isRouteValid(RPMCursor.getString(RPMCursor.getColumnIndex("RouteNumber")))) {
            do {
                String addressRecordType = RPMCursor.getString(RPMCursor.getColumnIndex("AddressRecordType"));

                if (addressRecordType.startsWith("Unique")) {

                    if (D) Log.d(TAG, "Found in RPM, with UniqueAdress!");
                    if (PurolatorSmartsortMQTT.getsInstance().getConfigData().getDeviceType().equals("FIXED")) {

                        Logger logger = new Logger();
                        final FixedScanResult fixedScanResult = new FixedScanResult();

                        municipality = RPMCursor.getString(RPMCursor.getColumnIndex("MunicipalityName"));
                        address = RPMCursor.getString(RPMCursor.getColumnIndex("StreetName"));
                        String fromStreetNo = RPMCursor.getString(RPMCursor.getColumnIndex("FromStreetNumber"));
                        String toStreetNo = RPMCursor.getString(RPMCursor.getColumnIndex("ToStreetNumber"));

                        if (fromStreetNo.equals(toStreetNo)) fixedScanResult.setStreetnumber(RPMCursor.getString(RPMCursor.getColumnIndex("FromStreetNumber")));
                        fixedScanResult.setStreetname(address);
                        if (RPMCursor.getString(RPMCursor.getColumnIndex("ToStreetNumber")).isEmpty())
                            fixedScanResult.setStreetnumber(RPMCursor.getString(RPMCursor.getColumnIndex("FromStreetNumber")));
                        fixedScanResult.setMunicipality(municipality);
                        if (RPMCursor.getString(RPMCursor.getColumnIndex("ToUnitNumber")).isEmpty())
                            fixedScanResult.setStreetunit(RPMCursor.getString(RPMCursor.getColumnIndex("FromUnitNumber")));



                        if (Utilities.getBarcodeLogType(barcode).equals("2D")){

                            //check if there is a \ and replace by |
                            barcode = barcode.replace("\\","|");

                            parsed = barcode.split("[|]");
                            for (String aParsed : parsed) {
                                String[] valuePair = aParsed.split("[~]");

                                if (valuePair.length >= 2) {


                                    switch (valuePair[0].toUpperCase()) {
                                        case "RO1":
                                            logger.setCustomerName(valuePair[1]);
                                            fixedScanResult.setAddressee(valuePair[1]);
                                            break;
                                        case "R03":             //street n#
                                            logger.setStreetNumber(valuePair[1]);
                                            fixedScanResult.setStreetnumber(valuePair[1]);
                                            break;
                                        case "R04":             //address
                                            parsedAddress = Utilities.parseAddress(valuePair[1]);
                                            logger.setStreetName(parsedAddress[1]);
                                            logger.setStreetType(parsedAddress[2]);
                                            fixedScanResult.setStreetname(parsedAddress[1]);
                                            fixedScanResult.setStreetunit(parsedAddress[2]);


                                            break;
                                        case "R05":             //address

                                            break;
                                        case "R06":             //municipality
                                            logger.setMunicipalityName(valuePair[1]);
                                            fixedScanResult.setMunicipality(valuePair[1]);
                                            break;
                                        case "R07":             //postal code
                                            logger.setPostalCode(valuePair[1]);
                                            fixedScanResult.setPostalCode(valuePair[1]);
                                            break;
                                        case "S04":
                                            logger.setDeliveryTime(valuePair[1]);
                                            break;
                                        case "S05":
                                            logger.setShipmentType(valuePair[1]);
                                            break;
                                        case "S06":
                                            logger.setDeliveryType(valuePair[1]);
                                            break;
                                        case "S07":
                                            logger.setDiversionCode(valuePair[1]);
                                            break;
                                        case "S15":
                                            logger.setHandlingClassType(valuePair[1]);
                                            break;
                                    }
                                }
                            }
                        }



                        fixedScanResult.setRouteNumber(RPMCursor.getString(RPMCursor.getColumnIndex("RouteNumber")));
                        String shelfNumber = RPMCursor.getString(RPMCursor.getColumnIndex("TruckShelfOverride"));
                        if (shelfNumber == null || shelfNumber.isEmpty()) {
                            shelfNumber = RPMCursor.getString(RPMCursor.getColumnIndex("ShelfNumber"));
                        }
                        fixedScanResult.setShelfNumber(shelfNumber);

                        fixedScanResult.setPostalCode(postalCode);
                        fixedScanResult.setScannedCode(barcode);
                        if (municipality != null || municipality.isEmpty())
                            municipality = RPMCursor.getString(RPMCursor.getColumnIndex("MunicipalityName"));
                        fixedScanResult.setPinCode(address + " " + streetNo + " " + postalCode + " " + municipality);
                        fixedScanResult.setScanDate(scanDate);
                        fixedScanResult.setGlassTarget("WAVE");
                        fixedScanResult.setDimensionData(dimensionData);

                        EventBus.getDefault().post(fixedScanResult);

                        if (!PurolatorSmartsortMQTT.getsInstance().getConfigData().isWaveValidation()) {
                            PurolatorSmartsortMQTT.getsInstance().sendToGlass(fixedScanResult);

                        }


                        uiUpdater.setRouteNumber(RPMCursor.getString(RPMCursor.getColumnIndex("RouteNumber")));
                        uiUpdater.setShelfNumber(shelfNumber);
                        uiUpdater.setDeliverySequence(RPMCursor.getString(RPMCursor.getColumnIndex("DeliverySequenceID")));
                        uiUpdater.setPrimarySort("");
                        uiUpdater.setSideofBelt("");
                        uiUpdater.setPostalCode(Utilities.getPostalCode(barcode));
                        uiUpdater.setScannedCode(barcode);
                        uiUpdater.setRemediationId(pincode);
                        if (municipality.isEmpty())
                            municipality = RPMCursor.getString(RPMCursor.getColumnIndex("MunicipalityName"));
                        uiUpdater.setPinCode(address + " " + streetNo + " " + postalCode + " " + municipality);
                        EventBus.getDefault().post(uiUpdater);


                        logger.setDevice_id(PurolatorSmartsortMQTT.getsInstance().getConfigData().getDeviceName());
                        logger.setUserCode(PurolatorSmartsortMQTT.getsInstance().getConfigData().getUserId());
                        logger.setTerminalID(PurolatorSmartsortMQTT.getsInstance().getConfigData().getTerminalID());
                        logger.setScannedCode(barcode);
                        logger.setBarcodeType("RPM-Unique-AddrLookup");
                        logger.setShelfOverride(RPMCursor.getString(RPMCursor.getColumnIndex("TruckShelfOverride")));
                        logger.setRouteNumber(RPMCursor.getString(RPMCursor.getColumnIndex("RouteNumber")));
                        logger.setShelfNumber(RPMCursor.getString(RPMCursor.getColumnIndex("ShelfNumber")));
                        logger.setDeliverySequence(RPMCursor.getString(RPMCursor.getColumnIndex("DeliverySequenceID")));
                        logger.setPostalCode(Utilities.getPostalCode(barcode));

                        EventBus.getDefault().post(logger);
                    } else if (PurolatorSmartsortMQTT.getsInstance().getConfigData().getDeviceType().equals("GLASS")) {
                        uiUpdater.setUpdateType(PurolatorSmartsortMQTT.UPD_BOTTOMSCREEN);
                        uiUpdater.setRouteNumber(RPMCursor.getString(RPMCursor.getColumnIndex("RouteNumber")));
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
                        if (municipality.isEmpty())
                            municipality = RPMCursor.getString(RPMCursor.getColumnIndex("MunicipalityName"));
                        uiUpdater.setPinCode(address + " " + streetNo + " " + postalCode + " " + municipality);

                        uiUpdater.setCorrectBay(false);

                        EventBus.getDefault().post(uiUpdater);

                    }

                        RPMCursor.close();
                    PurolatorSmartsortMQTT.getsInstance().setTransactionActive(PurolatorSmartsortMQTT.RPM_DBTYPE, false);


                    return true;
                }
            } while (RPMCursor.moveToNext());
            //check if there is a valid AdressRange
//            if (RPMCursor.moveToFirst() && RPMCursor.getCount() <= 2) {
            if (RPMCursor.moveToFirst()) {
                do {
                    String addressRecordType = RPMCursor.getString(RPMCursor.getColumnIndex("AddressRecordType"));

                    if (addressRecordType.startsWith("AddressRang")) {

                        if (D) Log.d(TAG, "Found in RPM, With AddressRange");
                        if (PurolatorSmartsortMQTT.getsInstance().getConfigData().getDeviceType().equals("FIXED")) {

                            Logger logger = new Logger();
                            final FixedScanResult fixedScanResult = new FixedScanResult();
                            municipality = RPMCursor.getString(RPMCursor.getColumnIndex("MunicipalityName"));
                            address = RPMCursor.getString(RPMCursor.getColumnIndex("StreetName"));
                            String fromStreetNo = RPMCursor.getString(RPMCursor.getColumnIndex("FromStreetNumber"));
                            String toStreetNo = RPMCursor.getString(RPMCursor.getColumnIndex("ToStreetNumber"));

                            if (fromStreetNo.equals(toStreetNo)) fixedScanResult.setStreetnumber(RPMCursor.getString(RPMCursor.getColumnIndex("FromStreetNumber")));

                            fixedScanResult.setStreetname(address);
                            if (RPMCursor.getString(RPMCursor.getColumnIndex("ToStreetNumber")).isEmpty())
                                fixedScanResult.setStreetnumber(RPMCursor.getString(RPMCursor.getColumnIndex("FromStreetNumber")));
                            fixedScanResult.setMunicipality(municipality);
                            if (RPMCursor.getString(RPMCursor.getColumnIndex("ToUnitNumber")).isEmpty())
                                fixedScanResult.setStreetunit(RPMCursor.getString(RPMCursor.getColumnIndex("FromUnitNumber")));



                            if (Utilities.getBarcodeLogType(barcode).equals("2D")){

                                //check if there is a \ and replace by |
                                barcode = barcode.replace("\\","|");

                                parsed = barcode.split("[|]");
                                for (String aParsed : parsed) {
                                    String[] valuePair = aParsed.split("[~]");

                                    if (valuePair.length >= 2) {


                                        switch (valuePair[0].toUpperCase()) {
                                            case "RO1":
                                                logger.setCustomerName(valuePair[1]);
                                                fixedScanResult.setAddressee(valuePair[1]);
                                                break;
                                            case "R03":             //street n#
                                                logger.setStreetNumber(valuePair[1]);
                                                fixedScanResult.setStreetnumber(valuePair[1]);
                                                break;
                                            case "R04":             //address
                                                parsedAddress = Utilities.parseAddress(valuePair[1]);
                                                logger.setStreetName(parsedAddress[1]);
                                                logger.setStreetType(parsedAddress[2]);
                                                fixedScanResult.setStreetname(parsedAddress[1]);
                                                fixedScanResult.setStreetunit(parsedAddress[2]);


                                                break;
                                            case "R05":             //address

                                                break;
                                            case "R06":             //municipality
                                                logger.setMunicipalityName(valuePair[1]);
                                                fixedScanResult.setMunicipality(valuePair[1]);
                                                break;
                                            case "R07":             //postal code
                                                logger.setPostalCode(valuePair[1]);
                                                fixedScanResult.setPostalCode(valuePair[1]);
                                                break;
                                            case "S04":
                                                logger.setDeliveryTime(valuePair[1]);
                                                break;
                                            case "S05":
                                                logger.setShipmentType(valuePair[1]);
                                                break;
                                            case "S06":
                                                logger.setDeliveryType(valuePair[1]);
                                                break;
                                            case "S07":
                                                logger.setDiversionCode(valuePair[1]);
                                                break;
                                            case "S15":
                                                logger.setHandlingClassType(valuePair[1]);
                                                break;
                                        }
                                    }
                                }
                            }
                            fixedScanResult.setRouteNumber(RPMCursor.getString(RPMCursor.getColumnIndex("RouteNumber")));
                            String shelfNumber = RPMCursor.getString(RPMCursor.getColumnIndex("TruckShelfOverride"));
                            if (shelfNumber.isEmpty()) {
                                shelfNumber = RPMCursor.getString(RPMCursor.getColumnIndex("ShelfNumber"));
                            }
                            fixedScanResult.setShelfNumber(shelfNumber);

                            fixedScanResult.setPostalCode(postalCode);
                            fixedScanResult.setScannedCode(barcode);
                            if (municipality.isEmpty())
                                municipality = RPMCursor.getString(RPMCursor.getColumnIndex("MunicipalityName"));
                            fixedScanResult.setPinCode(address + " " + streetNo + " " + postalCode + " " + municipality);
                            fixedScanResult.setScanDate(scanDate);
                            fixedScanResult.setGlassTarget("WAVE");
                            fixedScanResult.setDimensionData("");

                            EventBus.getDefault().post(fixedScanResult);

                            if (!PurolatorSmartsortMQTT.getsInstance().getConfigData().isWaveValidation()) {
                                PurolatorSmartsortMQTT.getsInstance().sendToGlass(fixedScanResult);

                            }


                            uiUpdater.setRouteNumber(RPMCursor.getString(RPMCursor.getColumnIndex("RouteNumber")));
                            uiUpdater.setShelfNumber(shelfNumber);
                            uiUpdater.setDeliverySequence(RPMCursor.getString(RPMCursor.getColumnIndex("DeliverySequenceID")));
                            uiUpdater.setPrimarySort("");
                            uiUpdater.setSideofBelt("");
                            uiUpdater.setPostalCode(Utilities.getPostalCode(barcode));
                            uiUpdater.setScannedCode(barcode);
                            if (municipality.isEmpty())
                                municipality = RPMCursor.getString(RPMCursor.getColumnIndex("MunicipalityName"));
                            uiUpdater.setPinCode(address + " " + streetNo + " " + postalCode + " " + municipality);

                            EventBus.getDefault().post(uiUpdater);


                            logger.setDevice_id(PurolatorSmartsortMQTT.getsInstance().getConfigData().getDeviceName());
                            logger.setUserCode(PurolatorSmartsortMQTT.getsInstance().getConfigData().getUserId());
                            logger.setTerminalID(PurolatorSmartsortMQTT.getsInstance().getConfigData().getTerminalID());
                            logger.setScannedCode(barcode);
                            logger.setBarcodeType("RPM-AddRange-AddrLookup");
                            logger.setShelfOverride(RPMCursor.getString(RPMCursor.getColumnIndex("TruckShelfOverride")));
                            logger.setRouteNumber(RPMCursor.getString(RPMCursor.getColumnIndex("RouteNumber")));
                            logger.setShelfNumber(RPMCursor.getString(RPMCursor.getColumnIndex("ShelfNumber")));
                            logger.setDeliverySequence(RPMCursor.getString(RPMCursor.getColumnIndex("DeliverySequenceID")));


                            EventBus.getDefault().post(logger);
                        } else if (PurolatorSmartsortMQTT.getsInstance().getConfigData().getDeviceType().equals("GLASS")) {
                            uiUpdater.setUpdateType(PurolatorSmartsortMQTT.UPD_BOTTOMSCREEN);
                            uiUpdater.setRouteNumber(RPMCursor.getString(RPMCursor.getColumnIndex("RouteNumber")));
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
                            if (municipality.isEmpty())
                                municipality = RPMCursor.getString(RPMCursor.getColumnIndex("MunicipalityName"));
                            uiUpdater.setPinCode(address + " " + streetNo + " " + postalCode + " " + municipality);

                            uiUpdater.setCorrectBay(false);

                            EventBus.getDefault().post(uiUpdater);


                        }
                        RPMCursor.close();
                        PurolatorSmartsortMQTT.getsInstance().setTransactionActive(PurolatorSmartsortMQTT.RPM_DBTYPE, false);


                        return true;
                    }
                } while (RPMCursor.moveToNext());
            }


        }

//        }

        RPMCursor.close();
        PurolatorSmartsortMQTT.getsInstance().setTransactionActive(PurolatorSmartsortMQTT.RPM_DBTYPE,false);
        return false;


    }
}
