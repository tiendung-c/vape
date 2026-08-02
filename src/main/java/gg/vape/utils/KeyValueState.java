package gg.vape.utils;

public final class KeyValueState<K, V> {
    private final K key;
    private final V value;

    private KeyValueState(K key, V value) {
        this.key = key;
        this.value = value;
    }

    public static <K, V> KeyValueState<K, V> create(K key, V value) {
        return new KeyValueState<K, V>(key, value);
    }

    public K getKey() {
        return this.key;
    }

    public V getValue() {
        return this.value;
    }
}
