package ceylon.language;

public abstract class Object extends Void {

    /** A developer-friendly string representing the instance. */
    public ceylon.language.String getString() {
        return String.instance("");
    }

    public synchronized static void addAnnotation(java.lang.Class klass,
                                                  Sequence<?> annos) {
        Types.create(klass).addAnnotations(klass, annos);
    }

    public synchronized static void addAnnotation(java.lang.Class klass,
                                         String memberName,
                                         Sequence<?> annos) {
        Types.create(klass).addAnnotations(klass, annos);
    }

    public Type getType () {
        return Types.create(getClass());
    }

    public Boolean identical(Object other) {
        return Boolean.instance(this == other);
    }
}
