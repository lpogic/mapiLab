package app.model.input;

public interface Source<T> extends Sup<T> {
    boolean present();
    T getOr(T reserve);
    default Sup<T> or(T reserve) {
        return () -> getOr(reserve);
    }
}
