package issues.i99;

public class DataHoarder {
    public String field1;
    public String field2;
    public String field3;
    public String field4;
    public String field5;

    public static class DH4{
        public DH4(DH3 dh3) {
            this.dh3 = dh3;
        }

        public DH3 dh3;
    }

    public static class DH3{
        public DH3(DH2 dh2) {
            this.dh2 = dh2;
        }

        public DH2 dh2;
    }


    public static class DH2{
        public DH2(DH1 dh1) {
            this.dh1 = dh1;
        }

        public DH1 dh1;
    }

    public static class DH1{
        public DH1(DataHoarder data) {
            this.data = data;
        }

        public DataHoarder data;
    }
}

