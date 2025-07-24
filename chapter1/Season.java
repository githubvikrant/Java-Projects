import java.util.Scanner;

public class Season {
    public static void main(String[] args) {
        Scanner input = new Scanner(System.in);
        
        try {
            System.out.print("Enter an integer number between 1 to 4: ");
            
            if (input.hasNextInt()) {  // Check if input is an integer
                int season = input.nextInt();
                
                switch (season) {
                    case 1:
                        System.out.println("Spring");
                        break;
                    case 2:
                        System.out.println("Summer");
                        break;
                    case 3:
                        System.out.println("Autumn");
                        break;
                    case 4:
                        System.out.println("Winter");
                        break;
                    default:
                        System.out.println("Invalid Season");
                }
            } else {
                System.out.println("Invalid input! Please enter a number.");
            }
        } finally {
            input.close(); // Ensures scanner is closed properly
        }
    }
}
