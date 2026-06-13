package nc.opt.aiassistant.service.vector;

import jakarta.annotation.PostConstruct;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Supplier;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Component;

@Component
@AllArgsConstructor
@Slf4j
public class VectorStoreServiceRegistry {

    private final ApplicationContext applicationContext;
    private final Map<DataType, Supplier<? extends VectorStoreServiceInterface>> converterServiceMap = new HashMap<>();

    // Initialize the map after the bean is constructed
    @PostConstruct
    public void init() {
        // Get all beans that implement VectorStoreServiceInterface
        Map<String, VectorStoreServiceInterface> converterBeans = applicationContext.getBeansOfType(VectorStoreServiceInterface.class);
        log.info("Found {} VectorStoreServiceInterface beans: {}", converterBeans.size(), converterBeans.keySet());
        // For each converter, map its dataType to the supplier
        converterBeans.values().forEach(converter -> {
            DataType type = converter.getDataType();
            log.info("Registering converter for DataType {}: {} (bean: {})", type, converter.getClass().getName(), converter);
            converterServiceMap.put(type, () -> converter);
        });
    }

    // Method to retrieve the appropriate service based on the campaingType
    public VectorStoreServiceInterface getConverterService(DataType campaingType) {
        Supplier<? extends VectorStoreServiceInterface> serviceSupplier = converterServiceMap.get(campaingType);
        return (serviceSupplier != null) ? serviceSupplier.get() : null;
    }
}
