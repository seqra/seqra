package test.samples;

public class ConstructorSample {
    private String name;
    private int value;

    public /*noArgConstructor:start*/ConstructorSample()/*noArgConstructor:end*/ {
        this.name = "default";
        this.value = 0;
    }

    public /*constructorEntry:start*/ConstructorSample(String name)/*constructorEntry:end*/ {
        this.name = name;
        this.value = 0;
    /*constructorExit:start*/}/*constructorExit:end*/

    public ConstructorSample(String name, int value) {
        this.name = name;
        this.value = value;
    }
}
