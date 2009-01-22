package javax.el;

import java.io.Serializable;

/**
 * This encapsulates a base model object and one of its properties.
 *
 * @since EL 2.2
 */

public class ValueReference implements Serializable {

    private Object base;
    private Object property;

    public ValueReference(Object base, Object porperty) {
        this.base = base;
        this.property = property;
    }

    public Object getBase() {
        return base;
    }

    public Object getProperty() {
        return property;
    }

}
