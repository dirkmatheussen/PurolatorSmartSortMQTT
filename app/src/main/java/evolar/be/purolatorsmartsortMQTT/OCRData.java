package evolar.be.purolatorsmartsortMQTT;

/**
 * One record of an OCR result, is stored in an array in PurolatorSmartsortMQTT
 * Used to keep the OCR results
 * Created by Dirk on 21/01/17.
 */

public class OCRData
{
    String lookupBarcode;
    String scannedBarcode;
    String ocrResult;

    public String getLookupBarcode() {
        return lookupBarcode;
    }

    public void setLookupBarcode(String lookupBarcode) {
        this.lookupBarcode = lookupBarcode;
    }

    public String getOcrResult() {
        return ocrResult;
    }

    public void setOcrResult(String ocrResult) {
        this.ocrResult = ocrResult;
    }

    public String getScannedBarcode() {
        return scannedBarcode;
    }

    public void setScannedBarcode(String scannedBarcode) {
        this.scannedBarcode = scannedBarcode;
    }
}
