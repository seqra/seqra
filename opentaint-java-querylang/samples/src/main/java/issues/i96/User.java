package issues.i96;

public class User {
    public void dataPassThrough(SpecificData data) {
        sink(data);
    }

    public void privateControlledData() {
        SpecificData data = new SpecificData();
        sink(data.getData());
    }

    public void outsideControlledData(SpecificData data) {
        String a = data.getData();
        sink(a);
    }

    public void sink(String a) {}

    public void sink(SpecificData a) {}
}
