package evolar.be.purolatorsmartsortMQTT;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.util.Log;

import java.util.ArrayList;
import java.util.Arrays;

import evolar.be.purolatorsmartsortMQTT.events.GlassDeviceInfo;

/**
 * Class with several static utilities
 * Created by Dirk on 5/04/16.
 */
public class Utilities {


    private static final String TAG = "Utilities";
    private static final boolean D = true;


    /**
     * Get the barcode type to log in the logging file
     *
     * @param barcode
     * @return
     */

    public static String getBarcodeLogType(String barcode){
        String barcodeType = "NONE";

        String[] parsed = barcode.split("[|]");
        if (parsed.length> 2) {
            return "2D";
        }

        if (barcode.length() == 34) return "NGB";
        if (barcode.length() == 12) {
            int asciiValue = (int) barcode.charAt(0);
            if (asciiValue > 57) return "PREPRINT";
        }
        if (barcode.length() == 11) return "UPSMANUAL";
        if (barcode.length() == 15 && barcode.startsWith("421")) return "UPSPOSTAL";
        if (barcode.length() == 18 && barcode.startsWith("1Z")) return "UPS1Z";
        if (barcode.length() == 8 && barcode.startsWith("60")) return "EMPLOYEE";


        //TODO Legacy Purolator
        //TODO UPS Maxicode

        return barcodeType;

    }

    /**
     * Parse the PINCode from the different types of Barcodes received
     * Returns null when no valid barcode is found
     * See Paragraph 4.4 of Functional Design SmartSort
     *
     * @param barcode
     * @return
     */

    public static String getPINCode(String barcode) {

        String pinCode = null;


        // Relabel pincode
        if (barcode.length() == 9 && barcode.startsWith(PurolatorSmartsortMQTT.getsInstance().getConfigData().getTerminalID())){
            return barcode;
        }

        if (barcode.length() < 11){
            return null;
        }

        if (barcode.startsWith("DAX")){             // a barcode used for internal use
            return null;
        }

        if (barcode.startsWith("spN")) {            // amazone barcodes
            return null;
        }

        //UPS Manual Waybill Barcode
        if (barcode.length() == 11){
            return barcode;
        }

        // a Legacy PINCode is scanned
        if (barcode.length() == 12 && !barcode.startsWith("A")) {
            return barcode;
        }

        //legacy Waybills, filter out the first 2 characters
        if (barcode.startsWith("A")){
            return barcode.substring(1);
        }

        //UPS 1Z Barcode -->
        if (barcode.length() == 18 && barcode.startsWith("1Z")){
            return barcode;
        }

        // Purolator PIN Codes
        if (barcode.substring(9, 10).equals("9") && barcode.length() == 34) {
            if (barcode.substring(10,11).equals("1")) pinCode = barcode.substring(11,20);
            if (barcode.substring(10,11).equals("2")) pinCode = barcode.substring(11,21);
            if (barcode.substring(10,11).equals("3")) pinCode = barcode.substring(11,22);
            if (barcode.substring(10,11).equals("4")) pinCode = barcode.substring(11,23);
            if (barcode.substring(10,11).equals("5")) pinCode = barcode.substring(11,24);
            return pinCode;

        }
        /*
        if (barcode.substring(9, 11).equals("94") && barcode.length() == 34) {
            pinCode = barcode.substring(11, 23);
        } else if (barcode.substring(9, 11).equals("92")&& barcode.length() == 34){
            pinCode = barcode.substring(11, 21);
        //Purolator NGB Barcode
        */
        if(barcode.length() == 34){
            int firstChar = Integer.valueOf(barcode.substring(9, 11)) + 64;
            int secondChar = Integer.valueOf(barcode.substring(11, 13)) + 64;
            int thirdChar = Integer.valueOf(barcode.substring(13, 15)) + 64;
            pinCode = Character.toString((char) firstChar);
            pinCode = pinCode + Character.toString((char) secondChar);
            pinCode = pinCode + Character.toString((char) thirdChar);
            pinCode = pinCode + barcode.substring(15,24);
        } else{
        //PDF 417 Barcode
            barcode = barcode.replace("\\","|");
            String[] parsed = barcode.split("[|]");

            for (String aParsed : parsed) {
                Log.d(TAG, "Parsed: " + aParsed);
                if (aParsed.toUpperCase().startsWith("S02") && aParsed.length() > 6) {
                    pinCode = aParsed.substring(4);
                    break;
                }
            }
        }

        //TODO Extract PINcode from Maxicode & Legacy Purolator


        return pinCode;
    }

    /**
     * Find a postal code in NGB/Puro2D/UPS
     *
     *
     *
     * @param barcode
     * @return postalCode or null
     */

    public static String getPostalCode(String barcode) {

        String postCode = null;


        if (barcode.length() == 9 && barcode.startsWith("OSNR")){
            return null;
        }

        //UPS Barcode?
        if (barcode.length() == 9){
            return barcode.substring(3);
        }

        // a direct PINCode is scanned, return null
        if (barcode.length() <= 12) {
            return null;
        }

        if (barcode.substring(9, 10).equals("9") && barcode.length() == 34) {
            String encodedPostCode = barcode.substring(0, 9);

            try {
                int firstChar = Integer.valueOf(encodedPostCode.substring(0, 2)) + 64;
                int secondChar = Integer.valueOf(encodedPostCode.substring(3, 5)) + 64;
                int thirdChar = Integer.valueOf(encodedPostCode.substring(6, 8)) + 64;

                postCode = Character.toString((char) firstChar);
                postCode = postCode + encodedPostCode.substring(2, 3);
                postCode = postCode + Character.toString((char) secondChar);
                postCode = postCode + encodedPostCode.substring(5, 6);
                postCode = postCode + Character.toString((char) thirdChar);
                postCode = postCode + encodedPostCode.substring(8, 9);


            } catch (Exception e) {

            }
        } else if (barcode.length() == 34){
            String encodedPostCode = barcode.substring(0, 9);

            try {
                int firstChar = Integer.valueOf(encodedPostCode.substring(0, 2)) + 64;
                int secondChar = Integer.valueOf(encodedPostCode.substring(3, 5)) + 64;
                int thirdChar = Integer.valueOf(encodedPostCode.substring(6, 8)) + 64;

                postCode = Character.toString((char) firstChar);
                postCode = postCode + encodedPostCode.substring(2, 3);
                postCode = postCode + Character.toString((char) secondChar);
                postCode = postCode + encodedPostCode.substring(5, 6);
                postCode = postCode + Character.toString((char) thirdChar);
                postCode = postCode + encodedPostCode.substring(8, 9);


            } catch (Exception e) {

            }

        } else {            //fetch PostalCode code from 2D Barcode
            String[] parsed = barcode.split("[|]");

            for (String aParsed : parsed) {
                Log.d(TAG, "Parsed: " + aParsed);
                if (aParsed.toUpperCase().startsWith("R07")) {
                    postCode = aParsed.substring(4);
                    break;
                }
            }

            //TODO Extract postalcode from UPS Code


        }


        return postCode;


    }

    /**
     * Find the targetdevice to send a message to, based on the routenumber
     *
     * @param targetIdentifier
     * @param targetType        //GLASS or WAVE
     * @return empy if target not found.
     */

    public static String makeTargetName( String targetType, String targetIdentifier){


        if (targetIdentifier.equals("FIXED")){
            return PurolatorSmartsortMQTT.getsInstance().getConfigData().getTerminalID()+"/"+targetIdentifier+"_"+PurolatorSmartsortMQTT.getsInstance().getConfigData().getTerminalID();
        }
        //loop
            for (GlassDeviceInfo glassDeviceInfo : PurolatorSmartsortMQTT.getsInstance().getConnectedGlasses()) {
                for (String route : glassDeviceInfo.getAssignedRoutes()) {
//                    if (D) Log.d(TAG, "Find device: " + route + " for: " + targetIdentifier);
                    if (route.equalsIgnoreCase(targetIdentifier) && glassDeviceInfo.getDeviceType().equals(targetType)) {
                        return PurolatorSmartsortMQTT.getsInstance().getConfigData().getTerminalID() + "/" + glassDeviceInfo.getDeviceName();
                    }
                }
            }
        return "";
    }

    /**
     * Returns the subscription topic from the senders, which is a Fixed Scanner
     *
     * @return
     */

    public static String getSubscribtionTopic(){

        return PurolatorSmartsortMQTT.getsInstance().getConfigData().getTerminalID()+"/"+PurolatorSmartsortMQTT.getsInstance().configData.getDeviceName();

    }

    /**
     * Parse the string of the ARC Scanner
     * COMPLETED DATA RECORD (you may get multiple results)
     * Format:
     * 2,x1,y1,x2,y2,x3,y3,x4,y4,z,tacho,rec_id,[brc1[;brc2[;brc3[;...]]]]<CR>
     *
     * “2” is record type for “completed data record”
     * “x1”, “y1”, … “x4”, “y4” are corner coordinates
     * “z” is piece height
     * “tacho” is tacho counter value for the piece
     * “rec_id” is Octo identifier for dimensions record
     * “brc1”, “brc2”,... are Piece ID bar codes attached to the given piece. These fields are missing when there are no bar codes present. Number of bar codes is not limited. Codes are separated with semicolons.
     * Sample with two PIN bar codes:
     *
     * 2,135,420,750,420,750,0,0,0,135,3245123412,32,1Z435432432432;54323143212143234321432<CR>
     *
     * @param arcScan
     * @return
     */

    public static String parseARCScan(String arcScan){

        if (arcScan.startsWith("1,")){          //Size record, transform it into a Heartbeat record
            return "0,HBT";
        }
        if (arcScan.startsWith("0,")){          //HBT record
            return arcScan;
        }
        String arcBarcode = null;
        String[] splitArcScan = arcScan.split(",");

        if (splitArcScan.length == 12){
            arcBarcode = "NOLABEL1234" ;      //no label on parcel. No remediation, must go straight (no left/right sorting)
        } else {
            ArrayList<String> arcBarcodes = new ArrayList<String>();
            ArrayList<String> pinCodes = new ArrayList<String>();

            for (int i = 12;i<splitArcScan.length;i++){
                if (getPINCode(splitArcScan[i])!=null) {
                    //first check if there is a duplicate
                    if (!pinCodes.contains(getPINCode(splitArcScan[i]))){
                        pinCodes.add(getPINCode(splitArcScan[i]));
                        arcBarcodes.add(splitArcScan[i]);
                    }
                }
            }
            //check if there are purolator barcodes on the package
            if (arcBarcodes.size() == 0){
                arcBarcode = "NOLABEL1234";
            } else if (arcBarcodes.size() > 2) {
                arcBarcode = "MULTI-CODES";
            } else if (arcBarcodes.size() == 2){
                arcBarcode = "MULTI-CODES";
                if (arcBarcodes.get(0).startsWith("A")) arcBarcode = arcBarcodes.get(1);
                if (arcBarcodes.get(1).startsWith("A")) arcBarcode = arcBarcodes.get(0);
            } else {
                arcBarcode = arcBarcodes.get(0);
            }

            boolean isUps = false;
                // TODO check if there are UPS valid barcodes: at least 2 barcodes: 1 starts with 1Z & second is 9 positions
        }
        return arcBarcode;
    }

    /**
     * Find the sequence number of the ARC Scan string and pass to the WAVE sorter
     * The messages are sometimes out of sync
     * @param arcScan
     * @return
     */

    public static String getARCSequence(String arcScan){
        if (arcScan.startsWith("1,")){          //Size only record
            return null;
        }
        if (arcScan.startsWith("0,")){          //HBT record
            return arcScan;
        }
        String barcode = null;
        String arcBarcode = null;
        String[] splitArcScan = arcScan.split(",");
        if (splitArcScan.length < 12){
            return null;
        }
        return splitArcScan[11];


    }

    /**
     * parse the Address Parameters from the 2D Barcode Address
     *
     *
     * @param address
     * @return
     *  [0] = StreetNumber
     *  [1] = StreetName
     *  [2] = StreetType : this can be empty
     *
     */


    public static String[] parseAddress(String address){

        String[] parsedAddress = {"","",""};


        String[] splitAddress = address.split("[ ]");
        String lastParam = splitAddress[splitAddress.length-1].trim().toUpperCase();

        //check if the last parameter is a streetType
        if (PurolatorSmartsortMQTT.getsInstance().getStreetTypes().contains(lastParam)){
            if (lastParam.toUpperCase().equals("ROAD")) lastParam = "RD";
            parsedAddress[2] = lastParam;

        }

        if (splitAddress.length ==2 ){   //must at least have number, streetname
            parsedAddress[0] = splitAddress[0].trim().toUpperCase();
            parsedAddress[1] = splitAddress[1].trim().toUpperCase();
        } else if (splitAddress.length > 2){
            parsedAddress[0] = splitAddress[0].trim();
            for (int i=1;i<splitAddress.length-1;i++){
                parsedAddress[1] = parsedAddress[1] + splitAddress[i].trim() +" ";
            }
            parsedAddress[1] = parsedAddress[1].substring(0,parsedAddress[1].length()-1).toUpperCase();
        }

        return parsedAddress;

    }

    /**
     * Check if the scanned barcode is a scan from a shelf/truck.
     * Needed for the shelf scanning of the glasses
     *
     * @param barcode
     * @return
     */

    public static boolean isShelfScan(String barcode){


        if (barcode.length() > 5){
            return false;
        }
        // if Shelf Validation is disabled, return false;
        return PurolatorSmartsortMQTT.getsInstance().getConfigData().isShelfValidation();
    }

    /**
     * Restart of the application
     *
     * @param c
     */

    public static void doRestart(Context c) {
        try {
            //check if the context is given
            if (c != null) {
                //fetch the packagemanager so we can get the default launch activity
                // (you can replace this intent with any other activity if you want
                PackageManager pm = c.getPackageManager();
                //check if we got the PackageManager
                if (pm != null) {
                    //create the intent with the default start activity for your application
                    Intent mStartActivity = pm.getLaunchIntentForPackage(
                            c.getPackageName()
                    );
                    if (mStartActivity != null) {
                        mStartActivity.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
                        //create a pending intent so the application is restarted after System.exit(0) was called.
                        // We use an AlarmManager to call this intent in 100ms
                        int mPendingIntentId = 223344;
                        PendingIntent mPendingIntent = PendingIntent
                                .getActivity(c, mPendingIntentId, mStartActivity,
                                        PendingIntent.FLAG_CANCEL_CURRENT);
                        AlarmManager mgr = (AlarmManager) c.getSystemService(Context.ALARM_SERVICE);
                        mgr.set(AlarmManager.RTC, System.currentTimeMillis() + 100, mPendingIntent);
                        //kill the application
                        System.exit(0);
                    } else {
                        Log.e(TAG, "Was not able to restart application, mStartActivity null");
                    }
                } else {
                    Log.e(TAG, "Was not able to restart application, PM null");
                }
            } else {
                Log.e(TAG, "Was not able to restart application, Context null");
            }
        } catch (Exception ex) {
            Log.e(TAG, "Was not able to restart application");
        }
    }

}
