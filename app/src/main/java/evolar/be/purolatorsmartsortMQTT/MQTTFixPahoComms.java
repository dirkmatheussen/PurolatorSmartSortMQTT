package evolar.be.purolatorsmartsortMQTT;


import android.util.Log;

import com.google.gson.Gson;

import org.eclipse.paho.android.service.MqttAndroidClient;
import org.eclipse.paho.client.mqttv3.IMqttActionListener;
import org.eclipse.paho.client.mqttv3.IMqttDeliveryToken;
import org.eclipse.paho.client.mqttv3.IMqttToken;
import org.eclipse.paho.client.mqttv3.MqttAsyncClient;
import org.eclipse.paho.client.mqttv3.MqttCallbackExtended;
import org.eclipse.paho.client.mqttv3.MqttConnectOptions;
import org.eclipse.paho.client.mqttv3.MqttException;
import org.eclipse.paho.client.mqttv3.MqttMessage;
import org.eclipse.paho.client.mqttv3.internal.wire.MqttReceivedMessage;
import org.greenrobot.eventbus.EventBus;
import org.greenrobot.eventbus.Subscribe;
import org.greenrobot.eventbus.ThreadMode;

import java.net.URISyntaxException;
import java.util.Date;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

import evolar.be.purolatorsmartsortMQTT.events.BarcodeScan;
import evolar.be.purolatorsmartsortMQTT.events.FixedMessage;
import evolar.be.purolatorsmartsortMQTT.events.FixedScanResult;
import evolar.be.purolatorsmartsortMQTT.events.GlassDeviceInfo;
import evolar.be.purolatorsmartsortMQTT.events.GlassMessage;
import evolar.be.purolatorsmartsortMQTT.events.UIUpdater;

/**
 * Class that handles communication with MQTT broker for publishing messages
 * Used by the fixed scanner input
 *  * It is the PurlatorSmartsortMQTT application object that instantiates the MQTTFixComms class

 * Communication is done with a JSon object of FixedScanResult
 *
 *
 * Created by Dirk on 4/04/16.
 */
public class MQTTFixPahoComms {

    private static final String TAG = "MQTTFixPahoComms";
    private static final boolean D = true;

    private boolean isArcAlive = false;
    private ScheduledExecutorService heartbeatScheduler;



    private MqttAndroidClient mqttClient;

    public MQTTFixPahoComms(){


        if (D) Log.d(TAG,"MQTTFixPahoComms created");

        EventBus.getDefault().register(this);
        setMQttConnection();




    }

    /**
     * Disconnect from the MQTT session, called when app is destroyed
     *
     */
    public void stopMQTTConnection(){


        //stop heartbeath handler
        heartbeatScheduler.shutdownNow();
        isArcAlive = false;
        try {
            if (mqttClient.isConnected()) {
                mqttClient.disconnect();
            }
        } catch (MqttException ex){

        }
    }



    /**
     * Makes an MQTT Connection & set the listener for incoming messages.
     * Incoming messages are a 'login message' of a Vuzix or a 'Put in shelf' message
     *
     */
    public void setMQttConnection() {

            IMqttToken token;
            MqttConnectOptions options = new MqttConnectOptions();
            options.setCleanSession(false);
            options.setAutomaticReconnect(true);

            handleARCHeartBeat();           //set heartbeath handler from ARC


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
                        if (D) Log.d(TAG, "Connection is successfull, subscribe for: " + Utilities.getSubscribtionTopic());

                        subscribeTopics();

                    }

                    @Override
                    public void onFailure(IMqttToken asyncActionToken, Throwable exception) {
                        PurolatorSmartsortMQTT.getsInstance().setMqttRestart(true);
                        Log.e(TAG,"Could not start MQTTService");

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

        String [] topicFilters = new String[7];
        int [] Qos = new int[7];


        topicFilters[0] = Utilities.getSubscribtionTopic();
        Qos[0] = 2;
        topicFilters[1] = Utilities.getSubscribtionTopic()+"/OCR";
        Qos[1] = 2;
        topicFilters[2] = Utilities.getSubscribtionTopic()+"/OCRTEST";
        Qos[2] = 2;
        topicFilters[3] = Utilities.getSubscribtionTopic()+"/ARC";
        Qos[3] = 2;
        topicFilters[4] = Utilities.getSubscribtionTopic()+"/WAVE";
        Qos[4] = 2;
        topicFilters[5] = Utilities.getSubscribtionTopic()+"/MISSORT";
        Qos[5] = 2;
        topicFilters[6] = Utilities.getSubscribtionTopic()+"/SCAN";
        Qos[6] = 2;


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

        }

    }


    private class MqttEventCallbackExtended implements MqttCallbackExtended{

        @Override
        public void connectComplete(boolean reconnect, String serverURI) {
            Log.d(TAG,"Connect complete arrived");

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


            if (!PurolatorSmartsortMQTT.getsInstance().isStopped()){
                FixedMessage fixedMessage = new FixedMessage();
                fixedMessage.setFixedSender(PurolatorSmartsortMQTT.getsInstance().getConfigData().getDeviceName());
                fixedMessage.setReceiver(PurolatorSmartsortMQTT.getsInstance().getConfigData().getTerminalID()); //broadcast a message to all glasses
                fixedMessage.setMessage("START");
                EventBus.getDefault().post(fixedMessage);

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

        if (D) Log.d(TAG, "Raw Message Received:  " + jsonIn);
        try {

            if (jsonIn.contains("GlassDeviceInfo")) {        //login information
                final GlassDeviceInfo glassDeviceInfo = gson.fromJson(jsonIn, GlassDeviceInfo.class);

                if (D) Log.d(TAG, "GlassDeviceInfo: " + glassDeviceInfo.getDeviceName());
                //process the information, first handled by PurolatorSmartsortMQTT then handled by PurolatorActivityFixed
                EventBus.getDefault().post(glassDeviceInfo);

                //send back the PUDRO Speed to all devices
                FixedMessage fixedMessage = new FixedMessage();
                fixedMessage.setFixedSender(PurolatorSmartsortMQTT.getsInstance().getConfigData().getDeviceName());
                fixedMessage.setReceiver(PurolatorSmartsortMQTT.getsInstance().getConfigData().getTerminalID()); //broadcast a message to all glasses
                fixedMessage.setValue(String.valueOf(PurolatorSmartsortMQTT.getsInstance().getPUDROSpeed()));
                fixedMessage.setMessage("PUDRO");
                EventBus.getDefault().post(fixedMessage);

            } else if (topic.contains("PUDRO")) {
                //direct message from PUDRO Speed sensor
                double speed = Double.valueOf(jsonIn.substring(jsonIn.indexOf("@") + 1, jsonIn.length()));
                if (D) Log.d(TAG, "Speed from PUDRO Device detected: " + speed);
                PurolatorSmartsortMQTT.getsInstance().setPUDROSpeed(speed);

            } else if (topic.contains("SCAN")) {      // data is coming from a fixed scanner, connected to a PC

                EventBus.getDefault().post(new BarcodeScan(jsonIn));

            } else if (topic.contains("ARC")) {      // data is coming the ARC or SICK scanner

                String barcode = Utilities.parseARCScan(jsonIn);

                if (barcode != null) {
                    if (barcode.contains("HBT")) {
                        handleARCHeartBeat();
                        //broadcast the heartbeat to the other devices
                        FixedMessage fixedMessage = new FixedMessage();
                        fixedMessage.setFixedSender(PurolatorSmartsortMQTT.getsInstance().getConfigData().getDeviceName());
                        fixedMessage.setMessage(barcode);
                        fixedMessage.setReceiver(PurolatorSmartsortMQTT.getsInstance().getConfigData().getTerminalID());
                        EventBus.getDefault().post(fixedMessage);

                    } else {
                        //increment number of parcels scanned by ARC
                        int scans = PurolatorSmartsortMQTT.getsInstance().getMaxTotalScans() + 1;
                        PurolatorSmartsortMQTT.getsInstance().setMaxTotalScans(scans);
                        EventBus.getDefault().post(new BarcodeScan(barcode, new Date(), jsonIn));
                    }
                }
            } else if (topic.contains("OCRTEST")) {
                OCRData ocrData = new OCRData();
                jsonIn = jsonIn.replace("@", "~");           //when coming from test app

                if (jsonIn.contains("~")) {
                    ocrData.setLookupBarcode(Utilities.getPINCode(jsonIn));
                    ocrData.setOcrResult(jsonIn);
                    ocrData.setScannedBarcode(Utilities.getPINCode(jsonIn));

                    PurolatorSmartsortMQTT.getsInstance().ocrDatas.add(ocrData);
                    // remove package from remediation list
                    UIUpdater uiUpdater = new UIUpdater();
                    uiUpdater.setRemediationId(Utilities.getPINCode(jsonIn));
                    uiUpdater.setErrorCode("REM");
                    uiUpdater.setUpdateType(PurolatorSmartsortMQTT.UPD_BOTTOMSCREEN);
                    EventBus.getDefault().post(uiUpdater);
                }
            } else if (topic.contains("OCR")) {
                OCRData ocrData = new OCRData();

                if (jsonIn.contains("~")) {

                    ocrData.setLookupBarcode(Utilities.getPINCode(jsonIn));
                    ocrData.setOcrResult(jsonIn);
                    ocrData.setScannedBarcode(Utilities.getPINCode(jsonIn));


                    PurolatorSmartsortMQTT.getsInstance().ocrDatas.add(ocrData);
                    // remove package from remediation list
                    UIUpdater uiUpdater = new UIUpdater();
                    uiUpdater.setRemediationId(Utilities.getPINCode(jsonIn));
                    uiUpdater.setErrorCode("REM");
                    uiUpdater.setUpdateType(PurolatorSmartsortMQTT.UPD_BOTTOMSCREEN);
                    EventBus.getDefault().post(uiUpdater);
                }
            } else if (topic.contains("MISSORT")) {                  //wrong sort of the packages, show this in SmartGlass

                final FixedScanResult fixedScanResult = gson.fromJson(jsonIn, FixedScanResult.class);
                fixedScanResult.setMissort(true);
                GlassMessage glassMessage = new GlassMessage();
                glassMessage.setMessage("MISSORT");
                glassMessage.setBarcode(fixedScanResult.getScannedCode());
                glassMessage.setFixedTarget("");
                glassMessage.setGlassName("");

                EventBus.getDefault().post(glassMessage);       //handled by PurolatorActivityFixed

            } else if (topic.contains("WAVE")) {

                final FixedScanResult fixedScanResult = gson.fromJson(jsonIn, FixedScanResult.class);
                fixedScanResult.setMissort(false);

                if (PurolatorSmartsortMQTT.getsInstance().getConfigData().isWaveValidation()) {
                    EventBus.getDefault().post(fixedScanResult);           //package is sorted, send message to Glass
                    // Show on tablet that package is sorted --> Handled by HandleFixedResult in Smart Glass
                }
            } else {                                            //message from smartGlass: put to shelf, or still alive response
                GlassMessage glassMessage = gson.fromJson(jsonIn, GlassMessage.class);

                //process the information
                EventBus.getDefault().post(glassMessage);           //handled in PurolatorActivityFixed
            }
        } catch (IllegalStateException ex) {
            if (D) Log.w(TAG, "Not a Json format: " + ex.getMessage());
        }

    }



    /**
     * Handle the heartbeat from ARC Scanner.
     * If no heartbeat received after 15 secs then give an errormessage
     *
     */

    private void handleARCHeartBeat(){

        if (isArcAlive) {
            if (!heartbeatScheduler.isShutdown()){
//                if (D) Log.d(TAG,"Reschedule hearbeat scheduler");
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
                        uiUpdater.setErrorMessage("Terminal ID: " + PurolatorSmartsortMQTT.getsInstance().configData.getTerminalID()+" Offline");
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
                uiUpdater.setErrorMessage("Terminal ID: " + PurolatorSmartsortMQTT.getsInstance().configData.getTerminalID()+" Offline");
                EventBus.getDefault().post(uiUpdater);


            }
        }, 10, TimeUnit.SECONDS);       //if no heartbeat, show error


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


    }




    /**
     * Publish a general message to all topics, used when tablet comes alive to inform the Glasses about the status
     * Or when a change in PUDRO Belt speed is detected
     * Do not send when in slave mode
     *
     * @param event
     */


    @Subscribe(threadMode = ThreadMode.ASYNC)
    public void onFixedMessageResult(FixedMessage event){

        if(PurolatorSmartsortMQTT.getsInstance().getConfigData().isSlaveMode()){
            return;
        }

        //create Json Object
        Gson gson = new Gson();

        final String json = gson.toJson(event);
        //publish to the target device (target topic)

        // Broadcast a message to a topic
        String targetTopic = event.getReceiver();


        if (D) Log.d(TAG,"Broadcast a message to: " + targetTopic);
        if (!targetTopic.isEmpty()) {
            try {
                MqttMessage mqttMessage = new MqttMessage();
                mqttMessage.setPayload(json.getBytes());
                mqttMessage.setQos(2);          //exactly once

                mqttClient.publish(targetTopic, mqttMessage);
            } catch (MqttException ex){
                Log.e(TAG,"Error sending a message: reason: " + ex.getMessage());

            }

        } else {
            if (D) Log.d(TAG,"Target glasses not found");
            // this is handled when a glass becomes active and is registered
        }

    }



    /**
     * Publish a message, which is  a scanned label.
     * Do not send when in slave mode
     *
     * @param event
     */
    @Subscribe

    public void onFixedScanResult(FixedScanResult event){

        if(PurolatorSmartsortMQTT.getsInstance().getConfigData().isSlaveMode()){
            return;
        }


        if (D) Log.d(TAG,"in Event");


        //create Json Object
        Gson gson = new Gson();
        final String json = gson.toJson(event);
        //publish to the target device (target topic)

        // Send a message to a topic, this is the Garam Sorter or a Vuzix glass, depending on the result of getTargetTopic
        String targetTopic = getTargetTopic(event);
        event.setGlassTarget(targetTopic);


        if (!targetTopic.isEmpty()) {
            try {
                MqttMessage mqttMessage = new MqttMessage();
                mqttMessage.setPayload(json.getBytes());
                mqttMessage.setQos(2);          //exactly once

                mqttClient.publish(targetTopic, mqttMessage);
            } catch (MqttException ex){

            }

        } else {
            if (D) Log.d(TAG,"Target glasses not found");
            // this is handled when a glass becomes active and is registered
        }

    }


    /**


    /**
     * Get the Target Topic to send the message to, which is a Vuzix Smart Glass or a Wave Sorter
     *
     *
     * @param event
     * @return
     */

    private String getTargetTopic(FixedScanResult event){

        String target = "GLASS";
        if (event.getGlassTarget()!= null && event.getGlassTarget().equals("WAVE")) target = "WAVE";
        String targetTopic = Utilities.makeTargetName(target,event.getRouteNumber());

        if (D) Log.d(TAG,"Target Topic: " + targetTopic);

        return targetTopic;

    }

}
