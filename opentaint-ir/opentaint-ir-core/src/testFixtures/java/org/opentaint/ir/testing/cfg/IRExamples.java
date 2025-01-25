package org.opentaint.ir.testing.cfg;

import java.io.*;
import java.net.DatagramSocket;

public class IRExamples {
    int x;

    public void testField(int x) {
        this.x = x;
    }

    public int testPrimitives(int a, int b) {
        int c = 0;
        if (a > b) {
            c = a;
        } else {
            c = b;
        }
        int d = c;
        int e = 0;
        if (d > 100) {
            e = 100 - d;
        } else {
            e = -d;
        }
        return e;
    }

    public int runBinarySearchIteratively(
            int[] sortedArray, int key, int low, int high) {
        int index = Integer.MAX_VALUE;

        while (low <= high) {
            int mid = low + ((high - low) / 2);
            if (sortedArray[mid] < key) {
                low = mid + 1;
            } else if (sortedArray[mid] > key) {
                high = mid - 1;
            } else if (sortedArray[mid] == key) {
                index = mid;
                break;
            }
        }
        return index;
    }

    public int runBinarySearchIterativelyTryCatch(
            int[] sortedArray, int key, int low, int high) {
        int index = Integer.MAX_VALUE;

        while (low <= high) {
            int mid = low + ((high - low) / 2);
            try {
                if (sortedArray[mid] < key) {
                    low = mid + 1;
                } else if (sortedArray[mid] > key) {
                    high = mid - 1;
                } else if (sortedArray[mid] == key) {
                    index = mid;
                    break;
                }
            } catch (Throwable e) {
                System.out.println("Exception");
            }
        }
        return index;
    }

    public boolean test(int element) {
        return (element == 123);
    }

    static public void sortTimes(String inputName, String outputName) throws IOException {
        String hour;
        try (BufferedReader reader = new BufferedReader(new FileReader(inputName))) {
            String line;
            while ((line = reader.readLine()) != null) {
                hour = line.substring(0, 2);
                if (Integer.parseInt(hour) >= 60) {
                    throw new NumberFormatException();
                }
            }
        }
    }

    static public void sortSequence(String inputName, String outputName) throws IOException {
        try (BufferedReader reader = new BufferedReader(new FileReader(inputName))) {
            String line;

            while ((line = reader.readLine()) != null) {
            }
        } catch (IOException e) {
        }

        try (BufferedWriter writer = new BufferedWriter(new FileWriter(outputName))) {
        } catch (IOException e) {
        }
    }

    static public void multiCatch(DatagramSocket s) {
        try {
            s.receive(null);
        } catch (IOException | IllegalStateException e) {
        }
    }

    public String concatTest(String s, int a) {
        return s + a;
    }

    public void initStringWithNull(String arg) {
        String myString = null;

        if (myString != null) {
            myString.length();
        }
    }

    public int testArrays(String[] arg) {
        int index = 12;
        String x = arg[index];
        if(x != null) {
            return x.length();
        }
        return -1;
    }
}
