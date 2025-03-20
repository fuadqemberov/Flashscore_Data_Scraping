package flashscore.weeklydatascraping.fffffff;

import java.util.Arrays;

public class SortingAlgorithms {

    public static void main(String[] args) {
        int[] array = new int[5];
        array[0] = 7;
        array[1] = 5;
        array[2] = 1;
        array[3] = 3;
        array[4] = 2;

       //bubble(array);
       // insertion(array);
       // selection(array);
        for(int a : array){
            System.out.println(a);
        }
    }

    public static void bubble(int[] array){
        for(int j=0;j<array.length-1;j++){
            for(int k=0;k<array.length-j-1;k++){
                if(array[k]>array[k+1]){
                    int temp = array[k+1];
                    array[k+1] = array[k];
                    array[k] =temp;
                }
            }
        }
    }

    public static void insertion(int[] array){
        for(int j=1;j<array.length;j++){
            for(int k=j;k>0;k--){
                if(array[k]< array[k-1]){
                    int temp = array[k-1];
                    array[k-1] = array[k];
                    array[k] =temp;
                }
            }
        }
    }

    public static void selection(int[] array){
        for(int i =0;i<array.length-1;i++){
            int enkicik= i;
            for(int j=i;j<array.length;j++){
                if(array[enkicik] > array[j]){
                    enkicik = j;
                }
            }

            int temp = array[enkicik]; // array[2]
            array[enkicik] = array[i];
            array[i] = temp;
        }

    }



 }
