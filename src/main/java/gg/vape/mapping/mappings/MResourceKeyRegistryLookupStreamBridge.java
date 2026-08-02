package gg.vape.mapping.mappings;

import gg.vape.mapping.MappedClasses;
import gg.vape.mapping.Mapping;
import gg.vape.mapping.MappingMethod;
import java.lang.reflect.Method;
import java.util.stream.Stream;

public class MResourceKeyRegistryLookupStreamBridge
extends Mapping {
    private static final String LIST_ELEMENTS_METHOD_NAME = "listElements";
    private final MappingMethod listElementsMethod;

    public MResourceKeyRegistryLookupStreamBridge() {
        super(MappedClasses.Da);
        this.listElementsMethod = this.Y(LIST_ELEMENTS_METHOD_NAME, true, Stream.class, new Class[]{});
    }

    public Stream<Object> listElements(Object registryLookup) {
        if (registryLookup == null) {
            return Stream.empty();
        }

        Object mappedResult = this.listElementsMethod.invokeObject(registryLookup, new Object[0]);
        if (mappedResult instanceof Stream) {
            return (Stream)mappedResult;
        }

        // Forge 1.21.x can expose HolderLookup through a transformed interface
        // for which the generated/native mapping invoker has no registered
        // Method yet. Resolve the vanilla runtime method directly as a fallback.
        try {
            Method method = registryLookup.getClass().getMethod(LIST_ELEMENTS_METHOD_NAME);
            method.setAccessible(true);
            Object reflectedResult = method.invoke(registryLookup);
            if (reflectedResult instanceof Stream) {
                return (Stream)reflectedResult;
            }
        }
        catch (Throwable ignored) {
            // Registry enumeration is optional for startup; callers receive an
            // empty stream instead of a null value that would cause an NPE.
        }
        return Stream.empty();
    }
}
