import org.jetbrains.annotations.NotNull;

public class UntrustedLoopBound {
    public static class Message {
        int readInt() {
            return 999;
        }
    }

    public void handle(@NotNull Message data) {
        int n = -(-(data.readInt() + 1));
        for (int i = 0; i < n; i++) {
            System.out.println("i = " + i);
        }
    }
}
