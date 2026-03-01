
public class PhantomDeclarationConsumer {

    PhantomDeclaration f = new PhantomDeclaration();

    public void call() {
        System.out.println(f.y);
        f.runSomething();
    }

}
