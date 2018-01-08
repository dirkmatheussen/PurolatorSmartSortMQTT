package evolar.be.purolatorsmartsortMQTT;

import android.os.Build;
import android.util.Log;

import com.google.gson.Gson;

import org.eclipse.paho.android.service.MqttAndroidClient;
import org.eclipse.paho.client.mqttv3.IMqttActionListener;
import org.eclipse.paho.client.mqttv3.IMqttDeliveryToken;
import org.eclipse.paho.client.mqttv3.IMqttToken;
import org.eclipse.paho.client.mqttv3.MqttCallbackExtended;
import org.eclipse.paho.client.mqttv3.MqttConnectOptions;
import org.eclipse.paho.client.mqttv3.MqttException;
import org.eclipse.paho.client.mqttv3.MqttMessage;
import org.greenrobot.eventbus.EventBus;
import org.greenrobot.eventbus.Subscribe;

import java.net.URISyntaxException;
import java.util.IllegalFormatCodePointException;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

import evolar.be.purolatorsmartsortMQTT.events.FixedMessage;
import evolar.be.purolatorsmartsortMQTT.events.FixedScanResult;
import evolar.be.purolatorsmartsortMQTT.events.GlassDeviceInfo;
import evolar.be.purolatorsmartsortMQTT.events.GlassMessage;
import evolar.be.purolatorsmartsortMQTT.events.RingBarcodeScan;
import evolar.be.purolatorsmartsortMQTT.events.UIUpdater;

/**
 * Class that handles communication with MQTT broker
 * This is the reciever class. This is for the VuzixM100 (Ringscanner) related activities
 * It is the PurlatorSmartsortMQTT application object that instantiates the MQTTGlassComms class
 * Communication is done with a JSon object.
 *
 *
 * Created by Dirk on 4/04/16.
 */
public class MQTTGlassPahoComms {

    private static final String TAG = "MQTTGlassPahoComms";
    private static final boolean D = true;

    private boolean isArcAlive = false;
    private ScheduledExecutorService heartbeatScheduler;


    private MqttAndroidClient mqttClient;

    public MQTTGlassPahoComms(){

        if (D) Log.d(TAG,"MQTTFixPahoComms created");


        EventBus.getDefault().register(this);
        setMQttConnection();

    }
    /**
    * Disconnect from the MQTT session, called when app is destroyed
    *
     *
     */
    public void stopMQTTConnection(){

        //stop heartbeath handler
        heartbeatScheduler.shutdownNow();
        isArcAlive = false;

        try {
            if (mqttClient !=null && mqttClient.isConnected()) {
                mqttClient.disconnect();
            }
        } catch (MqttException ex){

        }

    }

    /**
     * Makes an MQTT Connection & set the listener for incoming messages
     *
     */
    public void setMQttConnection() {
        IMqttToken token;
        MqttConnectOptions options = new MqttConnectOptions();
        options.setCleanSession(false);
        options.setAutomaticReconnect(true);



        String mqttUrl = PurolatorSmartsortMQTT.getsInstance().getConfigData().getMqttParams().getMQTT_URL();
        int mqttPort = PurolatorSmartsortMQTT.getsInstance().getConfigData().getMqttParams().getMQTT_PORT();

        String connectUrl = "tcp://" + mqttUrl + ":" + mqttPort;
        try {

            String clientId = PurolatorSmartsortMQTT.getsInstance().getConfigData().getDeviceName() + System.currentTimeMillis();

            mqttClient = new MqttAndroidClient(PurolatorSmartsortMQTT.getsInstance().getApplicationContext(), connectUrl, clientId);
            mqttClient.setCallback(new MqttEventCallbackExtended());

            mqttClient.connect(options, null,new IMqttActionListener() {
                @Override
                public void onSuccess(IMqttToken asyncActionToken) {
                    if (D) Log.e(TAG, "Connection is successfull, subscribe for: " + Utilities.getSubscribtionTopic());

                    subscribeTopics();
                    handleARCHeartBeat();           //set heartbeath handler from ARC

                }

                @Override
                public void onFailure(IMqttToken asyncActionToken, Throwable exception) {
                    PurolatorSmartsortMQTT.getsInstance().setMqttRestart(true);
                    Log.e(TAG,"Could not start MQTTService, reason: " + exception.getMessage());

                    UIUpdater uiUpdater = new UIUpdater();
                    uiUpdater.setUpdateType(PurolatorSmartsortMQTT.UPD_ERROR);
                    uiUpdater.setErrorCode(PurolatorSmartsortMQTT.NONE_TYPE);
                    uiUpdater.setErrorMessage("NO COMMUNICATION WITH MESSAGE SERVER");
                    EventBus.getDefault().post(uiUpdater);

                    uiUpdater.setUpdateType(PurolatorSmartsortMQTT.UPD_ERROR);
                    uiUpdater.setErrorCode("NEWINFO");
                    uiUpdater.setErrorMessage("Terminal ID: " + PurolatorSmartsortMQTT.getsInstance().configData.getTerminalID() + " Offline");
                    EventBus.getDefault().post(uiUpdater);

                }
            });




        } catch (MqttException ex) {


            Log.d(TAG,"MQTT Connect error:  "  + ex.getMessage());

            UIUpdater uiUpdater = new UIUpdater();
            uiUpdater.setUpdateType(PurolatorSmartsortMQTT.UPD_ERROR);
            uiUpdater.setErrorCode(PurolatorSmartsortMQTT.NONE_TYPE);
            uiUpdater.setErrorMessage("NO COMMUNICATION WITH MESSAGE SERVER");
            EventBus.getDefault().post(uiUpdater);

            uiUpdater.setUpdateType(PurolatorSmartsortMQTT.UPD_ERROR);
            uiUpdater.setErrorCode("NEWINFO");
            uiUpdater.setErrorMessage("Terminal ID: " + PurolatorSmartsortMQTT.getsInstance().configData.getTerminalID() + " Offline");
            EventBus.getDefault().post(uiUpdater);


        }


    }


    /**
     * Subscribe to the topics
     *
     */
    private void subscribeTopics(){

        String [] topicFilters = new String[4];
        int [] Qos = new int[4];


        topicFilters[0] = Utilities.getSubscribtionTopic();
        Qos[0] = 2;
        topicFilters[1] = PurolatorSmartsortMQTT.getsInstance().getConfigData().getTerminalID();
        Qos[1] = 2;
        topicFilters[2] = PurolatorSmartsortMQTT.getsInstance().getConfigData().getTerminalID()+"PUDRO";
        Qos[2] = 2;

        topicFilters[3] = Utilities.getSubscribtionTopic()+"/RINGSCAN";
        Qos[3] = 2;


        try {
            mqttClient.subscribe(topicFilters, Qos);
        } catch (MqttException ex){

            PurolatorSmartsortMQTT.getsInstance().setMqttRestart(true);

            UIUpdater uiUpdater = new UIUpdater();
            uiUpdater.setUpdateType(PurolatorSmartsortMQTT.UPD_ERROR);
            uiUpdater.setErrorCode(PurolatorSmartsortMQTT.NONE_TYPE);
            uiUpdater.setErrorMessage("NO COMMUNICATION WITH MESSAGE SERVER");
            EventBus.getDefault().post(uiUpdater);

            uiUpdater.setUpdateType(PurolatorSmartsortMQTT.UPD_ERROR);
            uiUpdater.setErrorCode("NEWINFO");
            uiUpdater.setErrorMessage("Terminal ID: " + PurolatorSmartsortMQTT.getsInstance().configData.getTerminalID() + " Offline");
            EventBus.getDefault().post(uiUpdater);

        } catch (IllegalArgumentException ex2){
            Log.e(TAG,"Error: " + ex2);
        }

    }

    private class MqttEventCallbackExtended implements MqttCallbackExtended{

        @Override
        public void connectComplete(boolean reconnect, String serverURI) {
            Log.d(TAG,"Connect complete arrived");
            Log.d(TAG,"Resending scanned products after disconnect");
            if (!PurolatorSmartsortMQTT.getsInstance().isStopped()) {

                PurolatorSmartsortMQTT.getsInstance().mqttRetryCount = 0;
                PurolatorSmartsortMQTT.getsInstance().setMqttRestart(false);
                UIUpdater uiUpdater = new UIUpdater();
                uiUpdater.setUpdateType(PurolatorSmartsortMQTT.UPD_ERROR);
                uiUpdater.setErrorCode("CLEARERROR");
                uiUpdater.setErrorMessage("");
                EventBus.getDefault().post(uiUpdater);

                uiUpdater = new UIUpdater();
                uiUpdater.setUpdateType(PurolatorSmartsortMQTT.UPD_ERROR);
                uiUpdater.setErrorCode("NEWINFO");
                uiUpdater.setErrorMessage("Terminal ID: " + PurolatorSmartsortMQTT.getsInstance().configData.getTerminalID() + " Arc Online");
                EventBus.getDefault().post(uiUpdater);





                GlassDeviceInfo startDeviceInfo = new GlassDeviceInfo();
                startDeviceInfo.setActive(true);
                startDeviceInfo.setDeviceName(PurolatorSmartsortMQTT.getsInstance().getConfigData().getDeviceName());
                startDeviceInfo.setAssignedRoutes(PurolatorSmartsortMQTT.getsInstance().getConfigData().getValidRoutes());
                startDeviceInfo.setUserName(PurolatorSmartsortMQTT.getsInstance().getConfigData().getUserId());
                startDeviceInfo.setDeviceType(PurolatorSmartsortMQTT.getsInstance().getConfigData().getDeviceType());

                EventBus.getDefault().post(startDeviceInfo);


                //Send all the scanned parcels not send to the glasses. it is thread-safe process
                synchronized (PurolatorSmartsortMQTT.getsInstance().getFailedGlassMessages()) {
                    while (PurolatorSmartsortMQTT.getsInstance().hasNextFailedGlassMessage()) {
                        GlassMessage failedMessage = PurolatorSmartsortMQTT.getsInstance().getNextFailedGlassMessage();
                        if (failedMessage != null) EventBus.getDefault().post(failedMessage);
                    }
                }
            }
        }

        @Override
        public void connectionLost(Throwable arg0) {
            if (D) Log.d(TAG,"Connection listener is DISconnected");

            PurolatorSmartsortMQTT.getsInstance().setMqttRestart(true);
            UIUpdater uiUpdater = new UIUpdater();

            uiUpdater.setUpdateType(PurolatorSmartsortMQTT.UPD_ERROR);
            uiUpdater.setErrorCode("NEWINFO");
            uiUpdater.setErrorMessage("Terminal ID: " + PurolatorSmartsortMQTT.getsInstance().configData.getTerminalID()+" Disconnected");
            EventBus.getDefault().post(uiUpdater);


            uiUpdater.setUpdateType(PurolatorSmartsortMQTT.UPD_ERROR);
            uiUpdater.setErrorCode(PurolatorSmartsortMQTT.NONE_TYPE);
            uiUpdater.setErrorMessage("NO COMMUNICATION WITH  MESSAGE SERVER");
            EventBus.getDefault().post(uiUpdater);
/*
            //try to handle a reconnect after 1 seconds
            if (!PurolatorSmartsortMQTT.getsInstance().isStopped()){

                PurolatorSmartsortMQTT.getsInstance().mqttRetryCount++;
                if (PurolatorSmartsortMQTT.getsInstance().mqttRetryCount <11) {

                    new Thread(new Runnable() {
                        @Override
                        public void run() {
                            try {
                                Log.d(TAG, "Try to restart after 1 seconds");
                                Thread.sleep(1000);
                            } catch (InterruptedException ex) {
                            }


                        }
                    }).start();
                } else {
                }
            }
*/



        }

        @Override
        public void deliveryComplete(IMqttDeliveryToken arg0) {

        }

        @Override
        public void messageArrived(String topic, final MqttMessage msg) throws Exception {
            handleMessage(topic,new String(msg.getPayload()));

        }


    }



    /**
     * Handle the incoming message
     *
     * @param topic
     * @param payload
     */


    public void handleMessage(String topic, String payload) {

        if (PurolatorSmartsortMQTT.getsInstance().isStopped()) {
            return;
        }
        String jsonIn = payload;

        Gson gson = new Gson();

//        if (D) Log.i(TAG, "Json Message Received:  " + jsonIn);
        if (jsonIn.contains("FixedMessage")){       //START - STOP - PUDRO


            FixedMessage fixedMessage = gson.fromJson(jsonIn, FixedMessage.class);

            if (fixedMessage.getMessage().contains("HBT")){           //heartbeat
                handleARCHeartBeat();
            } else {
                //process the result, handle by PuralotorActivityGlass
                EventBus.getDefault().post(fixedMessage);
            }
        } else if (topic.toString().contains("PUDRO")) {
            //direct message from PUDRO Speed sensor
            String speed = jsonIn.substring(jsonIn.indexOf("@") + 1, jsonIn.length());
            if (D) Log.d(TAG, "Speed from PUDRO Device detected: " + speed);
            FixedMessage fixedMessage = new FixedMessage();
            fixedMessage.setValue(speed);
            fixedMessage.setMessage("PUDRO");
            //process the result, handle by PuralotorActivityGlass
            EventBus.getDefault().post(fixedMessage);

        } else if (topic.toString().contains("RINGSCAN")){

            EventBus.getDefault().post(new RingBarcodeScan(jsonIn));        //HandleRingBarcode
        } else  {
            try {
                FixedScanResult fixedScanResult = gson.fromJson(jsonIn, FixedScanResult.class);
                //process the result, handle by HandleFixedResult
                EventBus.getDefault().post(fixedScanResult);
            }catch (Exception e){
                Log.d(TAG,"Error in JsonParse: " + e.getMessage());
            }
        }



    }

    /**
     * Publish a message from Glass to tablet (when package is scanned by ringscanner and ready to be put on the shelf
     * The tablet will remove the scanned package from the list
     *
     * @param event
     */


    @Subscribe
    public void onGlassMessageEvent(final GlassMessage event){
        if (D) Log.d(TAG,"in GlassMessage Event");

        //create Json Object
        Gson gson = new Gson();
        final String json = gson.toJson(event);
        //publish to the target device (target topic)


        if (json != null) {
            try {
                MqttMessage mqttMessage = new MqttMessage();
                mqttMessage.setPayload(json.getBytes());
                mqttMessage.setQos(2);          //exactly once

                mqttClient.publish(getTargetTopic(), mqttMessage);
                if (D) Log.d(TAG, "Success Publish of Json: " + json);

            } catch (MqttException ex){
                Log.e(TAG,"Error sending a message: reason: " + ex.getMessage());
                PurolatorSmartsortMQTT.getsInstance().addFailedGlassMessage(event);

            }

        } else {
            if (D) Log.d(TAG,"Target glasses not found");
            // this is handled when a glass becomes active and is registered
        }

    }





    /**
     * Publish a logon message with the information of the device
     *
     * @param event
     */
    @Subscribe
    public void onGlassDeviceInfoResult(GlassDeviceInfo event){

        if (D) Log.d(TAG,"in GlassDeviceInfo Event");

        //create Json Object
        Gson gson = new Gson();
        final String json = gson.toJson(event);
        //publish to the target device (target topic)


        if (json != null && mqttClient !=null) {
            try {
                MqttMessage mqttMessage = new MqttMessage();
                mqttMessage.setPayload(json.getBytes());
                mqttMessage.setQos(2);          //exactly once

                mqttClient.publish(getTargetTopic(), mqttMessage);
                if (D) Log.d(TAG, "Success Publish of Json: " + json);

            } catch (MqttException ex){
                Log.e(TAG,"Error sending a message reason: " + ex.getMessage());

            } catch (NullPointerException ex){
                Log.e(TAG,"Error sending a message reason: " + ex.getMessage());
            }

        } else {
            if (D) Log.d(TAG,"Target glasses not found");
            // this is handled when a glass becomes active and is registered
        }


    }

    /**
     * Handle the heartbeat from ARC Scanner.
     * If no heartbeat received after 10 secs then give an errormessage
     *
     */

    private void handleARCHeartBeat(){

        if (Build.MODEL.compareToIgnoreCase("m100") == 0) {             //caused problems with m100
            return;
        }

        if (isArcAlive) {
            if (!heartbeatScheduler.isShutdown()){
                heartbeatScheduler.shutdownNow();
                heartbeatScheduler = Executors.newScheduledThreadPool(1);
                ScheduledFuture scheduleLogFuture = heartbeatScheduler.schedule(new Runnable() {
                    @Override
                    public void run() {
                        if (D) Log.d(TAG,"ARCScanner is offline");
                        isArcAlive = false;
                        UIUpdater uiUpdater = new UIUpdater();
                        uiUpdater.setUpdateType(PurolatorSmartsortMQTT.UPD_ERROR);
                        uiUpdater.setErrorCode(PurolatorSmartsortMQTT.NONE_TYPE);
                        uiUpdater.setErrorMessage("NO COMMUNICATION WITH ARC SCANNER");
                        EventBus.getDefault().post(uiUpdater);

                        uiUpdater.setUpdateType(PurolatorSmartsortMQTT.UPD_ERROR);
                        uiUpdater.setErrorCode("NEWINFO");
                        uiUpdater.setErrorMessage("Terminal ID: " + PurolatorSmartsortMQTT.getsInstance().configData.getTerminalID()+" Offline (HBS)");
                        EventBus.getDefault().post(uiUpdater);


                    }
                }, 15, TimeUnit.SECONDS);       //if no heartbeat, show error

            }
            return;

        }
        if (D) Log.d(TAG,"Initiate hearbeat scheduler");
        heartbeatScheduler = Executors.newScheduledThreadPool(1);
        ScheduledFuture scheduleLogFuture = heartbeatScheduler.schedule(new Runnable() {
            @Override
            public void run() {
                if (D) Log.d(TAG,"ARCScanner is offline");
                isArcAlive = false;
                UIUpdater uiUpdater = new UIUpdater();
                uiUpdater.setUpdateType(PurolatorSmartsortMQTT.UPD_ERROR);
                uiUpdater.setErrorCode(PurolatorSmartsortMQTT.NONE_TYPE);
                uiUpdater.setErrorMessage("NO COMMUNICATION WITH ARC SCANNER");
                EventBus.getDefault().post(uiUpdater);

                uiUpdater.setUpdateType(PurolatorSmartsortMQTT.UPD_ERROR);
                uiUpdater.setErrorCode("NEWINFO");
                uiUpdater.setErrorMessage("Terminal ID: " + PurolatorSmartsortMQTT.getsInstance().configData.getTerminalID()+" Offline (HBS2)");
                EventBus.getDefault().post(uiUpdater);


            }
        }, 15, TimeUnit.SECONDS);       //if no heartbeat, show error


        isArcAlive = true;
        UIUpdater uiUpdater = new UIUpdater();
        uiUpdater.setUpdateType(PurolatorSmartsortMQTT.UPD_ERROR);
        uiUpdater.setErrorCode("CLEARERROR");
        uiUpdater.setErrorMessage("");
        EventBus.getDefault().post(uiUpdater);

        uiUpdater.setUpdateType(PurolatorSmartsortMQTT.UPD_ERROR);
        uiUpdater.setErrorCode("NEWINFO");
        uiUpdater.setErrorMessage("Terminal ID: " + PurolatorSmartsortMQTT.getsInstance().configData.getTerminalID()+" Arc Online");
        EventBus.getDefault().post(uiUpdater);

        //also send a message to Tablet for syncing

        GlassDeviceInfo startDeviceInfo = new GlassDeviceInfo();
        startDeviceInfo.setActive(true);
        startDeviceInfo.setDeviceName(PurolatorSmartsortMQTT.getsInstance().getConfigData().getDeviceName());
        startDeviceInfo.setAssignedRoutes(PurolatorSmartsortMQTT.getsInstance().getConfigData().getValidRoutes());
        startDeviceInfo.setUserName(PurolatorSmartsortMQTT.getsInstance().getConfigData().getUserId());
        startDeviceInfo.setDeviceType(PurolatorSmartsortMQTT.getsInstance().getConfigData().getDeviceType());

        EventBus.getDefault().post(startDeviceInfo);


    }



    /**
     * Get the Target Topic to send the message to, which is the Fixed Scanner
     *
     *
     * @return
     */

    private String getTargetTopic(){

        return Utilities.makeTargetName("FIXED","FIXED");
    }

}
