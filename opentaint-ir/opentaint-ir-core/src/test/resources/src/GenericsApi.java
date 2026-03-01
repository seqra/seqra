
import java.io.Closeable;

public class GenericsApi<T extends Closeable> {

    public T closeable;

    public GenericsApi(T closeable) {
        this.closeable = closeable;
    }

    public GenericsApi<T> call(GenericsApi<T> arg) {
        return new GenericsApi<>(arg.closeable);
    }

    public <W> GenericsApi<W> callGeneric(GenericsApi<W> arg) {
        return new GenericsApi<>(arg.closeable);
    }

}
