package flashscore.weeklydatascraping;


import java.util.Arrays;

public class main {
    public static void main(String[] args) {
        System.out.println(Arrays.toString(plusOne(new int[]{9, 9, 9})));

    }

    public static int strStr(String haystack, String needle) {
        if (needle.isEmpty()) return 0;

        for (int i = 0; i <= haystack.length() - needle.length(); i++) {
            int j = 0;
            while (j < needle.length() && haystack.charAt(i + j) == needle.charAt(j)) {
                j++;
            }
            if (j == needle.length()) {
                return i;
            }
        }
        return -1;
    }

    public static int removeElement(int[] nums, int val) {
        int diffrent = 0;
        for (int i = 0; i < nums.length; i++) {
            if (nums[i] != val) {
                nums[diffrent] = nums[i];
                diffrent++;
            }
        }
        for (int i = 0; i < nums.length; i++) {
            System.out.print(nums[i]);

        }
        System.out.println();
        return diffrent;
    }

    public static String longestCommonPrefix(String[] strs) {
        if (strs == null || strs.length == 0) {
            return "";
        }
        String prefix = strs[0];
        for (int i = 1; i < strs.length; i++) {
            while (!strs[i].startsWith(prefix)) { //fmin.startsWith(fminer)
                prefix = prefix.substring(0, prefix.length() - 1);
                if (prefix.isEmpty()) {
                    return "";
                }
            }
        }
        return prefix;
    }

    public static int searchInsert(int[] nums, int target) {
        int mid = nums.length / 2;
        int step = nums.length / 2;

        if (nums.length == 0) return 0;

        while (step > 0) {
            if (nums[mid] == target) {
                return mid;
            } else if (nums[mid] < target) {
                mid += step;
            } else {
                mid -= step;
            }

            step /= 2;
        }

        if (nums[mid] < target) return mid + 1;
        return mid;
    }

    public static int lengthOfLastWord(String s) {

        s = s.trim();
        int length = 0;
        for (int i = s.length() - 1; i >= 0; i--) {
            if (s.charAt(i) != ' ') {
                length++;
            } else if (s.charAt(i) == ' ') {
                break;
            }
        }
        return length;
    }


    public static int[] plusOne(int[] digits) {
        for (int i = digits.length - 1; i >= 0; i--) {
            if (digits[i] < 9) {
                digits[i]++;
                return digits;
            }
            digits[i] = 0;
        }
        int[] result = new int[digits.length + 1];
        result[0] = 1;
        return result;
    }

    public void merge(int[] nums1, int m, int[] nums2, int n) {
        int p1 = m - 1;
        int p2 = n - 1;
        int p = m + n - 1;

        int[] result = new int[m + n];

       for(int k=0;k<m+n;k++){
           for(int i =0; i < m; i++){
               result[k] = nums1[i];
           }

       }
    }

}



