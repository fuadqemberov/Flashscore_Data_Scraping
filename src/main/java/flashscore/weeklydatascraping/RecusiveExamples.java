package flashscore.weeklydatascraping;

public class RecusiveExamples {
    public static void main(String[] args) {
        String str = "radaru";
        boolean palidrom = isPolidrome(str, 0, str.length()-1);
        System.out.println("Bu verdiyiniz soz :" + str +" "+ palidrom + " dir");
    }

    private static boolean isPolidrome(String str, int start, int end) {
        if (start >= end) {
            return true;
        }
        if (str.charAt(start) != str.charAt(end)) {
            return false;
        }
        return isPolidrome(str, start + 1, end - 1);
    }

    private static int factorial(int i) {
        if (i == 0 || i == 1) {
            return 1;
        }
        return i * factorial(i - 1);
    }

    private static int sumArray(int[] arr, int length) {
        if (length == 0) {
            return 0;
        }
        return arr[length - 1] + sumArray(arr, length - 1);
    }
}
