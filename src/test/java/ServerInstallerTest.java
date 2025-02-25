import net.slimelabs.sls.installer.ServerInstaller;
import net.slimelabs.sls.server.core.Server;
import net.slimelabs.sls.server.core.ServerInstance;

public class ServerInstallerTest {

    public static void main(String[] args) {
        ServerInstaller serverInstaller = new ServerInstaller();
        serverInstaller.SERVERS_FOLDER = "/home/jesse/Desktop/network/proxy/plugins/sls/servers/sls-paper";
        serverInstaller.installServer("1.21");
    }

}
