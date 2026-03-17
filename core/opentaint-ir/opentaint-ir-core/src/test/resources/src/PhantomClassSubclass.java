
public class PhantomClassSubclass extends PhantomClass {

    @Override
    public void run() {
        super.run();
        System.out.println(super.intField);
        staticRun();
    }
}
