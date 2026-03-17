package issues.i95;

public class User {
    public void outsideControlledData(SpecificData data) {
        String a = data.getData();
        sink(a);
    }

    public void privateControlledData() {
        SpecificData data = new SpecificData();
        sink(data.getData());
    }

    public void sink(String a) {}
}
