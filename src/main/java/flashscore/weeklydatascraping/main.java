package flashscore.weeklydatascraping;

import java.util.Arrays;

public class main {
    public static void main(String[] args) {
     int[] nums1 = new int[]{1,2,3,0,0,0};
     int[] nums2 = new int[]{2,5,6};

     merge(nums1,3,nums2,3);
    }

    public static void merge(int[] nums1, int m, int[] nums2, int n) {
        int[] num3 = new int[m+n];
        int index = 0;
        for(int a=0;a<m;a++){
            num3[index] = nums1[a];
            index++;
        }

        for(int b=0;b<n;b++){
            num3[index] = nums2[b];
            index++;
        }

        int[] array = Arrays.stream(num3).sorted().toArray();
        System.out.print("[");
        for(int o : array){
            System.out.print(" "+o+" ");
        }
        System.out.print("]");

    }
}
