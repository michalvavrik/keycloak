import io.quarkus.datasource.common.runtime.DataSourceUtil;
public class scratch {
    public static void main(String[] args) {
        System.out.println(DataSourceUtil.isDefault("<default>"));
    }
}
