package evolar.be.purolatorsmartsortMQTT;

import android.util.Log;

import org.greenrobot.eventbus.EventBus;
import org.greenrobot.eventbus.Subscribe;

import java.util.ArrayList;
import java.util.Date;

import evolar.be.purolatorsmartsortMQTT.events.FixedBarcodeScan;
import evolar.be.purolatorsmartsortMQTT.events.GlassMessage;
import evolar.be.purolatorsmartsortMQTT.events.RingBarcodeScan;
import evolar.be.purolatorsmartsortMQTT.events.UIUpdater;

/**
 * Created by Dirk on 17/03/16.
 *  Handle the Ringbarcode: 2 options
 *  PIN code is scanned or Shelf code is scanned
 *
 */
public class HandleRingBarcode {

    private static final String TAG = "HandleRingBarcode";
    private static final boolean D = true;

    private String shelfBarcode = "";

    public HandleRingBarcode(){
        EventBus.getDefault().register(this);
    }

    @Subscribe(priority = 0)    //default priority
    public void onRingBarcodeScanEvent(RingBarcodeScan event){

        if (D) Log.d(TAG,"In event ");

        //check if it is a clear barcode
        if (event.getBarcodeResult().equals("CLEAR")) {

            //clear shelf information
            UIUpdater uiUpdater;
            uiUpdater = new UIUpdater();
            uiUpdater.setUpdateType(PurolatorSmartsortMQTT.UPD_CLEARSCREEN);
            uiUpdater.setScannedCode(null);

            EventBus.getDefault().post(uiUpdater);

            return;

        }
        //first check if the scanned value is a shelf/truck value

        if (Utilities.isShelfScan(event.getBarcodeResult())) {
            UIUpdater uiUpdater;

            if (PurolatorSmartsortMQTT.getsInstance().getPackageInShelf() != null && PurolatorSmartsortMQTT.getsInstance().getPackageInShelf().getShelfNumber().equals(event.getBarcodeResult())) {
                if(D) Log.d(TAG,"Correct shelf scanned");
                uiUpdater = new UIUpdater();
                uiUpdater.setErrorMessage(PurolatorSmartsortMQTT.getsInstance().getPackageInShelf().getRouteNumber()+" Shelf: " + PurolatorSmartsortMQTT.getsInstance().getPackageInShelf().getShelfNumber()+" ");            //show the scanned package information
                uiUpdater.setUpdateType(PurolatorSmartsortMQTT.UPD_BOTTOMSCREEN);
                //empty the screen & remove from PackageInShelf
                EventBus.getDefault().post(uiUpdater);
                return;
            }

            //confirmation scan of a shelf code when a different shelf is scanned.

            if (PurolatorSmartsortMQTT.getsInstance().getPackageInShelf() != null && shelfBarcode.equals(event.getBarcodeResult())) {
                if(D) Log.d(TAG,"Wrong shelf scanned -  scan confirmed");
                shelfBarcode = "";
                uiUpdater = new UIUpdater();
                uiUpdater.setErrorMessage(PurolatorSmartsortMQTT.getsInstance().getPackageInShelf().getRouteNumber()+" Shelf: " + event.getBarcodeResult()+" ");            //show the scanned package information
                uiUpdater.setUpdateType(PurolatorSmartsortMQTT.UPD_BOTTOMSCREEN);
                //empty the screen & remove from PackageInShelf
                EventBus.getDefault().post(uiUpdater);
                return;
            }



            //another shelf is scanned, ask for a confirmation scan
            if (PurolatorSmartsortMQTT.getsInstance().getPackageInShelf() != null && !PurolatorSmartsortMQTT.getsInstance().getPackageInShelf().getShelfNumber().equals(event.getBarcodeResult())) {
                if(D) Log.d(TAG,"Wrong shelf scanned -  confirmation scan is needed");

                uiUpdater = new UIUpdater();
                uiUpdater.setUpdateType(PurolatorSmartsortMQTT.UPD_ERROR);
                uiUpdater.setErrorMessage("You scanned: "+ event.getBarcodeResult()+"\nConfirm the different shelf scan");
                shelfBarcode = event.getBarcodeResult();
                EventBus.getDefault().post(uiUpdater);

                return;
            }
        }

        // otherwise check if it is package scanned to put into a shelf
        //check if it is a 2D Scan or an NGB Scan, use the PIN code as BarcodeScan.  --> this logic is also applied in HandleRingBarcode
        String barcodeType = Utilities.getBarcodeLogType(event.getBarcodeResult());
        String barcodeResult;
        if (barcodeType.equals(("2D"))|| barcodeType.equals("NGB")){
            barcodeResult = Utilities.getPINCode(event.getBarcodeResult());
        } else {
            barcodeResult = event.getBarcodeResult();
        }


        for (String barcodeScan: PurolatorSmartsortMQTT.packagesList.getBarcodeScans()){

//            Log.d(TAG,"PackageList: " + barcodeScan + " scanned code: "+barcodeResult);
            if (barcodeScan.equalsIgnoreCase(barcodeResult)){

                UIUpdater uiUpdater;


                if (PurolatorSmartsortMQTT.getsInstance().getPackageInShelf()!=null) {

                    // current package is not yet dropped --> put it back on the list en then put new scanned package on the shelf line


                    uiUpdater = new UIUpdater();
                    uiUpdater.setUpdateType(PurolatorSmartsortMQTT.UPD_ERROR);
                    uiUpdater.setScannedCode(PurolatorSmartsortMQTT.getsInstance().getPackageInShelf().getScannedCode());
                    uiUpdater.setErrorCode("SWITCHTOLIST");

                    EventBus.getDefault().post(uiUpdater);
                    //add back to the list of barcodescans
                    //check if it is a 2D Scan or an NGB Scan, use the PIN code as BarcodeScan.
                    barcodeType = Utilities.getBarcodeLogType(PurolatorSmartsortMQTT.getsInstance().getPackageInShelf().getScannedCode());
                    String barcode;
                    if (barcodeType.equals(("2D"))|| barcodeType.equals("NGB")){
                        barcode = Utilities.getPINCode(PurolatorSmartsortMQTT.getsInstance().getPackageInShelf().getScannedCode());
                    } else {
                        barcode = PurolatorSmartsortMQTT.getsInstance().getPackageInShelf().getScannedCode();
                    }
                    PurolatorSmartsortMQTT.packagesList.getBarcodeScans().add(barcode);

                    shelfBarcode = "";
                    
                    /**
                    uiUpdater.setUpdateType(PurolatorSmartsortMQTT.UPD_ERROR);
                    uiUpdater.setErrorMessage("Drop current package in a shelf");
                    **/


                } else {
/*
                    GlassMessage glassMessage = new GlassMessage();
                    glassMessage.setMessage("INSHELF");
                    glassMessage.setBarcode(event.getBarcodeResult());
                    glassMessage.setFixedTarget(Utilities.makeTargetName("FIXED", "FIXED"));
                    glassMessage.setGlassName(PurolatorSmartsortMQTT.getsInstance().getConfigData().getDeviceName());
                    EventBus.getDefault().post(glassMessage);
 */
                }

                GlassMessage glassMessage = new GlassMessage();
                glassMessage.setMessage("INSHELF");
                glassMessage.setBarcode(event.getBarcodeResult());
                glassMessage.setFixedTarget(Utilities.makeTargetName("FIXED", "FIXED"));
                glassMessage.setGlassName(PurolatorSmartsortMQTT.getsInstance().getConfigData().getDeviceName());
                EventBus.getDefault().post(glassMessage);

                uiUpdater = new UIUpdater();
                uiUpdater.setUpdateType(PurolatorSmartsortMQTT.UPD_BOTTOMSCREEN);
                uiUpdater.setScannedCode(event.getBarcodeResult());

                PurolatorSmartsortMQTT.packagesList.getBarcodeScans().remove(barcodeResult);


                EventBus.getDefault().post(uiUpdater);

                return;
            }
        }
        //not found in the list, do a barcode lookup

        barcodeType = PurolatorSmartsortMQTT.getsInstance().getBarcodeType(event.getBarcodeResult());
        if (!barcodeType.equals(PurolatorSmartsortMQTT.NONE_TYPE) && !barcodeType.equals(PurolatorSmartsortMQTT.EMPLOYEE_TYPE)){

            FixedBarcodeScan fixedBarcodeScan = new FixedBarcodeScan(event.getBarcodeResult(), barcodeType);
            fixedBarcodeScan.setScanDate(new Date());
            fixedBarcodeScan.setDimensionData("");
            EventBus.getDefault().post(fixedBarcodeScan);


        } else if (barcodeType != null && barcodeType.equals(PurolatorSmartsortMQTT.EMPLOYEE_TYPE)){
            PurolatorSmartsortMQTT.getsInstance().getConfigData().setUserId(event.getBarcodeResult());          //sets the username. Now the screen is ready to be unlocked

        }


    }



}
