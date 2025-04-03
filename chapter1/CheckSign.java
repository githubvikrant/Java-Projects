import java.util.Scanner;

public class CheckSign {
    public static void main(String[] args) {
        Scanner input = new Scanner(System.in);
        System.out.print("Enter an integer:");
        int num = input.nextInt();

        if(num == 0){
           System.out.println("number is 0");
        }
        else if(num < 0){
             System.out.println(num +" number is negative");
        }
        else System.out.println(num + " number is positive");

    input.close();

    }
}
