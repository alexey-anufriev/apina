package fi.evident.apina.spring.java.model;

import java.util.*;
import java.util.stream.Stream;

import static java.util.Arrays.asList;
import static java.util.Collections.singletonList;
import static java.util.Collections.unmodifiableList;
import static java.util.Objects.requireNonNull;
import static java.util.stream.Collectors.joining;

public final class JavaAnnotation {

    /** Name of the annotation type */
    private final QualifiedName name;

    /**
     * Attributes of the annotation.
     *
     * Type of values depends on the type of attributes:
     * <ul>
     *     <li>for basic Java types (String, Integer, etc) corresponding Java classes are used</li>
     *     <li>enumeration values are represented as {@link EnumValue}s</li>
     *     <li>nested annotations are represented as nested {@link JavaAnnotation}s</li>
     *     <li>for arrays, everything is stored inside {@code Object[]}</li>
     * </ul>
     */
    private final Map<String, Object> attributes = new LinkedHashMap<>();

    public JavaAnnotation(QualifiedName name) {
        this.name = requireNonNull(name);
    }

    public QualifiedName getName() {
        return name;
    }

    public void setAttribute(String name, Object value) {
        attributes.put(name, value);
    }

    public Optional<Object> getAttribute(String name) {
        return Optional.ofNullable(attributes.get(name));
    }

    public List<Object> getAttributeValues(String name) {
        Object value = attributes.get(name);
        if (value == null)
            return Collections.emptyList();
        else if (value instanceof Object[])
            return unmodifiableList(asList((Object[]) value));
        else
            return singletonList(value);
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append('@').append(name);

        if (attributes.size() == 1 && attributes.containsKey("value")) {
            sb.append('(');
            writeValue(sb, attributes.get("value"));
            sb.append(')');

        } else if (!attributes.isEmpty()) {
            sb.append('(');
            for (Iterator<Map.Entry<String, Object>> it = attributes.entrySet().iterator(); it.hasNext(); ) {
                Map.Entry<String, Object> entry = it.next();
                String name = entry.getKey();
                Object value = entry.getValue();

                sb.append(name).append('=');

                writeValue(sb, value);

                if (it.hasNext())
                    sb.append(", ");
            }
            sb.append(')');
        }

        return sb.toString();
    }

    private void writeValue(StringBuilder sb, Object value) {
        if (value instanceof Object[]) {
            Object[] array = (Object[]) value;

            if (array.length == 1)
                writePrimitive(sb, array[0]);
            else
                sb.append(Stream.of(array).map(Object::toString).collect(joining(",", "{", "}")));
        } else {
            writePrimitive(sb, value);
        }
    }

    private void writePrimitive(StringBuilder sb, Object value) {
        if (value instanceof String) {
            String s = (String) value;
            sb.append('"').append(s.replace("\"", "\\\"")).append('"');
        } else {
            sb.append(value);
        }
    }
}
