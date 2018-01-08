package evolar.be.purolatorsmartsortMQTT;

import android.util.Log;

import org.greenrobot.eventbus.EventBus;
import org.greenrobot.eventbus.Subscribe;

import evolar.be.purolatorsmartsortMQTT.events.BarcodeScan;
import evolar.be.purolatorsmartsortMQTT.events.FixedBarcodeScan;
import evolar.be.purolatorsmartsortMQTT.events.RingBarcodeScan;

/**
 * Class that manages & dispatches the scanned 1D or 2D codes to the correct business logic to handle the scanned value
 *
 * Created by Dirk on 17/03/16.
 */
public class BarcodeDispatch {
    // Debugging
    private static final String TAG = "BarcodeDispatch";
    private static final boolean D = true;


    public BarcodeDispatch(){
        EventBus.getDefault().register(this);

    }

    @Subscribe
    public void onBarcodeScan(BarcodeScan event) {
        if (D) Log.d(TAG, "Received Barcode: " + event.getBarcodeResult());

        // handling of Glass barcode scans

        if (PurolatorSmartsortMQTT.getsInstance().getConfigData().getDeviceType().equals("GLASS")) {
            //check is scan already exist -->

            for (String barcodeScan : PurolatorSmartsortMQTT.packagesList.getBarcodeScans()) {
                if (barcodeScan.equalsIgnoreCase(event.getBarcodeResult())) {
                    RingBarcodeScan ringBarcodeScan = new RingBarcodeScan(barcodeScan);
                    EventBus.getDefault().post(ringBarcodeScan);
                    return;
                }
            }

            //check if there is a correct route + shelf scan
            if (PurolatorSmartsortMQTT.getsInstance().getPackageInShelf() != null && PurolatorSmartsortMQTT.getsInstance().getPackageInShelf().getRouteNumber().equals(event.getBarcodeResult())) {
                RingBarcodeScan ringBarcodeScan = new RingBarcodeScan(event.getBarcodeResult());
                EventBus.getDefault().post(ringBarcodeScan);
                return;
            }
        }
        //send a fixedBarcodeScan event or update an employee scan.
        //find the barcodetype first

        String barcodeType = PurolatorSmartsortMQTT.getsInstance().getBarcodeType(event.getBarcodeResult());
        if (!barcodeType.equals(PurolatorSmartsortMQTT.NONE_TYPE) && !barcodeType.equals(PurolatorSmartsortMQTT.EMPLOYEE_TYPE)){
            //first check for a duplicate scan, which is not allowed
            //check if it is a 2D Scan or an NGB Scan, use the PIN code as BarcodeScan.  --> this logic is also applied in HandleRingBarcode
            String barcodeLogType = Utilities.getBarcodeLogType(event.getBarcodeResult());
            String adjustedBarcodeScan = event.getBarcodeResult();

            /*
            if (barcodeLogType.equals(("2D"))|| barcodeLogType.equals("NGB")){
                adjustedBarcodeScan =  Utilities.getPINCode(event.getBarcodeResult());
            }

            for (String barcodeScan : PurolatorSmartsortMQTT.packagesList.getBarcodeScans()) {
                if (barcodeScan.equalsIgnoreCase(adjustedBarcodeScan)) {
                    return;         //duplicate not allowed
                }
            }

            PurolatorSmartsortMQTT.packagesList.getBarcodeScans().add(adjustedBarcodeScan);

//            PurolatorSmartsortMQTT.packagesList.getBarcodeScans().add(event.getBarcodeResult());
            */
            FixedBarcodeScan fixedBarcodeScan = new FixedBarcodeScan(event.getBarcodeResult(), barcodeType);
            fixedBarcodeScan.setScanDate(event.getScanDate());
            fixedBarcodeScan.setDimensionData(event.getDimensionData());

            EventBus.getDefault().post(fixedBarcodeScan);

        } else if (barcodeType != null && barcodeType.equals(PurolatorSmartsortMQTT.EMPLOYEE_TYPE)){
            PurolatorSmartsortMQTT.getsInstance().getConfigData().setUserId(event.getBarcodeResult());          //sets the username. Now the screen is ready to be unlocked

        }
    }



}

