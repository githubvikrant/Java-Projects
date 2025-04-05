import java.util.Scanner;

public class Test {
    public static void main(String[] args) {
        System.out.print("Enter a character of alphabet : ");
        Scanner input = new Scanner(System.in);
        char alphabet = input.next().charAt(0);

        char ch = Character.toLowerCase(alphabet);

        if (!Character.isLetter(ch)) {
            System.out.println("Invalid input. Please enter an alphabet.");
        } else if (ch == 'a' || ch == 'e' || ch == 'i' || ch == 'o' || ch == 'u') {
            System.out.println(ch + " is a vowel.");
        } else {
            System.out.println(ch + " is a consonant.");
        }
        
        input.close();
    }

}
