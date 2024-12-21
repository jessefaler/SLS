import net.slimelabs.sls.server.core.Server;
import net.slimelabs.sls.utils.MinecraftJavaVersionMapper;

public class MinecraftJavaVersionMapperTest {

    public static void main(String[] args) throws Exception {
        System.out.println(MinecraftJavaVersionMapper.getRequiredJavaVersion("1.21"));
        System.out.println(MinecraftJavaVersionMapper.getRequiredJavaVersion("1.18"));
        System.out.println(MinecraftJavaVersionMapper.getRequiredJavaVersion("1.17"));
        System.out.println(MinecraftJavaVersionMapper.getRequiredJavaVersion("1.19.2"));
        System.out.println(MinecraftJavaVersionMapper.getRequiredJavaVersion("1.15.2"));
    }

}
