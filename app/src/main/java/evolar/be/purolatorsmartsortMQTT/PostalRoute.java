package evolar.be.purolatorsmartsortMQTT;

/**
 * Created by Dirk on 1/06/17.
 * Array of PostalCodes & assigned routes.
 * For manual assignment of routes to postalcodes
 * Is done on the a wrist device and stored on the tablet
 */

public class PostalRoute {
    private String mPostalCode;
    private String mRoute;

    public PostalRoute(){


    }

    public void setPostCode(String postalCode){
        mPostalCode = postalCode;
    }

    public String getPostalCode(){
        return mPostalCode;
    }

    public void setRoute(String route){
        mRoute = route;
    }

    public String getRoute(){
        return mRoute;
    }


}
