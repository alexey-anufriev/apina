package fi.evident.apina.java.reader;

import fi.evident.apina.java.model.JavaClass;
import fi.evident.apina.java.model.JavaField;
import org.junit.Test;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.util.Comparator;
import java.util.List;

import static fi.evident.apina.java.reader.JavaTypeMatchers.*;
import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertThat;

public class ClassMetadataReaderTest {

    @Test
    public void loadingFields() {
        JavaClass javaClass = loadClass(TestClass.class);

        List<JavaField> fields = javaClass.getFields();

        assertThat(javaClass.getSchema(), is(singletonSchema("T", basicType(CharSequence.class))));

        assertThat(fields.size(), is(3));
        assertThat(javaClass.getField("field1").getType(), is(typeWithRepresentation("java.lang.String")));
        assertThat(javaClass.getField("field2").getType(), is(typeWithRepresentation("java.util.List<java.lang.String>")));
        assertThat(javaClass.getField("field3").getType(), is(typeVariable("T")));
    }

    @Test
    public void innerClassWithOuterBounds() {
        loadClass(AnonymousInnerClassWithOuterBounds.createInnerClassInstance().getClass());
    }

    private static JavaClass loadClass(Class<?> cl) {
        try (InputStream in = openInputStreamForClassBytes(cl)) {
            return ClassMetadataReader.loadMetadata(in);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private static InputStream openInputStreamForClassBytes(Class<?> cl) throws IOException {
        String resourceName = cl.getName().replace('.', '/') + ".class";
        InputStream inputStream = cl.getClassLoader().getResourceAsStream(resourceName);
        if (inputStream != null)
            return inputStream;
        else
            throw new FileNotFoundException(resourceName);
    }

    @SuppressWarnings("unused")
    private static final class TestClass<T extends CharSequence> {

        public String field1;

        public List<String> field2;

        private T field3;

        public void method1() {
        }

        public String method2() {
            throw new UnsupportedOperationException();
        }

        public T method3(T x) {
            throw new UnsupportedOperationException();
        }
    }

    private static final class AnonymousInnerClassWithOuterBounds {

        @SuppressWarnings("Convert2Lambda")
        public static <T> Comparator<T> createInnerClassInstance() {
            return new Comparator<T>() {
                @SuppressWarnings("ComparatorMethodParameterNotUsed")
                @Override
                public int compare(T o1, T o2) {
                    return 0;
                }
            };
        }
    }
}