import com.protoxon.S4J.SLSAction;
import com.protoxon.S4J.SLSBuilder;
import com.protoxon.S4J.client.actions.ServerCreationAction;
import com.protoxon.S4J.client.entites.ClientServer;
import com.protoxon.S4J.client.entites.SLSClient;

public class create {

    public static void main(String args[]) {

        SLSClient api = SLSBuilder.createClient("http://localhost:5050", "sls-AsJF7g8Z3K_jJ0Zw7ARgWI3lyjkBja_UxYC2b2SCAd4A");

        SLSAction<ClientServer> action = api.createServer().setBlueprintId("chunk_runner");
        ClientServer server = action.execute();
        System.out.println(server.getId());

    }
}
