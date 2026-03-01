
public class PhantomInterfaceImpl implements PhantomInterface {

    @Override
    public void foo() {
        PhantomInterface.staticFoo();
    }
}
