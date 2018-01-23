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
 * Do a lookup in the PIN File
 *
 */
public class LookupFixedBarcodePIN {

    private static final String TAG = "LookupFixedBarcodePIN";
    private static final boolean D = true;



    public LookupFixedBarcodePIN(){
        EventBus.getDefault().register(this);
    }

    @Subscribe(priority = 8)    //2nd highest priority
    public void onFixedBarcodeScanEvent(FixedBarcodeScan event){

        if (D) Log.d(TAG,"In event");
        //first check if correct barcode type is scanned

        if(event.getBarcodeType().equals(PurolatorSmartsortMQTT.RPM_DBTYPE)){
            return;
        }

        //if PIN found, cancel Delivery to other classes with lower priority

       if (pinFound(Utilities.getPINCode(event.getBarcodeResult()),event.getBarcodeResult(),event.getScanDate(),event.getDimensionData())) {
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
     * @param barcode : the original scanned barcode
     * @return
     */
    private boolean pinFound(String pincode, String barcode, Date scanDate, String dimensionData) {

        String[] selectColumns = {"RouteNumber", "ShelfNumber", "TruckShelfOverride","DeliverySequenceID", "PrimarySort", "SideofBelt","PIN"};

        if(D) Log.d(TAG,"PinCode in pinFound: " + pincode);

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

        PurolatorSmartsortMQTT.getsInstance().setTransactionActive(PurolatorSmartsortMQTT.PIN_DBTYPE,true);

        Cursor PINCursor = database.query("PIN", selectColumns, "PIN=?", new String[]{pincode}, null, null, null, null);


        if (D) Log.d(TAG,"QueryReturn: " + PINCursor.toString());

        UIUpdater uiUpdater = new UIUpdater();

        // get the found data and post en event.
        if (PINCursor.moveToFirst()) {

            //first check if route is assigned to the preloader
 //           if (PurolatorSmartsortMQTT.getsInstance().isRouteValid(PINCursor.getString(PINCursor.getColumnIndex("RouteNumber")))) {
                if (PurolatorSmartsortMQTT.getsInstance().getConfigData().getDeviceType().equals("FIXED")){

                    if (D) Log.d(TAG,"Found in PIN!!!!");
                    final FixedScanResult fixedScanResult = new FixedScanResult();
                    Logger logger = new Logger();




                    if (Utilities.getBarcodeLogType(barcode).equals("2D")){

                        //check if there is a \ and replace by |
                        barcode = barcode.replace("\\","|");

                        String[] parsed = barcode.split("[|]");
                        for (String aParsed : parsed) {
                            String[] valuePair = aParsed.split("[~]");

                            if (valuePair.length >= 2) {


                                switch (valuePair[0].toUpperCase()) {
                                    case "RO1":
                                        logger.setCustomerName(valuePair[1]);
                                        fixedScanResult.setAddressee(valuePair[1]);

                                    case "R03":             //street n#
                                        logger.setStreetNumber(valuePair[1]);
                                        fixedScanResult.setStreetnumber(valuePair[1]);
                                        break;
                                    case "R04":             //address
                                        String parsedAddress[] = Utilities.parseAddress(valuePair[1]);
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


                    fixedScanResult.setRouteNumber(PINCursor.getString(PINCursor.getColumnIndex("RouteNumber")));
                    String shelfNumber = PINCursor.getString(PINCursor.getColumnIndex("TruckShelfOverride"));
                    if (shelfNumber.isEmpty()){
                        shelfNumber = PINCursor.getString(PINCursor.getColumnIndex("ShelfNumber"));
                    }
                    fixedScanResult.setShelfNumber(shelfNumber);
                    fixedScanResult.setDeliverySequence(PINCursor.getString(PINCursor.getColumnIndex("DeliverySequenceID")));
                    fixedScanResult.setPrimarySort(PINCursor.getString(PINCursor.getColumnIndex("PrimarySort")));
                    fixedScanResult.setSideofBelt(PINCursor.getString(PINCursor.getColumnIndex("SideofBelt")));
                    fixedScanResult.setPostalCode(Utilities.getPostalCode(barcode));
                    String readablePinCode =PINCursor.getString(PINCursor.getColumnIndex("PIN"));
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

                    uiUpdater.setRouteNumber(PINCursor.getString(PINCursor.getColumnIndex("RouteNumber")));
                    uiUpdater.setShelfNumber(shelfNumber);
                    uiUpdater.setDeliverySequence(PINCursor.getString(PINCursor.getColumnIndex("DeliverySequenceID")));
                    uiUpdater.setPrimarySort(PINCursor.getString(PINCursor.getColumnIndex("PrimarySort")));
                    uiUpdater.setSideofBelt(PINCursor.getString(PINCursor.getColumnIndex("SideofBelt")));
                    uiUpdater.setPinCode(PINCursor.getString(PINCursor.getColumnIndex("PIN"))+ " " + (fixedScanResult.getPostalCode() == null? " ":fixedScanResult.getPostalCode()));
                    uiUpdater.setScannedCode(barcode);
                    EventBus.getDefault().post(uiUpdater);

                    logger.setDevice_id(PurolatorSmartsortMQTT.getsInstance().getConfigData().getDeviceName());
                    logger.setUserCode(PurolatorSmartsortMQTT.getsInstance().getConfigData().getUserId());
                    logger.setTerminalID(PurolatorSmartsortMQTT.getsInstance().getConfigData().getTerminalID());
                    logger.setScannedCode(barcode);
                    logger.setBarcodeType("PIN");
                    logger.setPinCode(PINCursor.getString(PINCursor.getColumnIndex("PIN")));
                    logger.setPostalCode(Utilities.getPostalCode(barcode));

                    logger.setPrimarySort(PINCursor.getString(PINCursor.getColumnIndex("PrimarySort")));
                    logger.setSideofBelt(PINCursor.getString(PINCursor.getColumnIndex("SideofBelt")));
                    logger.setRouteNumber(PINCursor.getString(PINCursor.getColumnIndex("RouteNumber")));
                    logger.setShelfNumber(PINCursor.getString(PINCursor.getColumnIndex("ShelfNumber")));
                    logger.setShelfOverride(PINCursor.getString(PINCursor.getColumnIndex("TruckShelfOverride")));
                    logger.setDeliverySequence(PINCursor.getString(PINCursor.getColumnIndex("DeliverySequenceID")));


                    if (Utilities.getBarcodeLogType(barcode).equals("NGB")){
                        logger.setDeliveryTime(barcode.substring(26,28));
                        logger.setShipmentType(barcode.substring(28,29));
                        logger.setDeliveryType(barcode.substring(29,30));
                        logger.setDiversionCode(barcode.substring(30,31));
                    }

                    EventBus.getDefault().post(logger);
                } else if (PurolatorSmartsortMQTT.getsInstance().getConfigData().getDeviceType().equals("GLASS")) {
                    uiUpdater.setUpdateType(PurolatorSmartsortMQTT.UPD_BOTTOMSCREEN);
                    uiUpdater.setRouteNumber(PINCursor.getString(PINCursor.getColumnIndex("RouteNumber")));
                    String shelfNumber = PINCursor.getString(PINCursor.getColumnIndex("TruckShelfOverride"));
                    if (shelfNumber.isEmpty()){
                        shelfNumber = PINCursor.getString(PINCursor.getColumnIndex("ShelfNumber"));
                    }
                    uiUpdater.setShelfNumber(shelfNumber);
                    uiUpdater.setDeliverySequence(PINCursor.getString(PINCursor.getColumnIndex("DeliverySequenceID")));
                    uiUpdater.setPrimarySort(PINCursor.getString(PINCursor.getColumnIndex("PrimarySort")));
                    uiUpdater.setSideofBelt(PINCursor.getString(PINCursor.getColumnIndex("SideofBelt")));
                    uiUpdater.setPinCode(PINCursor.getString(PINCursor.getColumnIndex("PIN"))+ " " + (Utilities.getPostalCode(barcode) == null? " ":Utilities.getPostalCode(barcode)));
                    uiUpdater.setScannedCode(barcode);
                    uiUpdater.setPostalCode(Utilities.getPostalCode(barcode));
                    uiUpdater.setCorrectBay(false);
                    EventBus.getDefault().post(uiUpdater);

                }
                PINCursor.close();
                PurolatorSmartsortMQTT.getsInstance().setTransactionActive(PurolatorSmartsortMQTT.PIN_DBTYPE,false);

                return true;


 //           }

        }


        PINCursor.close();
        PurolatorSmartsortMQTT.getsInstance().setTransactionActive(PurolatorSmartsortMQTT.PIN_DBTYPE,false);

        return false;

    }


}
