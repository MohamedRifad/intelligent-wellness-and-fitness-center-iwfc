package iwfc.repository;

import iwfc.exception.DuplicateDataException;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;

public final class GenericRepository<T> {
    private final Map<String, T> items = new HashMap<>();
    private final Function<T, String> idExtractor;

    public GenericRepository(Function<T, String> idExtractor) {
        this.idExtractor = Objects.requireNonNull(idExtractor, "ID extractor is required");
    }

    public void add(T item) throws DuplicateDataException {
        Objects.requireNonNull(item, "Item is required");
        String id = requireId(idExtractor.apply(item));
        if (items.containsKey(id)) throw new DuplicateDataException("Duplicate ID: " + id);
        items.put(id, item);
    }

    public Optional<T> findById(String id) { return Optional.ofNullable(items.get(requireId(id))); }
    public boolean containsId(String id) { return items.containsKey(requireId(id)); }

    public List<T> findAll() {
        return Collections.unmodifiableList(new ArrayList<>(items.values()));
    }

    private static String requireId(String id) {
        if (id == null || id.isBlank()) throw new IllegalArgumentException("ID is required");
        return id.trim();
    }
}
