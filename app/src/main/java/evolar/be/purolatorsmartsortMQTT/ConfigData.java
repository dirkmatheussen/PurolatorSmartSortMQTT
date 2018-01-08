package evolar.be.purolatorsmartsortMQTT;

import android.os.Environment;
import android.util.Log;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Properties;

/**
 * Created by Dirk on 23/03/16.
 * Contains all configuration data, read from config files (local & on ftp)
 */
public class ConfigData {

    private static final String TAG = "ConfigData";
    private static final boolean D = true;

    private String serialNumber;

    private String terminalID;
    private ArrayList<String> validRoutes;
    private String pudroZone;
    private String deviceType;              //FIXED - GLASS
    private String userId;

    private boolean waveValidation = false;
    private boolean postalCodeLookup = true;
    private boolean shelfValidation = true;
    private boolean smartGlass = true;      //is it a smartglass or a keyboard capable devide (wrist device)
    private boolean slaveMode = false;      //in slaveMode is receiving all messages but not sending, except the start message
    private boolean autorestart = false;     //automatical restart of app is stopped with en error
    private boolean glassRemediation = true;     //remedation is done locally with the wrist computer.

    private String deviceName;              //name of the device, used for receiving MQTT messages

    private boolean sendLogcat = false;

    private FTPParams ftpParams;
    private MQTTParams mqttParams;

    public ConfigData(){


        validRoutes = new ArrayList<String>();
        ftpParams = new FTPParams();
        mqttParams = new MQTTParams();
        //TODO Read FTP credentials from a local config file

        if (!getConfigValues()) {
            ftpParams.setFTP_PORT(449);
            ftpParams.setFTP_URL("ftp2.purolator.com");
            ftpParams.setFTP_USER("Google");
            ftpParams.setFTP_PWSD("r9Dx(6LqgR");
            serialNumber = android.os.Build.SERIAL.toUpperCase();
        }
    }

    public String getSerialNumber() {
        return serialNumber;
    }

    public void setSerialNumber(String serialNumber) {
        this.serialNumber = serialNumber;
    }

    public String getTerminalID() {
        return terminalID;
    }

    public void setTerminalID(String terminalID) {
        this.terminalID = terminalID;
    }

    public ArrayList<String> getValidRoutes() {
        return validRoutes;
    }

    public void setValidRoutes(ArrayList<String> validRoutes) {
        this.validRoutes = validRoutes;
    }

    public String getPudroZone() {
        return pudroZone;
    }

    public void setPudroZone(String pudroZone) {
        this.pudroZone = pudroZone;
    }

    public String getUserId() {
        return userId;
    }

    public void setUserId(String userId) {
        this.userId = userId;
    }

    public String getDeviceType() {
        return deviceType;
    }

    public void setDeviceType(String deviceType) {
        this.deviceType = deviceType;
    }

    public String getDeviceName() {
        return deviceName;
    }

    public void setDeviceName(String deviceName) {
        this.deviceName = deviceName;
    }

    public boolean isWaveValidation() {
        return waveValidation;
    }

    public void setWaveValidation(boolean waveValidation) {
        this.waveValidation = waveValidation;
    }

    public boolean isPostalCodeLookup() {
        return postalCodeLookup;
    }

    public void setPostalCodeLookup(boolean postalCodeLookup) {
        this.postalCodeLookup = postalCodeLookup;
    }

    public boolean isShelfValidation() {
        return shelfValidation;
    }

    public void setShelfValidation(boolean shelfValidation) {
        this.shelfValidation = shelfValidation;
    }

    public boolean isAutorestart() {
        return autorestart;
    }

    public void setAutorestart(boolean autorestart) {
        this.autorestart = autorestart;
    }

    public boolean isSendLogcat() {
        return sendLogcat;
    }

    public void setSendLogcat(boolean sendLogcat) {
        this.sendLogcat = sendLogcat;
    }

    public FTPParams getFtpParams() {
        return ftpParams;
    }

    public MQTTParams getMqttParams() {
        return mqttParams;
    }

    public boolean isSmartGlass() {
        if (deviceType.equalsIgnoreCase("FIXED")) return false;
        return smartGlass;
    }

    public void setSmartGlass(boolean smartGlass) {
        this.smartGlass = smartGlass;
    }

    public boolean isSlaveMode() {
        return slaveMode;
    }

    public void setSlaveMode(boolean slaveMode) {
        this.slaveMode = slaveMode;
    }

    public boolean isGlassRemediation() {
        return glassRemediation;
    }

    public void setGlassRemediation(boolean glassRemediation) {
        this.glassRemediation = glassRemediation;
    }

    public class MQTTParams {
        private String MQTT_URL;
        private int MQTT_PORT;

        public String getMQTT_URL() {
            return MQTT_URL;
        }

        public void setMQTT_URL(String MQTT_URL) {
            this.MQTT_URL = MQTT_URL;
        }

        public int getMQTT_PORT() {
            return MQTT_PORT;
        }

        public void setMQTT_PORT(int MQTT_PORT) {
            this.MQTT_PORT = MQTT_PORT;
        }
    }


    public class FTPParams{

        private String FTP_URL;
        private String FTP_USER;
        private String FTP_PWSD;
        private int FTP_PORT;


        public String getFTP_URL() {
            return FTP_URL;
        }

        public void setFTP_URL(String FTP_URL) {
            this.FTP_URL = FTP_URL;
        }

        public String getFTP_USER() {
            return FTP_USER;
        }

        public void setFTP_USER(String FTP_USER) {
            this.FTP_USER = FTP_USER;
        }

        public String getFTP_PWSD() {
            return FTP_PWSD;
        }

        public void setFTP_PWSD(String FTP_PWSD) {
            this.FTP_PWSD = FTP_PWSD;
        }

        public int getFTP_PORT() {
            return FTP_PORT;
        }

        public void setFTP_PORT(int FTP_PORT) {
            this.FTP_PORT = FTP_PORT;
        }
    }


    /**
     * * Read the initialisation values from a file
     *
     * * @return true is successfull, false if file not found
     */
    private boolean  getConfigValues(){

        Properties props = new Properties();

        Log.d(TAG,"get the config values");


        try {

            File sdCard = Environment.getExternalStoragePublicDirectory("Configs");

            File config;

            int tries = 3;			//3 retries should be enough, Splash did a check already
            do {
                config = new File(sdCard,"smartsortconfig.xml");
                if (!config.exists()){
                    try {
                        Thread.sleep(500); // sleep for 1/2 second
                    } catch (InterruptedException e) {
                        if (D) Log.w(TAG, "Interrupted!");
                        break;
                    }

                } else {
                    break;
                }
            } while (--tries > 0);

            if (tries == 0){
                return false;       //config file does not exist, default values are used
            }

            InputStream in = new FileInputStream(config);
            props.loadFromXML(in);


            String ftpPort = props.getProperty("FTPPORT","449");
            ftpParams.setFTP_PORT(Integer.parseInt(ftpPort));
            ftpParams.setFTP_URL(props.getProperty("FTPURL","ftp2.purolator.com"));
            ftpParams.setFTP_USER(props.getProperty("FTPUSER", "Google"));
            ftpParams.setFTP_PWSD(props.getProperty("FTPPASSWD", "r9Dx(6LqgR"));
            serialNumber = props.getProperty("SERIALNUMBER", android.os.Build.SERIAL.toUpperCase());

            in.close();


        } catch (FileNotFoundException e) {
            Log.e(TAG, "File not found: " + e.toString());

            return false;
        } catch (IOException e) {
            Log.e(TAG, "Can not read file: " + e.toString());
            return false;
        }

        return true;
    }

}
