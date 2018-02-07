package evolar.be.purolatorsmartsortMQTT;

import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.util.Log;

import org.greenrobot.eventbus.EventBus;
import org.greenrobot.eventbus.Subscribe;

import java.util.Date;

import evolar.be.purolatorsmartsortMQTT.events.FixedBarcodeScan;
import evolar.be.purolatorsmartsortMQTT.events.FixedScanResult;
import evolar.be.purolatorsmartsortMQTT.events.Logger;
import evolar.be.purolatorsmartsortMQTT.events.UIUpdater;


/**
 * Created by Dirk on 05/02/16.
 * This is the first lookup after the OCR lookup.
 * Lookup in the PST Database to find new edited addresses (after remediation)
 * Used by the wrist devices
 *
 */
public class LookupFixedBarcodeADDR {

    private static final String TAG = "LookupFixedBarcodeADDR";
    private static final boolean D = true;

    private SQLiteDatabase database;


    public LookupFixedBarcodeADDR(){
        EventBus.getDefault().register(this);

    }


    @Subscribe(priority = 9)    //second highest priority
    public void onFixedBarcodeScanEvent(FixedBarcodeScan event){

        if (D) Log.d(TAG,"In event");

        //if Routeplan found, cancel Delivery to other classes with lower priority
        if (ADDRFound(Utilities.getPostalCode(event.getBarcodeResult()),event.getBarcodeResult(),event.getScanDate(),event.getDimensionData())) {
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

    private boolean ADDRFound(String postalCode, String barcode,Date scanDate, String dimensionData) {

        String[] selectColumns = {"PostalCode","Route","PIN","StreetName","StreetType","StreetNumber","UnitNumber","MunicipalityName","Addressee","ShelfNumber","DeliverySequenceID"};


        SQLiteDatabase database = PurolatorSmartsortMQTT.getsInstance().getDatabase(PurolatorSmartsortMQTT.PST_DBTYPE);



        if (D) Log.d(TAG,"Query parameters; Barcode: " + barcode);

        if (database == null){
            return false;
        }

        if (barcode == null){
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

        // check for PINcode
        Cursor PSTCursor = database.query("PostalRoute", selectColumns, "PIN=?", new String[]{barcode}, null, null, null, null);


        UIUpdater uiUpdater = new UIUpdater();


        if (D) Log.d(TAG,"QueryReturn for PIN: " + PSTCursor.toString());
        // get the found data and post en event.
        if (PSTCursor.moveToFirst()) {

            //first check if route is assigned to the preloader
            //           if (PurolatorSmartsortMQTT.getsInstance().isRouteValid(PINCursor.getString(PINCursor.getColumnIndex("RouteNumber")))) {
            if (PurolatorSmartsortMQTT.getsInstance().getConfigData().getDeviceType().equals("FIXED")){

                if (D) Log.d(TAG,"Found in PST!!!!");
                final FixedScanResult fixedScanResult = new FixedScanResult();

                fixedScanResult.setRouteNumber(PSTCursor.getString(PSTCursor.getColumnIndex("Route")));
                fixedScanResult.setShelfNumber(PSTCursor.getString(PSTCursor.getColumnIndex("ShelfNumber")));
                fixedScanResult.setDeliverySequence(PSTCursor.getString(PSTCursor.getColumnIndex("DeliverySequenceID")));
                fixedScanResult.setPrimarySort("");
                fixedScanResult.setSideofBelt("");
                fixedScanResult.setPostalCode(Utilities.getPostalCode(barcode));
                fixedScanResult.setMunicipality(PSTCursor.getString(PSTCursor.getColumnIndex("MunicipalityName")));
                fixedScanResult.setStreetname(PSTCursor.getString(PSTCursor.getColumnIndex("StreetName")));
                fixedScanResult.setStreetnumber(PSTCursor.getString(PSTCursor.getColumnIndex("StreetNumber")));
                fixedScanResult.setStreetunit(PSTCursor.getString(PSTCursor.getColumnIndex("UnitNumber")) );
                fixedScanResult.setAddressee(PSTCursor.getString(PSTCursor.getColumnIndex("Addressee")) );



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
                logger.setStreetName(fixedScanResult.getStreetname());
                logger.setStreetNumber(fixedScanResult.getStreetnumber());
                logger.setStreetType(fixedScanResult.getStreettype());
                logger.setUnitNumber(fixedScanResult.getStreetunit());
                logger.setMunicipalityName(fixedScanResult.getMunicipality());



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
                uiUpdater.setMunicipality(PSTCursor.getString(PSTCursor.getColumnIndex("MunicipalityName")));
                uiUpdater.setStreetname(PSTCursor.getString(PSTCursor.getColumnIndex("StreetName")));
                uiUpdater.setStreetnumber(PSTCursor.getString(PSTCursor.getColumnIndex("StreetNumber")));
                uiUpdater.setStreetunit(PSTCursor.getString(PSTCursor.getColumnIndex("UnitNumber")) );
                uiUpdater.setAddressee(PSTCursor.getString(PSTCursor.getColumnIndex("Addressee")) );

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
