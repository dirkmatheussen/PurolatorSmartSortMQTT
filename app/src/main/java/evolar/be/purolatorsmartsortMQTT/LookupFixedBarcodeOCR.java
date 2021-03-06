package evolar.be.purolatorsmartsortMQTT;

import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.provider.Contacts;
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
 * Do a lookup of the OCR'erd parcels, coming from remediation
 *
 */
public class LookupFixedBarcodeOCR {

    private static final String TAG = "LookupFixedBarcodeOCR";
    private static final boolean D = true;



    public LookupFixedBarcodeOCR(){
        EventBus.getDefault().register(this);
    }

    @Subscribe(priority = 10)    //highest priority
    public void onFixedBarcodeScanEvent(FixedBarcodeScan event){

        if (D) Log.d(TAG,"In event");
        //first check if correct barcode type is scanned

        if(event.getBarcodeType().equals(PurolatorSmartsortMQTT.RPM_DBTYPE)){
            return;
        }

        //if PIN found, cancel Delivery to other classes with lower priority


        if (pinFound(Utilities.getPINCode(event.getBarcodeResult()),event.getScanDate())) {
            EventBus.getDefault().cancelEventDelivery(event);
            //scan is valid, now check if this is the package, scanned to put into a shelf
            if (PurolatorSmartsortMQTT.getsInstance().getPackageInShelf() !=null
                    && PurolatorSmartsortMQTT.getsInstance().getPackageInShelf().getScannedCode().equals(event.getBarcodeResult())){

                UIUpdater uiUpdater = new UIUpdater();
                uiUpdater.setUpdateType(PurolatorSmartsortMQTT.UPD_ERROR);
                uiUpdater.setErrorMessage("Scan the route/shelf code");
                EventBus.getDefault().post(uiUpdater);

            } else{
                //scan is valid, add in the list --> not needed for the fixed scanner
//                PurolatorSmartsortMQTT.packagesList.getBarcodeScans().add(event.getBarcodeResult());
            }
        }
    }





    /**
     * Find the scanned barcode in the PINFile
     *
     * @param pincode : the pincode
     * @return
     */
    private boolean pinFound(String pincode, Date scanDate) {


        for (OCRData ocrData:PurolatorSmartsortMQTT.getsInstance().ocrDatas){

            if (ocrData.getLookupBarcode().equals(pincode)){
                if(D) Log.d(TAG,"PinCode in OCRFound: " + pincode);

                if (RPM2DFound(ocrData.getOcrResult(),ocrData.getScannedBarcode(), scanDate)){
                    PurolatorSmartsortMQTT.getsInstance().ocrDatas.remove(ocrData);
                    return true;
                }
                PurolatorSmartsortMQTT.getsInstance().ocrDatas.remove(ocrData);     //remove it anyway, even if not found in database
            }

        }

        return false;


    }

    /**
     * Find a Route based on the address in the barcode
     * Identical as in LookupFixedBarcodeRPM
     *
     * @param barcode
     * @return
     */

    private boolean RPM2DFound( String barcode, String scannedBarcode, Date scanDate){


        //check if there is a \ and replace by |
        barcode = barcode.replace("\\","|");

        String[] parsed = barcode.split("[|]");
        String streetNo = "-1";
        String unit = "-1";
        String address = "";
        String municipality =  "";
        String postalCode = "";
        String pincode = Utilities.getPINCode(barcode);
        String routeNumber ="";


        for (String aParsed : parsed) {
            String[] valuePair = aParsed.split("[~]");

            if (valuePair.length >= 2) {

                //TODO Check is address is filled in correctly in 2D Code R02/R03
                switch (valuePair[0].toUpperCase()) {
                    case "R02":
                        unit = valuePair[1].toUpperCase();
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
                    case "S16":
                        routeNumber = valuePair[1].toUpperCase();
                        break;
                }
            }
        }

        if (routeNumber.length() > 1){
            if(D) Log.d(TAG,"Found in OCR, ROutenumber ");

            if (PurolatorSmartsortMQTT.getsInstance().getConfigData().getDeviceType().equals("FIXED")) {


                final FixedScanResult fixedScanResult = new FixedScanResult();
                fixedScanResult.setRouteNumber(routeNumber);
                fixedScanResult.setPostalCode(postalCode);
                fixedScanResult.setScannedCode(scannedBarcode);
                String readablePincode = pincode;
                if (pincode.length() > 3) readablePincode = pincode.substring(pincode.length() - 4);

                fixedScanResult.setPinCode(readablePincode + " " + postalCode);
                fixedScanResult.setScanDate(scanDate);
                fixedScanResult.setGlassTarget("WAVE");
                fixedScanResult.setDimensionData("");
                fixedScanResult.setStreetname(address);  //Streettype is included
                fixedScanResult.setStreetnumber(streetNo);
                fixedScanResult.setMunicipality(municipality);
                fixedScanResult.setStreetunit(unit);
                fixedScanResult.setShelfNumber("");

                EventBus.getDefault().post(fixedScanResult);

                if (!PurolatorSmartsortMQTT.getsInstance().getConfigData().isWaveValidation()) {
                    PurolatorSmartsortMQTT.getsInstance().sendToGlass(fixedScanResult);

                }
                UIUpdater uiUpdater = new UIUpdater();


                uiUpdater.setRouteNumber(routeNumber);
                uiUpdater.setPrimarySort("");
                uiUpdater.setSideofBelt("");
                uiUpdater.setPostalCode(Utilities.getPostalCode(barcode));
                uiUpdater.setScannedCode(barcode); // must be corrected into Scanned Barcode
                uiUpdater.setScannedCode(scannedBarcode); // must be corrected into Scanned Barcode
                uiUpdater.setRemediationId(pincode);

                uiUpdater.setPinCode(readablePincode + " " + postalCode);
                EventBus.getDefault().post(uiUpdater);


                Logger logger = new Logger();
                logger.setDevice_id(PurolatorSmartsortMQTT.getsInstance().getConfigData().getDeviceName());
                logger.setUserCode(PurolatorSmartsortMQTT.getsInstance().getConfigData().getUserId());
                logger.setTerminalID(PurolatorSmartsortMQTT.getsInstance().getConfigData().getTerminalID());
                logger.setScannedCode(scannedBarcode);
                logger.setPinCode(pincode);

                logger.setRouteNumber(routeNumber);
                logger.setPostalCode(Utilities.getPostalCode(barcode));
                logger.setBarcodeType("OCR-Route");

                if (Utilities.getBarcodeLogType(barcode).equals("2D")) {
                    parsed = barcode.split("[|]");
                    for (String aParsed : parsed) {
                        String[] valuePair = aParsed.split("[~]");

                        if (valuePair.length >= 2) {


                            switch (valuePair[0].toUpperCase()) {
                                case "RO1":
                                    logger.setCustomerName(valuePair[1]);
                                    break;
                                case "R02":
                                    logger.setUnitNumber(valuePair[1]);

                                case "R03":             //street n#
                                    logger.setStreetNumber(valuePair[1]);
                                    break;
                                case "R04":             //address
                                    String[] parsedAddress = Utilities.parseAddress(valuePair[1]);
                                    logger.setStreetName(parsedAddress[1]);
                                    logger.setStreetType(parsedAddress[2]);
                                    break;
                                case "R05":             //address

                                    break;
                                case "R06":             //municipality
                                    logger.setMunicipalityName(valuePair[1]);
                                    break;
                                case "R07":             //postal code
                                    logger.setPostalCode(valuePair[1]);
                                    break;
                                case "S02":
                                    logger.setPinCode(valuePair[1]);
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
                EventBus.getDefault().post(logger);

            } else if (PurolatorSmartsortMQTT.getsInstance().getConfigData().getDeviceType().equals("GLASS")) {


            }
            return true;
        }
    //TODO CHECK IF THIS IS NEEDED
        //do additional parsing

        String[] selectColumns = {"RouteNumber", "ShelfNumber","MunicipalityName","AddressRecordType","TruckShelfOverride","DeliverySequenceID"};

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

                    if (D) Log.d(TAG, "Found in OCR, with UniqueAdress!");
                    if (PurolatorSmartsortMQTT.getsInstance().getConfigData().getDeviceType().equals("FIXED")) {


                        final FixedScanResult fixedScanResult = new FixedScanResult();
                        fixedScanResult.setRouteNumber(RPMCursor.getString(RPMCursor.getColumnIndex("RouteNumber")));
                        String shelfNumber = RPMCursor.getString(RPMCursor.getColumnIndex("TruckShelfOverride"));
                        if (shelfNumber.isEmpty()) {
                            shelfNumber = RPMCursor.getString(RPMCursor.getColumnIndex("ShelfNumber"));
                        }
                        fixedScanResult.setShelfNumber(shelfNumber);

                        fixedScanResult.setPostalCode(postalCode);
                        fixedScanResult.setScannedCode(scannedBarcode);
                        if (municipality.isEmpty())
                            municipality = RPMCursor.getString(RPMCursor.getColumnIndex("MunicipalityName"));
//                    fixedScanResult.setPinCode(address + " " + streetNo + " " + postalCode + " " + municipality);
                        String readablePincode = pincode;
                        if (pincode.length() > 3)
                            readablePincode = pincode.substring(pincode.length() - 4);

                        fixedScanResult.setPinCode(readablePincode + " " + postalCode);
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
                        uiUpdater.setScannedCode(barcode); // must be corrected into Scanned Barcode
                        uiUpdater.setScannedCode(scannedBarcode); // must be corrected into Scanned Barcode
                        uiUpdater.setRemediationId(pincode);

                        if (municipality.isEmpty())
                            municipality = RPMCursor.getString(RPMCursor.getColumnIndex("MunicipalityName"));

                        uiUpdater.setPinCode(readablePincode + " " + postalCode);
                        EventBus.getDefault().post(uiUpdater);


                        Logger logger = new Logger();
                        logger.setDevice_id(PurolatorSmartsortMQTT.getsInstance().getConfigData().getDeviceName());
                        logger.setUserCode(PurolatorSmartsortMQTT.getsInstance().getConfigData().getUserId());
                        logger.setTerminalID(PurolatorSmartsortMQTT.getsInstance().getConfigData().getTerminalID());
                        logger.setScannedCode(scannedBarcode);
                        logger.setPinCode(pincode);

                        logger.setShelfOverride(RPMCursor.getString(RPMCursor.getColumnIndex("TruckShelfOverride")));
                        logger.setRouteNumber(RPMCursor.getString(RPMCursor.getColumnIndex("RouteNumber")));
                        logger.setShelfNumber(RPMCursor.getString(RPMCursor.getColumnIndex("ShelfNumber")));
                        logger.setDeliverySequence(RPMCursor.getString(RPMCursor.getColumnIndex("DeliverySequenceID")));
                        logger.setPostalCode(Utilities.getPostalCode(barcode));
                        logger.setBarcodeType("OCR");

                        if (Utilities.getBarcodeLogType(barcode).equals("2D")) {
                            parsed = barcode.split("[|]");
                            for (String aParsed : parsed) {
                                String[] valuePair = aParsed.split("[~]");

                                if (valuePair.length >= 2) {


                                    switch (valuePair[0].toUpperCase()) {
                                        case "RO1":
                                            logger.setCustomerName(valuePair[1]);
                                            break;
                                        case "R02":
                                            logger.setUnitNumber(valuePair[1]);
                                            break;
                                        case "R03":             //street n#
                                            logger.setStreetNumber(valuePair[1]);
                                            break;
                                        case "R04":             //address
                                            parsedAddress = Utilities.parseAddress(valuePair[1]);
                                            logger.setStreetName(parsedAddress[1]);
                                            logger.setStreetType(parsedAddress[2]);
                                            break;
                                        case "R05":             //address

                                            break;
                                        case "R06":             //municipality
                                            logger.setMunicipalityName(valuePair[1]);
                                            break;
                                        case "R07":             //postal code
                                            logger.setPostalCode(valuePair[1]);
                                            break;
                                        case "S02":
                                            logger.setPinCode(valuePair[1]);
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
                        EventBus.getDefault().post(logger);


                    } else if (PurolatorSmartsortMQTT.getsInstance().getConfigData().getDeviceType().equals("GLASS")) {



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

                if (addressRecordType.startsWith("AddressRang")) {

                    if (D) Log.d(TAG, "Found in OCR, With AddressRange");
                    if (PurolatorSmartsortMQTT.getsInstance().getConfigData().getDeviceType().equals("FIXED")) {

                        FixedScanResult fixedScanResult = new FixedScanResult();
                        fixedScanResult.setRouteNumber(RPMCursor.getString(RPMCursor.getColumnIndex("RouteNumber")));
                        String shelfNumber = RPMCursor.getString(RPMCursor.getColumnIndex("TruckShelfOverride"));
                        if (shelfNumber == null || shelfNumber.isEmpty()) {
                            shelfNumber = RPMCursor.getString(RPMCursor.getColumnIndex("ShelfNumber"));
                        }
                        fixedScanResult.setShelfNumber(shelfNumber);

                        fixedScanResult.setPostalCode(postalCode);
                        fixedScanResult.setScannedCode(scannedBarcode);
                        if (municipality.isEmpty())
                            municipality = RPMCursor.getString(RPMCursor.getColumnIndex("MunicipalityName"));
//                    fixedScanResult.setPinCode(address + " " + streetNo + " " + postalCode + " " + municipality);
                        String readablePincode = pincode;
                        if (pincode.length() > 3)
                            readablePincode = pincode.substring(pincode.length() - 4);

                        fixedScanResult.setPinCode(readablePincode + " " + postalCode);
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
                        uiUpdater.setScannedCode(scannedBarcode);      //--> must be replaced by scannedBarcode
                        if (municipality == null || municipality.isEmpty())
                            municipality = RPMCursor.getString(RPMCursor.getColumnIndex("MunicipalityName"));
                        uiUpdater.setPinCode(readablePincode + " " + postalCode);
                        uiUpdater.setRemediationId(pincode);
                        EventBus.getDefault().post(uiUpdater);

                        Logger logger = new Logger();
                        logger.setDevice_id(PurolatorSmartsortMQTT.getsInstance().getConfigData().getDeviceName());
                        logger.setUserCode(PurolatorSmartsortMQTT.getsInstance().getConfigData().getUserId());
                        logger.setTerminalID(PurolatorSmartsortMQTT.getsInstance().getConfigData().getTerminalID());
                        logger.setScannedCode(scannedBarcode);
                        logger.setPinCode(pincode);
                        logger.setBarcodeType("OCR");
                        logger.setShelfOverride(RPMCursor.getString(RPMCursor.getColumnIndex("TruckShelfOverride")));
                        logger.setRouteNumber(RPMCursor.getString(RPMCursor.getColumnIndex("RouteNumber")));
                        logger.setShelfNumber(RPMCursor.getString(RPMCursor.getColumnIndex("ShelfNumber")));
                        logger.setDeliverySequence(RPMCursor.getString(RPMCursor.getColumnIndex("DeliverySequenceID")));
                        logger.setPostalCode(Utilities.getPostalCode(barcode));

                        if (Utilities.getBarcodeLogType(barcode).equals("2D")) {
                            parsed = barcode.split("[|]");
                            for (String aParsed : parsed) {
                                String[] valuePair = aParsed.split("[~]");

                                if (valuePair.length >= 2) {


                                    switch (valuePair[0].toUpperCase()) {
                                        case "RO1":
                                            logger.setCustomerName(valuePair[1]);
                                            break;
                                        case "R02":
                                            logger.setUnitNumber(valuePair[1]);
                                            break;
                                        case "R03":             //street n#
                                            logger.setStreetNumber(valuePair[1]);
                                            break;
                                        case "R04":             //address
                                            parsedAddress = Utilities.parseAddress(valuePair[1]);
                                            logger.setStreetName(parsedAddress[1]);
                                            logger.setStreetType(parsedAddress[2]);
                                            break;
                                        case "R05":             //address

                                            break;
                                        case "R06":             //municipality
                                            logger.setMunicipalityName(valuePair[1]);
                                            break;
                                        case "R07":             //postal code
                                            logger.setPostalCode(valuePair[1]);
                                            break;
                                        case "S02":
                                            logger.setPinCode(valuePair[1]);
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
                        EventBus.getDefault().post(logger);


                    } else if (PurolatorSmartsortMQTT.getsInstance().getConfigData().getDeviceType().equals("GLASS")) {


                    }
                    RPMCursor.close();
                    PurolatorSmartsortMQTT.getsInstance().setTransactionActive(PurolatorSmartsortMQTT.RPM_DBTYPE, false);

                    return true;
                }
            } while (RPMCursor.moveToNext());



        }

//        }

        RPMCursor.close();
        PurolatorSmartsortMQTT.getsInstance().setTransactionActive(PurolatorSmartsortMQTT.RPM_DBTYPE,false);
        return false;


    }

}
