package gg.vape.wrapper.impl;

import java.util.stream.Stream;

public class EnchantmentRegistryLookup
extends ResourceKeyRegistryLookup {
    public Stream<EnchantmentHolder> listElements() {
        if (EnchantmentRegistryLookup.vapeInstance.getMappingsMapperCompat().R2 == null) {
            return Stream.empty();
        }
        Stream<Object> elements = EnchantmentRegistryLookup.vapeInstance.getMappingsMapperCompat().R2.listElements(this.getObject());
        return elements == null ? Stream.empty() : elements.map(EnchantmentHolder::new);
    }

    public EnchantmentRegistryLookup(Object wrappedObject) {
        super(wrappedObject);
    }
}
