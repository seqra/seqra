
public class PhantomCodeConsumer {

    private final PhantomClass clazz = new PhantomClass();

    public void fieldRef() {
        System.out.println(clazz.intField);
        System.out.println(clazz.stringField);
    }

    public void methodRef() {
        clazz.run();
    }
}


