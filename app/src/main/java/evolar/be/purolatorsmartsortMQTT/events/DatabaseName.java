package evolar.be.purolatorsmartsortMQTT.events;

/**
 * Created by Dirk on 18/03/16.
 */
public class DatabaseName {


    private String databaseName;
    private String databaseType;

    public DatabaseName(String databaseType, String databaseName){

        this.databaseName = databaseName;
        this.databaseType = databaseType;
    }

    public String getDatabaseName() {
        return databaseName;
    }

    public void setDatabaseName(String databaseName) {
        this.databaseName = databaseName;
    }

    public String getDatabaseType() {
        return databaseType;
    }

    public void setDatabaseType(String databaseType) {
        this.databaseType = databaseType;
    }

}
