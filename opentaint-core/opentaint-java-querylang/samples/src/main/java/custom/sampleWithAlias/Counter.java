package custom.sampleWithAlias;

public class Counter {
    public static class One {
        public Two two;

        public One(String data) {
            this.two = new Two(data);
        }

        public String oneTwoThree() {
            return "1_" + this.two.twoThree() + "_1";
        }
    }

    public static class Two {
        public Three three;

        public Two(String data) {
            this.three = new Three(data);
        }

        public String twoThree() {
            return "2_" + this.three.three() + "_2";
        }
    }

    public static class Three {
        public String data;

        public Three(String data) {
            this.data = data;
        }

        public String three() {
            return "3_" + this.data + "_3";
        }
    }
}
