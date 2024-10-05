import static net.slimelabs.sls.utils.MemoryConverter.convertToMegabytes;

/**
 * A test class for the {@link net.slimelabs.sls.utils.MemoryConverter} class.
 * calls the convertToMegabytes() method with various inputs and prints the
 * return value to the console
 */
public class MemoryConverterTest {
    public static void main(String[] args) {
        System.out.println(convertToMegabytes("2.5GB")); // 2560
        System.out.println(convertToMegabytes("300.80mb")); // 300
        System.out.println(convertToMegabytes("500kb")); // 0
        System.out.println(convertToMegabytes("5.75gigabytes")); // 5888
        System.out.println(convertToMegabytes("1500.5KB")); // 1
    }
}
