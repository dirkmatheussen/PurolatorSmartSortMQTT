package evolar.be.purolatorsmartsortMQTT.events;

import java.util.Date;

/**
 * Created by Dirk on 16/03/16.
 * POJO Object that contains a barcode scan, triggered as an event
 */
public class BarcodeScan {


    private final String barcodeResult;
    private  Date scanDate;
    private String dimensionData;       //contains the complete ARC String

    public BarcodeScan(){

        barcodeResult =  null;
    }



    public BarcodeScan (String barcodeResult){


        barcodeResult = barcodeResult.replaceAll("\\r\\n|\\r|\\n", " ");

        this.barcodeResult =  barcodeResult;
    }

    public BarcodeScan(String barcodeResult, Date scanDate){
        this(barcodeResult);
        this.scanDate = scanDate;
    }

    public BarcodeScan(String barcodeResult, Date scanDate, String dimensionData){
        this(barcodeResult);
        this.scanDate = scanDate;
        this.dimensionData = dimensionData;
    }



    public String getBarcodeResult() {
        return barcodeResult;
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
