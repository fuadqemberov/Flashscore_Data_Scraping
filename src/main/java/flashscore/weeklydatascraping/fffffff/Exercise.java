package flashscore.weeklydatascraping.fffffff;

public class Exercise {
    public static int searchInArray(int[] intArray, int valueToSearch) {
        for(int i=0;i<intArray.length;i++){
            if(intArray[i]==valueToSearch){
                return i;
            }
          }
        return -1;
    }

    public static void main(String[] args) {
        int[] intArray = {1,2,3,4,5,6};
        System.out.println(searchInArray(intArray, 6)); // 5
    }

}