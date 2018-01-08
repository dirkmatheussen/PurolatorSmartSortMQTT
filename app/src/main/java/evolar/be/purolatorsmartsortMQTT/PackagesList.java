package evolar.be.purolatorsmartsortMQTT;

import java.util.ArrayList;

/**
 * Created by Dirk on 17/03/16.
 * Singleton class that keeps a list of all packages, scanned by the fixed scanner
 *
 */
public class PackagesList {
    private static PackagesList ourInstance = new PackagesList();

    public static PackagesList getInstance() {
        return ourInstance;
    }

    public ArrayList<String> getBarcodeScans() {
        return barcodeScans;
    }

    public void setBarcodeScans(ArrayList<String> barcodeScans) {
        this.barcodeScans = barcodeScans;
    }

    private ArrayList<String> barcodeScans = new ArrayList<String>();

    private PackagesList() {
    }


}
