package evolar.be.purolatorsmartsortMQTT;

import org.greenrobot.eventbus.EventBus;
import org.greenrobot.eventbus.Subscribe;
import org.greenrobot.eventbus.ThreadMode;

import java.util.ConcurrentModificationException;

import evolar.be.purolatorsmartsortMQTT.events.FixedScanResult;
import evolar.be.purolatorsmartsortMQTT.events.GlassMessage;
import evolar.be.purolatorsmartsortMQTT.events.UIUpdater;

/**
 * For Smart Glass.
 * Object that received the FixedScanResult from the PurolatorActivityFixed app, handles it and shows it on the
 * display of the smartglass
 * Created by Dirk on 4/04/16.
 */
public class HandleFixedResult {


    public HandleFixedResult(){

        EventBus.getDefault().register(this);


    }

    @Subscribe(threadMode = ThreadMode.ASYNC)
    public void onFixedScanResultEvent(FixedScanResult event){

        if (!PurolatorSmartsortMQTT.getsInstance().isDatabasesLoaded()){        //do not handle messages as long databases are not loaded
            return;
        }

        if (event.getPinCode().startsWith("MULTI")){            //do not handle packages with multiple barcodes
            return;
        }

        UIUpdater uiUpdater = new UIUpdater();



        uiUpdater.setRouteNumber(event.getRouteNumber());
        uiUpdater.setShelfNumber(event.getShelfNumber());
        uiUpdater.setDeliverySequence(event.getDeliverySequence());
        uiUpdater.setPrimarySort(event.getPrimarySort());
        uiUpdater.setSideofBelt(event.getSideofBelt());
        uiUpdater.setPinCode(event.getPinCode());
        uiUpdater.setPostalCode(event.getPostalCode());
        uiUpdater.setScannedCode(event.getScannedCode());
        uiUpdater.setScanDate(event.getScanDate());


        //check if it is a 2D Scan or an NGB Scan, use the PIN code as BarcodeScan.  --> this logic is also applied in HandleRingBarcode
        String barcodeType = Utilities.getBarcodeLogType(event.getScannedCode());
        String barcode;


        if (barcodeType.equals(("2D"))|| barcodeType.equals("NGB")){
            barcode = Utilities.getPINCode(event.getScannedCode());
        } else {
            barcode = event.getScannedCode();

        }



        try {
            //check if barcode is already on the list, this is not allowed
            for (String barcodeScan : PurolatorSmartsortMQTT.packagesList.getBarcodeScans()) {
                if (barcodeScan.equalsIgnoreCase(barcode)) {
                    return;
                }

            }
        } catch (ConcurrentModificationException ex){
            //try again
            //check if barcode is already on the list, this is not allowed
            for (String barcodeScan : PurolatorSmartsortMQTT.packagesList.getBarcodeScans()) {
                if (barcodeScan.equalsIgnoreCase(barcode)) {
                    return;
                }
            }
        }
        //Now update the screen if no duplicate
        EventBus.getDefault().post(uiUpdater);
        PurolatorSmartsortMQTT.packagesList.getBarcodeScans().add(barcode);
        //confirm that the scanned data is received --> the line on tablet will become green
        GlassMessage glassMessage =  new GlassMessage();
        glassMessage.setMessage("CONFIRMED");
        glassMessage.setBarcode(event.getScannedCode());
        glassMessage.setFixedTarget(Utilities.makeTargetName("FIXED","FIXED"));
        glassMessage.setGlassName(PurolatorSmartsortMQTT.getsInstance().getConfigData().getDeviceName());
        EventBus.getDefault().post(glassMessage);

    }
}
