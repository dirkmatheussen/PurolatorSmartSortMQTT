package evolar.be.purolatorsmartsortMQTT.events;

import java.util.Date;

/**
 * Created by Dirk on 16/03/16.
 * POJO Object that contains a barcode scan, triggered as an event. This scan originates from a fixed scanner
 */
public class FixedBarcodeScan {


    private final String barcodeResult;
    private final String barcodeType;
    private Date scanDate;
    private String dimensionData;


    public FixedBarcodeScan(){

        barcodeResult =  null;
        barcodeType = null;
    }

    public FixedBarcodeScan(String barcodeResult,String barcodeType){


        barcodeResult = barcodeResult.replaceAll("\\r\\n|\\r|\\n", " ");

        this.barcodeResult =  barcodeResult;
        this.barcodeType = barcodeType;

    }


    public String getBarcodeResult() {
        return barcodeResult;
    }

    public String getBarcodeType() {
        return barcodeType;
    }

    public Date getScanDate() {
        return scanDate;
    }

    public void setScanDate(Date scanDate) {
        this.scanDate = scanDate;
    }

    public String getDimensionData() {
        return dimensionData;
    }

    public void setDimensionData(String dimensionData) {
        this.dimensionData = dimensionData;
    }
}
