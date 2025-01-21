import net.slimelabs.sls.SLS;

/**
 * A class for testing the console logos
 */
public class LogoTest {
    public static void main(String[] args) {
        // NOTE: You first have to run the SLS package for changes to take place
        // Then you can run this class
        SLS sls = new SLS(null, null);
        System.out.println(sls.startMessage());
        System.out.println(sls.shutdownMessage());
    }
}
