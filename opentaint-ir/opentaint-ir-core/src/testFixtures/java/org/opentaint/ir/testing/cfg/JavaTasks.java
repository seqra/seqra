package org.opentaint.ir.testing.cfg;

import kotlin.NotImplementedError;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class JavaTasks {
    public static void insertionSort(List<List<String>> time) {
        for (int i = 1; i < time.size(); i++) {
            List<String> currentList = time.get(i);
            int current = Integer.parseInt(time.get(i).get(3));
            int j = i - 1;

            for (; j >= 0; j--) {
                if (Integer.parseInt(time.get(j).get(3)) > current) time.set(j + 1, time.get(j));
                else break;
            }

            time.set(j + 1, currentList);
        }
    }
    static public void sortTimes(String inputName, String outputName) throws IOException {
        List<List<String>> timeAM = new ArrayList<>();
        List<List<String>> timePM = new ArrayList<>();
        String hour, min, sec;
        String timeFlag;
        int sum;

        try (BufferedReader reader = new BufferedReader(new FileReader(inputName))) {
            String line;
            while ((line = reader.readLine()) != null) {
                List<String> current = new ArrayList<>();

                hour = line.substring(0, 2);
                min = line.substring(3, 5);
                sec = line.substring(6, 8);
                timeFlag = line.substring(9, 11);

                if (Integer.parseInt(hour) >= 60 || Integer.parseInt(min) >= 60 || Integer.parseInt(sec) >= 60) {
                    throw new NumberFormatException();
                }

                if (hour.equals("12")) sum = Integer.parseInt(min) * 60 + Integer.parseInt(sec);
                else sum = Integer.parseInt(hour) * 3600 + Integer.parseInt(min) * 60 + Integer.parseInt(sec);

                current.add(hour);
                current.add(min);
                current.add(sec);
                current.add(String.valueOf(sum));
                if (timeFlag.equals("AM")) timeAM.add(current);
                else if (timeFlag.equals("PM")) timePM.add(current);
                else throw new NumberFormatException();
            }
        }

        insertionSort(timeAM);
        insertionSort(timePM);

        try (BufferedWriter writer = new BufferedWriter(new FileWriter(outputName))) {
            for (List<String> currentList : timeAM) {
                writer.write(currentList.get(0) + ":" + currentList.get(1) + ":" + currentList.get(2) + " AM\n");
            }
            for (List<String> currentList : timePM) {
                writer.write(currentList.get(0) + ":" + currentList.get(1) + ":" + currentList.get(2) + " PM\n");
            }
        }
    }

    static public void sortAddresses(String inputName, String outputName) {
        throw new NotImplementedError();
    }

    public static void heapSort(List<Double> arr) throws IOException, ArrayIndexOutOfBoundsException {
        int n = arr.size();

        for (int i = n / 2 - 1; i >= 0; i--) {
            heapify(arr, n, i);
        }

        for (int i = n - 1; i >= 0; i--) {
            double temp = arr.get(0);
            arr.set(0, arr.get(i));
            arr.set(i, temp);

            heapify(arr, i, 0);
        }
    }

    public static void heapify(List<Double> arr, int n, int i) {
        int largest = i;
        int l = 2 * i + 1;
        int r = 2 * i + 2;

        if (l < n && arr.get(l) > arr.get(largest)) largest = l;

        if (r < n && arr.get(r) > arr.get(largest)) largest = r;

        if (largest != i) {
            double temp = arr.get(i);
            arr.set(i, arr.get(largest));
            arr.set(largest, temp);

            heapify(arr, n, largest);
        }
    }

    static public void sortTemperatures(String inputName, String outputName) throws IOException {
        List<Double> temperature = new ArrayList<>();

        try (BufferedReader reader = new BufferedReader(new FileReader(inputName))) {
            String line;

            while ((line = reader.readLine()) != null) {
                double number = Double.parseDouble(line);
                temperature.add(number);
            }
        } catch (IOException e) {
            e.printStackTrace();
        }

        heapSort(temperature);

        // O(n)
        try (BufferedWriter writer = new BufferedWriter(new FileWriter(outputName))) {
            for (Double number : temperature) {
                writer.write((number) + "\n");
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    static public void sortSequence(String inputName, String outputName) {
        int[] types = new int[100000000];
        ArrayList<Integer> numbers = new ArrayList<>();
        int maxNumberType = -1;
        int maxNumberValue = -1;

        try (BufferedReader reader = new BufferedReader(new FileReader(inputName))) {
            String line;

            while ((line = reader.readLine()) != null) {
                int current = Integer.parseInt(line);
                numbers.add(current);
                types[current]++;
            }
        } catch (IOException e) {
            e.printStackTrace();
        }

        for (int i = 0; i < types.length - 1; i++) {
            if (types[i] > maxNumberValue) {
                if (maxNumberType < i) maxNumberType = i;
                else continue;
                maxNumberValue = types[i];
            }
        }

        int finalMaxNumberType = maxNumberType;
        numbers.removeIf(element -> (element == finalMaxNumberType));

        try (BufferedWriter writer = new BufferedWriter(new FileWriter(outputName))) {
            for (Integer number : numbers) {
                writer.write(number + "\n");
            }

            for (int i = 0; i < maxNumberValue; i++) {
                writer.write(maxNumberType + "\n");
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    static public String longestCommonSubstring(String first, String second) {
        if (first.isEmpty() || second.isEmpty()) {
            return "";
        }

        int[][] twoDimArray = new int[first.length() + 1][second.length() + 1];
        int index = 0;
        int maxReps = 0;
        for (int i = 1; i <= first.length(); i++) {
            for (int j = 1; j <= second.length(); j++) {
                if (first.charAt(i - 1) != second.charAt(j - 1)) {
                    twoDimArray[i][j] = 0;
                }
                else {
                    twoDimArray[i][j] = twoDimArray[i - 1][j - 1] + 1;
                    if (twoDimArray[i][j] > maxReps) {
                        maxReps = twoDimArray[i][j];
                        index = i;
                    }
                }

            }
        }
        return first.substring(index - maxReps, index);
    }

    static public int calcPrimesNumber(int limit) {
        if (limit <= 1) return 0;
        int p = 2;
        int step = p;
        int numberPrimes = 0;
        boolean[] primeArray = new boolean[limit + 1];
        while (p * p <= limit) {
            if (!primeArray[p]) {
                for (int cycleP = p * p; cycleP <= limit; cycleP += step) {
                    primeArray[cycleP] = true;
                }
            }
            p++;
            step = 2 * p;
        }
        for (int i = 2; i <= limit; i++) {
            if (!primeArray[i]) {
                numberPrimes++;
            }
        }
        return numberPrimes;
    }
}

