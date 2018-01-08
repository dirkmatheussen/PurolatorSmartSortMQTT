package evolar.be.purolatorsmartsortMQTT.events;

/**
 * Created by Dirk on 16/03/16.
 * POJO Object that contains the database type to put to the FTP Server (in smartGlassUpload directory)
 */
public class PutDb {


    private final String databaseType;

    public PutDb(String databaseType){


        this.databaseType =  databaseType;
    }

    public String getDatabaseType() {
        return databaseType;
    }

}
