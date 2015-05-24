package fi.evident.apina.java.model.type;

import static java.util.Objects.requireNonNull;

/**
 * Represents a raw Java type, like {@link Class}.
 */
public final class JavaBasicType extends JavaType {

    public final String name;

    public JavaBasicType(String name) {
        this.name = requireNonNull(name);
    }

    public JavaBasicType(Class<?> cl) {
        this.name = requireNonNull(cl.getName());
    }

    @Override
    public <C, R> R accept(JavaTypeVisitor<C, R> visitor, C ctx) {
        return visitor.visit(this, ctx);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        JavaBasicType that = (JavaBasicType) o;

        return name.equals(that.name);
    }

    @Override
    public int hashCode() {
        return name.hashCode();
    }

    @Override
    public String toString() {
        return name;
    }

    public JavaBasicType toBasicType() {
        return this;
    }

    @Override
    public boolean isVoid() {
        return name.equals("void");
    }
}