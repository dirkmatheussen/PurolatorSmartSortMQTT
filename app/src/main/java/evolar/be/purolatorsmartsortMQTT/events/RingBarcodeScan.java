package evolar.be.purolatorsmartsortMQTT.events;

/**
 * Created by Dirk on 16/03/16.
 * POJO Object that contains a barcode scan, triggered as an event. This scan originates from a ringscanner
 */
public class RingBarcodeScan {


    private final String barcodeResult;

    public RingBarcodeScan(){

        barcodeResult =  null;
    }

    public RingBarcodeScan(String barcodeResult){


        barcodeResult = barcodeResult.replaceAll("\\r\\n|\\r|\\n", " ");

        this.barcodeResult =  barcodeResult;
    }

    public String getBarcodeResult() {
        return barcodeResult;
    }

}
