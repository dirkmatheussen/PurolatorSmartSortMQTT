package evolar.be.purolatorsmartsortMQTT.events;

/**
 * Created by Dirk on 16/03/16.
 * POJO Object that contains the database type to fetch from the FTP Server
 */
public class FetchDb {


    private final String databaseType;
    private final boolean silent;


    public FetchDb(String databaseType, boolean silent){


        this.databaseType =  databaseType;
        this.silent = silent;
    }

    public String getDatabaseType() {
        return databaseType;
    }

    public boolean isSilent() {
        return silent;
    }

}
