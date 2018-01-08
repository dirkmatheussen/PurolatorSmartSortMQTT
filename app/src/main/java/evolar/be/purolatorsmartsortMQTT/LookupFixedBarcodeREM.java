package evolar.be.purolatorsmartsortMQTT;

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
 * If Lookup for PIN failed & lookup for RoutePlan failed put into remediation mode
 *
 */
public class LookupFixedBarcodeREM {

    private static final String TAG = "LookupFixedBarcodeREM";
    private static final boolean D = true;

    public LookupFixedBarcodeREM(){
        EventBus.getDefault().register(this);

    }

    @Subscribe(priority = 0)    //lowest priority
    public void onFixedBarcodeScanEvent(FixedBarcodeScan event) {

        if (D) Log.d(TAG, "In Remedetion event");

            UIUpdater uiUpdater = new UIUpdater();

            String pinCode = Utilities.getPINCode(event.getBarcodeResult());
            if (pinCode == null) {
                pinCode = event.getBarcodeResult();
            }
            if (!pinCode.startsWith("NOLABEL") && PurolatorSmartsortMQTT.getsInstance().getConfigData().getDeviceType().equals("FIXED")) {         //if there is no label on the box, do not show the parcel in the screen

                uiUpdater.setUpdateType(PurolatorSmartsortMQTT.UPD_BOTTOMSCREEN);
                uiUpdater.setPinCode("Package " + pinCode + " in remediation");
                uiUpdater.setScannedCode(event.getBarcodeResult());
                uiUpdater.setPostalCode(Utilities.getPostalCode(event.getBarcodeResult()));
                uiUpdater.setRemediationId(pinCode);

                EventBus.getDefault().post(uiUpdater);

            } else if (PurolatorSmartsortMQTT.getsInstance().getConfigData().getDeviceType().equals("GLASS")) {
                uiUpdater.setUpdateType(PurolatorSmartsortMQTT.UPD_BOTTOMSCREEN);
                uiUpdater.setPinCode("Package " + pinCode + " in remediation");
                uiUpdater.setScannedCode(event.getBarcodeResult());
                uiUpdater.setPostalCode(Utilities.getPostalCode(event.getBarcodeResult()));
                uiUpdater.setRemediationId(pinCode);
                if (!pinCode.startsWith(("NOLABEL"))) {
                    uiUpdater.setRouteNumber("REM");
                } else {
                    uiUpdater.setRouteNumber("XXX");
                }
                uiUpdater.setCorrectBay(false);
                EventBus.getDefault().post(uiUpdater);

            }
        if (PurolatorSmartsortMQTT.getsInstance().getConfigData().getDeviceType().equals("FIXED")) {

            // send a message for Remediation
            final FixedScanResult fixedScanResult = new FixedScanResult();
            if (!pinCode.startsWith(("NOLABEL"))) {
                fixedScanResult.setRouteNumber("REM");          //remediation
            } else {
                fixedScanResult.setRouteNumber("XXX");          //let go (go straight)
            }
            fixedScanResult.setPostalCode(Utilities.getPostalCode(event.getBarcodeResult()));
            fixedScanResult.setPinCode(pinCode);
            fixedScanResult.setScannedCode(event.getBarcodeResult());
            fixedScanResult.setScanDate(event.getScanDate());
            fixedScanResult.setGlassTarget("WAVE");
            fixedScanResult.setDimensionData(event.getDimensionData());

            EventBus.getDefault().post(fixedScanResult);

            if (!PurolatorSmartsortMQTT.getsInstance().getConfigData().isWaveValidation()) {
                PurolatorSmartsortMQTT.getsInstance().sendToGlass(fixedScanResult);

            }

            Logger logger = new Logger();
            logger.setDevice_id(PurolatorSmartsortMQTT.getsInstance().getConfigData().getDeviceName());
            logger.setUserCode(PurolatorSmartsortMQTT.getsInstance().getConfigData().getUserId());
            logger.setTerminalID(PurolatorSmartsortMQTT.getsInstance().getConfigData().getTerminalID());
            logger.setScannedCode(event.getBarcodeResult());
            if (pinCode.startsWith("NOLABEL") || pinCode.startsWith(("MULTI"))) {
                logger.setBarcodeType("REM: " + pinCode);
            } else {
                logger.setBarcodeType("Remediation");
            }

            logger.setScannedCode(event.getBarcodeResult());
            logger.setPostalCode(Utilities.getPostalCode(event.getBarcodeResult()));

            EventBus.getDefault().post(logger);
        }

    }

}
