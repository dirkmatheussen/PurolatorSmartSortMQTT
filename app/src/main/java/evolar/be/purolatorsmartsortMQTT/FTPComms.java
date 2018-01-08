package evolar.be.purolatorsmartsortMQTT;

import android.content.Context;
import android.media.audiofx.EnvironmentalReverb;
import android.os.AsyncTask;
import android.os.Environment;
import android.os.Looper;
import android.util.Log;

import com.jcraft.jsch.Channel;
import com.jcraft.jsch.ChannelSftp;
import com.jcraft.jsch.JSch;
import com.jcraft.jsch.JSchException;
import com.jcraft.jsch.Session;
import com.jcraft.jsch.SftpATTRS;
import com.jcraft.jsch.SftpException;
import com.jcraft.jsch.UserInfo;
import com.jcraft.jsch.UIKeyboardInteractive;

import org.greenrobot.eventbus.EventBus;
import org.greenrobot.eventbus.Subscribe;
import org.greenrobot.eventbus.ThreadMode;

import java.io.File;
import java.io.FileFilter;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.IOException;
import java.io.LineNumberReader;
import java.nio.charset.Charset;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.Vector;

import evolar.be.purolatorsmartsortMQTT.events.DatabaseName;
import evolar.be.purolatorsmartsortMQTT.events.FetchDb;
import evolar.be.purolatorsmartsortMQTT.events.FixedScanResult;
import evolar.be.purolatorsmartsortMQTT.events.PutDb;
import evolar.be.purolatorsmartsortMQTT.events.Logger;
import evolar.be.purolatorsmartsortMQTT.events.UIUpdater;

/**
 * Created by Dirk on 18/03/16.
 * Class that handles all FTP Put & Gets
 *
 */
public class FTPComms {

    private static final String TAG = "FTPComms";
    private static final boolean D =  true;


    private static final int MAXFILES = 7;          //number of files to load

    private Context context;

//    private String databaseType;
    private boolean silent;
    private int dbCounter = 0;

    public FTPComms(Context context){

        EventBus.getDefault().register(this);
        this.context = context;

    }

    //get the database, call async on a new thread (otherwise it is executed on the UI Thread
    @Subscribe(threadMode = ThreadMode.ASYNC)
    public void onFetchDbEvent(final FetchDb event){

        if (D)Log.d(TAG,"Fetch Database for type: " + event.getDatabaseType());
 //       databaseType = event.getDatabaseType();
        silent = event.isSilent();
        try {
            if (Looper.myLooper() == null) Looper.prepare();
        } catch (RuntimeException e){
            if(D) Log.d(TAG,"Runtime exception: " + e.getLocalizedMessage());
        }
        new GetFTPFile().execute(new String[]{event.getDatabaseType()});

    }


    //get the database, call async on a new thread (otherwise it is executed on the UI Thread
    @Subscribe(threadMode = ThreadMode.ASYNC)
    public void onPutDbEvent(final PutDb event){

        if (D)Log.d(TAG,"Put Database to FTP server for type: " + event.getDatabaseType());
        try {
            if (Looper.myLooper() == null) Looper.prepare();
        } catch (RuntimeException e){
            if(D) Log.d(TAG,"Runtime exception: " + e.getLocalizedMessage());
        }
        new PutDbFTPFile().execute(new String[]{event.getDatabaseType()});
    }

    /**
     * Capture the logger event
     * Store in a log file.
     * Send the log file every xx seconds, or if more then 50 records in file
     *
     * @param event
     */

    @Subscribe(threadMode = ThreadMode.ASYNC)
    public void onLoggerEvent(final Logger event) {

        try {
            if (Looper.myLooper() == null) Looper.prepare();
        } catch (RuntimeException e) {
            if (D) Log.d(TAG, "Runtime exception: " + e.getLocalizedMessage());
        }

        if (event.getLogType() != null && event.getLogType().equals("FTPLOG")){
            putLog2FTP();
        } else {
            if (write2Log(event)) {
                putLog2FTP();
            }
        }
    }


    /**
     * Make a synchronized method to ensure that no conflicts appear
     *
     *
     */
    
    private synchronized void putLog2FTP(){
        new PutFTPFile().execute();

    }


    /**
     * Format the log information & write it to the logfile.
     * Return true if write is successfull & more then 50 entries in the file
     *
     * @param event
     * @return
     */


    private synchronized boolean write2Log(Logger event){


        String logFilePath;
//			String logFileName;

        try
        {
            Date timeStamp = new Date();
            SimpleDateFormat df = new SimpleDateFormat("ddMMyyyyHHmmss");
            SimpleDateFormat dfDay = new SimpleDateFormat("DDD");

            String serial = android.os.Build.SERIAL;
            if (serial.length()> 6) {
                serial = serial.substring(serial.length()-6, serial.length());
            }

            File logDirectory =  Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS);


            //first check if a .txt_ file exist,
            class UndTxtFilter implements FileFilter {
                @Override
                public boolean accept(File file) {
                    return !file.isHidden() && (file.getName().endsWith(".txt_"));
                }
            }
            File[] files = logDirectory.listFiles(new UndTxtFilter());

            if (files == null || files.length == 0){
                if (D) Log.d(TAG,"Create a new loggingfile");
                logFilePath = logDirectory.getAbsolutePath()+"/logresults_"+df.format(timeStamp)+serial+".txt_";
            } else {
                logFilePath = files[0].getAbsolutePath();
            }
/*
		      		if ((new File(logFilePath)).exists()){
		      			Log.d(TAG,logFilePath + " exists already");
		      		} else {
		      			Log.d(TAG,logFilePath + " does not exist");
		      		}
*/

            //count number of lines
            File file = new File(logFilePath);
            int logLines = 0;
            if (file.exists()) {
                LineNumberReader lineNumberReader = new LineNumberReader(new FileReader(file));
                lineNumberReader.skip(Long.MAX_VALUE);
                logLines = lineNumberReader.getLineNumber();
                lineNumberReader.close();
            }
            if (D) Log.d(TAG,"Number of lines: " + logLines);
            
            FileOutputStream stream = new FileOutputStream(logFilePath,true);		//append if exist

            byte[] buffer;


            String line = event.getDevice_id()+"|";
            line = line + event.getUserCode() + "|";
            line = line + event.getTerminalID() + "|";


            line = line + event.getParkingPlanMasterVersionID() + "|";
            line = line + event.getScannedCode() + "|";
            line = line + event.getPinCode() + "|";
            line = line + event.getPrePrintID()+ "|";
            line = line + event.getPrimarySort()+ "|";
            line = line + event.getSideofBelt() + "|";
            line = line + event.getRouteNumber() + "|";
            line = line + event.getSSStatusReason() + "|";
            line = line + event.getShelfNumber() + "|";
            line = line + event.getDeliverySequence()+ "|";
            line = line + event.getShelfOverride()+ "|";
            line = line + event.getSmartSortMode()+  "|";
            if (event.getScanDateTime() !=null) {
                line = line + df.format(event.getScanDateTime()) + "|";
            }
            line = line + event.getPostalCode()+ "|";
            line = line + event.getProvinceCode()+ "|";
            line = line + event.getMunicipalityName()+ "|";
            line = line + event.getStreetNumber()+ "|";
            line = line + event.getStreetNumberSuffix()+ "|";
            line = line + event.getStreetName()+ "|";
            line = line + event.getStreetType()+ "|";
            line = line + event.getStreetDirection()+ "|";
            line = line + event.getUnitNumber()+ "|";
            line = line + event.getCustomerName()+ "|";
            if (event.getPrintDateTime()!=null){
                line = line + df.format(event.getPrintDateTime())+ "|";
            }
            line = line + event.getDeliveryTime()+ "|";
            line = line + event.getShipmentType()+ "|";
            line = line + event.getDeliveryType()+ "|";
            line = line + event.getDiversionCode()+ "|";
            line = line + event.getHandlingClassType()+ "|";
            line = line + event.getPackageType()+ "|";
            line = line + event.getBarcodeType()+ "|";
            line = line + event.getResolvedBy()+ "|";
            line = line + event.getAlternateAddressFlag();
            line = line + "\n";

            line = line.replaceAll("null","");

            buffer = line.getBytes(Charset.forName("UTF-8"));


            stream.write(buffer);

            stream.flush();
            stream.close();
/*
            //write also to a log file (for backup purposes)
            mFilePath = "mnt/ext_sdcard/tntresults_"+dfDay.format(timeStamp)+".csv";
            stream = new FileOutputStream(mFilePath,true);		//append


            line =  "\""+ truncked +"\";"
                    +"\"" + df.format(timeStamp)+"\";"
                    +"\"" + position + "\";"
                    +"\"" + status+ "\";"
                    + "\""+ android.os.Build.SERIAL + "\""
                    +"\n";
            buffer = line.getBytes(Charset.forName("UTF-8"));


            stream.write(buffer);

            stream.flush();
            stream.close();
*/

            //count number of lines
            if (logLines > 50) {
                return true;
            } else {
                return false;
            }
        }
        catch(Exception e)
        {
            if (D) Log.e(TAG,"Could not save: " + e.toString());
        }





        return false;
    }

    /**
     * Write a database to the FTP Server
     * This is mainly for the PSTRPM Database so that it can be edited afterwards
     *
     * @param databaseType
     */


    private String putDbFtp(String databaseType){

        SimpleDateFormat df = new SimpleDateFormat("yyyyMMddhhmmss");

        JSch jsch = new JSch();
        Session session = null ;
        Channel channel = null;
        try {

            String ftpUser = PurolatorSmartsortMQTT.getsInstance().getConfigData().getFtpParams().getFTP_USER();
            String ftpUrl = PurolatorSmartsortMQTT.getsInstance().getConfigData().getFtpParams().getFTP_URL();
            String ftpPswd = PurolatorSmartsortMQTT.getsInstance().getConfigData().getFtpParams().getFTP_PWSD();
            int ftpPort = PurolatorSmartsortMQTT.getsInstance().getConfigData().getFtpParams().getFTP_PORT();

            session = jsch.getSession(ftpUser, ftpUrl, ftpPort);
            UserInfo ui = new MyUserInfo();
            session.setUserInfo(ui);
            session.setPassword(ftpPswd);
            session.setConfig("StrictHostKeyChecking", "no");

            if(D) Log.d(TAG,"Ready to connect to: "+ ftpUrl +" for uploading");
            session.connect();

            channel = session.openChannel("sftp");
            channel.connect();
            ChannelSftp sftpChannel = (ChannelSftp) channel;
            String targetDirectory;
            if (databaseType.equals(PurolatorSmartsortMQTT.INIT_DBTYPE)){
                channel.disconnect();
                session.disconnect();
                return ("no database");
            } else {
                targetDirectory = "smartGlassUpload";
            }
            sftpChannel.cd(targetDirectory);

            if (databaseType.equals(PurolatorSmartsortMQTT.PST_DBTYPE) && PurolatorSmartsortMQTT.getsInstance().getDatabase(PurolatorSmartsortMQTT.PST_DBTYPE) !=null) {
                String location = PurolatorSmartsortMQTT.getsInstance().getDatabase(PurolatorSmartsortMQTT.PST_DBTYPE).getPath();
                FileInputStream fis = new FileInputStream(location);
                String filename = PurolatorSmartsortMQTT.getsInstance().getConfigData().getTerminalID() + PurolatorSmartsortMQTT.PST_DBTYPE+"_"
                        + df.format(new Date())
                        +".db3";
                sftpChannel.put(fis,filename);

                fis.close();

            }
            channel.disconnect();
            session.disconnect();


        } catch (JSchException e2) {
            e2.printStackTrace();
        } catch (IOException e) {
            channel.disconnect();
            session.disconnect();
            e.printStackTrace();
        } catch (SftpException e) {
            e.printStackTrace();
        }
        return("uploaded");
    }



    /**
     * Get a database or config file from the FTP Server
     *
     * @param databaseType
     * @return
     */


    private String getFtp(String databaseType) {


        JSch jsch = new JSch();
        Session session = null ;
        Channel channel = null;
        try {

            String ftpUser = PurolatorSmartsortMQTT.getsInstance().getConfigData().getFtpParams().getFTP_USER();
            String ftpUrl = PurolatorSmartsortMQTT.getsInstance().getConfigData().getFtpParams().getFTP_URL();
            String ftpPswd = PurolatorSmartsortMQTT.getsInstance().getConfigData().getFtpParams().getFTP_PWSD();
            int ftpPort = PurolatorSmartsortMQTT.getsInstance().getConfigData().getFtpParams().getFTP_PORT();

            session = jsch.getSession(ftpUser, ftpUrl, ftpPort);
            UserInfo ui = new MyUserInfo();
            session.setUserInfo(ui);
            session.setPassword(ftpPswd);
            session.setConfig("StrictHostKeyChecking", "no");

            if(D) Log.d(TAG,"Ready to connect to: "+ ftpUrl);
            session.connect();

            channel = session.openChannel("sftp");
            channel.connect();
            ChannelSftp sftpChannel = (ChannelSftp) channel;
            String targetDirectory;
            if (databaseType.equals(PurolatorSmartsortMQTT.INIT_DBTYPE)){
                targetDirectory = "Configs";
            } else {
                targetDirectory = "smartglass";
            }
            sftpChannel.cd(targetDirectory);
            java.util.Vector fileList = sftpChannel.ls(".");

            ArrayList<ChannelSftp.LsEntry> lsEntries = new ArrayList<ChannelSftp.LsEntry>();
            for (int i = 0; i < fileList.size(); i++) {
                if (fileList.elementAt(i) instanceof ChannelSftp.LsEntry) {
                    lsEntries.add((ChannelSftp.LsEntry) fileList.elementAt(i));
                }
            }

            //sort on most recent file

            Collections.sort(lsEntries, new Comparator<ChannelSftp.LsEntry>(){
                @Override
                public int compare(ChannelSftp.LsEntry file1, ChannelSftp.LsEntry file2) {
                    return file2.getAttrs().getMTime() - file1.getAttrs().getMTime();
                }
            });

/*
            for (ChannelSftp.LsEntry lsEntry:lsEntries) {

                String fileName;
                fileName = lsEntry.getFilename();
            }

*/

            for (ChannelSftp.LsEntry lsEntry:lsEntries){

                String fileName;
                fileName = lsEntry.getFilename();
//                if (D) Log.d(TAG, "Listing in order: " + fileName.toUpperCase() + " " + android.os.Build.SERIAL.toUpperCase());
                boolean storeFile = false;

                String terminalId = PurolatorSmartsortMQTT.getsInstance().getConfigData().getTerminalID();
                switch (databaseType) {
                    case PurolatorSmartsortMQTT.PIN_DBTYPE:
                        if (fileName.toUpperCase().startsWith(terminalId + PurolatorSmartsortMQTT.PIN_DBTYPE)) {
                            storeFile = true;
                        }
                        break;
                    case PurolatorSmartsortMQTT.RPM_DBTYPE:
                        if (fileName.toUpperCase().startsWith(terminalId + PurolatorSmartsortMQTT.RPM_DBTYPE)) {
                            storeFile = true;
                        }
                        break;
                    case PurolatorSmartsortMQTT.PST_DBTYPE:
                        if (fileName.toUpperCase().startsWith(terminalId + PurolatorSmartsortMQTT.PST_DBTYPE)) {
                            storeFile = true;
                        }
                        break;
                    case PurolatorSmartsortMQTT.HFPUMASTER_DBTYPE:
                        if (fileName.toUpperCase().startsWith(terminalId + PurolatorSmartsortMQTT.HFPUMASTER_DBTYPE)) {
                            storeFile = true;
                        }
                        break;
                    case PurolatorSmartsortMQTT.HFPUPC_DBTYPE:
                        if (fileName.toUpperCase().startsWith(terminalId + PurolatorSmartsortMQTT.HFPUPC_DBTYPE)) {
                            storeFile = true;
                        }
                        break;
                    case PurolatorSmartsortMQTT.PRE_DBTYPE:
                        if (fileName.toUpperCase().startsWith(terminalId + PurolatorSmartsortMQTT.PRE_DBTYPE)) {
                            storeFile = true;
                        }
                        break;
                    case PurolatorSmartsortMQTT.PARK_DBTYPE:
                        if (fileName.toUpperCase().startsWith(terminalId + PurolatorSmartsortMQTT.PARK_DBTYPE)) {
                            storeFile = true;
                        }
                        break;
                    case PurolatorSmartsortMQTT.INIT_DBTYPE:
                        if (fileName.toUpperCase().startsWith(PurolatorSmartsortMQTT.getsInstance().getConfigData().getSerialNumber().toUpperCase())) {
                            storeFile = true;
                        }
                        break;
                }
                if(storeFile) {
                    if (D) Log.d(TAG, "Database file is found, check if local exist... " + fileName);
                    String intFiles[] = context.getFilesDir().list();
                    for (String intFile : intFiles) {
//                        if (D) Log.d(TAG, "Local:  " + intFile + " Remote: " + fileName);
                        if (intFile.equals(fileName)) {
                            File dbFile = new File(context.getFilesDir() + "/" + fileName);
                            EventBus.getDefault().post(new DatabaseName(databaseType, dbFile.getPath()));
                            updateSpinner(databaseType,true,databaseType);
                            channel.disconnect();
                            session.disconnect();
                            return dbFile.getPath();
                        }
                    }
                     FileOutputStream fos = context.openFileOutput(fileName, Context.MODE_PRIVATE);
                    sftpChannel.get(fileName, fos);
                    fos.close();
                    channel.disconnect();
                    session.disconnect();

                    File dbFile = new File(context.getFilesDir() + "/" + fileName);
                    if (dbFile.exists()) {
                        if (D) Log.d(TAG, "Database file is copied to local" + dbFile.getPath());
                         updateSpinner(databaseType,true,databaseType);
                        //send the information, handeld in PurolatorSmartsortMQTTT
                        EventBus.getDefault().post(new DatabaseName(databaseType, dbFile.getPath()));

                    } else {
                        if (D) Log.d(TAG, "Database file is copied but not found. " + dbFile.getPath());
                     }

                    return dbFile.getPath();
                } else {
//                    if (D)Log.d(TAG,"Database File of type: " + databaseType +" not yet found, next loop...");
                 }
            }

            if (!silent || databaseType.equals(PurolatorSmartsortMQTT.INIT_DBTYPE)) {
                updateSpinner(databaseType,false,databaseType);

//                UIUpdater uiUpdater = new UIUpdater();//               uiUpdater.setUpdateType(PurolatorSmartsortMQTT.UPD_ERROR);
//                uiUpdater.setErrorMessage("Can not read the " + databaseType +" from FTP" );
//                EventBus.getDefault().post(uiUpdater);
            }

        } catch (JSchException e2) {
            e2.printStackTrace();
        } catch (SftpException e) {
            e.printStackTrace();
        } catch (FileNotFoundException e) {
            e.printStackTrace();

        } catch (IOException e){
            channel.disconnect();
            session.disconnect();
            e.printStackTrace();
        }
        // problem with FTP server or network connection, use local version, if any.
        //TODO TEST
//        String intFiles[] = context.getFilesDir().list();

        //sort the files, most recent first

        FileFilter filter = new FileFilter() {
            @Override
            public boolean accept(File pathname) {
                if (pathname.getName().endsWith(".db3-journal")) {
                    return false;
                } else {
                    return true;
                }
            }
        };

        ArrayList<File> validFiles = new ArrayList<File>();
        File validFilesArr[] = context.getFilesDir().listFiles(filter);
        for (File validFile:validFilesArr){
            validFiles.add(validFile);
        }


        Collections.sort(validFiles, new Comparator<File>(){
            @Override
            public int compare(File file1, File file2) {
                return (int) (file2.lastModified() - file1.lastModified());
            }
        });



        for (File validFile : validFiles) {
            String fileName = validFile.getName();
            boolean storeFile = false;
            String terminalId = PurolatorSmartsortMQTT.getsInstance().getConfigData().getTerminalID();
            switch (databaseType) {
                case PurolatorSmartsortMQTT.PIN_DBTYPE:
                    if (fileName.toUpperCase().startsWith(terminalId + PurolatorSmartsortMQTT.PIN_DBTYPE)) {
                        storeFile = true;
                    }
                    break;
                case PurolatorSmartsortMQTT.RPM_DBTYPE:
                    if (fileName.toUpperCase().startsWith(terminalId + PurolatorSmartsortMQTT.RPM_DBTYPE)) {
                        storeFile = true;
                    }
                    break;
                case PurolatorSmartsortMQTT.PST_DBTYPE:
                    if (fileName.toUpperCase().startsWith(terminalId + PurolatorSmartsortMQTT.PST_DBTYPE)) {
                        storeFile = true;
                    }
                    break;
                case PurolatorSmartsortMQTT.HFPUMASTER_DBTYPE:
                    if (fileName.toUpperCase().startsWith(terminalId + PurolatorSmartsortMQTT.HFPUMASTER_DBTYPE)) {
                        storeFile = true;
                    }
                    break;
                case PurolatorSmartsortMQTT.HFPUPC_DBTYPE:
                    if (fileName.toUpperCase().startsWith(terminalId + PurolatorSmartsortMQTT.HFPUPC_DBTYPE)) {
                        storeFile = true;
                    }
                    break;
                case PurolatorSmartsortMQTT.PRE_DBTYPE:
                    if (fileName.toUpperCase().startsWith(terminalId + PurolatorSmartsortMQTT.PRE_DBTYPE)) {
                        storeFile = true;
                    }
                    break;
                case PurolatorSmartsortMQTT.PARK_DBTYPE:
                    if (fileName.toUpperCase().startsWith(terminalId + PurolatorSmartsortMQTT.PARK_DBTYPE)) {
                        storeFile = true;
                    }
                    break;
                case PurolatorSmartsortMQTT.INIT_DBTYPE:
                    if (fileName.toUpperCase().startsWith(PurolatorSmartsortMQTT.getsInstance().getConfigData().getSerialNumber().toUpperCase())) {
                        storeFile = true;
                    }
                    break;
            }

            if (storeFile) {
                File dbFile = new File(context.getFilesDir() + "/" + fileName);
                EventBus.getDefault().post(new DatabaseName(databaseType, dbFile.getPath()));
                updateSpinner(databaseType,true,databaseType);
                return dbFile.getPath();
            }
        }

        return null;
    }

    private void updateSpinner(String dbType,boolean loadOk,String databaseType){

        // check if initiated from background, then do not show

        if (silent) {
            return;
        }

        // counter for the number if files loaded
        dbCounter++;
        if (D) Log.d(TAG,"Database counter: " + dbCounter);

        UIUpdater uiUpdate;
        if (dbCounter < MAXFILES) {
            uiUpdate = new UIUpdater();
            uiUpdate.setUpdateType(PurolatorSmartsortMQTT.UPD_STATUSPINNER);
            if (loadOk) {
                uiUpdate.setErrorMessage(databaseType + " "+context.getResources().getString(R.string.fileload));
            } else {
                uiUpdate.setErrorMessage(databaseType + " "+context.getResources().getString(R.string.fileloadfail));

            }
            EventBus.getDefault().postSticky(uiUpdate);
        }
        else {
            uiUpdate = new UIUpdater();
            uiUpdate.setUpdateType(PurolatorSmartsortMQTT.UPD_STOPSPINNER);
            EventBus.getDefault().postSticky(uiUpdate);
            dbCounter = 0;
            PurolatorSmartsortMQTT.getsInstance().setDatabasesLoaded(true);     //indicate all databases are loaded
            //signal that application is ready.


        }


    }

    public static class MyUserInfo implements UserInfo, UIKeyboardInteractive {

        @Override
        public String getPassphrase() {
            return null;
        }
        @Override
        public String getPassword() {
            return null;
        }
        @Override
        public boolean promptPassphrase(String arg0) {
            return false;
        }
        @Override
        public boolean promptPassword(String arg0) {
            return false;
        }
        @Override
        public boolean promptYesNo(String arg0) {
            return false;
        }
        @Override
        public void showMessage(String arg0) {
        }
        @Override
        public String[] promptKeyboardInteractive(String arg0, String arg1,
                                                  String arg2, String[] arg3, boolean[] arg4) {
            return null;
        }
    }


    /**
     * get a Database from the FTP Server
     * The returnd name is the full path name where the db is stored
     *
     */
    //

    private class GetFTPFile extends AsyncTask <String,Void,String>{


        @Override
        protected String doInBackground(String... params) {
            return getFtp(params[0]);
        }

        @Override
        protected void onPreExecute() {
            super.onPreExecute();
        }

        @Override
        protected void onPostExecute(String fileName) {
//            super.onPostExecute(fileName);
            if (D) Log.d(TAG,"Post execute, result: " + fileName);
//            if (fileName != null) EventBus.getDefault().post(new DatabaseName(databaseType,fileName));


        }
    }

    /**
     * put a Database from the FTP Server
     *
     */
    //

    private class PutDbFTPFile extends AsyncTask <String,Void, String >{


        @Override
        protected String doInBackground(String... params) {
            return putDbFtp(params[0]);
        }

        @Override
        protected void onPreExecute() {
            super.onPreExecute();
        }
        @Override
        protected void onPostExecute(String status) {
//            super.onPostExecute(fileName);
            if (D) Log.d(TAG,"Database put to server: " + status);
        }
    }

    private class PutFTPFile extends AsyncTask <Void,Void,Boolean>{


        @Override
        protected Boolean doInBackground(Void... params) {

            JSch jsch = new JSch();
            Session session = null ;
            Channel channel = null;


            if (D) Log.d(TAG,"+++In PutFTPFile+++ ");

            try {
                File logDirectory = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS);


                //first check if a .txt_ file exist,
                class UndTxtFilter implements FileFilter {
                    @Override
                    public boolean accept(File file) {
                        return !file.isHidden() && (file.getName().endsWith(".txt_"));
                    }
                }
                File[] files = logDirectory.listFiles(new UndTxtFilter());


                //no files to process
                if (files == null ) return true;
                if (files.length == 0) return true;


                String ftpUser = PurolatorSmartsortMQTT.getsInstance().getConfigData().getFtpParams().getFTP_USER();
                String ftpUrl = PurolatorSmartsortMQTT.getsInstance().getConfigData().getFtpParams().getFTP_URL();
                String ftpPswd = PurolatorSmartsortMQTT.getsInstance().getConfigData().getFtpParams().getFTP_PWSD();
                int ftpPort = PurolatorSmartsortMQTT.getsInstance().getConfigData().getFtpParams().getFTP_PORT();

                session = jsch.getSession(ftpUser, ftpUrl, ftpPort);
                UserInfo ui = new MyUserInfo();
                session.setUserInfo(ui);
                session.setPassword(ftpPswd);
                session.setConfig("StrictHostKeyChecking", "no");

                if(D) Log.d(TAG,"Ready to connect to: "+ ftpUrl);
                session.connect();

                channel = session.openChannel("sftp");
                channel.connect();
                ChannelSftp sftpChannel = (ChannelSftp) channel;
                String targetDirectory = "Logs";

                sftpChannel.cd(targetDirectory);




                for (int i=0;i<files.length;i++){
                    String logFilePath = files[i].getAbsolutePath();
                    String putFileName = new File(logFilePath).getName();
                    putFileName = putFileName.substring(0,putFileName.length()-1);   //remove the underscore

                    if (D) Log.d(TAG,"Ready to put file to FTP: " + putFileName);

                    FileInputStream fis = new FileInputStream(logFilePath);
                    sftpChannel.put(logFilePath,putFileName);

                    fis.close();
                    channel.disconnect();
                    session.disconnect();

                    new File(logFilePath).delete();

                    if (D) Log.d(TAG,"File logged & deleted");
                }

                /*
		      		if ((new File(logFilePath)).exists()){
		      			Log.d(TAG,logFilePath + " exists already");
		      		} else {
		      			Log.d(TAG,logFilePath + " does not exist");
		      		}
*/

            } catch (JSchException e2) {
                e2.printStackTrace();
            } catch (SftpException e) {
                e.printStackTrace();
            } catch (FileNotFoundException e) {
                channel.disconnect();
                session.disconnect();
                e.printStackTrace();

            } catch (IOException e){
                e.printStackTrace();
            }

            return false;
        }

        @Override
        protected void onPreExecute() {
            super.onPreExecute();
        }

        @Override
        protected void onPostExecute(Boolean aBoolean) {
            super.onPostExecute(aBoolean);
        }
    }

}
