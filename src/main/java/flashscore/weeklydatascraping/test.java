package flashscore.weeklydatascraping;

public class test {
    public static int[] findTopTwoScores(int[] array) {
        int firstHighest = 0;
        int secondHighest = 0;

       for(int value : array){
           if(value > firstHighest){
               secondHighest = firstHighest;
               firstHighest = value;
           } else if (value < firstHighest && value > secondHighest) {
               secondHighest = value;
           }
       }


        return new int[]{firstHighest,secondHighest};
    }

    public static void main(String[] args) {
        int[] myArray = {84,90,85,84,1,2,0};
        int[] result = findTopTwoScores(myArray);
        for (int i = 0; i < result.length; i++) {
            System.out.println(result[i]);
        }
    }
}
