
public class PhantomInterfaceConsumer {

    private final PhantomInterface iface = new PhantomInterfaceImpl();

    public void methodRef() {
        iface.foo();
    }

}
